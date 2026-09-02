package com.wshake.service.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 受控 Git 来源的凭据引用表；API 仅接收引用名，永不返回凭据内容。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.agent-skill.git")
public class GitSkillSourceProperties {

    private Map<String, Credential> credentials = new LinkedHashMap<>();

    @Data
    public static class Credential {
        private String username = "";
        private String password = "";
    }
}
