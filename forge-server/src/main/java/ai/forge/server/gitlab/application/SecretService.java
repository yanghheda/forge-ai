package ai.forge.server.gitlab.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SecretService {

    /* GCM 完整认证标签位数，用于同时校验密文完整性。 */
    private static final int GCM_TAG_BITS = 128;

    /* NIST 推荐的 GCM 96 位随机 IV 字节数。 */
    private static final int IV_BYTES = 12;

    /* 仅从部署环境注入、永不持久化的 256 位 AES 主密钥。 */
    private final SecretKeySpec masterKey;

    /* 支持后续并行读取旧密钥并重加密的部署密钥版本。 */
    private final int keyVersion;

    /* 为每次写入生成不可复用 IV 的系统安全随机源。 */
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretService(
            @Value("${forge.security.secret-master-key}") String encodedMasterKey,
            @Value("${forge.security.secret-key-version:1}") int keyVersion) {
        byte[] decoded = decodeMasterKey(encodedMasterKey);
        if (decoded.length != 32) {
            throw new IllegalStateException("Secret master key must be a Base64 encoded 256-bit value");
        }
        if (keyVersion < 1) {
            throw new IllegalStateException("Secret key version must be positive");
        }
        this.masterKey = new SecretKeySpec(decoded, "AES");
        this.keyVersion = keyVersion;
    }

    public EncryptedSecret encrypt(long workspaceId, String type, String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad(workspaceId, type, keyVersion));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(iv),
                    keyVersion,
                    fingerprint(plaintext));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret cannot be encrypted", exception);
        }
    }

    public String decrypt(long workspaceId, String type, EncryptedSecret encrypted) {
        if (encrypted.keyVersion() != keyVersion) {
            throw new SecretDecryptionException(new IllegalStateException("Unsupported key version"));
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    masterKey,
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(encrypted.iv())));
            cipher.updateAAD(aad(workspaceId, type, encrypted.keyVersion()));
            return new String(
                    cipher.doFinal(Base64.getDecoder().decode(encrypted.ciphertext())),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new SecretDecryptionException(exception);
        }
    }

    private String fingerprint(String plaintext) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(masterKey.getEncoded(), "HmacSHA256"));
        byte[] digest = mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 9));
    }

    private static byte[] aad(long workspaceId, String type, int keyVersion) {
        byte[] typeBytes = type.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(Long.BYTES + Integer.BYTES + typeBytes.length)
                .putLong(workspaceId)
                .putInt(keyVersion)
                .put(typeBytes)
                .array();
    }

    private static byte[] decodeMasterKey(String encodedMasterKey) {
        if (encodedMasterKey == null || encodedMasterKey.isBlank()) {
            throw new IllegalStateException("Secret master key is required");
        }
        try {
            return Base64.getDecoder().decode(encodedMasterKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Secret master key must be valid Base64", exception);
        }
    }
}
