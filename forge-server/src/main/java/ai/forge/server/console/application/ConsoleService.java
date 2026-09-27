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

    public ConsoleService(OrganizationAccessService access, PermissionEvaluator permissions, ConsoleStore mapper, ai.forge.server.workitem.application.WorkItemStore workItems) {
        this.access = access; this.permissions = permissions; this.mapper = mapper; this.workItems = workItems;
    }

    public List<ConsoleModels.SearchResult> search(long userId, String query) {
        long organizationId = organization(userId); permissions.requireOrganization(userId, organizationId, "requirement.read");
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2 || normalized.length() > 100) throw new IllegalArgumentException("query length must be 2..100");
        return mapper.search(organizationId, normalized).stream().map(row -> new ConsoleModels.SearchResult(
                text(row,"type"), text(row,"id"), text(row,"title"), text(row,"subtitle"))).toList();
    }

    public List<ConsoleModels.NotificationView> notifications(long userId, int limit) {
        long organizationId = organization(userId);
        return mapper.notifications(organizationId,userId,Math.max(1,Math.min(limit,100))).stream().map(row ->
                new ConsoleModels.NotificationView(number(row,"id"),text(row,"type"),text(row,"title"),text(row,"body"),
                        nullableText(row,"resource_type"),nullableText(row,"resource_id"),instant(row.get("read_at")),instant(row.get("created_at")))).toList();
    }

    public long unread(long userId) { long organizationId=organization(userId); return mapper.unread(organizationId,userId); }
    public void markRead(long userId,long id) { long organizationId=organization(userId); if(mapper.markRead(organizationId,userId,id)!=1) throw new ResourceNotFoundException(); }

    public List<ConsoleModels.BoardItem> board(long userId) {
        long organizationId=organization(userId); permissions.requireOrganization(userId,organizationId,"task.read");
        return mapper.board(organizationId).stream().map(row->new ConsoleModels.BoardItem(number(row,"id"),text(row,"item_key"),text(row,"type"),text(row,"title"),text(row,"lane"),number(row,"position"),nullableNumber(row,"assignee_user_id"),number(row,"version"),nullableNumber(row,"requirement_id"),text(row,"priority"),instant(row.get("due_at")),nullableText(row,"assignee_name"),nullableText(row,"blocked_reason"))).toList();
    }

    public List<ConsoleModels.RequirementOption> boardRequirements(long userId,Long assigneeUserId) {
        long organizationId=organization(userId); permissions.requireOrganization(userId,organizationId,"task.read");
        return mapper.requirementsByParticipant(organizationId,assigneeUserId).stream().map(row->new ConsoleModels.RequirementOption(number(row,"id"),text(row,"item_key"),text(row,"title"))).toList();
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
