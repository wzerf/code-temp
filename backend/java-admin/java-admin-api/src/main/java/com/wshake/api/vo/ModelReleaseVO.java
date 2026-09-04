package com.wshake.api.vo;

import com.wshake.service.model.ModelControlService.ModelReleaseView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型 Release VO（不含密钥;hasSecret 仅标记是否有密钥）。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = ModelReleaseView.class)
@Schema(description = "模型 Release")
public class ModelReleaseVO {

    private Long id;
    private Long ownerUserId;
    private String name;
    private String scope;
    private String code;
    private String status;
    private Integer version;
    private String provider;
    private String baseUrl;
    private String modelName;
    private String capabilities;
    private String parameterGuardrails;
    private Long contextLength;
    private Boolean hasSecret;
    private Long sourceDraftId;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
