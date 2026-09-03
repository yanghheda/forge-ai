package ai.forge.server.auth.domain;

public final class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Authentication required");
    }
}
