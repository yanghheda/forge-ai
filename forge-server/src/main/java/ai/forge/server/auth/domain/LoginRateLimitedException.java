package ai.forge.server.auth.domain;

public final class LoginRateLimitedException extends RuntimeException {

    public LoginRateLimitedException() {
        super("Too many login attempts");
    }
}
