package ai.forge.server.workitem.application;

public interface DevelopmentQaStore {

    DevelopmentQaSummary summarize(long workspaceId, long projectId, long requirementId);
}
