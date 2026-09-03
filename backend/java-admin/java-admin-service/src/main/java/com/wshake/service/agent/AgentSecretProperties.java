package com.wshake.service.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 平台密钥配置属性（业务 {@code encrypted_secret} 的落库加密主密钥）。
 *
 * <p>对应 {@code application.yml} 的 {@code app.agent-secret.*}。
 * 仅存密钥密文,明文不进入数据库/日志/审计；解密只发生在需要的边界。
 *
 * @author wshake
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.agent-secret")
public class AgentSecretProperties {

    /**
     * AES-256 主密钥（Base64,32 字节）。生产必须通过环境变量注入,禁止硬编码入库。
     */
    private String masterKey = "";
}
