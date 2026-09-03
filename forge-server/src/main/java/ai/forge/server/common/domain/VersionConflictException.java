package ai.forge.server.common.domain;

public class VersionConflictException extends RuntimeException {

    public VersionConflictException() {
        super("Resource version conflict");
    }
}
