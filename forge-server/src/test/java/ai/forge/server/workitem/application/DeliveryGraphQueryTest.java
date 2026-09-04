package ai.forge.server.workitem.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.authorization.application.PermissionStore;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeliveryGraphQueryTest {

    @Test
    void returnsRootOnlyForAnEmptyGraph() {
        DeliveryGraph graph = query(snapshot(
                List.of(item(1, null, WorkItemType.REQUIREMENT)), List.of(), List.of()), allPermissions()).get(
                7, 10, 20, 1);

        assertThat(graph.nodes()).extracting(DeliveryGraph.Node::id).containsExactly("work-item:1");
        assertThat(graph.edges()).isEmpty();
        assertThat(graph.truncated()).isFalse();
    }

    @Test
    void traversesCyclesOnceAndIncludesDocuments() {
        DeliveryGraphStore.Snapshot snapshot = snapshot(
                List.of(
                        item(1, null, WorkItemType.REQUIREMENT),
                        item(2, null, WorkItemType.UX_TASK),
                        item(3, null, WorkItemType.DEV_TASK)),
                List.of(relation(1, 1, 2), relation(2, 2, 3), relation(3, 3, 1)),
                List.of(new DeliveryGraphStore.Document(8, 1, "PRD", "Login PRD", "PUBLISHED")));

        DeliveryGraph graph = query(snapshot, allPermissions()).get(7, 10, 20, 1);

        assertThat(graph.nodes()).extracting(DeliveryGraph.Node::id)
                .containsExactly("work-item:1", "work-item:2", "work-item:3", "document:8");
        assertThat(graph.edges()).hasSize(4);
        assertThat(graph.truncated()).isFalse();
    }

    @Test
    void filtersUnauthorizedNodesAndDoesNotTraverseThroughThem() {
        DeliveryGraphStore.Snapshot snapshot = snapshot(
                List.of(
                        item(1, null, WorkItemType.REQUIREMENT),
                        item(2, null, WorkItemType.UX_TASK),
                        item(3, null, WorkItemType.DEV_TASK)),
                List.of(relation(1, 1, 2), relation(2, 2, 3)),
                List.of(new DeliveryGraphStore.Document(8, 1, "PRD", "Private PRD", "PUBLISHED")));

        DeliveryGraph graph = query(snapshot, Set.of("requirement.read", "task.read")).get(7, 10, 20, 1);

        assertThat(graph.nodes()).extracting(DeliveryGraph.Node::id).containsExactly("work-item:1");
        assertThat(graph.edges()).isEmpty();
    }

    @Test
    void truncatesAtFiveHundredNodes() {
        List<DeliveryGraphStore.Item> items = new ArrayList<>();
        List<DeliveryGraphStore.Relation> relations = new ArrayList<>();
        items.add(item(1, null, WorkItemType.REQUIREMENT));
        for (long id = 2; id <= 501; id++) {
            items.add(item(id, null, WorkItemType.UX_TASK));
            relations.add(relation(id, 1, id));
        }

        DeliveryGraph graph = query(snapshot(items, relations, List.of()), allPermissions()).get(7, 10, 20, 1);

        assertThat(graph.nodes()).hasSize(500);
        assertThat(graph.truncated()).isTrue();
        assertThat(graph.maxDepth()).isEqualTo(8);
        assertThat(graph.maxNodes()).isEqualTo(500);
    }

    @Test
    void marksGraphsBeyondEightEdgesAsTruncated() {
        List<DeliveryGraphStore.Item> items = new ArrayList<>();
        List<DeliveryGraphStore.Relation> relations = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            items.add(item(id, null, id == 1 ? WorkItemType.REQUIREMENT : WorkItemType.UX_TASK));
            if (id > 1) {
                relations.add(relation(id, id - 1, id));
            }
        }

        DeliveryGraph graph = query(snapshot(items, relations, List.of()), allPermissions()).get(7, 10, 20, 1);

        assertThat(graph.nodes()).hasSize(9);
        assertThat(graph.truncated()).isTrue();
    }

    private DeliveryGraphQuery query(DeliveryGraphStore.Snapshot snapshot, Set<String> permissions) {
        PermissionStore permissionStore = new FixedPermissionStore(permissions);
        return new DeliveryGraphQuery((workspaceId, projectId) -> snapshot, new PermissionEvaluator(permissionStore));
    }

    private Set<String> allPermissions() {
        return Set.of("requirement.read", "ux.read", "task.read", "document.read");
    }

    private DeliveryGraphStore.Snapshot snapshot(
            List<DeliveryGraphStore.Item> items,
            List<DeliveryGraphStore.Relation> relations,
            List<DeliveryGraphStore.Document> documents) {
        return new DeliveryGraphStore.Snapshot(items, relations, documents);
    }

    private DeliveryGraphStore.Item item(long id, Long parentId, WorkItemType type) {
        return new DeliveryGraphStore.Item(
                id, parentId, type, "FORGE-" + id, "Item " + id,
                type == WorkItemType.REQUIREMENT ? WorkItemStatus.DRAFT : WorkItemStatus.TODO);
    }

    private DeliveryGraphStore.Relation relation(long id, long sourceId, long targetId) {
        return new DeliveryGraphStore.Relation(id, sourceId, targetId, "RELATES_TO");
    }

    private record FixedPermissionStore(
            /* 测试场景显式允许的项目权限集合。 */ Set<String> permissions) implements PermissionStore {

        @Override
        public Set<String> findWorkspacePermissionSet(long userId, long workspaceId) {
            return Set.of();
        }

        @Override
        public Optional<ProjectAccess> findProjectAccess(long userId, long workspaceId, long projectId) {
            return Optional.of(new ProjectAccess(true, new LinkedHashSet<>(), permissions));
        }
    }
}
