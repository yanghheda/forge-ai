package ai.forge.server.workitem.domain;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency key was already used for another action");
    }
}
