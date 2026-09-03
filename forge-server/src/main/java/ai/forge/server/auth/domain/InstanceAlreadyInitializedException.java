package ai.forge.server.auth.domain;

public final class InstanceAlreadyInitializedException extends RuntimeException {

    public InstanceAlreadyInitializedException() {
        super("Instance has already been initialized");
    }
}
