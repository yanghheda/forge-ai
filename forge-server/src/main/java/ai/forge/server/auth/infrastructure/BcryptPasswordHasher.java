package ai.forge.server.auth.infrastructure;

import ai.forge.server.auth.application.PasswordHasher;
import at.favre.lib.crypto.bcrypt.BCrypt;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public final class BcryptPasswordHasher implements PasswordHasher {

    /* BCrypt 工作因子，在安全性与自托管实例响应时间之间取平衡。 */
    private static final int COST = 12;

    @Override
    public String hash(String password) {
        char[] passwordCharacters = password.toCharArray();
        try {
            return BCrypt.with(BCrypt.Version.VERSION_2B).hashToString(COST, passwordCharacters);
        } finally {
            Arrays.fill(passwordCharacters, '\0');
        }
    }

    @Override
    public boolean matches(String password, String passwordHash) {
        char[] passwordCharacters = password.toCharArray();
        try {
            return BCrypt.verifyer().verify(passwordCharacters, passwordHash).verified;
        } finally {
            Arrays.fill(passwordCharacters, '\0');
        }
    }
}
