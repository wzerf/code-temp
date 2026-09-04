package com.wshake.api.vo;

import com.wshake.service.model.ModelControlService.ModelDraftView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型草稿 VO（不含密钥密文）。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = ModelDraftView.class)
@Schema(description = "模型草稿")
public class ModelDraftVO {

    private Long id;
    private Long ownerUserId;
    private String name;
    private String scope;
    private String code;
    private String status;
    private String provider;
    private String baseUrl;
    private String modelName;
    private String capabilities;
    private String parameterGuardrails;
    private Long contextLength;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
