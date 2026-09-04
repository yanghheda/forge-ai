package ai.forge.server.agent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!test-unit")
public class ApprovalCipher {

    /* 每次加密使用的新随机 nonce 来源。 */
    private final SecureRandom secureRandom = new SecureRandom();

    /* 从部署 Secret 单向派生的 AES 密钥，不进入数据库。 */
    private final SecretKeySpec key;

    public ApprovalCipher(@Value("${forge.infrastructure.agent.jwt-secret}") String secret) {
        try {
            this.key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Approval encryption key cannot be derived", exception);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[12];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, envelope, 0, nonce.length);
            System.arraycopy(ciphertext, 0, envelope, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (Exception exception) {
            throw new IllegalStateException("Approval arguments cannot be encrypted", exception);
        }
    }

    public String decrypt(String envelope) {
        try {
            byte[] decoded = Base64.getDecoder().decode(envelope);
            byte[] nonce = java.util.Arrays.copyOfRange(decoded, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(decoded, 12, decoded.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Approval arguments cannot be decrypted", exception);
        }
    }
}
