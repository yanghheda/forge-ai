package ai.forge.server.auth.application;

public interface LoginRateLimiter {

    void checkAndRecord(String remoteAddress, String normalizedEmail);
}
