package ai.forge.server.organization.domain;

public class MemberEmailConflictException extends RuntimeException {
    public MemberEmailConflictException() {
        super("Email already exists");
    }
}
