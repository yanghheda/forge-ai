package ai.forge.server.qa.application;

public class QaStateException extends RuntimeException {

    public QaStateException() {
        super("QA operation is not available from the current state");
    }
}
