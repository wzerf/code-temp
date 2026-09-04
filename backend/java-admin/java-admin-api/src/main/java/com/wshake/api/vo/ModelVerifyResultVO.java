package com.wshake.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型探测验证结果 VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模型探测验证结果")
public class ModelVerifyResultVO {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "消息")
    private String message;

    @Schema(description = "目标 modelName 是否出现在远端目录")
    private Boolean modelNameMatched;

    @Schema(description = "远端目录模型 id")
    private List<String> remoteModelIds;
}
