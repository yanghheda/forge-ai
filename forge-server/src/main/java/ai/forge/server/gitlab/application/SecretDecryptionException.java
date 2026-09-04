package ai.forge.server.gitlab.application;

public final class SecretDecryptionException extends RuntimeException {

    public SecretDecryptionException(Throwable cause) {
        super("Secret cannot be decrypted or its integrity check failed", cause);
    }
}
