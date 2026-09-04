package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 驳回模型草稿请求。
 *
 * @author wshake
 */
@Data
@Schema(description = "驳回模型草稿")
public class RejectModelDraftRequest {

    @Size(max = 512)
    @Schema(description = "驳回原因(对用户可见)")
    private String reason;
}
