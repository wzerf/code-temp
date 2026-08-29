package com.wshake.infra.agent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgentRuntimePropertiesTest {

    @Test
    void disabledRuntime_doesNotRequireModelCredentials() {
        assertThatCode(new AgentRuntimeProperties()::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledRuntime_requiresCompleteModelCredentials() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setEnabled(true);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model-name");
    }

    @Test
    void enabledRuntime_rejectsLeaseShorterThanOneTurn() {
        AgentRuntimeProperties properties = configuredProperties();
        properties.setExecutionLease(
                properties.getModelTimeout().multipliedBy(4).minusSeconds(1));
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("execution-lease is too short");
    }

    @Test
    void enabledRuntime_acceptsCompleteConfiguration() {
        assertThatCode(configuredProperties()::validate).doesNotThrowAnyException();
    }

    private static AgentRuntimeProperties configuredProperties() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setEnabled(true);
        properties.setModelName("test-model");
        properties.setBaseUrl("https://example.test/v1");
        properties.setApiKey("test-key");
        return properties;
    }
}
