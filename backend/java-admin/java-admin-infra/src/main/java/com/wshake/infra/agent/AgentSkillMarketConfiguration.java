package com.wshake.infra.agent;

import io.agentscope.core.skill.repository.mysql.MysqlSkillRepository;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentSkillMarketProperties.class)
public class AgentSkillMarketConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    MysqlSkillRepository mysqlSkillRepository(DataSource dataSource, AgentSkillMarketProperties properties) {
        String databaseName = properties.getDatabaseName();
        if (databaseName == null || databaseName.isBlank()) {
            databaseName = catalog(dataSource);
        }
        return MysqlSkillRepository.builder(dataSource)
                .databaseName(databaseName)
                .skillsTableName("agent_skill")
                .resourcesTableName("agent_skill_resource")
                .createIfNotExist(false)
                .writeable(false)
                .build();
    }

    private static String catalog(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            if (catalog == null || catalog.isBlank()) {
                throw new IllegalStateException("app.agent-skill.database-name is required");
            }
            return catalog;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to resolve skill market database name", exception);
        }
    }
}
