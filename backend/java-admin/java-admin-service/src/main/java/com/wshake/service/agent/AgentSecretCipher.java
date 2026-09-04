package com.wshake.service.agent;

import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 平台业务密钥加解密（AES-256-GCM）。
 *
 * <p>用于 Skill/MCP 的 {@code encrypted_secret} 落库加密：
 * <ul>
 *     <li>数据库/日志/审计只出现密文;明文不落任何数据面</li>
 *     <li>密文格式: base64(iv(12) + ciphertext + tag(16)) 的 combined 布局</li>
 * </ul>
 *
 * @author wshake
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSecretCipher {

    /** dev 本地联调默认密钥；生产必须经 {@code app.agent-secret.master-key} 覆盖。非真实凭据,仅本地回退。 */
    static final String DEV_MASTER_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="; // gitleaks:allow 固定 dev 回退值,非真实密钥

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final AgentSecretProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    private byte[] masterKeyBytes;

    @PostConstruct
    public void init() {
        synchronized (this) {
            if (masterKeyBytes != null) {
                return;
            }
            String key = properties.getMasterKey();
            if (key == null || key.isBlank()) {
                log.warn("app.agent-secret.master-key 未配置,使用 dev 默认密钥(仅限本地联调;生产必须配置)");
                key = DEV_MASTER_KEY;
            }
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(key);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("app.agent-secret.master-key 必须是 Base64", e);
            }
            if (decoded.length != KEY_BYTES) {
                throw new IllegalStateException("app.agent-secret.master-key 必须解码为 32 字节(AES-256)");
            }
            this.masterKeyBytes = decoded;
        }
    }

    private byte[] keyBytes() {
        if (masterKeyBytes == null) {
            init();
        }
        return masterKeyBytes;
    }

    /**
     * AES-256-GCM 加密并返回 Base64 combined 密文。
     *
     * @param plainText 明文;null/空返回空串（无密钥场景,如 MARKET 草稿）
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(
                    Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(sealed, 0, combined, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "密钥加密失败");
        }
    }

    /**
     * 解密 Base64 combined 密文;空串返回 null。
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("密文过短");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] sealed = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, sealed, 0, sealed.length);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID, "密钥解密失败");
        }
    }
}
