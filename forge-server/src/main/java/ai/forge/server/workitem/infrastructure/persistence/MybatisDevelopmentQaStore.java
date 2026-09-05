package ai.forge.server.workitem.infrastructure.persistence;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workitem.application.DevelopmentQaStore;
import ai.forge.server.workitem.application.DevelopmentQaSummary;
import ai.forge.server.workitem.application.DevelopmentQaTask;
import ai.forge.server.workitem.domain.WorkItemStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test-unit")
public class MybatisDevelopmentQaStore implements DevelopmentQaStore {

    /* 执行显式携带租户与项目范围的研发汇总 SQL。 */
    private final DevelopmentQaMapper mapper;

    public MybatisDevelopmentQaStore(DevelopmentQaMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DevelopmentQaSummary summarize(long workspaceId, long projectId, long requirementId) {
        Map<String, Object> header = mapper.findHeader(workspaceId, projectId, requirementId).stream()
                .findFirst()
                .orElseThrow(ResourceNotFoundException::new);
        return new DevelopmentQaSummary(
                requirementId,
                booleanValue(header.get("ci_required")),
                booleanValue(header.get("repository_configured")),
                mapper.findTasks(workspaceId, projectId, requirementId).stream()
                        .map(this::task)
                        .toList());
    }

    private DevelopmentQaTask task(Map<String, Object> row) {
        LocalDateTime syncedAt = (LocalDateTime) row.get("last_synced_at");
        return new DevelopmentQaTask(
                number(row, "id"),
                text(row, "item_key"),
                text(row, "title"),
                WorkItemStatus.valueOf(text(row, "status")),
                number(row, "version"),
                nullableText(row, "branch_name"),
                nullableText(row, "branch_commit_sha"),
                nullableNumber(row, "merge_request_id"),
                nullableText(row, "merge_request_url"),
                nullableText(row, "merge_request_head_sha"),
                nullableNumber(row, "pipeline_id"),
                nullableText(row, "pipeline_commit_sha"),
                nullableText(row, "pipeline_status"),
                syncedAt == null ? null : syncedAt.toInstant(ZoneOffset.UTC));
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static Long nullableNumber(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).longValue();
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private static String nullableText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean booleanValue
                ? booleanValue
                : ((Number) value).intValue() != 0;
    }
}
