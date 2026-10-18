package ai.forge.server.workitem.application;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workitem.domain.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class DeliveryWorkflowPolicy {
    /* 需求内负责人、执行人与本轮评审票的公司范围事实。 */
    private final DeliveryWorkflowStore store;
    /* 读取四个领域负责人的人员配置。 */
    private final OrganizationRequirementStore participants;

    public DeliveryWorkflowPolicy(DeliveryWorkflowStore store, OrganizationRequirementStore participants) {
        this.store = store;
        this.participants = participants;
    }

    public long lock(long organizationId, long itemId) {
        Long root = store.requirementId(organizationId, itemId);
        if (root == null || store.lockRequirement(organizationId, root) == null) throw new ResourceNotFoundException();
        return root;
    }

    public boolean testingActive(long organizationId, long requirementId) {
        String status = store.requirementStatus(organizationId, requirementId);
        return "IN_QA".equals(status) || "IN_FIX_VERIFICATION".equals(status);
    }

    public boolean leader(long organizationId, long requirementId, String role, long userId) {
        return store.isLeader(organizationId, requirementId, role, userId);
    }

    public boolean member(long organizationId, long requirementId, String role, long userId) {
        return leader(organizationId, requirementId, role, userId) || store.isExecutor(organizationId, requirementId, role, userId);
    }

    /* 公司是否已绑定可用的 GitLab 仓库；供板面提示研发准入材料。 */
    public boolean hasActiveRepositoryBinding(long organizationId) {
        return store.hasActiveRepositoryBinding(organizationId);
    }

    public boolean canAct(long userId, WorkItem item, WorkflowAction action) {
        long org = item.organizationId();
        if (item.type() != WorkItemType.REQUIREMENT) {
            Long root = store.requirementId(org, item.id());
            if (root == null) return false;
            String role = role(item.type());
            if (action == WorkflowAction.APPROVE || action == WorkflowAction.REJECT) return leader(org, root, role, userId);
            return Long.valueOf(userId).equals(item.assigneeUserId()) && store.isExecutor(org, root, role, userId);
        }
        return switch (action) {
            case APPROVE_PRODUCT_REVIEW -> java.util.Arrays.stream(RequirementParticipantRole.values())
                    .anyMatch(role -> leader(org, item.id(), role.name(), userId) && !store.hasVote(org, item.id(), role.name()));
            case REJECT_PRODUCT_REVIEW -> java.util.Arrays.stream(RequirementParticipantRole.values())
                    .anyMatch(role -> leader(org, item.id(), role.name(), userId));
            case SUBMIT_PRODUCT_REVIEW -> member(org, item.id(), "PRODUCT", userId) || item.reporterUserId() == userId;
            case APPROVE_REQUIREMENT, REJECT_REQUIREMENT -> leader(org, item.id(), "PRODUCT", userId);
            /* 验收由产品领域成员（负责人或该需求产品执行人）确认交付物，与 QA_PASS、SUBMIT_UX_REVIEW 的成员级结论一致（ADR-024）。 */
            case ACCEPT_REQUIREMENT -> member(org, item.id(), "PRODUCT", userId);
            /* 四方评审由产品负责人或该需求产品域执行人发起，与提交需求审核保持同一产品侧职责。 */
            case START_PRODUCT_REVIEW -> member(org, item.id(), "PRODUCT", userId);
            case START_UX, APPROVE_UX_REVIEW, REJECT_UX_REVIEW -> leader(org, item.id(), "UX", userId);
            /* UX 阶段评审由本需求 UX 域成员（设计师执行人或 UX 负责人）在交付物齐备后自行提交，与产品侧提交保持同一职责边界。 */
            case SUBMIT_UX_REVIEW -> member(org, item.id(), "UX", userId);
            /* 阶段开工由领域负责人确认任务并启动，与 START_UX 保持同一职责；阶段提交与结论归完成该阶段工作的执行人。 */
            case START_DEVELOPMENT -> leader(org, item.id(), "DEVELOPER", userId);
            case SUBMIT_FOR_QA -> member(org, item.id(), "DEVELOPER", userId);
            case START_QA -> leader(org, item.id(), "QA", userId);
            case QA_PASS, QA_FAIL -> member(org, item.id(), "QA", userId);
            default -> false;
        };
    }

    public void requireAction(long userId, WorkItem item, WorkflowAction action) {
        if (!canAct(userId, item, action)) throw new ResourceNotFoundException();
    }

    public void requireCreation(long userId, WorkItem requirement, WorkItemType type, Long assignee) {
        String role = role(type);
        if (requirement.type() != WorkItemType.REQUIREMENT) throw new InvalidTransitionException();
        if (type == WorkItemType.BUG) {
            if (!member(requirement.organizationId(), requirement.id(), "QA", userId)) throw new ResourceNotFoundException();
            if (requirement.status() != WorkItemStatus.IN_QA && requirement.status() != WorkItemStatus.IN_FIX_VERIFICATION)
                throw new InvalidTransitionException();
        } else {
            if (!leader(requirement.organizationId(), requirement.id(), role, userId)) throw new ResourceNotFoundException();
            WorkItemStatus expected = switch (type) {
                case UX_TASK -> WorkItemStatus.READY_FOR_UX;
                case DEV_TASK -> WorkItemStatus.READY_FOR_DEV;
                case QA_TASK -> WorkItemStatus.READY_FOR_QA;
                default -> throw new InvalidTransitionException();
            };
            if (requirement.status() != expected) throw new InvalidTransitionException();
        }
        requireAssignee(requirement.organizationId(), requirement.id(), type, assignee);
    }

    public void requireAssignee(long org, long requirementId, WorkItemType type, Long assignee) {
        if (assignee == null || !store.isExecutor(org, requirementId, role(type), assignee)) throw new ResourceNotFoundException();
    }

    public List<String> missing(WorkItem item, WorkflowAction action) {
        List<String> missing = new ArrayList<>();
        long org = item.organizationId();
        long id = item.id();
        if (item.type() != WorkItemType.REQUIREMENT) {
            Long parent = store.requirementId(org, id);
            String expected = switch (item.type()) {
                case UX_TASK -> "UX_IN_PROGRESS";
                case DEV_TASK -> "IN_DEVELOPMENT";
                case QA_TASK -> "IN_QA";
                default -> "IN_QA";
            };
            String actual = parent == null ? null : store.requirementStatus(org, parent);
            if (!expected.equals(actual) && !(item.type() == WorkItemType.QA_TASK && "IN_FIX_VERIFICATION".equals(actual)))
                missing.add("activeTaskStage");
            return missing;
        }
        if (action == WorkflowAction.APPROVE_REQUIREMENT || action == WorkflowAction.START_PRODUCT_REVIEW || action == WorkflowAction.APPROVE_PRODUCT_REVIEW) {
            var assigned = participants.findParticipants(org, id);
            for (RequirementParticipantRole role : RequirementParticipantRole.values()) {
                if (assigned.stream().noneMatch(p -> p.role() == role && leader(org, id, role.name(), p.userId()))) missing.add("leader:" + role);
                if (store.executors(org, id, role.name()).stream().noneMatch(user -> store.isExecutor(org, id, role.name(), user))) missing.add("executor:" + role);
            }
        }
        String startType = switch (action) {
            case START_UX -> "UX_TASK";
            case START_DEVELOPMENT -> "DEV_TASK";
            case START_QA -> "QA_TASK";
            default -> null;
        };
        if (startType != null && store.countTasks(org, id, startType) == 0) missing.add("task:" + startType);
        if (action == WorkflowAction.SUBMIT_UX_REVIEW && (store.countTasks(org, id, "UX_TASK") == 0 || store.countIncompleteTasks(org, id, "UX_TASK") > 0)) missing.add("unfinishedUxTasks");
        if (action == WorkflowAction.QA_FAIL || action == WorkflowAction.QA_PASS) {
            if (!store.allCasesExecuted(org, id)) missing.add("allTestCasesExecuted");
        }
        if (action == WorkflowAction.QA_PASS || action == WorkflowAction.ACCEPT_REQUIREMENT) {
            if (store.incompleteBugs(org, id) > 0) missing.add("unfinishedBugs");
            if (store.countIncompleteTasks(org, id, "QA_TASK") > 0) missing.add("unfinishedQaTasks");
        }
        return missing;
    }

    public TransitionDefinition recordReview(long userId, WorkItem item, TransitionDefinition definition) {
        if (definition.action() == WorkflowAction.START_PRODUCT_REVIEW || definition.action() == WorkflowAction.REJECT_PRODUCT_REVIEW)
            store.clearVotes(item.organizationId(), item.id());
        if (definition.action() != WorkflowAction.APPROVE_PRODUCT_REVIEW) return definition;
        for (RequirementParticipantRole role : RequirementParticipantRole.values()) {
            if (leader(item.organizationId(), item.id(), role.name(), userId) && !store.hasVote(item.organizationId(), item.id(), role.name()))
                store.vote(item.organizationId(), item.id(), role.name(), userId);
        }
        if (store.countVotes(item.organizationId(), item.id()) == 4) return definition;
        return new TransitionDefinition(definition.type(), definition.from(), definition.action(), definition.from(), definition.requiredPermission(), definition.guards());
    }

    public static String role(WorkItemType type) {
        return switch (type) {
            case UX_TASK -> "UX";
            case DEV_TASK, BUG -> "DEVELOPER";
            case QA_TASK -> "QA";
            default -> "PRODUCT";
        };
    }
}
