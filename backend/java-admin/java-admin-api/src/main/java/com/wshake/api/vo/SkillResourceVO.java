package com.wshake.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillResourceVO {

    private String path;
    private String content;
    private String contentHash;
}
