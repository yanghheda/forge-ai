package ai.forge.server.auth.domain;

public final class WeakPasswordException extends RuntimeException {

    public WeakPasswordException() {
        super("Password does not meet security requirements");
    }
}
