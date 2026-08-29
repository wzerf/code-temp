package com.wshake.infra.agent;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 只读 Agent 运行路径配置；密钥仅从部署环境注入。 */
@Data
@ConfigurationProperties(prefix = "app.agent-runtime")
public class AgentRuntimeProperties {

    private boolean enabled = false;
    private String modelName;
    private String baseUrl;
    private String apiKey;
    private Duration requestIdTtl = Duration.ofMinutes(10);
    private Duration executionLease = Duration.ofMinutes(10);
    private Duration modelTimeout = Duration.ofMinutes(2);

    @PostConstruct
    void validateOnStartup() {
        validate();
    }

    public void validate() {
        if (!enabled) {
            return;
        }
        require(modelName, "app.agent-runtime.model-name is required");
        require(baseUrl, "app.agent-runtime.base-url is required");
        require(apiKey, "app.agent-runtime.api-key is required");
        positive(requestIdTtl, "app.agent-runtime.request-id-ttl must be positive");
        positive(executionLease, "app.agent-runtime.execution-lease must be positive");
        positive(modelTimeout, "app.agent-runtime.model-timeout must be positive");
        if (executionLease.compareTo(modelTimeout.multipliedBy(4)) < 0) {
            throw new IllegalStateException("app.agent-runtime.execution-lease is too short for one agent turn");
        }
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }

    private static void positive(Duration value, String message) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(message);
        }
    }
}
