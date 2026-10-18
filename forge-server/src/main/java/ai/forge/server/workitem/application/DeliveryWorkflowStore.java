package ai.forge.server.workitem.application;

import java.util.List;

public interface DeliveryWorkflowStore {
    List<Long> taskIds(long organizationId, long requirementId);
    String requirementStatus(long organizationId, long requirementId);
    Long requirementId(long organizationId, long workItemId);
    Long lockRequirement(long organizationId, long requirementId);
    boolean isLeader(long organizationId, long requirementId, String role, long userId);
    boolean isExecutor(long organizationId, long requirementId, String role, long userId);
    /* 公司是否已绑定 ACTIVE 的 GitLab 仓库与连接；开始研发前必须满足。 */
    boolean hasActiveRepositoryBinding(long organizationId);
    List<Long> executors(long organizationId, long requirementId, String role);
    void deleteExecutors(long organizationId, long requirementId, String role);
    void addExecutor(long organizationId, long requirementId, String role, long userId, long actorId);
    int countTasks(long organizationId, long requirementId, String type);
    int countIncompleteTasks(long organizationId, long requirementId, String type);
    boolean allCasesExecuted(long organizationId, long requirementId);
    int incompleteBugs(long organizationId, long requirementId);
    void clearVotes(long organizationId, long requirementId);
    void vote(long organizationId, long requirementId, String role, long userId);
    int countVotes(long organizationId, long requirementId);
    boolean hasVote(long organizationId, long requirementId, String role);
    void touchRequirement(long organizationId, long requirementId);
}
