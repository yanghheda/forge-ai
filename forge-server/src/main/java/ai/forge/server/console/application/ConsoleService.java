package ai.forge.server.console.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.organization.application.OrganizationAccessService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class ConsoleService {
    /* 看板允许持久化的固定泳道。 */
    private static final Set<String> LANES = Set.of("TODO", "IN_PROGRESS", "BLOCKED", "WAITING_VERIFICATION", "DONE");
    /* 解析当前用户唯一公司上下文。 */ private final OrganizationAccessService access;
    /* 执行公司权限校验。 */ private final PermissionEvaluator permissions;
    /* 执行控制台聚合查询与看板写入。 */ private final ConsoleStore mapper;

    /* 看板排序不能伪造工作项业务状态。 */
    private final ai.forge.server.workitem.application.WorkItemStore workItems;

    /* 按与服务端流转一致的规则投影任务卡片可执行动作。 */
    private final ai.forge.server.workitem.application.RequirementTransitionService transitions;

    /* 任务卡片的空动作投影，用于缺陷与不可操作场景。 */
    private static final ai.forge.server.workitem.application.RequirementTransitionService.ActionHints NO_HINTS =
            new ai.forge.server.workitem.application.RequirementTransitionService.ActionHints(java.util.List.of(), java.util.Map.of());

    public ConsoleService(OrganizationAccessService access, PermissionEvaluator permissions, ConsoleStore mapper,
            ai.forge.server.workitem.application.WorkItemStore workItems,
            ai.forge.server.workitem.application.RequirementTransitionService transitions) {
        this.access = access; this.permissions = permissions; this.mapper = mapper; this.workItems = workItems; this.transitions = transitions;
    }

    public List<ConsoleModels.SearchResult> search(long userId, String query) {
        long organizationId = organization(userId); permissions.requireOrganization(userId, organizationId, "requirement.read");
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2 || normalized.length() > 100) throw new IllegalArgumentException("query length must be 2..100");
        return mapper.search(organizationId, normalized).stream().map(row -> new ConsoleModels.SearchResult(
                text(row,"type"), text(row,"id"), text(row,"title"), text(row,"subtitle"))).toList();
    }

    public List<ConsoleModels.BoardItem> board(long userId) {
        long organizationId=organization(userId); permissions.requireOrganization(userId,organizationId,"task.read");
        List<Map<String,Object>> rows = mapper.board(organizationId);
        Map<Long, ai.forge.server.workitem.domain.WorkItem> items = new java.util.HashMap<>();
        for (var item : workItems.findByIdsAndScope(organizationId, rows.stream().map(row -> number(row,"id")).toList())) items.put(item.id(), item);
        Map<String, ai.forge.server.workitem.application.RequirementTransitionService.ActionHints> memo = new java.util.HashMap<>();
        return rows.stream().map(row -> {
            var hints = hints(userId, row, items, memo);
            return new ConsoleModels.BoardItem(number(row,"id"),text(row,"item_key"),text(row,"type"),text(row,"title"),text(row,"lane"),number(row,"position"),nullableNumber(row,"assignee_user_id"),number(row,"version"),nullableNumber(row,"requirement_id"),text(row,"priority"),instant(row.get("due_at")),nullableText(row,"assignee_name"),nullableText(row,"blocked_reason"),hints.availableActions(),hints.guardHints());
        }).toList();
    }

    /* 看板只对角色任务投影可执行动作；同需求同类型同状态且执行人归属一致时复用一次计算结果。 */
    private ai.forge.server.workitem.application.RequirementTransitionService.ActionHints hints(
            long userId, Map<String,Object> row, Map<Long, ai.forge.server.workitem.domain.WorkItem> items,
            Map<String, ai.forge.server.workitem.application.RequirementTransitionService.ActionHints> memo) {
        String type = text(row,"type");
        if (!type.endsWith("_TASK")) return NO_HINTS;
        ai.forge.server.workitem.domain.WorkItem item = items.get(number(row,"id"));
        if (item == null) return NO_HINTS;
        String key = type + "|" + text(row,"lane") + "|" + nullableNumber(row,"requirement_id") + "|" + Long.valueOf(userId).equals(nullableNumber(row,"assignee_user_id"));
        var cached = memo.get(key);
        if (cached != null) return cached;
        var computed = transitions.hints(userId, item);
        memo.put(key, computed);
        return computed;
    }

    public List<ConsoleModels.RequirementOption> boardRequirements(long userId,Long assigneeUserId) {
        long organizationId=organization(userId); permissions.requireOrganization(userId,organizationId,"task.read");
        return mapper.requirementsByParticipant(organizationId,assigneeUserId).stream().map(row->new ConsoleModels.RequirementOption(number(row,"id"),text(row,"item_key"),text(row,"title"))).toList();
    }

    public List<ConsoleModels.TaskCreationOption> taskCreationOptions(long userId) {
        long organizationId=organization(userId); permissions.requireOrganization(userId,organizationId,"task.read");
        Map<Long,Map<String,Object>> heads=new java.util.LinkedHashMap<>();
        Map<Long,List<ConsoleModels.ExecutorOption>> executors=new java.util.LinkedHashMap<>();
        Map<String,Boolean> creationAllowed=new java.util.HashMap<>();
        for (Map<String,Object> row : mapper.taskCreationOptions(organizationId,userId)) {
            ai.forge.server.workitem.domain.WorkItemType taskType=creationTaskType(text(row,"requirement_status"));
            if (taskType==null) continue;
            /* 创建权限按任务类型对应的模块资源校验（QA_TASK 属 qa 模块），不改变需求阶段判定。 */
            if (!creationAllowed.computeIfAbsent(taskType.name(),key->permissions.hasOrganizationPermission(userId,organizationId,taskType.permissionResource()+".create"))) continue;
            long requirementId=number(row,"requirement_id");
            heads.putIfAbsent(requirementId,row);
            List<ConsoleModels.ExecutorOption> options=executors.computeIfAbsent(requirementId,key->new java.util.ArrayList<>());
            Long executorUserId=nullableNumber(row,"executor_user_id");
            if (executorUserId!=null && options.stream().noneMatch(option->option.userId()==executorUserId)) options.add(new ConsoleModels.ExecutorOption(executorUserId,nullableText(row,"executor_display_name")));
        }
        return heads.entrySet().stream().map(entry->{
            Map<String,Object> row=entry.getValue(); long requirementId=entry.getKey();
            return new ConsoleModels.TaskCreationOption(requirementId,text(row,"requirement_item_key"),text(row,"requirement_title"),
                    creationTaskType(text(row,"requirement_status")),List.copyOf(executors.get(requirementId)));
        }).toList();
    }

    /* 需求准备态唯一决定可创建的任务类型，与 DeliveryWorkflowPolicy.requireCreation 保持同一映射。 */
    private static ai.forge.server.workitem.domain.WorkItemType creationTaskType(String status) {
        return switch (status) {
            case "READY_FOR_UX" -> ai.forge.server.workitem.domain.WorkItemType.UX_TASK;
            case "READY_FOR_DEV" -> ai.forge.server.workitem.domain.WorkItemType.DEV_TASK;
            case "READY_FOR_QA" -> ai.forge.server.workitem.domain.WorkItemType.QA_TASK;
            default -> null;
        };
    }

    public void move(long userId,long workItemId,String lane,long position) {
        long organizationId=organization(userId);
        if(!LANES.contains(lane)||position<0) throw new IllegalArgumentException("invalid board position");
        if(mapper.workItemExists(organizationId,workItemId)!=1) throw new ResourceNotFoundException();
        var item = workItems.findByIdAndScope(organizationId,workItemId).orElseThrow(ResourceNotFoundException::new);
        /* 看板排序是工作项所属模块的写操作，按类型取该模块的编辑权限，而不是跨模块的通用任务权限。 */
        permissions.requireOrganization(userId,organizationId,item.type().permissionResource()+".edit");
        if (!item.status().name().equals(lane)) throw new IllegalArgumentException("Use task actions to change status before sorting");
        mapper.savePosition(organizationId,userId,workItemId,lane,position);
    }

    private long organization(long userId){return access.requireContext(userId).organizationId();}
    private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static Long nullableNumber(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:((Number)value).longValue();}
    private static String text(Map<String,Object> row,String key){return row.get(key).toString();}
    private static String nullableText(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:value.toString();}
    private static java.time.Instant instant(Object value){return value==null?null:((LocalDateTime)value).toInstant(ZoneOffset.UTC);}
}
