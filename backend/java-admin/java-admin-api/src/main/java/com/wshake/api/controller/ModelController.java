package com.wshake.api.controller;

import com.wshake.api.dto.BatchCreateModelDraftRequest;
import com.wshake.api.dto.CreateModelDraftRequest;
import com.wshake.api.dto.ProbeModelRequest;
import com.wshake.api.dto.RejectModelDraftRequest;
import com.wshake.api.dto.UpdateModelDraftRequest;
import com.wshake.api.vo.ModelDraftVO;
import com.wshake.api.vo.ModelReleaseVO;
import com.wshake.api.vo.ModelVerifyResultVO;
import com.wshake.common.exception.BizException;
import com.wshake.common.request.RequestContext;
import com.wshake.common.result.PageData;
import com.wshake.common.result.Result;
import com.wshake.service.model.ModelControlService;
import com.wshake.service.model.ModelControlService.BatchCreateModelCommand;
import com.wshake.service.model.ModelControlService.CreateModelCommand;
import com.wshake.service.model.ModelControlService.ModelDraftItem;
import com.wshake.service.model.ModelControlService.ModelReleaseView;
import com.wshake.service.model.ModelControlService.UpdateModelCommand;
import com.wshake.service.port.ModelProbePort.ProbeResult;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型管理（路径 {@code /api/system/model/*}）。
 *
 * @author wshake
 */
@Tag(name = "模型管理", description = "草稿 CRUD/verify/submit/approve/reject + Release + 可用模型池")
@RestController
@RequestMapping("/api/system/model")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ModelController {

    private final ModelControlService modelService;
    private final Converter converter;

    @GetMapping("/draft/list")
    @Operation(summary = "模型草稿分页", description = "data={items,total}")
    public Result<PageData<ModelDraftVO>> draftList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status) {
        PageData<ModelControlService.ModelDraftView> pd = modelService.pageDraft(
                ModelControlService.ModelListQuery.of(page, pageSize, ownerUserId, name, scope, status));
        return Result.ok(PageData.of(converter.convert(pd.getItems(), ModelDraftVO.class), pd.getTotal()));
    }

    @GetMapping("/draft/all")
    @Operation(summary = "模型草稿全量")
    public Result<List<ModelDraftVO>> draftAll(
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status) {
        PageData<ModelControlService.ModelDraftView> pd = modelService.pageDraft(
                ModelControlService.ModelListQuery.of(1, Integer.MAX_VALUE, ownerUserId, name, scope, status));
        return Result.ok(converter.convert(pd.getItems(), ModelDraftVO.class));
    }

    @GetMapping("/draft/{id}")
    @Operation(summary = "模型草稿详情")
    public Result<ModelDraftVO> draftDetail(@PathVariable Long id) {
        return Result.ok(converter.convert(modelService.getDraft(id), ModelDraftVO.class));
    }

    @PostMapping("/draft")
    @Operation(summary = "创建模型草稿")
    public Result<ModelDraftVO> createDraft(@Valid @RequestBody CreateModelDraftRequest req) {
        CreateModelCommand cmd = new CreateModelCommand(
                req.getName(),
                req.getScope(),
                req.getCode(),
                req.getProvider(),
                req.getBaseUrl(),
                req.getModelName(),
                req.getCapabilities(),
                req.getParameterGuardrails(),
                req.getContextLength(),
                req.getPlainSecret(),
                req.getRemark(),
                RequestContext.userIdOrNull());
        return Result.ok(converter.convert(modelService.createDraft(cmd), ModelDraftVO.class));
    }

    @PostMapping("/draft/batch")
    @Operation(summary = "批量创建模型草稿(同一连接配置)")
    public Result<List<ModelDraftVO>> createDrafts(@Valid @RequestBody BatchCreateModelDraftRequest req) {
        List<ModelDraftItem> items = new java.util.ArrayList<>();
        for (BatchCreateModelDraftRequest.Item item : req.getItems()) {
            items.add(new ModelDraftItem(
                    item.getName(),
                    item.getModelName(),
                    item.getCode(),
                    item.getCapabilities(),
                    item.getParameterGuardrails(),
                    item.getContextLength()));
        }
        BatchCreateModelCommand cmd = new BatchCreateModelCommand(
                req.getScope(),
                req.getProvider(),
                req.getBaseUrl(),
                req.getPlainSecret(),
                req.getCapabilities(),
                req.getParameterGuardrails(),
                req.getRemark(),
                RequestContext.userIdOrNull(),
                items);
        return Result.ok(converter.convert(modelService.createDrafts(cmd), ModelDraftVO.class));
    }

    @PostMapping("/probe")
    @Operation(summary = "创建前探测远端模型目录(不落库)")
    public Result<ModelVerifyResultVO> probeCatalog(@Valid @RequestBody ProbeModelRequest req) {
        ProbeResult result;
        try {
            result = modelService.probeCatalog(req.getProvider(), req.getBaseUrl(), req.getPlainSecret());
        } catch (BizException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
        return Result.ok(
                new ModelVerifyResultVO(true, result.message(), result.modelNameMatched(), result.remoteModelIds()));
    }

    @PutMapping("/draft/{id}")
    @Operation(summary = "更新模型草稿")
    public Result<ModelDraftVO> updateDraft(@PathVariable Long id, @Valid @RequestBody UpdateModelDraftRequest req) {
        UpdateModelCommand cmd = new UpdateModelCommand(
                req.getName(),
                req.getCode(),
                req.getProvider(),
                req.getBaseUrl(),
                req.getModelName(),
                req.getCapabilities(),
                req.getParameterGuardrails(),
                req.getContextLength(),
                req.getPlainSecret(),
                req.getRemark());
        return Result.ok(converter.convert(modelService.updateDraft(id, cmd), ModelDraftVO.class));
    }

    @DeleteMapping("/draft/{id}")
    @Operation(summary = "软删模型草稿")
    public Result<Void> deleteDraft(@PathVariable Long id) {
        modelService.softDeleteDraft(id);
        return Result.ok(null);
    }

    @PostMapping("/draft/{id}/verify")
    @Operation(summary = "探测验证(返回远端目录摘要,不改状态)")
    public Result<ModelVerifyResultVO> verify(@PathVariable Long id) {
        ProbeResult result;
        try {
            result = modelService.verify(id);
        } catch (BizException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
        return Result.ok(
                new ModelVerifyResultVO(true, result.message(), result.modelNameMatched(), result.remoteModelIds()));
    }

    @PostMapping("/draft/{id}/submit")
    @Operation(summary = "提交审核(官方模型必走)")
    public Result<Void> submit(@PathVariable Long id) {
        modelService.submit(id);
        return Result.ok(null);
    }

    @PostMapping("/draft/{id}/withdraw")
    @Operation(summary = "撤回审核")
    public Result<Void> withdraw(@PathVariable Long id) {
        modelService.withdraw(id);
        return Result.ok(null);
    }

    @PostMapping("/draft/{id}/approve")
    @Operation(summary = "发布 Release(官方须待审;私有可从草稿直接发布;再次探测冻结)")
    public Result<ModelReleaseVO> approve(@PathVariable Long id) {
        ModelReleaseView view = modelService.approve(id);
        return Result.ok(converter.convert(view, ModelReleaseVO.class));
    }

    @PostMapping("/draft/{id}/reject")
    @Operation(summary = "驳回草稿")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) RejectModelDraftRequest req) {
        modelService.reject(id, req == null ? "" : req.getReason());
        return Result.ok(null);
    }

    @GetMapping("/release/list")
    @Operation(summary = "模型 Release 分页")
    public Result<PageData<ModelReleaseVO>> releaseList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String name) {
        PageData<ModelReleaseView> pd =
                modelService.pageRelease(ModelControlService.ReleaseListQuery.of(page, pageSize, scope, status, name));
        return Result.ok(PageData.of(converter.convert(pd.getItems(), ModelReleaseVO.class), pd.getTotal()));
    }

    @GetMapping("/release/{id}")
    @Operation(summary = "模型 Release 详情")
    public Result<ModelReleaseVO> releaseDetail(@PathVariable Long id) {
        return Result.ok(converter.convert(modelService.getRelease(id), ModelReleaseVO.class));
    }

    @GetMapping("/available")
    @Operation(summary = "可用模型池(官方 PUBLISHED + 本人私有 PUBLISHED)")
    public Result<List<ModelReleaseVO>> available(@RequestParam(required = false) Long ownerUserId) {
        Long owner = ownerUserId != null ? ownerUserId : RequestContext.userIdOrNull();
        return Result.ok(converter.convert(modelService.listAvailable(owner), ModelReleaseVO.class));
    }

    @PostMapping("/release/{id}/deprecate")
    @Operation(summary = "弃用单个 Release")
    public Result<Void> deprecate(@PathVariable Long id) {
        modelService.deprecate(id);
        return Result.ok(null);
    }
}
