package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentRun;
import ai.forge.server.agent.domain.AgentRunStatus;
import ai.forge.server.agent.domain.ToolExecutionRejectedException;
import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.document.application.DocumentService;
import ai.forge.server.document.application.RagSearchService;
import ai.forge.server.document.application.SearchChunk;
import ai.forge.server.project.application.ProjectQueryService;
import ai.forge.server.workitem.application.DeliveryGraph;
import ai.forge.server.workitem.application.DeliveryGraphQuery;
import ai.forge.server.workitem.application.WorkItemCommandService;
import ai.forge.server.workitem.application.WorkItemQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("!test-unit")
public class AgentToolExecuteService {

    /* 提供契约白名单与 Tool 元数据。 */
    private final ToolContractRegistry toolContractRegistry;

    /* 读取 Run 权威事实，包括发起用户与确认策略。 */
    private final AgentRunStore runStore;

    /* 持久化 MEDIUM 写操作的幂等事实与结构化结果。 */
    private final AgentToolCallStore toolCallStore;

    /* 执行入口权限的最终服务端校验。 */
    private final PermissionEvaluator permissionEvaluator;

    /* get_project 的后端应用服务。 */
    private final ProjectQueryService projectQueryService;

    /* get_work_item 与 create_ux_task 的后端应用服务。 */
    private final WorkItemQueryService workItemQueryService;

    /* create_requirement 的后端应用服务。 */
    private final WorkItemCommandService workItemCommandService;

    /* get_delivery_graph 的后端应用服务。 */
    private final DeliveryGraphQuery deliveryGraphQuery;

    /* search_documents 的后端检索门面。 */
    private final RagSearchService ragSearchService;

    /* 文档创建 Tool 的后端应用服务。 */
    private final DocumentService documentService;

    /* 构造结构化结果投影；复用全局 JavaTime 配置。 */
    private final ObjectMapper objectMapper;

    /* 确保 MEDIUM 写副作用与幂等成功记录在同一个本地事务内提交。 */
    private final TransactionTemplate transactionTemplate;

