package ai.forge.server.workitem.infrastructure.persistence;

import ai.forge.server.workitem.application.DeliveryGraphStore;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test-unit")
public class MybatisDeliveryGraphStore implements DeliveryGraphStore {

    /* 使用三条带完整租户范围的查询构成交付图一致性快照。 */
    private final DeliveryGraphMapper mapper;

    public MybatisDeliveryGraphStore(DeliveryGraphMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Snapshot load(long workspaceId, long projectId) {
        return new Snapshot(
                mapper.findItems(workspaceId, projectId).stream().map(this::item).toList(),
                mapper.findRelations(workspaceId, projectId).stream().map(this::relation).toList(),
                mapper.findDocuments(workspaceId, projectId).stream().map(this::document).toList());
    }

    private Item item(Map<String, Object> row) {
        return new Item(
                number(row, "id"),
                nullableNumber(row, "parent_id"),
                WorkItemType.valueOf(text(row, "type")),
                text(row, "item_key"),
                text(row, "title"),
                WorkItemStatus.valueOf(text(row, "status")));
    }

    private Relation relation(Map<String, Object> row) {
        return new Relation(
                number(row, "id"),
                number(row, "source_id"),
                number(row, "target_id"),
                text(row, "relation_type"));
    }

    private Document document(Map<String, Object> row) {
        return new Document(
                number(row, "id"),
                number(row, "work_item_id"),
                text(row, "type"),
                text(row, "title"),
                text(row, "status"));
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private Long nullableNumber(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).longValue();
    }

    private String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }
}
