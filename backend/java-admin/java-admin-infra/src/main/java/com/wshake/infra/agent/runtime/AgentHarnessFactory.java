package com.wshake.infra.agent.runtime;

import com.wshake.infra.agent.formatter.XaiChatFormatter;
import com.wshake.infra.agent.tool.*;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import com.wshake.service.repository.AgentSkillReleaseResourceRepository;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模型客户端与 HarnessAgent 装配工厂。
 *
 * <p>对齐 docs/agent-module-architecture.md §5.6 运行面安全开关：显式禁用文件系统工具、
 * shell、memory tools/hooks、workspace context、@path 展开、subagents、默认 workspace
 * skills。平台 Java 工具仅按 {@code permission_policy.allowedTools} 白名单注册。
 *
 * <p>Skill 装配：保留 dynamic skill 加载机制（DynamicSkillMiddleware），仓库是平台只读
 * {@link BindingSkillRepository}（绑定冻结 Release 快照），agent 无创建/写入能力。
 * MCP 装配：绑定 MCP 经 {@link BindingMcpAssembler} 构建客户端注册握手；权限策略对
 * 「服务端装配的工具集」（绑定 MCP 工具 + 白名单 Java 工具）显式放行，其余默认 ASK。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentHarnessFactory {

    private final AgentStateStoreProvider stateStoreProvider;
    private final AgentRuntimeProperties properties;
    private final AgentSkillReleaseRepository skillReleaseRepository;
    private final AgentSkillReleaseResourceRepository skillResourceRepository;
    private final BindingMcpAssembler mcpAssembler;
    private final ViewImageTool viewImageTool;
    private final GenerateImageTool generateImageTool;
    private final EditImageTool editImageTool;
    private final MultiEditImageTool multiEditImageTool;

    /** 平台白名单工具注册表：name → 工具实例。view_image 为常驻只读工具，不经白名单门控。 */
    private static final List<io.agentscope.core.tool.AgentTool> PLATFORM_TOOLS = List.of(new PlatformTimeTool());

    /**
     * 按运行计划构建 HarnessAgent。
     *
     * @param plan 运行计划（含冻结模型/密钥/提示词/白名单 + Skill/MCP 装配）
     * @return HarnessAgent（调用方负责 close）
     */
    public HarnessAgent create(AgentRunPlan plan) {
        Toolkit toolkit = new Toolkit();
        registerPlatformTools(toolkit, plan.allowedTools());
        toolkit.registerTool(viewImageTool);
        toolkit.registerTool(generateImageTool);
        toolkit.registerTool(editImageTool);
        toolkit.registerTool(multiEditImageTool);
        // 绑定 MCP：握手 + 固定工具名单;失败即拒绝首启
        registerMcpClients(toolkit, plan);
        AgentStateStore stateStore = stateStoreProvider.stateStore();
        Path workspace =
                Path.of(System.getProperty("java.io.tmpdir"), "agent-workspace", "session-" + plan.sessionId());
        try {
            java.nio.file.Files.createDirectories(workspace);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法创建 agent workspace: " + workspace, e);
        }
        String sysPrompt = nz(plan.systemPrompt());
        if (!sysPrompt.contains("generate_image")) {
            sysPrompt = sysPrompt
                    + "\n\n生图指引（通用，不绑定具体对象）：\n"
                    + "- 首次出图用 generate_image；已有一张图后，任何\"改一下/去掉.../换成.../按这张图改\"都视为增量编辑，优先走 edit_image（单图）或 multi_edit_image（多图），不要回退到 generate_image 重绘整图。\n"
                    + "- 改图必须携带参考图：若上下文没有 image_url，先用 view_image 读取上一张生成图获得可访问链接，再调 edit_image；有多张参考则用 multi_edit_image。\n"
                    + "- 改图 prompt 必须显式约束\"仅对指定元素做最小改动，其余构图/主体/文字/色板/布局/比例保持不变\"，并保持与原图一致的 aspect_ratio/resolution；不要新增或移除未提及的元素。";
        }
        var builder = HarnessAgent.builder()
                .name(nz(plan.agentName()))
                .agentId("platform-agent")
                .description("Platform Agent")
                .sysPrompt(sysPrompt)
                .model(OpenAIChatModel.builder()
                        .apiKey(plan.plainSecret())
                        .baseUrl(plan.baseUrl())
                        .modelName(plan.modelName())
                        .endpointPath(plan.endpointPath())
                        .formatter(formatterFor(plan))
                        .stream(true)
                        .build())
                .toolkit(toolkit)
                .stateStore(stateStore)
                .maxIters(properties.getMaxIters())
                // 权限策略：服务端装配的工具集（MCP + 白名单 Java）显式放行
                .permissionContext(buildPermissionContext(toolkit))
                // docs §5.6 安全开关
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableWorkspaceContext()
                .disableAtPathExpansion()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableDefaultWorkspaceSkills()
                // 最小工作区隔离（禁用 workspace 工具后仅占位;按 session 分目录,避免跨会话串扰）
                .workspace(workspace);
        // 绑定 Skill（只读仓库 → DynamicSkillMiddleware 加载冻结快照）
        BindingSkillRepository skillRepo = BindingSkillRepository.assemble(
                plan.skills(), skillReleaseRepository, skillResourceRepository, "session-" + plan.sessionId());
        builder.skillRepository(skillRepo);
        return builder.build();
    }

    private OpenAIChatFormatter formatterFor(AgentRunPlan plan) {
        return plan.baseUrl().contains("api.x.ai") ? new XaiChatFormatter() : new OpenAIChatFormatter();
    }

    /** 权限上下文：放行 toolkit 中已装配的全部工具（服务端装配即授权），默认 ASK。
     *
     * <p>注意：{@code ruleContent} 必须为 null。{@code ToolBase.matchRule} 默认实现仅在
     * {@code ruleContent == null} 时返回 true；非空字符串会导致 allow 规则永远不命中，
     * MCP 工具全部落入 ASK → 会话卡死在 HITL ASKING 态（下次运行报
     * "Agent is paused for human-in-the-loop confirmation"）。 */
    private PermissionContextState buildPermissionContext(Toolkit toolkit) {
        var ctxBuilder = PermissionContextState.builder().mode(PermissionMode.DEFAULT);
        for (String toolName : toolkit.getToolNames()) {
            ctxBuilder.addAllowRule(toolName, new PermissionRule(toolName, null, PermissionBehavior.ALLOW, "platform"));
        }
        for (String toolName : List.of("wait_async_results", "load_skill_through_path", "read_skill")) {
            ctxBuilder.addAllowRule(toolName, new PermissionRule(toolName, null, PermissionBehavior.ALLOW, "platform"));
        }
        return ctxBuilder.build();
    }

    /** 按白名单注册平台 Java 工具；白名单为空时不注册任何平台工具。 */
    private void registerPlatformTools(Toolkit toolkit, List<String> allowedTools) {
        if (allowedTools == null) {
            return;
        }
        for (String name : allowedTools) {
            PLATFORM_TOOLS.stream()
                    .filter(t -> t.getName().equals(name))
                    .findFirst()
                    .ifPresent(toolkit::registerTool);
        }
    }

    /** 注册绑定 MCP 客户端：连接 Release 冻结配置 + 首启握手（未知即拒绝）。OAuth 用运行用户 token。 */
    private void registerMcpClients(Toolkit toolkit, AgentRunPlan plan) {
        List<io.agentscope.core.tool.mcp.McpClientWrapper> wrappers = new ArrayList<>();
        try {
            for (var entry : plan.mcps()) {
                wrappers.add(mcpAssembler.assembleOne(entry, plan.ownerUserId()));
            }
            for (var wrapper : wrappers) {
                // 握手 + 固定工具名单;失败抛错由调用方转首启拒绝
                toolkit.registerMcpClient(wrapper).block(Duration.ofSeconds(15));
            }
        } catch (RuntimeException e) {
            for (var wrapper : wrappers) {
                try {
                    wrapper.close();
                } catch (Exception ignore) {
                    // 已失败的装配,close 忽略
                }
            }
            throw e;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
