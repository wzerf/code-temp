package com.wshake.infra.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.agent-skill")
public class AgentSkillMarketProperties {

    /** MysqlSkillRepository 使用的 schema 名；空则使用 DataSource catalog。 */
    private String databaseName = "";
}
