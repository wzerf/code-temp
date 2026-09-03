package com.wshake.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Git 来源同步结果 VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Git 来源同步结果")
public class GitSyncResultVO {

    private Long sourceId;
    private String commitSha;
    private List<ItemVO> items;

    /** 单包结果。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "单包同步结果")
    public static class ItemVO {
        private String skillPath;
        /** CREATED / UPDATED / UNCHANGED / CONFLICT / FAILED */
        private String result;

        private String message;
    }
}
