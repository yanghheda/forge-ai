package ai.forge.server.console.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.organization.application.OrganizationAccessService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class ConsoleService {
    /* 看板允许持久化的固定泳道。 */
    private static final Set<String> LANES = Set.of("TODO", "IN_PROGRESS", "BLOCKED", "DONE");
    /* 解析当前用户唯一公司上下文。 */ private final OrganizationAccessService access;
    /* 执行公司权限校验。 */ private final PermissionEvaluator permissions;
    /* 执行控制台聚合查询与看板写入。 */ private final ConsoleStore mapper;

    public ConsoleService(OrganizationAccessService access, PermissionEvaluator permissions, ConsoleStore mapper) {
        this.access = access; this.permissions = permissions; this.mapper = mapper;
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

    public ConsoleModels.Dashboard dashboard(long userId) {
        long organizationId=organization(userId); permissions.requireOrganization(userId,organizationId,"requirement.read");
        Map<String,Long> stages=new LinkedHashMap<>(); mapper.stages(organizationId).forEach(row->stages.put(text(row,"status"),number(row,"total")));
        Map<String,Long> values=new LinkedHashMap<>(); mapper.trend(organizationId).forEach(row->values.put(row.get("day").toString(),number(row,"total")));
        List<ConsoleModels.TrendPoint> trend=java.util.stream.IntStream.rangeClosed(0,6).mapToObj(offset->{String day=LocalDate.now(ZoneOffset.UTC).minusDays(6-offset).toString(); return new ConsoleModels.TrendPoint(day,values.getOrDefault(day,0L));}).toList();
        return new ConsoleModels.Dashboard(mapper.weeklyDeliveries(organizationId),mapper.activeAgents(organizationId),mapper.personalTodos(organizationId,userId),stages,trend);
    }

    public List<ConsoleModels.BoardItem> board(long userId) {
        long organizationId=organization(userId); permissions.requireOrganization(userId,organizationId,"task.read");
        return mapper.board(organizationId).stream().map(row->new ConsoleModels.BoardItem(number(row,"id"),text(row,"item_key"),text(row,"type"),text(row,"title"),text(row,"lane"),number(row,"position"),nullableNumber(row,"assignee_user_id"),number(row,"version"))).toList();
    }

    public void move(long userId,long workItemId,String lane,long position) {
        long organizationId=organization(userId); permissions.requireOrganization(userId,organizationId,"task.edit");
        if(!LANES.contains(lane)||position<0) throw new IllegalArgumentException("invalid board position");
        if(mapper.workItemExists(organizationId,workItemId)!=1) throw new ResourceNotFoundException();
        mapper.savePosition(organizationId,userId,workItemId,lane,position);
    }

    private long organization(long userId){return access.requireContext(userId).organizationId();}
    private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static Long nullableNumber(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:((Number)value).longValue();}
    private static String text(Map<String,Object> row,String key){return row.get(key).toString();}
    private static String nullableText(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:value.toString();}
    private static java.time.Instant instant(Object value){return value==null?null:((LocalDateTime)value).toInstant(ZoneOffset.UTC);}
}
