package com.wshake.api.controller;

import com.wshake.api.dto.CreateSkillDraftRequest;
import com.wshake.api.dto.RejectSkillDraftRequest;
import com.wshake.api.dto.UpdateSkillDraftRequest;
import com.wshake.api.vo.SkillDraftVO;
import com.wshake.api.vo.SkillReleaseVO;
import com.wshake.common.result.PageData;
import com.wshake.common.result.Result;
import com.wshake.service.skill.SkillControlService;
import com.wshake.service.skill.SkillControlService.CreateSkillCommand;
import com.wshake.service.skill.SkillControlService.ResourceInput;
import com.wshake.service.skill.SkillControlService.SkillResourceBundle;
import com.wshake.service.skill.SkillControlService.SkillView;
import com.wshake.service.skill.SkillControlService.UpdateSkillCommand;
import io.github.linpeilie.Converter;
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
 * Skill 管理/市场（路径 {@code /api/system/skill/*}）。
 *
 * @author wshake
 */
@Tag(name = "Skill 管理", description = "草稿 CRUD/submit/approve/reject + Release + 市场")
@RestController
@RequestMapping("/api/system/skill")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SkillController {

    private final SkillControlService skillService;
    private final Converter converter;

    @GetMapping("/draft/list")
    @Operation(summary = "Skill 草稿分页", description = "data={items,total}")
    public Result<PageData<SkillDraftVO>> draftList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status) {
        PageData<SkillView> pd = skillService.pageDraft(
                SkillControlService.SkillListQuery.of(page, pageSize, ownerUserId, name, visibility, status));
        return Result.ok(PageData.of(converter.convert(pd.getItems(), SkillDraftVO.class), pd.getTotal()));
    }

    @GetMapping("/draft/all")
    @Operation(summary = "Skill 草稿全量")
    public Result<List<SkillDraftVO>> draftAll(
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status) {
        PageData<SkillView> pd = skillService.pageDraft(
                SkillControlService.SkillListQuery.of(1, Integer.MAX_VALUE, ownerUserId, name, visibility, status));
        return Result.ok(converter.convert(pd.getItems(), SkillDraftVO.class));
    }

    @GetMapping("/draft/{id}")
    @Operation(summary = "Skill 草稿详情")
    public Result<SkillDraftVO> draftDetail(@PathVariable Long id) {
        return Result.ok(converter.convert(skillService.getDraft(id), SkillDraftVO.class));
    }

    @GetMapping("/draft/{id}/resources")
    @Operation(summary = "Skill 草稿内容包(SKILL.md + 资源)")
    public Result<SkillResourceBundle> draftBundle(@PathVariable Long id) {
        return Result.ok(skillService.getDraftBundle(id));
    }

    @PostMapping("/draft")
    @Operation(summary = "创建 Skill 草稿")
    public Result<SkillDraftVO> createDraft(@Valid @RequestBody CreateSkillDraftRequest req) {
        return Result.ok(converter.convert(skillService.createDraft(toCreateCommand(req)), SkillDraftVO.class));
    }

    @PutMapping("/draft/{id}")
    @Operation(summary = "更新 Skill 草稿")
    public Result<SkillDraftVO> updateDraft(@PathVariable Long id, @Valid @RequestBody UpdateSkillDraftRequest req) {
        List<ResourceInput> resources = req.getResources() == null ? null : toResourceInputs(req.getResources());
        return Result.ok(converter.convert(
                skillService.updateDraft(
                        id,
                        new UpdateSkillCommand(
                                req.getName(),
                                req.getDescription(),
                                req.getSkillContent(),
                                req.getRemark(),
                                resources)),
                SkillDraftVO.class));
    }

    @PutMapping("/draft/{id}/resources")
    @Operation(summary = "保存 Skill 草稿资源(整体替换)")
    public Result<SkillDraftVO> saveResources(@PathVariable Long id, @RequestBody UpdateSkillDraftRequest req) {
        if (req.getResources() == null) {
            throw new com.wshake.common.exception.BizException(
                    com.wshake.common.result.ResultCode.PARAM_INVALID, "resources 不能为空");
        }
        List<ResourceInput> resources = toResourceInputs(req.getResources());
        return Result.ok(converter.convert(
                skillService.updateDraft(id, new UpdateSkillCommand(null, null, null, null, resources)),
                SkillDraftVO.class));
    }

    @DeleteMapping("/draft/{id}")
    @Operation(summary = "软删 Skill 草稿")
    public Result<Void> deleteDraft(@PathVariable Long id) {
        skillService.softDeleteDraft(id);
        return Result.ok(null);
    }

    @PostMapping("/draft/{id}/submit")
    @Operation(summary = "提交审核")
    public Result<Void> submit(@PathVariable Long id) {
        skillService.submit(id);
        return Result.ok(null);
    }

    @PostMapping("/draft/{id}/withdraw")
    @Operation(summary = "撤回审核")
    public Result<Void> withdraw(@PathVariable Long id) {
        skillService.withdraw(id);
        return Result.ok(null);
    }

    @PostMapping("/draft/{id}/approve")
    @Operation(summary = "通过审核并发布 Release")
    public Result<SkillReleaseVO> approve(@PathVariable Long id) {
        return Result.ok(converter.convert(skillService.approve(id), SkillReleaseVO.class));
    }

    @PostMapping("/draft/{id}/reject")
    @Operation(summary = "驳回草稿")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) RejectSkillDraftRequest req) {
        skillService.reject(id, req == null ? "" : req.getReason());
        return Result.ok(null);
    }

    @GetMapping("/release/list")
    @Operation(summary = "Skill Release 分页")
    public Result<PageData<SkillReleaseVO>> releaseList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String name) {
        PageData<SkillControlService.SkillReleaseView> pd = skillService.pageRelease(
                SkillControlService.ReleaseListQuery.of(page, pageSize, visibility, status, name));
        return Result.ok(PageData.of(converter.convert(pd.getItems(), SkillReleaseVO.class), pd.getTotal()));
    }

    @GetMapping("/release/{id}")
    @Operation(summary = "Skill Release 详情")
    public Result<SkillReleaseVO> releaseDetail(@PathVariable Long id) {
        return Result.ok(converter.convert(skillService.getRelease(id), SkillReleaseVO.class));
    }

    @GetMapping("/release/{id}/resources")
    @Operation(summary = "Skill Release 内容包(SKILL.md + 冻结资源)")
    public Result<SkillResourceBundle> releaseResources(@PathVariable Long id) {
        return Result.ok(skillService.getReleaseBundle(id));
    }

    @GetMapping("/market")
    @Operation(summary = "Skill 市场列表(按 name 取最新 MARKET PUBLISHED Release)")
    public Result<List<SkillReleaseVO>> market() {
        return Result.ok(converter.convert(skillService.listMarket(), SkillReleaseVO.class));
    }

    @GetMapping("/release/bindable")
    @Operation(summary = "可绑定 Skill 候选(MARKET 最新)")
    public Result<List<SkillReleaseVO>> bindable() {
        return Result.ok(converter.convert(skillService.listBindable(null), SkillReleaseVO.class));
    }

    @PostMapping("/market/{id}/take-down")
    @Operation(summary = "市场下架(置 DEPRECATED)")
    public Result<Void> takeDown(@PathVariable Long id) {
        skillService.takeDownMarket(id);
        return Result.ok(null);
    }

    @PostMapping("/release/{id}/deprecate")
    @Operation(summary = "弃用单个 Release")
    public Result<Void> deprecate(@PathVariable Long id) {
        skillService.deprecate(id);
        return Result.ok(null);
    }

    private static CreateSkillCommand toCreateCommand(CreateSkillDraftRequest req) {
        List<ResourceInput> resources = req.getResources() == null ? List.of() : toResourceInputs(req.getResources());
        return new CreateSkillCommand(
                req.getName(),
                req.getDescription(),
                req.getSkillContent(),
                req.getVisibility(),
                req.getRemark(),
                null,
                resources);
    }

    private static List<ResourceInput> toResourceInputs(List<CreateSkillDraftRequest.ResourceInputRequest> items) {
        List<ResourceInput> out = new ArrayList<>();
        for (CreateSkillDraftRequest.ResourceInputRequest in : items) {
            out.add(new ResourceInput(in.getResourcePath(), in.getContent()));
        }
        return out;
    }
}
