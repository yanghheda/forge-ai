package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.notification.application.NotificationDraft;
import ai.forge.server.notification.application.NotificationService;
import ai.forge.server.notification.domain.NotificationTarget;
import ai.forge.server.notification.domain.NotificationType;
import ai.forge.server.workitem.domain.GuardResult;
import ai.forge.server.workitem.domain.IdempotencyConflictException;
import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.RequirementWorkflowRegistry;
import ai.forge.server.workitem.domain.TransitionContext;
import ai.forge.server.workitem.domain.TransitionDefinition;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemEvent;
import ai.forge.server.workitem.domain.WorkflowAction;
import ai.forge.server.workitem.domain.WorkflowGuardFailedException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class RequirementTransitionService {

    /* 读取带公司范围的 Work Item 当前事实。 */
    private final WorkItemStore workItemStore;

    /* 在资源加载后执行动作对应的最终权限检查。 */
    private final PermissionEvaluator permissionEvaluator;

    /* 将 Action 解析为服务端固定的 From、To、权限与 Guard。 */
    private final RequirementWorkflowRegistry workflowRegistry;

    /* 原子写入状态、评审记录和追加活动事件。 */
    private final RequirementTransitionStore transitionStore;

    /* 对所有调用入口执行需求内职责与阶段准入校验。 */
    private final DeliveryWorkflowPolicy policy;

    /* 流转成功推进时，在业务事务内按"动作 → 下一责任方"写入评审与交接通知。 */
    private final NotificationService notificationService;

    public RequirementTransitionService(
            WorkItemStore workItemStore,
            PermissionEvaluator permissionEvaluator,
            RequirementWorkflowRegistry workflowRegistry,
            RequirementTransitionStore transitionStore, DeliveryWorkflowPolicy policy,
            NotificationService notificationService) {
        this.workItemStore = workItemStore;
        this.permissionEvaluator = permissionEvaluator;
        this.workflowRegistry = workflowRegistry;
        this.transitionStore = transitionStore;
        this.policy = policy;
        this.notificationService = notificationService;
    }

    @Transactional
    public RequirementTransitionStore.TransitionResult transition(
            long userId,
            long organizationId,
            long workItemId,
            WorkflowAction action,
            long expectedVersion,
            String idempotencyKey,
            String reason) {
        return transition(
                userId,
                organizationId,
                workItemId,
                action,
                expectedVersion,
                idempotencyKey,
                reason,
                null);
    }

    @Transactional
    public RequirementTransitionStore.TransitionResult transition(
            long userId,
            long organizationId,
            long workItemId,
            WorkflowAction action,
            long expectedVersion,
            String idempotencyKey,
            String reason,
            List<String> checklist) {
        policy.lock(organizationId, workItemId);
        WorkItem current = workItemStore.findByIdAndScope(organizationId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
        Optional<RequirementTransitionStore.TransitionResult> previous =
                transitionStore.findResultByIdempotencyKey(organizationId, workItemId, idempotencyKey);
        if (previous.isPresent()) {
            if (previous.get().action() != action) {
                throw new IdempotencyConflictException();
            }
            TransitionDefinition replayDefinition = workflowRegistry.requireAction(current.type(), action);
            permissionEvaluator.requireOrganization(
                    userId, organizationId, replayDefinition.requiredPermission());
            policy.requireAction(userId, current, action == WorkflowAction.APPROVE_PRODUCT_REVIEW
                    ? WorkflowAction.REJECT_PRODUCT_REVIEW : action);
            return previous.get();
        }
        if (current.version() != expectedVersion) throw new ai.forge.server.common.domain.VersionConflictException();
        TransitionDefinition definition = workflowRegistry.require(current.type(), current.status(), action);
        permissionEvaluator.requireOrganization(userId, organizationId, definition.requiredPermission());
        policy.requireAction(userId, current, action);
        LinkedHashSet<String> missing = new LinkedHashSet<>(policy.missing(current, action));
        TransitionContext context = new TransitionContext(current, normalizeReason(reason), checklist);
        for (var guard : definition.guards()) {
            GuardResult result = guard.evaluate(context);
            missing.addAll(result.missing());
        }
        if (!missing.isEmpty()) {
            throw new WorkflowGuardFailedException(List.copyOf(missing));
        }
        RequirementTransitionStore.TransitionResult result = transitionStore.transition(
                organizationId,
                workItemId,
                userId,
                expectedVersion,
                idempotencyKey,
                context.reason(),
                policy.recordReview(userId, current, definition),
                context.checklist());
        notifyHandoff(userId, organizationId, current, result);
        return result;
    }

    /*
     * 仅在流转成功且状态真正推进时按"流转动作 → 下一责任方"发送通知：幂等重放分支已提前返回，
     * 不会命中这里；未集齐四方票的 APPROVE_PRODUCT_REVIEW 因目标状态回退到原状态而不通知；
     * 收件人由 NotificationService 去重并自动排除操作者本人。未列入映射的动作不通知。
     */
    private void notifyHandoff(long actorUserId, long organizationId, WorkItem current,
            RequirementTransitionStore.TransitionResult result) {
        if (result.status() == current.status()) return;
        long requirementId = current.id();
        List<Long> recipients;
        NotificationType type;
        NotificationTarget target;
        String title;
        String body;
        switch (result.action()) {
            case SUBMIT_PRODUCT_REVIEW -> {
                recipients = requirementLeaders(organizationId, requirementId, RequirementParticipantRole.PRODUCT);
                type = NotificationType.REVIEW_REQUESTED;
                target = NotificationTarget.REQUIREMENT;
                title = "待评审：" + current.itemKey();
                body = "需求已提交产品评审，请你作为产品负责人及时评审。";
            }
            case START_PRODUCT_REVIEW -> {
                recipients = new ArrayList<>();
                for (RequirementParticipantRole role : RequirementParticipantRole.values()) {
                    recipients.addAll(requirementLeaders(organizationId, requirementId, role));
                }
                type = NotificationType.REVIEW_REQUESTED;
                target = NotificationTarget.REQUIREMENT;
                title = "待会签：" + current.itemKey();
                body = "需求已进入四方联合评审，请你作为领域负责人确认会签。";
            }
            case APPROVE_PRODUCT_REVIEW -> {
                recipients = requirementLeaders(organizationId, requirementId, RequirementParticipantRole.UX);
                type = NotificationType.REQUIREMENT_HANDOFF;
                target = NotificationTarget.REQUIREMENT;
                title = current.itemKey() + " 进入 UX 阶段";
                body = "产品四方评审已通过，需求进入 UX 阶段，请你作为 UX 负责人安排设计。";
            }
            case SUBMIT_UX_REVIEW -> {
                recipients = requirementLeaders(organizationId, requirementId, RequirementParticipantRole.UX);
                type = NotificationType.REVIEW_REQUESTED;
                target = NotificationTarget.REQUIREMENT;
                title = "待评审：" + current.itemKey();
                body = "需求已提交 UX 评审，请你作为 UX 负责人及时评审。";
            }
            case APPROVE_UX_REVIEW -> {
                recipients = requirementLeaders(organizationId, requirementId, RequirementParticipantRole.DEVELOPER);
                type = NotificationType.REQUIREMENT_HANDOFF;
                target = NotificationTarget.REQUIREMENT;
                title = current.itemKey() + " 进入开发阶段";
                body = "UX 评审已通过，需求进入研发准备，请你作为开发负责人安排实现。";
            }
            case SUBMIT_FOR_QA -> {
                recipients = requirementLeaders(organizationId, requirementId, RequirementParticipantRole.QA);
                type = NotificationType.REQUIREMENT_HANDOFF;
                target = NotificationTarget.REQUIREMENT;
                title = current.itemKey() + " 待质量验证";
                body = "开发任务已完成并提交质量验证，请你作为测试负责人安排测试。";
            }
            case QA_FAIL -> {
                recipients = requirementMembers(organizationId, requirementId, RequirementParticipantRole.DEVELOPER);
                type = NotificationType.REQUIREMENT_HANDOFF;
                target = NotificationTarget.REQUIREMENT_DEVELOPMENT;
                title = current.itemKey() + " QA 未通过，请安排修复";
                body = "质量验证未通过，需求已回流研发，请你安排修复后重新提交。";
            }
            case QA_PASS -> {
                recipients = requirementMembers(organizationId, requirementId, RequirementParticipantRole.PRODUCT);
                type = NotificationType.REQUIREMENT_HANDOFF;
                target = NotificationTarget.REQUIREMENT;
                title = current.itemKey() + " 已通过质量验证";
                body = "质量验证已通过，需求进入验收，请你作为产品负责人确认验收。";
            }
            case ACCEPT_REQUIREMENT -> {
                recipients = requirementLeaders(organizationId, requirementId, RequirementParticipantRole.DEVELOPER);
                type = NotificationType.REQUIREMENT_HANDOFF;
                target = NotificationTarget.REQUIREMENT_RELEASE;
                title = current.itemKey() + " 已进入发布准备";
                body = "产品已确认验收，需求进入发布准备，请你作为开发负责人安排发布。";
            }
            case REJECT_REQUIREMENT, REJECT_PRODUCT_REVIEW -> {
                recipients = List.of(current.reporterUserId());
                type = NotificationType.REQUIREMENT_HANDOFF;
                target = NotificationTarget.REQUIREMENT;
                title = current.itemKey() + " 被退回，请补充后重新提交";
                body = "需求已被退回草稿，请你补充材料后重新提交。";
            }
            case REJECT_UX_REVIEW -> {
                recipients = requirementMembers(organizationId, requirementId, RequirementParticipantRole.UX);
                type = NotificationType.REQUIREMENT_HANDOFF;
                target = NotificationTarget.REQUIREMENT_UX;
                title = current.itemKey() + " UX 评审未通过";
                body = "UX 评审被退回，需求返回交付物补充状态，请你补充材料后重新提交。";
            }
            default -> {
                return;
            }
        }
        notificationService.notify(new NotificationDraft(
                organizationId, recipients, actorUserId, type, title, body, target, Long.toString(requirementId)));
    }

    /* 按领域角色反查该需求负责人，供流转通知组装收件人。 */
    private List<Long> requirementLeaders(long organizationId, long requirementId, RequirementParticipantRole role) {
        return notificationService.requirementLeaders(organizationId, requirementId, role.name());
    }

    /* 按领域角色反查该需求负责人与执行人并集，供流转通知组装收件人。 */
    private List<Long> requirementMembers(long organizationId, long requirementId, RequirementParticipantRole role) {
        return notificationService.requirementMembers(organizationId, requirementId, role.name());
    }

    public List<WorkItemEvent> events(
            long userId, long organizationId, long workItemId) {
        WorkItem item = workItemStore.findByIdAndScope(organizationId, workItemId)
                .orElseThrow(ResourceNotFoundException::new);
        permissionEvaluator.requireOrganization(
                userId, organizationId, item.type().permissionResource() + ".read");
        return transitionStore.findEvents(organizationId, workItemId);
    }

    public ActionHints hints(long userId, WorkItem item) {
        List<WorkflowAction> actions = new java.util.ArrayList<>();
        Map<WorkflowAction, List<String>> guardHints = new LinkedHashMap<>();
        for (TransitionDefinition definition : workflowRegistry.definitions()) {
            if (definition.type() != item.type() || definition.from() != item.status()
                    || !policy.canAct(userId, item, definition.action())
                    || !permissionEvaluator.hasOrganizationPermission(
                            userId, item.organizationId(), definition.requiredPermission())) {
                continue;
            }
            actions.add(definition.action());
            LinkedHashSet<String> missing = new LinkedHashSet<>(policy.missing(item, definition.action()));
            TransitionContext context = new TransitionContext(item, null, null);
            definition.guards().forEach(guard -> missing.addAll(guard.evaluate(context).missing()));
            if (!missing.isEmpty()) {
                guardHints.put(definition.action(), List.copyOf(missing));
            }
        }
        return new ActionHints(List.copyOf(actions), Map.copyOf(guardHints));
    }

    public record ActionHints(
            /* 当前用户在当前状态可尝试执行的动作。 */ List<WorkflowAction> availableActions,
            /* 各动作尚缺少的确定性准入材料。 */ Map<WorkflowAction, List<String>> guardHints) {}

    private String normalizeReason(String reason) {
        return reason == null ? null : reason.trim();
    }
}
