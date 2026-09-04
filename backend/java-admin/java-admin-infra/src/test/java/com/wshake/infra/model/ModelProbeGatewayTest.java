package com.wshake.infra.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelProbeGatewayTest {

    @Test
    void modelsUrl_appendsModelsAndStripsSlash() {
        assertThat(ModelProbeGateway.modelsUrl("https://api.openai.com/v1"))
                .isEqualTo("https://api.openai.com/v1/models");
        assertThat(ModelProbeGateway.modelsUrl("https://api.openai.com/v1/"))
                .isEqualTo("https://api.openai.com/v1/models");
        assertThat(ModelProbeGateway.modelsUrl("https://api.openai.com/v1/models"))
                .isEqualTo("https://api.openai.com/v1/models");
    }
}
