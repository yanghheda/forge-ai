package ai.forge.server.workspace.domain;

public final class MemberEmailConflictException extends RuntimeException {

    public MemberEmailConflictException() {
        super("A user account already exists for this email");
    }
}
