package com.wshake.api.controller;

import com.wshake.api.dto.CreateGitSkillSourceRequest;
import com.wshake.api.dto.CreateSkillDraftRequest;
import com.wshake.api.dto.InstallSkillRequest;
import com.wshake.api.dto.RejectSkillDraftRequest;
import com.wshake.api.dto.SyncGitSkillSourceRequest;
import com.wshake.api.dto.UpdateGitSkillSourceRequest;
import com.wshake.api.dto.UpdateSkillDraftRequest;
import com.wshake.api.vo.BindableSkillVO;
import com.wshake.api.vo.GitSkillPreviewVO;
import com.wshake.api.vo.GitSkillSourceVO;
import com.wshake.api.vo.GitSkillSyncResultVO;
import com.wshake.api.vo.SkillDraftVO;
import com.wshake.api.vo.SkillInstallVO;
import com.wshake.api.vo.SkillMarketVO;
import com.wshake.api.vo.SkillReleaseVO;
import com.wshake.common.request.RequestContext;
import com.wshake.common.result.Result;
import com.wshake.service.agent.SkillControlModels.CreateSkillDraftCommand;
import com.wshake.service.agent.SkillControlModels.UpdateSkillDraftCommand;
import com.wshake.service.agent.SkillControlService;
import com.wshake.service.agent.GitSkillSourceModels.CreateCommand;
import com.wshake.service.agent.GitSkillSourceModels.UpdateCommand;
import com.wshake.service.agent.GitSkillSourceService;
import com.wshake.service.repository.SysUserRoleRepository;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent Skill", description = "Skill 草稿、审核发布、市场与安装")
@RestController
@RequestMapping("/api/agent/skills")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AgentSkillController {

    private final SkillControlService skillControlService;
    private final Converter converter;
    private final GitSkillSourceService gitSkillSourceService;
    private final SysUserRoleRepository userRoleRepository;

    @PostMapping("/drafts")
    @Operation(summary = "创建 Skill 草稿")
    public Result<SkillDraftVO> createDraft(@Valid @RequestBody CreateSkillDraftRequest request) {
        return Result.ok(converter.convert(
                skillControlService.createDraft(new CreateSkillDraftCommand(
                        RequestContext.requireUserId(),
                        request.getName(),
                        request.getDescription(),
                        request.getSkillContent(),
                        request.getVisibility(),
                        request.getResources(),
                        request.getBasedOnReleaseId(),
                        request.getRemark())),
                SkillDraftVO.class));
    }

    @GetMapping("/drafts")
    @Operation(summary = "列出当前用户的 Skill 草稿")
    public Result<List<SkillDraftVO>> listDrafts() {
        return Result.ok(
                converter.convert(skillControlService.listDrafts(RequestContext.requireUserId()), SkillDraftVO.class));
    }

    @GetMapping("/drafts/{id}")
    @Operation(summary = "获取 Skill 草稿")
    public Result<SkillDraftVO> getDraft(@PathVariable Long id) {
        return Result.ok(converter.convert(
                skillControlService.getDraft(id, RequestContext.requireUserId()), SkillDraftVO.class));
    }

    @PutMapping("/drafts/{id}")
    @Operation(summary = "更新 Skill 草稿")
    public Result<SkillDraftVO> updateDraft(
            @PathVariable Long id, @RequestBody(required = false) UpdateSkillDraftRequest body) {
        UpdateSkillDraftRequest request = body == null ? new UpdateSkillDraftRequest() : body;
        return Result.ok(converter.convert(
                skillControlService.updateDraft(new UpdateSkillDraftCommand(
                        id,
                        RequestContext.requireUserId(),
                        request.getDescription(),
                        request.isDescriptionPresent(),
                        request.getSkillContent(),
                        request.isSkillContentPresent(),
                        request.getResources(),
                        request.isResourcesPresent(),
                        request.getRemark(),
                        request.isRemarkPresent())),
                SkillDraftVO.class));
    }

    @PostMapping("/drafts/{id}/submit")
    @Operation(summary = "提交 Skill 草稿审核")
    public Result<SkillDraftVO> submit(@PathVariable Long id) {
        return Result.ok(
                converter.convert(skillControlService.submit(id, RequestContext.requireUserId()), SkillDraftVO.class));
    }

    @PostMapping("/drafts/{id}/withdraw")
    @Operation(summary = "撤回 Skill 审核")
    public Result<SkillDraftVO> withdraw(@PathVariable Long id) {
        return Result.ok(converter.convert(
                skillControlService.withdraw(id, RequestContext.requireUserId()), SkillDraftVO.class));
    }

    @PostMapping("/drafts/{id}/approve")
    @Operation(summary = "审核通过并发布 Skill Release")
    public Result<SkillReleaseVO> approve(@PathVariable Long id) {
        return Result.ok(converter.convert(
                skillControlService.approve(id, RequestContext.requireUserId()), SkillReleaseVO.class));
    }

    @PostMapping("/drafts/{id}/reject")
    @Operation(summary = "驳回 Skill 草稿")
    public Result<SkillDraftVO> reject(
            @PathVariable Long id, @RequestBody(required = false) RejectSkillDraftRequest body) {
        String comment = body == null ? "" : body.getComment();
        return Result.ok(converter.convert(
                skillControlService.reject(id, comment, RequestContext.requireUserId()), SkillDraftVO.class));
    }

    @GetMapping("/market")
    @Operation(summary = "列出技能市场当前已发布 Skill")
    public Result<List<SkillMarketVO>> listMarket() {
        return Result.ok(converter.convert(skillControlService.listMarket(), SkillMarketVO.class));
    }

    @DeleteMapping("/market/{name}")
    @Operation(summary = "下架市场 Skill 当前行")
    public Result<Void> unlist(@PathVariable String name) {
        skillControlService.unlistMarket(name, RequestContext.requireUserId());
        return Result.ok(null);
    }

    @PostMapping("/install")
    @Operation(summary = "安装市场 Skill")
    public Result<SkillInstallVO> install(@Valid @RequestBody InstallSkillRequest request) {
        return Result.ok(converter.convert(
                skillControlService.install(RequestContext.requireUserId(), request.getName()), SkillInstallVO.class));
    }

    @DeleteMapping("/install/{id}")
    @Operation(summary = "卸载 Skill")
    public Result<Void> uninstall(@PathVariable Long id) {
        skillControlService.uninstall(id, RequestContext.requireUserId());
        return Result.ok(null);
    }

    @GetMapping("/bindable")
    @Operation(summary = "列出当前用户可绑定的 Skill Release")
    public Result<List<BindableSkillVO>> listBindable() {
        return Result.ok(converter.convert(
                skillControlService.listBindable(RequestContext.requireUserId()), BindableSkillVO.class));
    }

    @GetMapping("/releases/{id}")
    @Operation(summary = "获取 Skill Release")
    public Result<SkillReleaseVO> getRelease(@PathVariable Long id) {
        return Result.ok(converter.convert(skillControlService.getRelease(id), SkillReleaseVO.class));
    }

    @PostMapping("/releases/{id}/deprecate")
    @Operation(summary = "弃用 Skill Release")
    public Result<SkillReleaseVO> deprecate(@PathVariable Long id) {
        return Result.ok(converter.convert(
                skillControlService.deprecate(id, RequestContext.requireUserId()), SkillReleaseVO.class));
    }
    @PostMapping("/git-sources")
    @Operation(summary = "创建 Git Skill 来源")
    public Result<GitSkillSourceVO> createGitSource(@Valid @RequestBody CreateGitSkillSourceRequest request) {
        Long userId = RequestContext.requireUserId();
        return Result.ok(toGitSourceVO(gitSkillSourceService.create(new CreateCommand(
                userId, isAdministrator(userId), request.getScope(), request.getUrl(), request.getRef(), request.getSubdirectory(), request.getSecretRef()))));
    }

    @GetMapping("/git-sources")
    @Operation(summary = "列出可管理的 Git Skill 来源")
    public Result<List<GitSkillSourceVO>> listGitSources() {
        Long userId = RequestContext.requireUserId();
        return Result.ok(gitSkillSourceService.list(userId, isAdministrator(userId)).stream().map(this::toGitSourceVO).toList());
    }

    @GetMapping("/git-sources/{id}")
    @Operation(summary = "获取 Git Skill 来源")
    public Result<GitSkillSourceVO> getGitSource(@PathVariable Long id) {
        Long userId = RequestContext.requireUserId();
        return Result.ok(toGitSourceVO(gitSkillSourceService.get(id, userId, isAdministrator(userId))));
    }

    @PutMapping("/git-sources/{id}")
    @Operation(summary = "更新 Git Skill 来源")
    public Result<GitSkillSourceVO> updateGitSource(
            @PathVariable Long id, @RequestBody(required = false) UpdateGitSkillSourceRequest body) {
        Long userId = RequestContext.requireUserId();
        UpdateGitSkillSourceRequest request = body == null ? new UpdateGitSkillSourceRequest() : body;
        return Result.ok(toGitSourceVO(gitSkillSourceService.update(new UpdateCommand(
                id, userId, isAdministrator(userId), request.getUrl(), request.isUrlPresent(), request.getRef(), request.isRefPresent(), request.getSubdirectory(), request.isSubdirectoryPresent(), request.getSecretRef(), request.isSecretRefPresent()))));
    }

    @DeleteMapping("/git-sources/{id}")
    @Operation(summary = "删除 Git Skill 来源")
    public Result<Void> deleteGitSource(@PathVariable Long id) {
        Long userId = RequestContext.requireUserId();
        gitSkillSourceService.delete(id, userId, isAdministrator(userId));
        return Result.ok(null);
    }

    @PostMapping("/git-sources/{id}/preview")
    @Operation(summary = "预览 Git 来源中的 Skill 包")
    public Result<GitSkillPreviewVO> previewGitSource(@PathVariable Long id) {
        Long userId = RequestContext.requireUserId();
        var view = gitSkillSourceService.preview(id, userId, isAdministrator(userId));
        return Result.ok(new GitSkillPreviewVO(view.sourceId(), view.commitSha(), view.skills().stream()
                .map(item -> new GitSkillPreviewVO.Item(item.skillPath(), item.name(), item.description(), item.contentHash(), item.resourceCount(), item.totalBytes()))
                .toList()));
    }

    @PostMapping("/git-sources/{id}/sync")
    @Operation(summary = "将 Git Skill 包同步为草稿")
    public Result<GitSkillSyncResultVO> syncGitSource(
            @PathVariable Long id, @Valid @RequestBody SyncGitSkillSourceRequest request) {
        Long userId = RequestContext.requireUserId();
        var view = gitSkillSourceService.sync(id, request.getExpectedCommitSha(), request.getSkillPaths(), userId, isAdministrator(userId));
        return Result.ok(new GitSkillSyncResultVO(view.sourceId(), view.commitSha(), view.results().stream()
                .map(item -> new GitSkillSyncResultVO.Item(item.skillPath(), item.name(), item.status(), item.draftId(), item.message()))
                .toList()));
    }

    private boolean isAdministrator(Long userId) {
        return userRoleRepository.userHasRootRole(userId);
    }

    private GitSkillSourceVO toGitSourceVO(com.wshake.service.agent.GitSkillSourceModels.SourceView view) {
        return new GitSkillSourceVO(view.id(), view.scope(), view.ownerUserId(), view.url(), view.ref(), view.subdirectory(), view.hasSecretRef(), view.lastCommitSha(), view.lastSyncedAt(), view.status(), view.lastError(), view.createdAt(), view.updatedAt());
    }
}
