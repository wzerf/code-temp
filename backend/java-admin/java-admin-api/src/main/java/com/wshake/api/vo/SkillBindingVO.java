package com.wshake.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillBindingVO {

    private Long skillReleaseId;
    private String skillName;
    private String contentHash;
    private boolean overrideWinner;
}
