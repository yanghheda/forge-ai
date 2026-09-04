package ai.forge.server.gitlab.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretServiceTest {

    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsWithFreshIvAndDecryptsOnlyInsideTheService() {
        SecretService service = new SecretService(MASTER_KEY, 1);

        EncryptedSecret first = service.encrypt(7L, "GITLAB_TOKEN", "glpat-never-return-this");
        EncryptedSecret second = service.encrypt(7L, "GITLAB_TOKEN", "glpat-never-return-this");

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
        assertThat(first.ciphertext()).doesNotContain("glpat-never-return-this");
        assertThat(service.decrypt(7L, "GITLAB_TOKEN", first)).isEqualTo("glpat-never-return-this");
    }

    @Test
    void rejectsCiphertextTamperingAndScopeSubstitution() {
        SecretService service = new SecretService(MASTER_KEY, 1);
        EncryptedSecret encrypted = service.encrypt(7L, "GITLAB_TOKEN", "secret-token");
        byte[] changed = Base64.getDecoder().decode(encrypted.ciphertext());
        changed[changed.length - 1] ^= 1;
        EncryptedSecret tampered = new EncryptedSecret(
                Base64.getEncoder().encodeToString(changed),
                encrypted.iv(),
                encrypted.keyVersion(),
                encrypted.fingerprint());

        assertThatThrownBy(() -> service.decrypt(7L, "GITLAB_TOKEN", tampered))
                .isInstanceOf(SecretDecryptionException.class);
        assertThatThrownBy(() -> service.decrypt(8L, "GITLAB_TOKEN", encrypted))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void failsFastWhenTheDeploymentMasterKeyIsMissingOrNot256Bits() {
        assertThatThrownBy(() -> new SecretService("", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master key");
        assertThatThrownBy(() -> new SecretService(Base64.getEncoder().encodeToString(new byte[16]), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256-bit");
    }
}
