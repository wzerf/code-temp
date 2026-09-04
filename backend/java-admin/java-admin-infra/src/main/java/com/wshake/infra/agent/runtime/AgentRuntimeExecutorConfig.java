package com.wshake.infra.agent.runtime;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 运行执行器配置：Java 21 虚拟线程池（SSE 长连接按需占线程，不阻塞 Tomcat worker）。
 *
 * @author wshake
 */
@Configuration
public class AgentRuntimeExecutorConfig {

    @Bean(name = "agentRunExecutor")
    public Executor agentRunExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
