package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会话记住模型选择请求。{@code modelReleaseId} 为空表示清除，回落 Revision 默认。
 *
 * @author wshake
 */
@Data
@Schema(description = "绑定会话模型")
public class BindSessionModelRequest {

    @Schema(description = "模型 Release id；传 null 则清除会话选择")
    private Long modelReleaseId;
}
