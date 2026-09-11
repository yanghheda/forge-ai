package ai.forge.server.workitem.application;

public interface DevelopmentQaStore {

    DevelopmentQaSummary summarize(long organizationId, long requirementId);
}
