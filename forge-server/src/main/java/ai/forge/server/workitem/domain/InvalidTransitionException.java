package ai.forge.server.workitem.domain;

public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException() {
        super("Workflow action is not available from the current state");
    }
}
