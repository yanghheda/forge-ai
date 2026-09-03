package ai.forge.server.auth.application;

public interface PasswordHasher {

    String hash(String password);

    boolean matches(String password, String passwordHash);
}
