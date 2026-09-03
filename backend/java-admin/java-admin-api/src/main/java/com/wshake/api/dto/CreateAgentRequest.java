package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 Agent 定义请求。
 *
 * @author wshake
 */
@Data
@Schema(description = "创建 Agent 定义")
public class CreateAgentRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "名称(唯一)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 512)
    @Schema(description = "描述")
    private String description;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;

    @Schema(description = "所有者用户 id(0=平台)")
    private Long ownerUserId;

    @Schema(description = "1=启用 0=禁用", example = "1")
    private Integer isEnabled;
}