    public AgentToolExecuteService(
            ToolContractRegistry toolContractRegistry,
            AgentRunStore runStore,
            AgentToolCallStore toolCallStore,
            PermissionEvaluator permissionEvaluator,
            ProjectQueryService projectQueryService,
            WorkItemQueryService workItemQueryService,
            WorkItemCommandService workItemCommandService,
            DeliveryGraphQuery deliveryGraphQuery,
            RagSearchService ragSearchService,
            DocumentService documentService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.toolContractRegistry = toolContractRegistry;
        this.runStore = runStore;
        this.toolCallStore = toolCallStore;
        this.permissionEvaluator = permissionEvaluator;
        this.projectQueryService = projectQueryService;
        this.workItemQueryService = workItemQueryService;
        this.workItemCommandService = workItemCommandService;
        this.deliveryGraphQuery = deliveryGraphQuery;
        this.ragSearchService = ragSearchService;
        this.documentService = documentService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /* 按契约防线顺序执行一个 Tool Call；scope 全部来自服务端 Run 事实。 */
    public AgentToolExecution execute(
            long workspaceId, long projectId, String runId, String toolName, String toolCallId, JsonNode arguments) {
        ToolContract contract = toolContractRegistry.findTool(toolName)
                .orElseThrow(() -> new ToolExecutionRejectedException(
                        HttpStatus.NOT_FOUND.value(), "TOOL_NOT_FOUND", "Tool contract is not registered"));
        AgentRun run = requireActiveRun(workspaceId, projectId, runId);
        requireSkillAllows(run, toolName);
        requireSchema(contract, arguments);
        try {
            permissionEvaluator.requireProject(run.userId(), workspaceId, projectId, contract.requiredPermission());
        } catch (ResourceNotFoundException exception) {
            throw new ToolExecutionRejectedException(
                    HttpStatus.FORBIDDEN.value(), "PERMISSION_DENIED", "Current run user lacks tool permission");
        }
        if (contract.mediumRisk()) {
            return executeMediumTool(workspaceId, projectId, runId, toolName, toolCallId, arguments, contract, run);
        }
        return AgentToolExecution.succeeded(
                toolName, toolCallId, false, executeApplicationService(workspaceId, projectId, run, toolName, arguments));
    }

    /* MEDIUM 写操作受 Run 确认策略与幂等键双重保护。 */
    private AgentToolExecution executeMediumTool(
            long workspaceId,
            long projectId,
            String runId,
            String toolName,
            String toolCallId,
            JsonNode arguments,
            ToolContract contract,
            AgentRun run) {
        switch (run.mediumToolConfirmation()) {
            case DENY -> {
                return AgentToolExecution.rejected(
                        toolName, toolCallId, "Run policy denies MEDIUM tool execution");
            }
            case ASK -> {
                return AgentToolExecution.pendingConfirmation(toolName, toolCallId);
            }
            case ALLOW -> {
                return executeIdempotentMediumTool(workspaceId, projectId, runId, toolName, toolCallId, arguments, contract, run);
            }
            default -> throw new ToolExecutionRejectedException(
                    HttpStatus.CONFLICT.value(), "UNSUPPORTED_CONFIRMATION", "Unknown medium confirmation policy");
        }
    }

    /* 以 runId 与 toolCallId 组成的幂等键保证成功副作用只发生一次。 */
    private AgentToolExecution executeIdempotentMediumTool(
            long workspaceId,
            long projectId,
            String runId,
            String toolName,
            String toolCallId,
            JsonNode arguments,
            ToolContract contract,
            AgentRun run) {
        String idempotencyKey = runId + ":" + toolCallId;
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> {
                var replayed = toolCallStore.findSuccessful(workspaceId, projectId, runId, idempotencyKey);
                if (replayed.isPresent()) {
                    return replay(toolName, toolCallId, arguments, contract, replayed.orElseThrow());
                }
                AgentToolExecution execution = AgentToolExecution.succeeded(
                        toolName,
                        toolCallId,
                        false,
                        executeApplicationService(workspaceId, projectId, run, toolName, arguments));
                toolCallStore.record(
                        workspaceId,
                        projectId,
                        runId,
                        toolCallId,
                        toolName,
                        contract.version(),
                        contract.riskLevel(),
                        arguments.toString(),
                        execution.result().toString(),
                        idempotencyKey);
                return execution;
            }));
        } catch (DuplicateKeyException exception) {
            /* 并发相同调用只允许唯一事务提交；输家回滚业务副作用后重放赢家结果。 */
            return toolCallStore.findSuccessful(workspaceId, projectId, runId, idempotencyKey)
                    .map(stored -> replay(toolName, toolCallId, arguments, contract, stored))
                    .orElseThrow(() -> exception);
        }
    }

    /* 同一幂等键只能重放完全相同的 Tool、契约版本和结构化参数。 */
    private AgentToolExecution replay(
            String toolName,
            String toolCallId,
            JsonNode arguments,
            ToolContract contract,
            AgentToolCallStore.StoredToolCall stored) {
        if (!toolName.equals(stored.toolName())
                || contract.version() != stored.toolVersion()
                || !arguments.equals(readTree(stored.argumentsJson()))) {
            throw new ToolExecutionRejectedException(
                    HttpStatus.CONFLICT.value(),
                    "IDEMPOTENCY_KEY_REUSED",
                    "Tool call id was already used with different input");
        }
        return AgentToolExecution.succeeded(
                toolName, toolCallId, true, readTree(stored.resultJson()));
    }

    /* 把 Tool 分发到对应应用服务；模型提供的数据只作为参数，不作为授权。 */
    private JsonNode executeApplicationService(
            long workspaceId, long projectId, AgentRun run, String toolName, JsonNode arguments) {
        try {
            return switch (toolName) {
                case "get_project" -> project(workspaceId, projectId, run);
                case "get_work_item" -> workItem(workspaceId, projectId, run, arguments);
                case "get_delivery_graph" -> deliveryGraph(workspaceId, projectId, run, arguments);
                case "search_documents" -> searchDocuments(workspaceId, run, arguments);
                case "create_requirement" -> createRequirement(workspaceId, projectId, run, arguments);
                case "create_ux_task" -> createUxTask(workspaceId, projectId, run, arguments);
                case "create_prd_document" -> createDocument(workspaceId, projectId, run, arguments, "PRD");
                case "create_ux_document" -> createDocument(workspaceId, projectId, run, arguments, "UX_SPEC");
                default -> throw new ToolExecutionRejectedException(
                        HttpStatus.NOT_FOUND.value(), "TOOL_NOT_FOUND", "Tool has no backend executor");
            };
        } catch (ResourceNotFoundException exception) {
            throw new ToolExecutionRejectedException(
                    HttpStatus.NOT_FOUND.value(), "RESOURCE_NOT_FOUND", "Scoped resource does not exist");
        } catch (IllegalArgumentException exception) {
            throw new ToolExecutionRejectedException(
                    HttpStatus.BAD_REQUEST.value(), "INVALID_ARGUMENT", "Tool arguments violate business rules");
        }
    }

    private JsonNode project(long workspaceId, long projectId, AgentRun run) {
        var project = projectQueryService.get(run.userId(), workspaceId, projectId);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", project.id());
        result.put("key", project.key());
        result.put("name", project.name());
        result.put("description", project.description());
        result.put("status", project.status().name());
        return result;
    }

    private JsonNode workItem(long workspaceId, long projectId, AgentRun run, JsonNode arguments) {
        var item = workItemQueryService.get(
                run.userId(), workspaceId, projectId, arguments.path("workItemId").asLong());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", item.id());
        result.put("itemKey", item.itemKey());
        result.put("type", item.type().name());
        result.put("title", item.title());
        result.put("description", item.description());
        result.put("status", item.status().name());
        result.put("priority", item.priority().name());
        if (item.assigneeUserId() != null) {
            result.put("assigneeUserId", item.assigneeUserId());
        }
        result.put("version", item.version());
        return result;
    }

    private JsonNode deliveryGraph(long workspaceId, long projectId, AgentRun run, JsonNode arguments) {
        DeliveryGraph graph = deliveryGraphQuery.get(
                run.userId(), workspaceId, projectId, arguments.path("requirementId").asLong());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("requirementId", arguments.path("requirementId").asLong());
        ArrayNode nodes = result.putArray("nodes");
        for (DeliveryGraph.Node node : graph.nodes()) {
            ObjectNode entry = nodes.addObject();
            entry.put("id", node.id());
            entry.put("kind", node.kind());
            entry.put("resourceId", node.resourceId());
            entry.put("type", node.type());
            entry.put("title", node.title());
            entry.put("status", node.status());
            entry.put("depth", node.depth());
        }
        ArrayNode edges = result.putArray("edges");
        for (DeliveryGraph.Edge edge : graph.edges()) {
            ObjectNode entry = edges.addObject();
            entry.put("id", edge.id());
            entry.put("source", edge.source());
            entry.put("target", edge.target());
            entry.put("type", edge.type());
        }
        result.put("truncated", graph.truncated());
        return result;
    }

    private JsonNode searchDocuments(long workspaceId, AgentRun run, JsonNode arguments) {
        String documentType = arguments.path("documentType").isTextual()
                ? arguments.path("documentType").asText()
                : null;
        Integer topK = arguments.path("topK").isNumber() ? arguments.path("topK").asInt() : null;
        List<SearchChunk> chunks = ragSearchService.search(
                run.userId(),
                workspaceId,
                arguments.path("query").asText(),
                documentType,
                topK);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("query", arguments.path("query").asText());
        ArrayNode hits = result.putArray("chunks");
        for (SearchChunk chunk : chunks) {
            ObjectNode entry = hits.addObject();
            entry.put("documentId", chunk.documentId());
            entry.put("versionId", chunk.versionId());
            entry.put("chunkIndex", chunk.chunkIndex());
            entry.put("title", chunk.title());
            entry.put("documentType", chunk.documentType());
            if (chunk.workItemId() != null) {
                entry.put("workItemId", chunk.workItemId());
            }
            entry.put("score", chunk.score());
            entry.put("text", chunk.text());
        }
        return result;
    }

    private JsonNode createRequirement(long workspaceId, long projectId, AgentRun run, JsonNode arguments) {
        var item = workItemCommandService.create(
                run.userId(),
                workspaceId,
                projectId,
                ai.forge.server.workitem.domain.WorkItemType.REQUIREMENT,
                arguments.path("title").asText(),
                textOrNull(arguments, "description"),
                enumOrNull(arguments, "priority"),
                longOrNull(arguments, "assigneeId"),
                null);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", item.id());
        result.put("itemKey", item.itemKey());
        result.put("status", item.status().name());
        result.put("version", item.version());
        return result;
    }

    private JsonNode createUxTask(long workspaceId, long projectId, AgentRun run, JsonNode arguments) {
        var item = workItemCommandService.createUxTask(
                run.userId(),
                workspaceId,
                projectId,
                arguments.path("requirementId").asLong(),
                arguments.path("title").asText(),
                textOrNull(arguments, "description"),
                enumOrNull(arguments, "priority"),
                longOrNull(arguments, "assigneeId"));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", item.id());
        result.put("itemKey", item.itemKey());
        result.put("parentId", arguments.path("requirementId").asLong());
        result.put("status", item.status().name());
        result.put("version", item.version());
        return result;
    }

    private JsonNode createDocument(
            long workspaceId, long projectId, AgentRun run, JsonNode arguments, String documentType) {
        var document = documentService.create(
                run.userId(),
                workspaceId,
                projectId,
                arguments.path("requirementId").asLong(),
                documentType,
                arguments.path("title").asText());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", document.id());
        result.put("documentType", document.type());
        result.put("title", document.title());
        result.put("status", document.status());
        result.put("version", document.version());
        return result;
    }

    private AgentRun requireActiveRun(long workspaceId, long projectId, String runId) {
        AgentRun run = runStore.find(workspaceId, projectId, runId)
                .orElseThrow(() -> new ToolExecutionRejectedException(
                        HttpStatus.NOT_FOUND.value(), "RUN_NOT_FOUND", "Run does not exist in scope"));
        if (run.status() != AgentRunStatus.RUNNING) {
            throw new ToolExecutionRejectedException(
                    HttpStatus.CONFLICT.value(), "RUN_NOT_ACTIVE", "Run is not in RUNNING state");
        }
        return run;
    }

    private void requireSkillAllows(AgentRun run, String toolName) {
        List<String> allowed = toolContractRegistry.effectiveToolNames(
                ai.forge.server.agent.domain.AgentSkill.valueOf(run.skill().name()));
        if (!allowed.contains(toolName)) {
            throw new ToolExecutionRejectedException(
                    HttpStatus.FORBIDDEN.value(),
                    "TOOL_NOT_ALLOWED_FOR_SKILL",
                    "Tool is not in the allowlist of the run skill");
        }
    }

    private void requireSchema(ToolContract contract, JsonNode arguments) {
        List<String> errors = ToolArgumentValidator.validate(contract.inputSchema(), arguments);
        if (!errors.isEmpty()) {
            throw new ToolExecutionRejectedException(
                    HttpStatus.BAD_REQUEST.value(), "SCHEMA_INVALID", String.join("; ", errors));
        }
    }

    private String textOrNull(JsonNode arguments, String field) {
        return arguments.path(field).isTextual() ? arguments.path(field).asText() : null;
    }

    private Long longOrNull(JsonNode arguments, String field) {
        return arguments.path(field).isNumber() ? arguments.path(field).asLong() : null;
    }

    private ai.forge.server.workitem.domain.WorkItemPriority enumOrNull(JsonNode arguments, String field) {
        return arguments.path(field).isTextual()
                ? ai.forge.server.workitem.domain.WorkItemPriority.valueOf(arguments.path(field).asText())
                : null;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Persisted tool result is not valid JSON", exception);
        }
    }
}
