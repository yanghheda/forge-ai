package ai.forge.server.auth.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsPasswordWithSufficientLengthLettersAndDigits() {
        assertThatCode(() -> PasswordPolicy.validate("correct-horse-42")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortOrSingleCategoryPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short1")).isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("onlyletterslong"))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void rejectsPasswordBeyondBcryptUtf8Limit() {
        assertThatThrownBy(() -> PasswordPolicy.validate("密码密码密码密码密码密码密码密码密码密码密码密码1a"))
                .isInstanceOf(WeakPasswordException.class);
    }
}
