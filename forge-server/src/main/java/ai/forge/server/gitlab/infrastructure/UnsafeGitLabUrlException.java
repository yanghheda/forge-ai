package ai.forge.server.gitlab.infrastructure;

public final class UnsafeGitLabUrlException extends RuntimeException {

    public UnsafeGitLabUrlException(String message) {
        super(message);
    }
}
