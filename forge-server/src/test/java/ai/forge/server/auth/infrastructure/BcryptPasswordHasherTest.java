package ai.forge.server.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BcryptPasswordHasherTest {

    private final BcryptPasswordHasher passwordHasher = new BcryptPasswordHasher();

    @Test
    void hashesWithRandomSaltAndVerifiesWithoutRecoveringPassword() {
        String firstHash = passwordHasher.hash("correct-horse-42");
        String secondHash = passwordHasher.hash("correct-horse-42");

        assertThat(firstHash).startsWith("$2b$12$").isNotEqualTo(secondHash);
        assertThat(passwordHasher.matches("correct-horse-42", firstHash)).isTrue();
        assertThat(passwordHasher.matches("incorrect-horse-42", firstHash)).isFalse();
        assertThat(firstHash).doesNotContain("correct-horse-42");
    }
}
