package ai.forge.server.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApprovalCipherTest {

    @Test
    void encryptsWithFreshNonceAndRestoresExactNormalizedArguments() {
        ApprovalCipher cipher = new ApprovalCipher("test-only-internal-jwt-secret-32-bytes-minimum");
        String arguments = "{\"requirementId\":42,\"title\":\"UX Spec\"}";

        String first = cipher.encrypt(arguments);
        String second = cipher.encrypt(arguments);

        assertThat(first).isNotEqualTo(second).doesNotContain("UX Spec");
        assertThat(cipher.decrypt(first)).isEqualTo(arguments);
        assertThat(cipher.decrypt(second)).isEqualTo(arguments);
    }
}
