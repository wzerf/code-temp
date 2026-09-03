package com.wshake.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Git 来源预览结果 VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Git 来源预览结果")
public class GitPreviewResultVO {

    private Long sourceId;
    private String ref;
    /** 解析出的精确 commit sha */
    private String commitSha;

    private String url;
    private List<PackageVO> packages;

    /** 扫描到的 Skill 包。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Skill 包")
    public static class PackageVO {
        private String skillPath;
        private String name;
        private String description;
        private String contentHash;
        private String skillContent;
        /** 资源文件数(SKILL.md 之外)。 */
        private Integer resourceCount;
        /** 资源文件路径列表。 */
        private List<String> resourcePaths;
    }
}
