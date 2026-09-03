package ai.forge.server.project.domain;

public class ProjectKeyConflictException extends RuntimeException {

    public ProjectKeyConflictException() {
        super("Project key already exists in this workspace");
    }
}
