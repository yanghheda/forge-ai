package ai.forge.server.workitem.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.workitem.domain.WorkItemType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class DeliveryGraphQuery {

    /* 防止异常关系图导致无界遍历。 */
    static final int MAX_DEPTH = 8;

    /* 控制单次响应和前端渲染成本。 */
    static final int MAX_NODES = 500;

    /* 批量读取同一项目的图快照。 */
    private final DeliveryGraphStore store;

    /* 在投影节点前执行服务端项目授权。 */
    private final PermissionEvaluator permissions;

    public DeliveryGraphQuery(DeliveryGraphStore store, PermissionEvaluator permissions) {
        this.store = store;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public DeliveryGraph get(long userId, long workspaceId, long projectId, long requirementId) {
        permissions.requireProject(userId, workspaceId, projectId, "requirement.read");
        DeliveryGraphStore.Snapshot snapshot = store.load(workspaceId, projectId);
        Map<Long, DeliveryGraphStore.Item> allItems = new HashMap<>();
        snapshot.items().forEach(item -> allItems.put(item.id(), item));
        DeliveryGraphStore.Item root = allItems.get(requirementId);
        if (root == null || root.type() != WorkItemType.REQUIREMENT) {
            throw new ResourceNotFoundException();
        }

        Map<WorkItemType, Boolean> readableTypes = new HashMap<>();
        for (WorkItemType type : WorkItemType.values()) {
            readableTypes.put(type, permissions.hasProjectPermission(
                    userId, workspaceId, projectId, type.permissionResource() + ".read"));
        }
        boolean documentsReadable = permissions.hasProjectPermission(
                userId, workspaceId, projectId, "document.read");
        Map<Long, List<Long>> adjacency = adjacency(snapshot, allItems, readableTypes);
        Map<Long, Integer> depths = traverse(requirementId, adjacency);
        List<DeliveryGraphStore.Item> reachable = depths.keySet().stream()
                .map(allItems::get)
                .sorted(Comparator.comparingInt((DeliveryGraphStore.Item item) -> depths.get(item.id()))
                        .thenComparingLong(DeliveryGraphStore.Item::id))
                .toList();

        List<DeliveryGraph.Node> nodes = new ArrayList<>();
        Set<Long> includedItems = new HashSet<>();
        boolean truncated = hasDepthOverflow(depths, adjacency);
        for (DeliveryGraphStore.Item item : reachable) {
            if (nodes.size() == MAX_NODES) {
                truncated = true;
                break;
            }
            includedItems.add(item.id());
            nodes.add(itemNode(item, depths.get(item.id())));
        }

        if (documentsReadable) {
            for (DeliveryGraphStore.Document document : snapshot.documents().stream()
                    .sorted(Comparator.comparingLong(DeliveryGraphStore.Document::id)).toList()) {
                Integer itemDepth = depths.get(document.workItemId());
                if (itemDepth == null || !includedItems.contains(document.workItemId())) {
                    continue;
                }
                if (itemDepth + 1 > MAX_DEPTH || nodes.size() == MAX_NODES) {
                    truncated = true;
                    continue;
                }
                nodes.add(documentNode(document, itemDepth + 1));
            }
        }
        return new DeliveryGraph(
                List.copyOf(nodes),
                edges(snapshot, includedItems, nodes),
                truncated,
                MAX_DEPTH,
                MAX_NODES);
    }

    private Map<Long, List<Long>> adjacency(
            DeliveryGraphStore.Snapshot snapshot,
            Map<Long, DeliveryGraphStore.Item> items,
            Map<WorkItemType, Boolean> readableTypes) {
        Map<Long, List<Long>> result = new HashMap<>();
        for (DeliveryGraphStore.Item item : items.values()) {
            if (!readableTypes.get(item.type())) {
                continue;
            }
            result.computeIfAbsent(item.id(), ignored -> new ArrayList<>());
            if (item.parentId() != null && readable(items.get(item.parentId()), readableTypes)) {
                connect(result, item.parentId(), item.id());
            }
        }
        for (DeliveryGraphStore.Relation relation : snapshot.relations()) {
            if (readable(items.get(relation.sourceId()), readableTypes)
                    && readable(items.get(relation.targetId()), readableTypes)) {
                connect(result, relation.sourceId(), relation.targetId());
            }
        }
        result.values().forEach(neighbors -> neighbors.sort(Long::compareTo));
        return result;
    }

    private boolean readable(DeliveryGraphStore.Item item, Map<WorkItemType, Boolean> readableTypes) {
        return item != null && readableTypes.get(item.type());
    }

    private void connect(Map<Long, List<Long>> adjacency, long first, long second) {
        adjacency.computeIfAbsent(first, ignored -> new ArrayList<>()).add(second);
        adjacency.computeIfAbsent(second, ignored -> new ArrayList<>()).add(first);
    }

    private Map<Long, Integer> traverse(long rootId, Map<Long, List<Long>> adjacency) {
        Map<Long, Integer> depths = new LinkedHashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        depths.put(rootId, 0);
        queue.add(rootId);
        while (!queue.isEmpty()) {
            long current = queue.remove();
            int depth = depths.get(current);
            if (depth == MAX_DEPTH) {
                continue;
            }
            for (long neighbor : adjacency.getOrDefault(current, List.of())) {
                if (!depths.containsKey(neighbor)) {
                    depths.put(neighbor, depth + 1);
                    queue.add(neighbor);
                }
            }
        }
        return depths;
    }

    private boolean hasDepthOverflow(Map<Long, Integer> depths, Map<Long, List<Long>> adjacency) {
        return depths.entrySet().stream()
                .filter(entry -> entry.getValue() == MAX_DEPTH)
                .anyMatch(entry -> adjacency.getOrDefault(entry.getKey(), List.of()).stream()
                        .anyMatch(neighbor -> !depths.containsKey(neighbor)));
    }

    private DeliveryGraph.Node itemNode(DeliveryGraphStore.Item item, int depth) {
        return new DeliveryGraph.Node(
                "work-item:" + item.id(),
                "WORK_ITEM",
                item.id(),
                item.type().name(),
                item.itemKey() + " · " + item.title(),
                item.status().name(),
                depth);
    }

    private DeliveryGraph.Node documentNode(DeliveryGraphStore.Document document, int depth) {
        return new DeliveryGraph.Node(
                "document:" + document.id(),
                "DOCUMENT",
                document.id(),
                document.type(),
                document.title(),
                document.status(),
                depth);
    }

    private List<DeliveryGraph.Edge> edges(
            DeliveryGraphStore.Snapshot snapshot,
            Set<Long> includedItems,
            List<DeliveryGraph.Node> nodes) {
        Set<String> nodeIds = nodes.stream().map(DeliveryGraph.Node::id).collect(java.util.stream.Collectors.toSet());
        List<DeliveryGraph.Edge> result = new ArrayList<>();
        for (DeliveryGraphStore.Item item : snapshot.items()) {
            if (item.parentId() != null && includedItems.contains(item.id()) && includedItems.contains(item.parentId())) {
                result.add(new DeliveryGraph.Edge(
                        "parent:" + item.parentId() + ":" + item.id(),
                        "work-item:" + item.parentId(),
                        "work-item:" + item.id(),
                        "PARENT"));
            }
        }
        for (DeliveryGraphStore.Relation relation : snapshot.relations()) {
            if (includedItems.contains(relation.sourceId()) && includedItems.contains(relation.targetId())) {
                result.add(new DeliveryGraph.Edge(
                        "relation:" + relation.id(),
                        "work-item:" + relation.sourceId(),
                        "work-item:" + relation.targetId(),
                        relation.type()));
            }
        }
        for (DeliveryGraphStore.Document document : snapshot.documents()) {
            String documentId = "document:" + document.id();
            if (includedItems.contains(document.workItemId()) && nodeIds.contains(documentId)) {
                result.add(new DeliveryGraph.Edge(
                        "document:" + document.id(),
                        "work-item:" + document.workItemId(),
                        documentId,
                        "HAS_DOCUMENT"));
            }
        }
        return result.stream().sorted(Comparator.comparing(DeliveryGraph.Edge::id)).toList();
    }
}
