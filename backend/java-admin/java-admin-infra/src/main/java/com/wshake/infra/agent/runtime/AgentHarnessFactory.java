package com.wshake.infra.agent.runtime;

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

    /** 平台白名单工具注册表：name → 工具实例。 */
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
        var builder = HarnessAgent.builder()
                .name(nz(plan.agentName()))
                .agentId("platform-agent")
                .description("Platform Agent")
                .sysPrompt(nz(plan.systemPrompt()))
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

    /** 权限上下文：放行 toolkit 中已装配的全部工具（服务端装配即授权），默认 ASK。 */
    private PermissionContextState buildPermissionContext(Toolkit toolkit) {
        var ctxBuilder = PermissionContextState.builder().mode(PermissionMode.DEFAULT);
        for (String toolName : toolkit.getToolNames()) {
            ctxBuilder.addAllowRule(
                    toolName,
                    new PermissionRule(toolName, "platform-assembled tool", PermissionBehavior.ALLOW, "platform"));
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

    /** 注册绑定 MCP 客户端：连接 Release 冻结配置 + 首启握手（未知即拒绝）。 */
    private void registerMcpClients(Toolkit toolkit, AgentRunPlan plan) {
        List<io.agentscope.core.tool.mcp.McpClientWrapper> wrappers = new ArrayList<>();
        try {
            for (var entry : plan.mcps()) {
                wrappers.add(mcpAssembler.assembleOne(entry));
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
