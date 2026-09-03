package com.wshake.api.controller;

import com.wshake.api.dto.CreateGitSourceRequest;
import com.wshake.api.dto.UpdateGitSourceRequest;
import com.wshake.api.vo.GitPreviewResultVO;
import com.wshake.api.vo.GitSourceVO;
import com.wshake.api.vo.GitSyncResultVO;
import com.wshake.common.result.Result;
import com.wshake.service.git.GitSkillSourceService;
import com.wshake.service.git.GitSkillSourceService.CreateGitSourceCommand;
import com.wshake.service.git.GitSkillSourceService.GitPreviewResult;
import com.wshake.service.git.GitSkillSourceService.GitSourceView;
import com.wshake.service.git.GitSkillSourceService.GitSyncResult;
import com.wshake.service.git.GitSkillSourceService.SkillPackageScan;
import com.wshake.service.git.GitSkillSourceService.SkillResourceData;
import com.wshake.service.git.GitSkillSourceService.UpdateGitSourceCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
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
 * Skill Git 受控导入（路径 {@code /api/system/skill/git-source/*}）。
 *
 * @author wshake
 */
@Tag(name = "Skill Git 来源", description = "Git 受控导入来源 CRUD + preview/sync")
@RestController
@RequestMapping("/api/system/skill/git-source")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class GitSkillSourceController {

    private final GitSkillSourceService gitSkillSourceService;

    @GetMapping("/list")
    @Operation(summary = "Git 来源列表")
    public Result<List<GitSourceVO>> list(
            @RequestParam(required = false) String scope, @RequestParam(required = false) Long ownerUserId) {
        List<GitSourceView> views = gitSkillSourceService.listSources(scope, ownerUserId);
        return Result.ok(toSourceVOs(views));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Git 来源详情")
    public Result<GitSourceVO> detail(@PathVariable Long id) {
        return Result.ok(toSourceVO(gitSkillSourceService.getSource(id)));
    }

    @PostMapping
    @Operation(summary = "创建 Git 来源")
    public Result<GitSourceVO> create(@Valid @RequestBody CreateGitSourceRequest req) {
        CreateGitSourceCommand cmd = new CreateGitSourceCommand(
                req.getScope(),
                req.getOwnerUserId(),
                req.getUrl(),
                req.getRef(),
                req.getSubdirectory(),
                req.getPlainSecret(),
                req.getRemark());
        return Result.ok(toSourceVO(gitSkillSourceService.createSource(cmd)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 Git 来源")
    public Result<GitSourceVO> update(@PathVariable Long id, @Valid @RequestBody UpdateGitSourceRequest req) {
        UpdateGitSourceCommand cmd =
                new UpdateGitSourceCommand(req.getRef(), req.getSubdirectory(), req.getPlainSecret(), req.getRemark());
        return Result.ok(toSourceVO(gitSkillSourceService.updateSource(id, cmd)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删 Git 来源")
    public Result<Void> delete(@PathVariable Long id) {
        gitSkillSourceService.deleteSource(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/preview")
    @Operation(summary = "预览(解析 ref → commit_sha + 扫描包,不写草稿)")
    public Result<GitPreviewResultVO> preview(@PathVariable Long id) {
        GitPreviewResult result = gitSkillSourceService.preview(id);
        return Result.ok(toPreviewVO(result));
    }

    @PostMapping("/{id}/sync")
    @Operation(summary = "同步(expectedCommitSha 校验后逐包幂等同步)")
    public Result<GitSyncResultVO> sync(@PathVariable Long id, @RequestBody SyncRequest req) {
        GitSyncResult result = gitSkillSourceService.sync(id, req.commitSha());
        return Result.ok(toSyncVO(result));
    }

    /** 同步请求体。 */
    public record SyncRequest(String commitSha) {}

    private static GitSourceVO toSourceVO(GitSourceView v) {
        return new GitSourceVO(
                v.id(),
                v.scope(),
                v.ownerUserId(),
                v.url(),
                v.ref(),
                v.subdirectory(),
                v.lastCommitSha(),
                v.lastSyncedAt(),
                v.status(),
                v.lastError(),
                v.remark(),
                v.isEnabled(),
                v.deletedAt(),
                v.createdAt(),
                v.updatedAt(),
                v.createdBy(),
                v.updatedBy());
    }

    private static List<GitSourceVO> toSourceVOs(List<GitSourceView> views) {
        List<GitSourceVO> out = new ArrayList<>();
        for (GitSourceView v : views) {
            out.add(toSourceVO(v));
        }
        return out;
    }

    private static GitPreviewResultVO toPreviewVO(GitPreviewResult r) {
        List<GitPreviewResultVO.PackageVO> pkgs = new ArrayList<>();
        for (SkillPackageScan p : r.packages()) {
            List<String> paths = p.resources() == null
                    ? List.of()
                    : p.resources().stream()
                            .map(SkillResourceData::resourcePath)
                            .toList();
            pkgs.add(new GitPreviewResultVO.PackageVO(
                    p.skillPath(), p.name(), p.description(), p.contentHash(), p.skillContent(), paths.size(), paths));
        }
        return new GitPreviewResultVO(r.sourceId(), r.ref(), r.commitSha(), r.url(), pkgs);
    }

    private static GitSyncResultVO toSyncVO(GitSyncResult r) {
        List<GitSyncResultVO.ItemVO> items = new ArrayList<>();
        for (GitSkillSourceService.GitSyncItem i : r.items()) {
            items.add(new GitSyncResultVO.ItemVO(i.skillPath(), i.result(), i.message()));
        }
        return new GitSyncResultVO(r.sourceId(), r.commitSha(), items);
    }
}
