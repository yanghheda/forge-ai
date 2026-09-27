package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentRun;
import ai.forge.server.agent.domain.AgentRunStatus;
import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.agent.domain.ToolExecutionRejectedException;
import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.document.application.DocumentContent;
import ai.forge.server.document.application.DocumentService;
import ai.forge.server.document.application.RagSearchService;
import ai.forge.server.document.application.SearchChunk;
import ai.forge.server.document.domain.Document;
import ai.forge.server.gitlab.application.DevelopmentService;
import ai.forge.server.gitlab.application.PipelineService;
import ai.forge.server.organization.application.OrganizationAccessService;
import ai.forge.server.qa.application.BugService;
import ai.forge.server.qa.application.BugView;
import ai.forge.server.qa.application.QaService;
import ai.forge.server.qa.application.TestResultView;
import ai.forge.server.qa.application.TestRunView;
import ai.forge.server.qa.domain.BugAction;
import ai.forge.server.qa.domain.TestResultStatus;
import ai.forge.server.release.application.ReleaseService;
import ai.forge.server.release.application.DeploymentService;
import ai.forge.server.workitem.application.DeliveryGraph;
import ai.forge.server.workitem.application.DeliveryGraphQuery;
import ai.forge.server.workitem.application.DeliveryWorkflowStore;
import ai.forge.server.workitem.application.OrganizationRequirementService;
import ai.forge.server.workitem.application.OrganizationRequirementStore;
import ai.forge.server.workitem.application.OrganizationRequirementView;
import ai.forge.server.workitem.application.WorkItemCommandService;
import ai.forge.server.workitem.application.WorkItemQueryService;
import ai.forge.server.workitem.application.RequirementDetailsService;
import ai.forge.server.workitem.application.RequirementTransitionService;
import ai.forge.server.workitem.application.RequirementTransitionStore;
import ai.forge.server.workitem.domain.RequirementDetails;
import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import ai.forge.server.workitem.domain.WorkflowAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
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

    /* 把 ASK 策略升级为持久审批并在恢复时验证冻结事实。 */
    private final ApprovalService approvalService;

    /* 执行入口权限的最终服务端校验。 */
    private final PermissionEvaluator permissionEvaluator;

    /* get_organization 的后端应用服务。 */
    private final OrganizationAccessService organizationAccessService;

    /* get_work_item 与 create_ux_task 的后端应用服务。 */
    private final WorkItemQueryService workItemQueryService;

    /* create_requirement 的后端应用服务。 */
    private final WorkItemCommandService workItemCommandService;

    /* 阶段推进复用人工入口的固定状态机、权限、版本与 Guard。 */
    private final RequirementTransitionService requirementTransitionService;

    /* get_delivery_graph 的后端应用服务。 */
    private final DeliveryGraphQuery deliveryGraphQuery;

    /* search_documents 的后端检索门面。 */
    private final RagSearchService ragSearchService;

    /* 文档创建 Tool 的后端应用服务。 */
    private final DocumentService documentService;

    /* Developer 写 Tool 复用人工路径的状态、GitLab 幂等与恢复编排。 */
    private final DevelopmentService developmentService;

    /* Developer 只读 CI Tool 复用日志大小限制与脱敏边界。 */
    private final PipelineService pipelineService;

    /* QA Agent 仅创建 Test Case 草稿，不写执行结论。 */
    private final QaService qaService;

    /* QA Agent 创建 Bug 草稿时复用人工入口的关系与状态约束。 */
    private final BugService bugService;

    /* Release Agent 只读后端 Precheck，并通过受控写入口编辑 Note。 */
    private final ReleaseService releaseService;
    /* HIGH 审批通过后创建明确标记的模拟部署。 */
    private final DeploymentService deploymentService;

    /* 构造结构化结果投影；复用全局 JavaTime 配置。 */
    private final ObjectMapper objectMapper;

    /* 确保 MEDIUM 写副作用与幂等成功记录在同一个本地事务内提交。 */
    private final TransactionTemplate transactionTemplate;

    /* 向受控模型提供已核验的负责人及执行人，禁止凭空指派。 */
    private final ai.forge.server.workitem.application.OrganizationRequirementStore participants;

    /* 需求列表与公司成员读取 Tool 的后端应用服务。 */
    private final OrganizationRequirementService organizationRequirementService;

    /* 把任务与缺陷上溯到根需求，保证 Tool 参数不越过 Run 绑定的交付范围。 */
    private final DeliveryWorkflowStore deliveryWorkflowStore;

    /* 需求详情的业务目标、范围与验收标准读写入口。 */
    private final RequirementDetailsService requirementDetailsService;

    public AgentToolExecuteService(
            ToolContractRegistry toolContractRegistry,
            AgentRunStore runStore,
            AgentToolCallStore toolCallStore,
            ApprovalService approvalService,
            PermissionEvaluator permissionEvaluator,
            OrganizationAccessService organizationAccessService,
            WorkItemQueryService workItemQueryService,
            WorkItemCommandService workItemCommandService,
            RequirementTransitionService requirementTransitionService,
            DeliveryGraphQuery deliveryGraphQuery,
            RagSearchService ragSearchService,
            DocumentService documentService,
            DevelopmentService developmentService,
            PipelineService pipelineService,
            QaService qaService,
            BugService bugService,
            ReleaseService releaseService,
            DeploymentService deploymentService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            ai.forge.server.workitem.application.OrganizationRequirementStore participants,
            OrganizationRequirementService organizationRequirementService,
            DeliveryWorkflowStore deliveryWorkflowStore,
            RequirementDetailsService requirementDetailsService) {
        this.participants = participants;
        this.organizationRequirementService = organizationRequirementService;
        this.deliveryWorkflowStore = deliveryWorkflowStore;
        this.requirementDetailsService = requirementDetailsService;
        this.toolContractRegistry = toolContractRegistry;
        this.runStore = runStore;
        this.toolCallStore = toolCallStore;
        this.approvalService = approvalService;
        this.permissionEvaluator = permissionEvaluator;
        this.organizationAccessService = organizationAccessService;
        this.workItemQueryService = workItemQueryService;
        this.workItemCommandService = workItemCommandService;
        this.requirementTransitionService = requirementTransitionService;
        this.deliveryGraphQuery = deliveryGraphQuery;
        this.ragSearchService = ragSearchService;
        this.documentService = documentService;
        this.developmentService = developmentService;
        this.pipelineService = pipelineService;
        this.qaService = qaService;
        this.bugService = bugService;
        this.releaseService = releaseService;
        this.deploymentService = deploymentService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /* 按契约防线顺序执行一个 Tool Call；scope 全部来自服务端 Run 事实。 */
    public AgentToolExecution execute(
            long organizationId, String runId, String toolName, String toolCallId, JsonNode arguments) {
        ToolContract contract = toolContractRegistry.findTool(toolName)
                .orElseThrow(() -> new ToolExecutionRejectedException(
                        HttpStatus.NOT_FOUND.value(), "TOOL_NOT_FOUND", "Tool contract is not registered"));
        AgentRun run = requireActiveRun(organizationId, runId);
        requireSkillAllows(run, toolName);
        requireSchema(contract, arguments);
        try {
            permissionEvaluator.requireOrganization(run.userId(), organizationId, contract.requiredPermission());
        } catch (ResourceNotFoundException exception) {
            throw new ToolExecutionRejectedException(
                    HttpStatus.FORBIDDEN.value(), "PERMISSION_DENIED", "Current run user lacks tool permission");
        }
        if (contract.forcedConfirmation()) {
            return executeForcedApprovalTool(organizationId, runId, toolName, toolCallId, arguments, contract, run);
        }
        if (contract.mediumRisk()) {
            return executeMediumTool(organizationId, runId, toolName, toolCallId, arguments, contract, run);
        }
        return AgentToolExecution.succeeded(
                toolName, toolCallId, false, executeApplicationService(organizationId, run, toolName, arguments));
    }

    /* HIGH 风险与契约强制确认的 Tool 都无条件先人工审批；Run 的 ALLOW 只适用于其余 MEDIUM。 */
    private AgentToolExecution executeForcedApprovalTool(long organizationId, String runId, String toolName,
            String toolCallId, JsonNode arguments, ToolContract contract, AgentRun run) {
        if (!approvalService.existsForCall(organizationId, runId, toolCallId)) {
            ApprovalSnapshot approval = approvalService.request(run, contract, toolCallId, arguments);
            return AgentToolExecution.pendingApproval(toolName, toolCallId, approval.id());
        }
        JsonNode frozenArguments = approvalService.requireApproved(run, contract, toolCallId, arguments);
        return executeApprovedTool(organizationId, runId, toolName, toolCallId,
                frozenArguments, contract, run);
    }

    /* MEDIUM 写操作受 Run 确认策略与幂等键双重保护。 */
    private AgentToolExecution executeMediumTool(
            long organizationId,
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
                if (!approvalService.existsForCall(organizationId, runId, toolCallId)) {
                    ApprovalSnapshot approval = approvalService.request(run, contract, toolCallId, arguments);
                    return AgentToolExecution.pendingApproval(toolName, toolCallId, approval.id());
                }
                JsonNode frozenArguments = approvalService.requireApproved(run, contract, toolCallId, arguments);
                return executeApprovedTool(
                        organizationId, runId, toolName, toolCallId, frozenArguments, contract, run);
            }
            case ALLOW -> {
                return executeIdempotentMediumTool(organizationId, runId, toolName, toolCallId, arguments, contract, run);
            }
            default -> throw new ToolExecutionRejectedException(
                    HttpStatus.CONFLICT.value(), "UNSUPPORTED_CONFIRMATION", "Unknown medium confirmation policy");
        }
    }

    private AgentToolExecution executeApprovedTool(
            long organizationId,
            String runId,
            String toolName,
            String toolCallId,
            JsonNode arguments,
            ToolContract contract,
            AgentRun run) {
        if ("start_development".equals(toolName)) {
            return executeRecoverableRemoteTool(
                    organizationId, runId, toolName, toolCallId, arguments, contract, run, true);
        }
        if ("deploy_release".equals(toolName)) {
            return executeApprovedDeployment(organizationId, runId, toolName, toolCallId,
                    arguments, contract, run);
        }
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            var replayed = toolCallStore.findSuccessful(
                    organizationId, runId, runId + ":" + toolCallId);
            if (replayed.isPresent()) {
                return replay(toolName, toolCallId, arguments, contract, replayed.orElseThrow());
            }
            AgentToolExecution execution = AgentToolExecution.succeeded(
                    toolName, toolCallId, false,
                    executeApplicationService(organizationId, run, toolName, arguments));
            approvalService.complete(organizationId, runId, toolCallId, execution.result());
            return execution;
        }));
    }

    /* HIGH 失败不会自动重试；仅成功结果写入原 Tool Call 并可安全重放。 */
    private AgentToolExecution executeApprovedDeployment(long organizationId, String runId,
            String toolName, String toolCallId, JsonNode arguments, ToolContract contract, AgentRun run) {
        var replayed = toolCallStore.findSuccessful(organizationId, runId, runId + ":" + toolCallId);
        if (replayed.isPresent()) {
            return replay(toolName, toolCallId, arguments, contract, replayed.orElseThrow());
        }
        String approvalId = approvalService.approvalIdForCall(organizationId, runId, toolCallId);
        var deployment = deploymentService.requestFromApprovedAgent(run.userId(), organizationId,
                arguments.path("releaseId").asLong(), arguments.path("simulateFailure").asBoolean(false),
                runId + ":" + toolCallId, approvalId, runId);
        AgentToolExecution execution = AgentToolExecution.succeeded(
                toolName, toolCallId, false, objectMapper.valueToTree(deployment));
        approvalService.complete(organizationId, runId, toolCallId, execution.result());
        return execution;
    }

    /* 以 runId 与 toolCallId 组成的幂等键保证成功副作用只发生一次。 */
    private AgentToolExecution executeIdempotentMediumTool(
            long organizationId,
            String runId,
            String toolName,
            String toolCallId,
            JsonNode arguments,
            ToolContract contract,
            AgentRun run) {
        if ("start_development".equals(toolName)) {
            return executeRecoverableRemoteTool(
                    organizationId, runId, toolName, toolCallId, arguments, contract, run, false);
        }
        String idempotencyKey = runId + ":" + toolCallId;
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> {
                var replayed = toolCallStore.findSuccessful(organizationId, runId, idempotencyKey);
                if (replayed.isPresent()) {
                    return replay(toolName, toolCallId, arguments, contract, replayed.orElseThrow());
                }
                AgentToolExecution execution = AgentToolExecution.succeeded(
                        toolName,
                        toolCallId,
                        false,
                        executeApplicationService(organizationId, run, toolName, arguments));
                toolCallStore.record(
                        organizationId,
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
            return toolCallStore.findSuccessful(organizationId, runId, idempotencyKey)
                    .map(stored -> replay(toolName, toolCallId, arguments, contract, stored))
                    .orElseThrow(() -> exception);
        }
    }

    /* GitLab 网络调用绝不位于本地事务内；崩溃空窗由 DevelopmentService 的远端 reconcile 收敛。 */
    private AgentToolExecution executeRecoverableRemoteTool(
            long organizationId,
            String runId,
            String toolName,
            String toolCallId,
            JsonNode arguments,
            ToolContract contract,
            AgentRun run,
            boolean approved) {
        String idempotencyKey = runId + ":" + toolCallId;
        var replayed = toolCallStore.findSuccessful(organizationId, runId, idempotencyKey);
        if (replayed.isPresent()) {
            return replay(toolName, toolCallId, arguments, contract, replayed.orElseThrow());
        }
        AgentToolExecution execution = AgentToolExecution.succeeded(
                toolName,
                toolCallId,
                false,
                executeApplicationService(organizationId, run, toolName, arguments));
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> {
                if (approved) {
                    approvalService.complete(organizationId, runId, toolCallId, execution.result());
                } else {
                    toolCallStore.record(
                            organizationId,
                            runId,
                            toolCallId,
                            toolName,
                            contract.version(),
                            contract.riskLevel(),
                            arguments.toString(),
                            execution.result().toString(),
                            idempotencyKey);
                }
                return execution;
            }));
        } catch (DuplicateKeyException exception) {
            return toolCallStore.findSuccessful(organizationId, runId, idempotencyKey)
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
            long organizationId, AgentRun run, String toolName, JsonNode arguments) {
        try {
            return switch (toolName) {
                case "get_organization" -> organization(run);
                case "get_work_item" -> workItem(organizationId, run, arguments);
                case "list_requirements" -> listRequirements(organizationId, run, arguments);
                case "list_tasks" -> listTasks(organizationId, run, arguments);
                case "list_bugs" -> listBugs(organizationId, run, arguments);
                case "list_members" -> listMembers(run);
                case "get_test_run" -> getTestRun(organizationId, run, arguments);
                case "get_delivery_graph" -> deliveryGraph(organizationId, run, arguments);
                case "search_documents" -> searchDocuments(organizationId, run, arguments);
                case "create_requirement" -> createRequirement(organizationId, run, arguments);
                case "delete_requirement" -> deleteRequirement(organizationId, run, arguments);
                case "update_requirement" -> updateWorkItem(
                        organizationId, run, arguments, WorkItemType.REQUIREMENT, "requirementId");
                case "update_requirement_details" -> updateRequirementDetails(organizationId, run, arguments);
                case "update_task" -> updateWorkItem(organizationId, run, arguments, null, "taskId");
                case "update_bug" -> updateWorkItem(organizationId, run, arguments, WorkItemType.BUG, "bugId");
                case "assign_executors" -> assignExecutors(organizationId, run, arguments);
                case "assign_participants" -> assignParticipants(organizationId, run, arguments);
                case "advance_task" -> advanceTask(organizationId, run, arguments);
                case "advance_bug" -> advanceBug(organizationId, run, arguments);
                case "create_ux_task" -> createUxTask(organizationId, run, arguments);
                case "create_prd_document" -> createDocument(organizationId, run, arguments, "PRD");
                case "create_ux_document" -> createDocument(organizationId, run, arguments, "UX_SPEC");
                case "create_tech_design" -> createDocument(
                        organizationId, run, arguments, "TECH_DESIGN");
                case "publish_document" -> publishDocument(organizationId, run, arguments);
                case "publish_ux_document" -> publishDocument(organizationId, run, arguments);
                case "create_dev_task" -> createDevTask(organizationId, run, arguments);
                case "start_development" -> startDevelopment(organizationId, run, arguments);
                case "get_pipeline_log" -> pipelineLog(organizationId, run, arguments);
                case "create_test_case" -> createTestCase(organizationId, run, arguments);
                case "create_bug" -> createBug(organizationId, run, arguments);
                case "start_test_run" -> startTestRun(organizationId, run, arguments);
                case "record_test_result" -> recordTestResult(organizationId, run, arguments);
                case "complete_test_run" -> completeTestRun(organizationId, run, arguments);
                case "reopen_test_run" -> reopenTestRun(organizationId, run, arguments);
                case "advance_requirement" -> advanceRequirement(organizationId, run, arguments);
                case "get_release_precheck" -> releasePrecheck(organizationId, run, arguments);
                case "create_release" -> createRelease(organizationId, run, arguments);
                case "run_release_precheck" -> runReleasePrecheck(organizationId, run, arguments);
                case "update_release_note" -> updateReleaseNote(organizationId, run, arguments);
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

    private JsonNode releasePrecheck(long organizationId, AgentRun run, JsonNode arguments) {
        var release = releaseService.get(
                run.userId(), organizationId, arguments.path("releaseId").asLong());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("releaseId", release.id());
        result.put("versionName", release.versionName());
        result.put("releaseVersion", release.version());
        if (release.latestPrecheck() == null) {
            result.putNull("precheck");
        } else {
            result.set("precheck", objectMapper.valueToTree(release.latestPrecheck()));
        }
        return result;
    }

    private JsonNode createRelease(long organizationId, AgentRun run, JsonNode arguments) {
        List<Long> requirementIds = new java.util.ArrayList<>();
        arguments.path("requirementIds").forEach(item -> requirementIds.add(item.asLong()));
        var release = releaseService.create(run.userId(), organizationId,
                arguments.path("versionName").asText(), arguments.path("environment").asText(),
                requirementIds, arguments.path("approvalTtlMinutes").asLong(60));
        return objectMapper.valueToTree(release);
    }

    private JsonNode runReleasePrecheck(long organizationId, AgentRun run, JsonNode arguments) {
        return objectMapper.valueToTree(releaseService.precheck(
                run.userId(), organizationId, arguments.path("releaseId").asLong()));
    }

    private JsonNode updateReleaseNote(long organizationId, AgentRun run, JsonNode arguments) {
        var release = releaseService.updateNote(
                run.userId(), organizationId, arguments.path("releaseId").asLong(),
                arguments.path("note").asText(), arguments.path("expectedVersion").asLong());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("releaseId", release.id());
        result.put("releaseVersion", release.version());
        result.put("note", release.releaseNote());
        return result;
    }

    private JsonNode organization(AgentRun run) {
        var organization = organizationAccessService.requireContext(run.userId());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", organization.organizationId());
        result.put("name", organization.organizationName());
        return result;
    }

    private JsonNode workItem(long organizationId, AgentRun run, JsonNode arguments) {
        var item = workItemQueryService.get(
                run.userId(), organizationId, arguments.path("workItemId").asLong());
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
        if (item.type() == WorkItemType.REQUIREMENT) {
            result.set("participants", objectMapper.valueToTree(participants.findParticipants(organizationId, item.id())));
        } else {
            /* 任务与缺陷只暴露所属根需求，不暴露其他需求的交付范围。 */
            Long root = deliveryWorkflowStore.requirementId(organizationId, item.id());
            if (root != null) {
                result.put("parentRequirementId", root);
            }
        }
        if (item.type() == WorkItemType.BUG) {
            /* 缺陷动作由 BugService 的独立状态机计算，不属于 Requirement 转换表。 */
            BugView bug = bugService.get(run.userId(), organizationId, item.id());
            result.set("availableActions", objectMapper.valueToTree(bug.availableActions()));
        } else {
            var hints = requirementTransitionService.hints(run.userId(), item);
            result.set("availableActions", objectMapper.valueToTree(hints.availableActions()));
            result.set("guardHints", objectMapper.valueToTree(hints.guardHints()));
        }
        result.put("version", item.version());
        return result;
    }

    private JsonNode listRequirements(long organizationId, AgentRun run, JsonNode arguments) {
        WorkItemStatus status = arguments.path("status").isTextual()
                ? WorkItemStatus.valueOf(arguments.path("status").asText())
                : null;
        var page = organizationRequirementService.list(
                run.userId(),
                arguments.path("mine").asBoolean(false),
                textOrNull(arguments, "query"),
                status,
                arguments.path("page").isNumber() ? arguments.path("page").asInt() : 1,
                arguments.path("pageSize").isNumber() ? arguments.path("pageSize").asInt() : 20);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("page", page.page());
        result.put("pageSize", page.pageSize());
        result.put("total", page.total());
        ArrayNode items = result.putArray("items");
        for (OrganizationRequirementView item : page.items()) {
            ObjectNode entry = items.addObject();
            entry.put("id", item.id());
            entry.put("itemKey", item.itemKey());
            entry.put("title", item.title());
            entry.put("status", item.status().name());
            entry.put("priority", item.priority().name());
            entry.put("version", item.version());
        }
        return result;
    }

    private JsonNode listTasks(long organizationId, AgentRun run, JsonNode arguments) {
        long requirementId = scopedRequirementId(run, arguments);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("requirementId", requirementId);
        ArrayNode items = result.putArray("items");
        for (var task : workItemCommandService.tasks(run.userId(), organizationId, requirementId)) {
            ObjectNode entry = items.addObject();
            entry.put("id", task.id());
            entry.put("itemKey", task.itemKey());
            entry.put("type", task.type().name());
            entry.put("title", task.title());
            entry.put("status", task.status().name());
            if (task.assigneeUserId() != null) {
                entry.put("assigneeUserId", task.assigneeUserId());
            }
            entry.put("version", task.version());
        }
        return result;
    }

    private JsonNode listBugs(long organizationId, AgentRun run, JsonNode arguments) {
        long requirementId = scopedRequirementId(run, arguments);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("requirementId", requirementId);
        ArrayNode items = result.putArray("items");
        for (BugView bug : bugService.list(run.userId(), organizationId, requirementId)) {
            ObjectNode entry = items.addObject();
            entry.put("id", bug.id());
            entry.put("itemKey", bug.itemKey());
            entry.put("title", bug.title());
            entry.put("status", bug.status().name());
            entry.put("severity", bug.severity().name());
            if (bug.assigneeUserId() != null) {
                entry.put("assigneeUserId", bug.assigneeUserId());
            }
            entry.put("version", bug.version());
            entry.set("availableActions", objectMapper.valueToTree(bug.availableActions()));
        }
        return result;
    }

    private JsonNode listMembers(AgentRun run) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode items = result.putArray("items");
        for (var member : organizationRequirementService.members(run.userId())) {
            ObjectNode entry = items.addObject();
            entry.put("userId", member.userId());
            entry.put("displayName", member.displayName());
            entry.put("email", member.email());
            entry.set("roles", objectMapper.valueToTree(member.roles()));
        }
        return result;
    }

    private JsonNode getTestRun(long organizationId, AgentRun run, JsonNode arguments) {
        TestRunView testRun = arguments.path("testRunId").isNumber()
                ? qaService.run(run.userId(), organizationId, arguments.path("testRunId").asLong())
                : qaService.latestRun(run.userId(), organizationId, scopedRequirementId(run, arguments));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", testRun.id());
        result.put("requirementId", testRun.requirementId());
        result.put("environment", testRun.environment());
        result.put("status", testRun.status().name());
        result.put("version", testRun.version());
        if (testRun.summary() == null) {
            result.putNull("summary");
        } else {
            result.set("summary", objectMapper.valueToTree(testRun.summary()));
        }
        result.set("decision", objectMapper.valueToTree(testRun.decision()));
        ArrayNode results = result.putArray("results");
        for (TestResultView item : testRun.results()) {
            ObjectNode entry = results.addObject();
            entry.put("id", item.id());
            entry.put("testCaseId", item.testCaseId());
            entry.put("title", item.title());
            entry.put("status", item.status().name());
            entry.put("actualResult", item.actualResult());
            entry.set("evidence", objectMapper.valueToTree(item.evidence()));
            entry.put("version", item.version());
        }
        return result;
    }

    /* 需求级参数必须落在 Run 绑定的根需求内；缺省时直接使用根需求，避免模型猜测交付范围。 */
    private long scopedRequirementId(AgentRun run, JsonNode arguments) {
        Long requested = longOrNull(arguments, "requirementId");
        if (run.workItemId() == null) {
            if (requested == null) {
                throw new ToolExecutionRejectedException(
                        HttpStatus.BAD_REQUEST.value(), "REQUIREMENT_SCOPE_REQUIRED",
                        "Tool requires a requirement scope but the run is not bound to one");
            }
            return requested;
        }
        if (requested != null && requested != run.workItemId()) {
            throw new ToolExecutionRejectedException(
                    HttpStatus.FORBIDDEN.value(), "RUN_WORK_ITEM_MISMATCH",
                    "Tool must target the requirement bound to the run");
        }
        return run.workItemId();
    }

    private JsonNode deliveryGraph(long organizationId, AgentRun run, JsonNode arguments) {
        DeliveryGraph graph = deliveryGraphQuery.get(
                run.userId(), organizationId, arguments.path("requirementId").asLong());
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

    private JsonNode searchDocuments(long organizationId, AgentRun run, JsonNode arguments) {
        String documentType = arguments.path("documentType").isTextual()
                ? arguments.path("documentType").asText()
                : null;
        Integer topK = arguments.path("topK").isNumber() ? arguments.path("topK").asInt() : null;
        List<SearchChunk> chunks = ragSearchService.search(
                run.userId(),
                organizationId,
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

    private JsonNode createRequirement(long organizationId, AgentRun run, JsonNode arguments) {
        var item = workItemCommandService.create(
                run.userId(),
                organizationId,
                ai.forge.server.workitem.domain.WorkItemType.REQUIREMENT,
                arguments.path("title").asText(),
                textOrNull(arguments, "description"),
                enumOrNull(arguments, "priority"),
                longOrNull(arguments, "assigneeId"),
                null);
        runStore.bindConversationToRequirement(organizationId, run.id(), item.id());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", item.id());
        result.put("itemKey", item.itemKey());
        result.put("status", item.status().name());
        result.put("version", item.version());
        return result;
    }

    /* 需求、任务与缺陷共用同一更新服务；目标类型、交付范围与角色领域在服务端先复核。 */
    private JsonNode updateWorkItem(long organizationId, AgentRun run, JsonNode arguments,
            WorkItemType expectedType, String idField) {
        long workItemId = arguments.path(idField).asLong();
        WorkItem item = expectedType == WorkItemType.REQUIREMENT
                ? requireBoundRequirement(organizationId, run, workItemId)
                : requireRunScopedItem(organizationId, run, workItemId, expectedType);
        requireDomainMatch(run, item.type());
        var updated = workItemCommandService.update(
                run.userId(),
                organizationId,
                workItemId,
                textOrNull(arguments, "title"),
                textOrNull(arguments, "description"),
                enumOrNull(arguments, "priority"),
                longOrNull(arguments, "assigneeUserId"),
                instantOrNull(arguments, "dueAt"),
                arguments.path("expectedVersion").asLong());
        return workItemProjection(updated);
    }

    /* 需求详情按字段合并：入参缺失的字段保留当前值，避免模型局部更新时清空既有材料。 */
    private JsonNode updateRequirementDetails(long organizationId, AgentRun run, JsonNode arguments) {
        long requirementId = arguments.path("requirementId").asLong();
        requireBoundRequirement(organizationId, run, requirementId);
        requireDomainMatch(run, WorkItemType.REQUIREMENT);
        RequirementDetails current = requirementDetailsService.get(run.userId(), organizationId, requirementId);
        List<String> acceptanceCriteria = arguments.path("acceptanceCriteria").isArray()
                ? acceptanceCriteriaOf(arguments)
                : current.acceptanceCriteria();
        RequirementDetails updated = requirementDetailsService.save(
                run.userId(),
                organizationId,
                requirementId,
                mergedText(arguments, "goal", current.goal()),
                mergedText(arguments, "inScope", current.inScope()),
                mergedText(arguments, "outOfScope", current.outOfScope()),
                acceptanceCriteria,
                mergedText(arguments, "businessValue", current.businessValue()),
                arguments.path("expectedVersion").asLong());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("workItemId", updated.workItemId());
        result.put("goal", updated.goal());
        result.put("inScope", updated.inScope());
        result.put("outOfScope", updated.outOfScope());
        result.set("acceptanceCriteria", objectMapper.valueToTree(updated.acceptanceCriteria()));
        result.put("businessValue", updated.businessValue());
        result.put("version", updated.version());
        return result;
    }

    /* 需求删除只对草稿开放且不可逆；契约已声明 requires_confirmation，写入前必然经过人工确认。 */
    private JsonNode deleteRequirement(long organizationId, AgentRun run, JsonNode arguments) {
        long requirementId = arguments.path("requirementId").asLong();
        requireBoundRequirement(organizationId, run, requirementId);
        requireDomainMatch(run, WorkItemType.REQUIREMENT);
        organizationRequirementService.delete(
                run.userId(), requirementId, arguments.path("expectedVersion").asLong());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("requirementId", requirementId);
        result.put("deleted", true);
        return result;
    }

    /* 各领域执行人集合由本领域负责人在早期阶段维护；服务端按最新事实复核负责人身份。 */
    private JsonNode assignExecutors(long organizationId, AgentRun run, JsonNode arguments) {
        long requirementId = requireBoundRequirementId(run, arguments);
        requireDomainMatch(run, WorkItemType.REQUIREMENT);
        RequirementParticipantRole role = RequirementParticipantRole.valueOf(arguments.path("role").asText());
        List<Long> executorUserIds = new java.util.ArrayList<>();
        arguments.path("executorUserIds").forEach(item -> executorUserIds.add(item.asLong()));
        organizationRequirementService.replaceExecutors(run.userId(), requirementId, role, executorUserIds);
        return participantsProjection(organizationId, requirementId);
    }

    /* 负责人集合决定四票会签的授权图，落库前必须经人工确认。 */
    private JsonNode assignParticipants(long organizationId, AgentRun run, JsonNode arguments) {
        long requirementId = requireBoundRequirementId(run, arguments);
        requireDomainMatch(run, WorkItemType.REQUIREMENT);
        List<OrganizationRequirementStore.ParticipantAssignment> assignments = new java.util.ArrayList<>();
        for (JsonNode assignment : arguments.path("assignments")) {
            assignments.add(new OrganizationRequirementStore.ParticipantAssignment(
                    RequirementParticipantRole.valueOf(assignment.path("role").asText()),
                    assignment.path("userId").asLong()));
        }
        organizationRequirementService.replaceParticipants(run.userId(), requirementId, assignments);
        return participantsProjection(organizationId, requirementId);
    }

    /* 任务生命周期动作：目标状态与准入条件由服务端固定状态机决定，审核动作永不进入白名单。 */
    private JsonNode advanceTask(long organizationId, AgentRun run, JsonNode arguments) {
        long taskId = arguments.path("taskId").asLong();
        WorkItem item = requireRunScopedItem(organizationId, run, taskId, null);
        requireDomainMatch(run, item.type());
        WorkflowAction action = WorkflowAction.valueOf(arguments.path("action").asText());
        if (!AgentStageActionPolicy.allowsTask(run.skill(), action)) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "ACTION_NOT_ALLOWED_FOR_SKILL", "Workflow action is not allowed for the run skill");
        }
        RequirementTransitionStore.TransitionResult transition = requirementTransitionService.transition(
                run.userId(), organizationId, taskId, action,
                arguments.path("expectedVersion").asLong(), taskIdempotencyKey(run, "advance_task", taskId),
                textOrNull(arguments, "reason"), null);
        return transitionProjection(transition);
    }

    /* 缺陷修复与验证由 BugService 按“修复人 / QA 成员”二次授权，Skill 只排除无关角色。 */
    private JsonNode advanceBug(long organizationId, AgentRun run, JsonNode arguments) {
        long bugId = arguments.path("bugId").asLong();
        requireRunScopedItem(organizationId, run, bugId, WorkItemType.BUG);
        requireDomainMatch(run, WorkItemType.BUG);
        BugAction action = BugAction.valueOf(arguments.path("action").asText());
        if (!AgentStageActionPolicy.allowsBug(run.skill(), action)) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "ACTION_NOT_ALLOWED_FOR_SKILL", "Bug action is not allowed for the run skill");
        }
        List<String> fixEvidence = new java.util.ArrayList<>();
        arguments.path("fixEvidence").forEach(item -> fixEvidence.add(item.asText()));
        BugView bug = bugService.transition(
                run.userId(), organizationId, bugId, action,
                arguments.path("expectedVersion").asLong(), textOrNull(arguments, "reason"), fixEvidence,
                taskIdempotencyKey(run, "advance_bug", bugId));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("action", action.name());
        result.put("status", bug.status().name());
        result.put("version", bug.version());
        result.set("availableActions", objectMapper.valueToTree(bug.availableActions()));
        return result;
    }

    /* 幂等键必须包含 Tool 名与目标项，避免同名动作（如 BLOCK）在任务与缺陷之间互相重放。 */
    private String taskIdempotencyKey(AgentRun run, String toolName, long itemId) {
        return run.id() + ":" + toolName + ":" + itemId;
    }

    /* 写工具只能作用于 Run 绑定的根需求；未绑定或越界一律拒绝，避免模型自行指定交付范围。 */
    private long requireBoundRequirementId(AgentRun run, JsonNode arguments) {
        long requested = arguments.path("requirementId").asLong();
        if (run.workItemId() == null || run.workItemId() != requested) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "RUN_WORK_ITEM_MISMATCH", "Tool must target the requirement bound to the run");
        }
        return requested;
    }

    private WorkItem requireBoundRequirement(long organizationId, AgentRun run, long requirementId) {
        requireBoundRequirementId(run, requirementId);
        return requireRunScopedItem(organizationId, run, requirementId, WorkItemType.REQUIREMENT);
    }

    private long requireBoundRequirementId(AgentRun run, long requirementId) {
        if (run.workItemId() == null || run.workItemId() != requirementId) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "RUN_WORK_ITEM_MISMATCH", "Tool must target the requirement bound to the run");
        }
        return requirementId;
    }

    /* 目标工作项必须存在、类型匹配且上溯到的根需求等于 Run 绑定需求；expectedType 为空表示任一角色任务。 */
    private WorkItem requireRunScopedItem(long organizationId, AgentRun run, long workItemId, WorkItemType expectedType) {
        WorkItem item = workItemQueryService.get(run.userId(), organizationId, workItemId);
        boolean typeMatches = expectedType == null
                ? item.type() == WorkItemType.UX_TASK
                        || item.type() == WorkItemType.DEV_TASK
                        || item.type() == WorkItemType.QA_TASK
                : item.type() == expectedType;
        if (!typeMatches) {
            throw new ToolExecutionRejectedException(HttpStatus.BAD_REQUEST.value(),
                    "WORK_ITEM_TYPE_MISMATCH", "Tool requires a work item of the expected type");
        }
        Long root = deliveryWorkflowStore.requirementId(organizationId, workItemId);
        if (run.workItemId() == null || root == null || !root.equals(run.workItemId())) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "RUN_WORK_ITEM_MISMATCH", "Tool must target a work item under the requirement bound to the run");
        }
        return item;
    }

    /* 任务与缺陷类型必须与 Run 的角色领域一致，避免 Skill 成为横向扩权通道（ADR-023 §5）。 */
    private void requireDomainMatch(AgentRun run, WorkItemType type) {
        AgentSkill skill = run.skill();
        boolean allowed = switch (type) {
            case UX_TASK -> skill == AgentSkill.UX;
            case DEV_TASK -> skill == AgentSkill.DEVELOPER;
            case QA_TASK -> skill == AgentSkill.QA;
            case BUG -> skill == AgentSkill.DEVELOPER || skill == AgentSkill.QA;
            case REQUIREMENT -> skill == AgentSkill.PRODUCT;
        };
        if (!allowed) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "WORK_ITEM_NOT_IN_SKILL_DOMAIN", "Work item type is outside the run skill domain");
        }
    }

    private JsonNode workItemProjection(WorkItem item) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", item.id());
        result.put("itemKey", item.itemKey());
        result.put("type", item.type().name());
        result.put("status", item.status().name());
        result.put("priority", item.priority().name());
        if (item.assigneeUserId() != null) {
            result.put("assigneeUserId", item.assigneeUserId());
        }
        result.put("version", item.version());
        return result;
    }

    private JsonNode participantsProjection(long organizationId, long requirementId) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("requirementId", requirementId);
        result.set("participants", objectMapper.valueToTree(participants.findParticipants(organizationId, requirementId)));
        return result;
    }

    private JsonNode transitionProjection(RequirementTransitionStore.TransitionResult transition) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("action", transition.action().name());
        result.put("status", transition.status().name());
        result.put("version", transition.version());
        result.put("eventId", transition.eventId());
        return result;
    }

    /* 创建测试运行只建立执行容器，不产生任何执行结论；结论由 record_test_result 单独记录。 */
    private JsonNode startTestRun(long organizationId, AgentRun run, JsonNode arguments) {
        long requirementId = requireBoundRequirementId(run, arguments);
        requireQaSkill(run);
        TestRunView testRun = qaService.createRun(
                run.userId(), organizationId, requirementId, arguments.path("environment").asText());
        return testRunProjection(testRun);
    }

    /* 执行结论必须经人工确认后落库；契约已声明 requires_confirmation，此处的写入复用同一人工入口。 */
    private JsonNode recordTestResult(long organizationId, AgentRun run, JsonNode arguments) {
        TestRunView testRun = requireRunScopedTestRun(organizationId, run, arguments.path("runId").asLong());
        List<String> evidence = new java.util.ArrayList<>();
        arguments.path("evidence").forEach(item -> evidence.add(item.asText()));
        TestResultView updated = qaService.updateResult(
                run.userId(), organizationId, testRun.id(), arguments.path("resultId").asLong(),
                TestResultStatus.valueOf(arguments.path("status").asText()),
                textOrNull(arguments, "actualResult"), evidence,
                arguments.path("expectedVersion").asLong());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("runId", testRun.id());
        result.put("resultId", updated.id());
        result.put("testCaseId", updated.testCaseId());
        result.put("status", updated.status().name());
        result.put("version", updated.version());
        return result;
    }

    /* 完成测试运行会固化放行结论，同样必须经人工确认。 */
    private JsonNode completeTestRun(long organizationId, AgentRun run, JsonNode arguments) {
        TestRunView testRun = requireRunScopedTestRun(organizationId, run, arguments.path("runId").asLong());
        TestRunView completed = qaService.completeRun(
                run.userId(), organizationId, testRun.id(), arguments.path("expectedVersion").asLong());
        return testRunProjection(completed);
    }

    /* 重开测试运行会清空固化统计并追加审计记录，属 QA 专属的受控写操作。 */
    private JsonNode reopenTestRun(long organizationId, AgentRun run, JsonNode arguments) {
        TestRunView testRun = requireRunScopedTestRun(organizationId, run, arguments.path("runId").asLong());
        TestRunView reopened = qaService.reopenRun(
                run.userId(), organizationId, testRun.id(), arguments.path("expectedVersion").asLong(),
                arguments.path("reason").asText(), taskIdempotencyKey(run, "reopen_test_run", testRun.id()));
        return testRunProjection(reopened);
    }

    /* 测试执行仅对 QA Skill 开放；其他阶段即使根需求处于测试中也不得代为执行。 */
    private void requireQaSkill(AgentRun run) {
        if (run.skill() != AgentSkill.QA) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "ACTION_NOT_ALLOWED_FOR_SKILL", "Test execution is limited to the QA skill");
        }
    }

    /* 测试运行必须属于 Run 绑定的根需求，避免模型借 runId 越过交付范围。 */
    private TestRunView requireRunScopedTestRun(long organizationId, AgentRun run, long runId) {
        requireQaSkill(run);
        TestRunView testRun = qaService.run(run.userId(), organizationId, runId);
        if (run.workItemId() == null || run.workItemId() != testRun.requirementId()) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "RUN_WORK_ITEM_MISMATCH", "Test run must belong to the requirement bound to the run");
        }
        return testRun;
    }

    private JsonNode testRunProjection(TestRunView testRun) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", testRun.id());
        result.put("requirementId", testRun.requirementId());
        result.put("environment", testRun.environment());
        result.put("status", testRun.status().name());
        result.put("version", testRun.version());
        return result;
    }

    private JsonNode createUxTask(long organizationId, AgentRun run, JsonNode arguments) {
        var item = workItemCommandService.createUxTask(
                run.userId(),
                organizationId,
                arguments.path("requirementId").asLong(),
                arguments.path("title").asText(),
                textOrNull(arguments, "description"),
                enumOrNull(arguments, "priority"),
                longOrNull(arguments, "assigneeUserId"));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", item.id());
        result.put("itemKey", item.itemKey());
        result.put("parentId", arguments.path("requirementId").asLong());
        result.put("status", item.status().name());
        result.put("version", item.version());
        return result;
    }

    private JsonNode createDevTask(long organizationId, AgentRun run, JsonNode arguments) {
        var item = developmentService.createDevTask(
                run.userId(),
                organizationId,
                arguments.path("requirementId").asLong(),
                arguments.path("title").asText(),
                textOrNull(arguments, "description"),
                longOrNull(arguments, "assigneeUserId"));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", item.id());
        result.put("itemKey", item.itemKey());
        result.put("parentId", arguments.path("requirementId").asLong());
        result.put("status", item.status().name());
        result.put("version", item.version());
        return result;
    }

    private JsonNode startDevelopment(long organizationId, AgentRun run, JsonNode arguments) {
        var result = developmentService.start(
                run.userId(),
                organizationId,
                arguments.path("devTaskId").asLong(),
                textOrNull(arguments, "targetBranch"),
                run.id() + ":dev-task:" + arguments.path("devTaskId").asLong());
        ObjectNode output = objectMapper.createObjectNode();
        output.put("branchId", result.branch().id());
        output.put("branchName", result.branch().name());
        output.put("mergeRequestId", result.mergeRequest().id());
        output.put("mergeRequestIid", result.mergeRequest().remoteMrIid());
        output.put("mergeRequestUrl", result.mergeRequest().webUrl());
        output.put("reconciled", result.reconciled());
        return output;
    }

    private JsonNode pipelineLog(long organizationId, AgentRun run, JsonNode arguments) {
        var log = pipelineService.logTail(
                run.userId(),
                organizationId,
                arguments.path("pipelineId").asLong(),
                arguments.path("jobId").asLong());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("pipelineId", log.pipelineId());
        result.put("jobId", log.jobId());
        result.put("content", log.content());
        result.put("truncated", log.truncated());
        result.put("redacted", log.redacted());
        return result;
    }

    private JsonNode createTestCase(long organizationId, AgentRun run, JsonNode arguments) {
        List<String> steps = new java.util.ArrayList<>();
        arguments.path("steps").forEach(step -> steps.add(step.asText()));
        var created = qaService.createCase(run.userId(), organizationId,
                arguments.path("requirementId").asLong(), arguments.path("title").asText(),
                textOrNull(arguments, "preconditions"), steps, arguments.path("expectedResult").asText(),
                ai.forge.server.qa.domain.TestCasePriority.valueOf(arguments.path("priority").asText()),
                ai.forge.server.qa.domain.TestCaseType.FUNCTIONAL);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", created.id());
        result.put("requirementId", created.requirementId());
        result.put("status", "DRAFT");
        result.put("version", created.version());
        return result;
    }

    private JsonNode createBug(long organizationId, AgentRun run, JsonNode arguments) {
        List<String> steps = new java.util.ArrayList<>();
        arguments.path("reproductionSteps").forEach(step -> steps.add(step.asText()));
        var created = bugService.create(run.userId(), organizationId,
                arguments.path("requirementId").asLong(), longOrNull(arguments, "testRunId"),
                longOrNull(arguments, "testResultId"), longOrNull(arguments, "devTaskId"),
                arguments.path("title").asText(),
                ai.forge.server.qa.domain.BugSeverity.valueOf(arguments.path("severity").asText()), steps,
                arguments.path("expectedResult").asText(), arguments.path("actualResult").asText(), longOrNull(arguments, "assigneeUserId"));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", created.id());
        result.put("itemKey", created.itemKey());
        result.put("status", created.status().name());
        result.put("version", created.version());
        return result;
    }

    private JsonNode advanceRequirement(long organizationId, AgentRun run, JsonNode arguments) {
        long requirementId = arguments.path("requirementId").asLong();
        if (run.workItemId() == null || run.workItemId() != requirementId) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "RUN_WORK_ITEM_MISMATCH", "Workflow action must target the requirement bound to the run");
        }
        WorkflowAction action = WorkflowAction.valueOf(arguments.path("action").asText());
        requireSkillAction(run, action);
        List<String> checklist = null;
        if (arguments.path("checklist").isArray()) {
            java.util.ArrayList<String> submittedChecklist = new java.util.ArrayList<>();
            arguments.path("checklist").forEach(item -> submittedChecklist.add(item.asText()));
            checklist = submittedChecklist;
        }
        RequirementTransitionStore.TransitionResult transition = requirementTransitionService.transition(
                run.userId(), organizationId, requirementId, action,
                arguments.path("expectedVersion").asLong(), run.id() + ":" + action.name(),
                textOrNull(arguments, "reason"), checklist);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("action", transition.action().name());
        result.put("status", transition.status().name());
        result.put("version", transition.version());
        result.put("eventId", transition.eventId());
        return result;
    }

    private void requireSkillAction(AgentRun run, WorkflowAction action) {
        if (!AgentStageActionPolicy.allows(run.skill(), action)) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "ACTION_NOT_ALLOWED_FOR_SKILL", "Workflow action is not allowed for the run skill");
        }
    }

    private JsonNode createDocument(
            long organizationId, AgentRun run, JsonNode arguments, String documentType) {
        long requirementId = arguments.path("requirementId").asLong();
        var document = documentService.listByWorkItem(
                        run.userId(), organizationId, requirementId).stream()
                .filter(candidate -> documentType.equals(candidate.type()))
                .filter(candidate -> candidate.currentVersionId() == null)
                .findFirst()
                .orElse(null);
        if (document == null) {
            document = documentService.create(
                    run.userId(),
                    organizationId,
                    requirementId,
                    documentType,
                    arguments.path("title").asText());
        }
        if (arguments.path("contentMarkdown").isTextual()) {
            document = documentService.save(
                    run.userId(),
                    organizationId,
                    document.id(),
                    document.version(),
                    DocumentContent.fromMarkdown(arguments.path("contentMarkdown").asText())
                            .proseMirrorJson());
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", document.id());
        result.put("documentType", document.type());
        result.put("title", document.title());
        result.put("status", document.status());
        result.put("version", document.version());
        if (document.currentVersionId() == null) {
            result.putNull("currentVersionId");
            result.put("currentVersionNo", 0);
        } else {
            result.put("currentVersionId", document.currentVersionId());
            result.put("currentVersionNo", 1);
        }
        return result;
    }

    /* 发布文档版本是产品评审与 UX 评审的准入前提；只允许发布 Run 绑定需求下的文档。 */
    private JsonNode publishDocument(long organizationId, AgentRun run, JsonNode arguments) {
        long documentId = arguments.path("documentId").asLong();
        Document document = documentService.get(run.userId(), organizationId, documentId);
        Long documentWorkItemId = document.workItemId();
        if (run.workItemId() == null || documentWorkItemId == null
                || !run.workItemId().equals(documentWorkItemId)) {
            throw new ToolExecutionRejectedException(HttpStatus.FORBIDDEN.value(),
                    "RUN_WORK_ITEM_MISMATCH", "Document must belong to the requirement bound to the run");
        }
        Long requestedVersionId = longOrNull(arguments, "versionId");
        Long versionId = requestedVersionId == null ? document.currentVersionId() : requestedVersionId;
        if (versionId == null) {
            throw new ToolExecutionRejectedException(HttpStatus.BAD_REQUEST.value(),
                    "INVALID_ARGUMENT", "Document has no saved version to publish");
        }
        Document published = documentService.publish(run.userId(), organizationId, documentId, versionId,
                arguments.path("expectedVersion").asLong());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", published.id());
        result.put("documentType", published.type());
        result.put("title", published.title());
        result.put("status", published.status());
        result.put("publishedVersionId", versionId);
        result.put("version", published.version());
        return result;
    }

    private AgentRun requireActiveRun(long organizationId, String runId) {
        AgentRun run = runStore.find(organizationId, runId)
                .orElseThrow(() -> new ToolExecutionRejectedException(
                        HttpStatus.NOT_FOUND.value(), "RUN_NOT_FOUND", "Run does not exist in scope"));
        if (run.status() != AgentRunStatus.RUNNING && run.status() != AgentRunStatus.WAITING_APPROVAL) {
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

    /* 字段缺失表示“保持原值”；显式传入 null 表示“清空”，其余情况取文本值。 */
    private String mergedText(JsonNode arguments, String field, String current) {
        if (!arguments.has(field)) {
            return current;
        }
        return arguments.path(field).isTextual() ? arguments.path(field).asText() : null;
    }

    private List<String> acceptanceCriteriaOf(JsonNode arguments) {
        List<String> criteria = new java.util.ArrayList<>();
        arguments.path("acceptanceCriteria").forEach(item -> criteria.add(item.asText()));
        return criteria;
    }

    private Long longOrNull(JsonNode arguments, String field) {
        return arguments.path(field).isNumber() ? arguments.path(field).asLong() : null;
    }

    /* 契约用 ISO-8601 字符串表达 UTC 截止时间；非法格式由 IllegalArgumentException 转为参数错误。 */
    private Instant instantOrNull(JsonNode arguments, String field) {
        return arguments.path(field).isTextual() ? Instant.parse(arguments.path(field).asText()) : null;
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
