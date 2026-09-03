package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 Agent 定义请求。
 *
 * @author wshake
 */
@Data
@Schema(description = "更新 Agent 定义")
public class UpdateAgentRequest {

    @Size(max = 128)
    @Schema(description = "名称")
    private String name;

    @Size(max = 512)
    @Schema(description = "描述")
    private String description;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;
}
