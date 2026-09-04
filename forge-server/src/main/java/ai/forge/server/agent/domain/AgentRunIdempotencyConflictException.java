package ai.forge.server.agent.domain;

public final class AgentRunIdempotencyConflictException extends RuntimeException {

    public AgentRunIdempotencyConflictException() {
        super("Agent Run clientRequestId was reused with different input");
    }
}
