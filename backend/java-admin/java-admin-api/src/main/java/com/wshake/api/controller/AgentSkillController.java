package com.wshake.api.controller;

import com.wshake.api.dto.SkillDtos.CreateSkillDraftRequest;
import com.wshake.api.dto.SkillDtos.GitSourceRequest;
import com.wshake.api.dto.SkillDtos.GitSyncRequest;
import com.wshake.api.dto.SkillDtos.ReviewRequest;
import com.wshake.api.dto.SkillDtos.SetSkillResourcesRequest;
import com.wshake.api.dto.SkillDtos.UpdateSkillDraftRequest;
import com.wshake.common.result.PageData;
import com.wshake.common.result.Result;
import com.wshake.service.agent.skill.GitSkillSourceService;
import com.wshake.service.agent.skill.SkillControlService;
import com.wshake.service.agent.skill.SkillManageModels.CreateSkillDraftCommand;
import com.wshake.service.agent.skill.SkillManageModels.GitPreviewResult;
import com.wshake.service.agent.skill.SkillManageModels.GitSourceCommand;
import com.wshake.service.agent.skill.SkillManageModels.GitSyncCommand;
import com.wshake.service.agent.skill.SkillManageModels.GitSyncResult;
import com.wshake.service.agent.skill.SkillManageModels.ReviewCommand;
import com.wshake.service.agent.skill.SkillManageModels.SkillDraftListQuery;
import com.wshake.service.agent.skill.SkillManageModels.SkillDraftResourceView;
import com.wshake.service.agent.skill.SkillManageModels.SkillDraftView;
import com.wshake.service.agent.skill.SkillManageModels.SkillReleaseView;
import com.wshake.service.agent.skill.SkillManageModels.SkillResourceCommand;
import com.wshake.service.agent.skill.SkillManageModels.UpdateSkillDraftCommand;
import com.wshake.service.entity.AgentSkillGitSource;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill 管理 / 市场（路径 {@code /api/agent/skill/*}）。
 *
 * @author wshake
 */
@Tag(name = "Skill 管理", description = "草稿/审核/Release/市场派生/Git 导入")
@RestController
@RequestMapping("/api/agent/skill")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AgentSkillController {

    private final SkillControlService skillControlService;
    private final GitSkillSourceService gitSkillSourceService;

    @GetMapping("/list")
    @Operation(summary = "Skill 草稿分页")
    public Result<PageData<SkillDraftView>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status) {
        return Result.ok(skillControlService.pageDrafts(
                SkillDraftListQuery.of(page, pageSize, ownerUserId, name, visibility, status)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Skill 草稿详情")
    public Result<SkillDraftView> detail(@PathVariable Long id) {
        return Result.ok(skillControlService.getDraft(id));
    }

    @PostMapping
    @Operation(summary = "创建 Skill 草稿")
    public Result<SkillDraftView> create(@Valid @RequestBody CreateSkillDraftRequest req) {
        return Result.ok(skillControlService.createDraft(new CreateSkillDraftCommand(
                req.getName(),
                req.getSkillContent(),
                req.getVisibility(),
                req.getOwnerUserId(),
                req.getBasedOnReleaseId(),
                req.getRemark(),
                req.getIsEnabled())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 Skill 草稿")
    public Result<SkillDraftView> update(@PathVariable Long id, @RequestBody UpdateSkillDraftRequest req) {
        return Result.ok(skillControlService.updateDraft(new UpdateSkillDraftCommand(
                id,
                req.getName(),
                req.getSkillContent(),
                req.getVisibility(),
                req.getOwnerUserId(),
                req.getRemark(),
                req.getIsEnabled())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删 Skill 草稿")
    public Result<SkillDraftView> delete(@PathVariable Long id) {
        return Result.ok(skillControlService.softDelete(id));
    }

    @GetMapping("/{id}/resources")
    @Operation(summary = "查看 Skill 草稿资源")
    public Result<List<SkillDraftResourceView>> resources(@PathVariable Long id) {
        return Result.ok(skillControlService.getResources(id));
    }

    @PutMapping("/{id}/resources")
    @Operation(summary = "设置 Skill 草稿资源")
    public Result<List<SkillDraftResourceView>> setResources(
            @PathVariable Long id, @RequestBody SetSkillResourcesRequest req) {
        List<SkillResourceCommand> resources = req.getResources() == null
                ? List.of()
                : req.getResources().stream()
                        .map(r -> new SkillResourceCommand(r.getResourcePath(), r.getContent()))
                        .toList();
        return Result.ok(skillControlService.setResources(id, resources));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "提交 Skill 审核")
    public Result<SkillDraftView> submit(@PathVariable Long id) {
        return Result.ok(skillControlService.submit(id));
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "审核 Skill 草稿")
    public Result<SkillReleaseView> review(@PathVariable Long id, @RequestBody ReviewRequest req) {
        return Result.ok(skillControlService.review(id, new ReviewCommand(req.getAction(), req.getComment())));
    }

    @GetMapping("/release/list")
    @Operation(summary = "Skill Release 列表")
    public Result<PageData<SkillReleaseView>> releaseList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status) {
        int p = page == null || page < 1 ? 1 : page;
        int s = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        return Result.ok(skillControlService.pageReleases(p, s, ownerUserId, name, visibility, status));
    }

    @GetMapping("/release/{id}")
    @Operation(summary = "Skill Release 详情")
    public Result<SkillReleaseView> releaseDetail(@PathVariable Long id) {
        return Result.ok(skillControlService.getRelease(id));
    }

    @PostMapping("/release/{id}/deprecate")
    @Operation(summary = "弃用 Skill Release")
    public Result<SkillReleaseView> deprecate(@PathVariable Long id) {
        return Result.ok(skillControlService.deprecate(id));
    }

    @GetMapping("/market")
    @Operation(summary = "Skill 市场列表")
    public Result<List<SkillReleaseView>> market() {
        return Result.ok(skillControlService.market());
    }

    // ---------- Git ----------

    @GetMapping("/git/source/list")
    @Operation(summary = "Git 来源列表")
    public Result<List<AgentSkillGitSource>> gitSources() {
        return Result.ok(gitSkillSourceService.listSources());
    }

    @PostMapping("/git/source")
    @Operation(summary = "创建 Git 来源")
    public Result<AgentSkillGitSource> createGitSource(@Valid @RequestBody GitSourceRequest req) {
        return Result.ok(gitSkillSourceService.createSource(new GitSourceCommand(
                req.getScope(),
                req.getOwnerUserId(),
                req.getUrl(),
                req.getRef(),
                req.getSubdirectory(),
                req.getEncryptedSecret(),
                req.getRemark(),
                req.getIsEnabled())));
    }

    @DeleteMapping("/git/source/{id}")
    @Operation(summary = "删除 Git 来源")
    public Result<AgentSkillGitSource> deleteGitSource(@PathVariable Long id) {
        return Result.ok(gitSkillSourceService.softDeleteSource(id));
    }

    @PostMapping("/git/preview")
    @Operation(summary = "Git 导入预览")
    public Result<GitPreviewResult> gitPreview(@RequestBody GitSyncRequest req) {
        return Result.ok(gitSkillSourceService.preview(req.getSourceId()));
    }

    @PostMapping("/git/sync")
    @Operation(summary = "Git 导入同步")
    public Result<GitSyncResult> gitSync(@RequestBody GitSyncRequest req) {
        return Result.ok(gitSkillSourceService.sync(
                new GitSyncCommand(req.getSourceId(), req.getExpectedCommitSha(), req.getSkillPaths())));
    }
}
