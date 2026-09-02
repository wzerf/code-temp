package com.wshake.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class SyncGitSkillSourceRequest {

    @NotBlank
    @Size(max = 64)
    private String expectedCommitSha;

    @NotEmpty
    @Size(max = 100)
    private List<@NotBlank @Size(max = 500) String> skillPaths;
}
