package ai.forge.server.auth.domain;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    /* 防止容易被穷举的短密码进入本地身份库。 */
    private static final int MINIMUM_CHARACTERS = 12;

    /* BCrypt 可完整处理的 UTF-8 输入上限，超过后必须拒绝而非静默截断。 */
    private static final int MAXIMUM_UTF8_BYTES = 72;

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null
                || password.codePointCount(0, password.length()) < MINIMUM_CHARACTERS
                || password.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_UTF8_BYTES
                || password.codePoints().noneMatch(Character::isLetter)
                || password.codePoints().noneMatch(Character::isDigit)) {
            throw new WeakPasswordException();
        }
    }
}
