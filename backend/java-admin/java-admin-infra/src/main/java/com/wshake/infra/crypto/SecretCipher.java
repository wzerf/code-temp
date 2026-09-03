package com.wshake.infra.crypto;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.springframework.stereotype.Component;

/**
 * {@code encrypted_secret} 加解密封装（MCP / Git 来源密钥密文）。
 *
 * <p>采用「RSA-OAEP 包 AES-256-GCM」混合加密：随机 AES key 用平台 RSA 公钥加密，
 * 明文用 AES-GCM 加密，密文格式为 {@code v1:<rsaEncryptedKey>:<ciphertext>:<tagIv>}。
 * 明文不进入数据库、日志或模型上下文。
 *
 * <p>密钥对来源 {@link ServerKeyPairProvider}（Redis cache-aside + 进程内缓存），
 * 与请求安全加解密共用同一平台密钥对，避免另设密钥托管。
 *
 * @author wshake
 */
@Component
public final class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final String SEP = ":";

    private final ServerKeyPairProvider keyPairProvider;
    private final CryptoService cryptoService;

    public SecretCipher(ServerKeyPairProvider keyPairProvider, CryptoService cryptoService) {
        this.keyPairProvider = keyPairProvider;
        this.cryptoService = cryptoService;
    }

    /**
     * 加密明文，返回可落库的密文字符串。
     *
     * @param plainText 明文；null / 空串原样返回（允许无密钥的 MARKET 连接模板）
     * @return 密文（{@code v1:...}），或 null / 空串
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        String aesKey = CryptoService.generateAesKey();
        PublicKey publicKey = CryptoService.parsePublicKeyPem(keyPairProvider.getPublicKey());
        String encryptedKey = cryptoService.rsaEncrypt(aesKey, publicKey);
        CryptoService.EncryptResult sealed = cryptoService.aesEncrypt(plainText, aesKey, null);
        return PREFIX + encryptedKey + SEP + sealed.ciphertext() + SEP + sealed.tagIv();
    }

    /**
     * 解密落库密文。
     *
     * @param cipherText 密文；null / 空串原样返回
     * @return 明文，或 null / 空串
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        String body = cipherText.startsWith(PREFIX) ? cipherText.substring(PREFIX.length()) : cipherText;
        String[] parts = body.split(SEP, 3);
        if (parts.length != 3) {
            throw new CryptoService.CryptoException("Invalid encrypted secret format");
        }
        PrivateKey privateKey = CryptoService.parsePrivateKeyPem(keyPairProvider.getPrivateKeyPem());
        String aesKey = cryptoService.rsaDecrypt(parts[0], privateKey);
        byte[] decrypted = cryptoService.aesDecryptCiphertextAndTag(parts[1], parts[2], aesKey, null);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
