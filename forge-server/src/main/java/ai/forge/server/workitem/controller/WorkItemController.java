package ai.forge.server.workitem.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.auth.domain.AuthContext;
import ai.forge.server.workitem.application.WorkItemCommandService;
import ai.forge.server.workitem.application.WorkItemCollaborationService;
import ai.forge.server.workitem.application.WorkItemPage;
import ai.forge.server.workitem.application.WorkItemQueryService;
import ai.forge.server.workitem.application.DeliveryGraph;
import ai.forge.server.workitem.application.DeliveryGraphQuery;
import ai.forge.server.workitem.application.RequirementTransitionService;
import ai.forge.server.workitem.application.RequirementDetailsService;
import ai.forge.server.workitem.domain.RequirementDetails;
import ai.forge.server.workitem.application.RequirementTransitionStore;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.ActivityItem;
import ai.forge.server.workitem.domain.WorkItemRelation;
import ai.forge.server.workitem.domain.WorkItemRelationType;
import ai.forge.server.workitem.domain.WorkItemLabel;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
import ai.forge.server.workitem.domain.WorkItemType;
import ai.forge.server.workitem.domain.WorkItemEvent;
import ai.forge.server.workitem.domain.WorkflowAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@Profile("!test-unit")
@RequestMapping("/api/v1/work-items")
@Tag(name = "Work Item", description = "Requirement 与角色 Task 的创建、查询、分页和基础字段编辑")
public class WorkItemController {

    /* 执行服务端类型授权、原子编号创建和乐观锁编辑。 */
    private final WorkItemCommandService commandService;

    /* 执行强制租户与项目范围的详情和分页查询。 */
    private final WorkItemQueryService queryService;

    /* 执行 Requirement 固定 Action，并读取同范围内的追加活动事件。 */
    private final RequirementTransitionService transitionService;

    /* 读写 Requirement 的结构化产品材料。 */
    private final RequirementDetailsService detailsService;

    /* 管理非树状关系、用户评论与统一 Activity 投影。 */
    private final WorkItemCollaborationService collaborationService;

    /* 以有界批量查询投影 Requirement 的端到端交付关系。 */
    private final DeliveryGraphQuery deliveryGraphQuery;

    public WorkItemController(
            WorkItemCommandService commandService,
            WorkItemQueryService queryService,
            RequirementTransitionService transitionService,
            RequirementDetailsService detailsService,
            WorkItemCollaborationService collaborationService,
            DeliveryGraphQuery deliveryGraphQuery) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.transitionService = transitionService;
        this.detailsService = detailsService;
        this.collaborationService = collaborationService;
        this.deliveryGraphQuery = deliveryGraphQuery;
    }

    @GetMapping("/{workItemId}/details")
    public RequirementDetails details(
            @PathVariable long workItemId, @RequestParam long workspaceId, @RequestParam long projectId,
            HttpServletRequest request) {
        return detailsService.get(AuthController.requireContext(request).userId(), workspaceId, projectId, workItemId);
    }

    @PutMapping("/{workItemId}/details")
    public RequirementDetails saveDetails(
            @PathVariable long workItemId, @RequestParam long workspaceId, @RequestParam long projectId,
            @Valid @RequestBody RequirementDetailsRequest body, HttpServletRequest request) {
        return detailsService.save(
                AuthController.requireContext(request).userId(), workspaceId, projectId, workItemId,
                body.goal(), body.inScope(), body.outOfScope(), body.acceptanceCriteria(),
                body.businessValue(), body.expectedVersion());
    }

    @PostMapping
    @Operation(
            summary = "创建 Work Item",
            description = "按 type 校验 requirement/ux/task.create；itemKey、status 和 reporter 仅由服务端生成。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "工作项已使用项目内原子编号创建"),
        @ApiResponse(responseCode = "400", description = "类型或基础字段不合法"),
        @ApiResponse(responseCode = "404", description = "项目、权限范围或负责人项目成员不存在")
    })
    public ResponseEntity<WorkItem> create(
            @Valid @RequestBody CreateWorkItemRequest body, HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        WorkItem item = commandService.create(
                context.userId(),
                body.workspaceId(),
                body.projectId(),
                body.type(),
                body.title(),
                body.description(),
                body.priority(),
                body.assigneeUserId(),
                body.dueAt());
        return ResponseEntity.created(URI.create("/api/v1/work-items/" + item.id())).body(item);
    }

    @GetMapping("/{workItemId}")
    @Operation(
            summary = "读取 Work Item",
            description = "按实际 type 校验 read 权限，并同时限制 workspaceId、projectId 和未删除状态。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "工作项详情"),
        @ApiResponse(responseCode = "404", description = "资源不存在、已删除、越权或 scope 不匹配")
    })
    public WorkItemDetailResponse get(
            @PathVariable long workItemId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        WorkItem item = queryService.get(context.userId(), workspaceId, projectId, workItemId);
        RequirementTransitionService.ActionHints hints = transitionService.hints(context.userId(), item);
        return WorkItemDetailResponse.from(item, hints);
    }

    @GetMapping
    @Operation(
            summary = "分页查询 Work Item",
            description = "列表固定按 itemNumber 倒序；page 从 1 开始，pageSize 最大 100，筛选值使用枚举白名单。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "项目范围内的工作项分页摘要"),
        @ApiResponse(responseCode = "400", description = "分页或枚举筛选参数不合法"),
        @ApiResponse(responseCode = "404", description = "项目不存在或当前用户无读取范围")
    })
    public WorkItemPage list(
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            @RequestParam(required = false) WorkItemType type,
            @RequestParam(required = false) WorkItemStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return queryService.list(context.userId(), workspaceId, projectId, type, status, page, pageSize);
    }

    @PatchMapping("/{workItemId}")
    @Operation(
            summary = "编辑 Work Item 基础字段",
            description = "按实际 type 校验 edit 权限；必须携带 expectedVersion，编号、类型和状态不能通过本接口修改。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "基础字段已更新且 version 增加"),
        @ApiResponse(responseCode = "400", description = "基础字段或 expectedVersion 不合法"),
        @ApiResponse(responseCode = "409", description = "expectedVersion 已过期"),
        @ApiResponse(responseCode = "404", description = "资源、权限范围或负责人项目成员不存在")
    })
    public WorkItem update(
            @PathVariable long workItemId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            @Valid @RequestBody UpdateWorkItemRequest body,
            HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return commandService.update(
                context.userId(),
                workspaceId,
                projectId,
                workItemId,
                body.title(),
                body.description(),
                body.priority(),
                body.assigneeUserId(),
                body.dueAt(),
                body.expectedVersion());
    }

    @PostMapping("/{workItemId}/transitions")
    @Operation(
            summary = "执行 Requirement 固定工作流动作",
            description = "客户端只提交 Action、expectedVersion、幂等键和必要的 UX checklist；目标状态由服务端 Registry 决定。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "转换、评审记录和活动事件已在同一事务提交"),
        @ApiResponse(responseCode = "409", description = "状态、版本或幂等键冲突"),
        @ApiResponse(responseCode = "422", description = "Guard 未满足，details.missing 返回缺失条件"),
        @ApiResponse(responseCode = "404", description = "资源、scope 或动作权限不可见")
    })
    public RequirementTransitionStore.TransitionResult transition(
            @PathVariable long workItemId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            @Valid @RequestBody TransitionRequest body,
            HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return transitionService.transition(
                context.userId(),
                workspaceId,
                projectId,
                workItemId,
                body.action(),
                body.expectedVersion(),
                body.idempotencyKey(),
                body.reason(),
                body.checklist());
    }

    @GetMapping("/{workItemId}/events")
    @Operation(summary = "读取 Work Item 状态事件", description = "按事件 id 升序返回追加写工作流事件。")
    public List<WorkItemEvent> events(
            @PathVariable long workItemId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return transitionService.events(context.userId(), workspaceId, projectId, workItemId);
    }

    @PostMapping("/{workItemId}/relations")
    public ResponseEntity<WorkItemRelation> createRelation(
            @PathVariable long workItemId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            @Valid @RequestBody CreateRelationRequest body,
            HttpServletRequest request) {
        WorkItemRelation relation = collaborationService.createRelation(
                AuthController.requireContext(request).userId(),
                workspaceId,
                projectId,
                workItemId,
                body.targetId(),
                body.relationType());
        return ResponseEntity.created(URI.create("/api/v1/work-items/" + workItemId + "/relations/" + relation.id()))
                .body(relation);
    }

    @GetMapping("/{workItemId}/relations")
    public List<WorkItemRelation> relations(
            @PathVariable long workItemId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            HttpServletRequest request) {
        return collaborationService.relations(
                AuthController.requireContext(request).userId(), workspaceId, projectId, workItemId);
    }

    @PostMapping("/{workItemId}/labels")
    public ResponseEntity<Void> addLabel(
            @PathVariable long workItemId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            @Valid @RequestBody AddLabelRequest body,
            HttpServletRequest request) {
        collaborationService.addLabel(
                AuthController.requireContext(request).userId(),
                workspaceId,
                projectId,
                workItemId,
                body.label());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{workItemId}/comments")
    public ResponseEntity<ActivityItem> comment(
            @PathVariable long workItemId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            @Valid @RequestBody CreateCommentRequest body,
            HttpServletRequest request) {
        ActivityItem comment = collaborationService.comment(
                AuthController.requireContext(request).userId(),
                workspaceId,
                projectId,
                workItemId,
                body.body());
        return ResponseEntity.created(URI.create("/api/v1/work-items/" + workItemId + "/comments/" + comment.id()))
                .body(comment);
    }

    @GetMapping("/{workItemId}/activity")
    @Operation(summary = "读取统一 Activity", description = "按发生时间合并工作流事件和未删除评论。")
    public List<ActivityItem> activity(
            @PathVariable long workItemId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            HttpServletRequest request) {
        return collaborationService.activity(
                AuthController.requireContext(request).userId(), workspaceId, projectId, workItemId);
    }

    @GetMapping("/{workItemId}/delivery-graph")
    @Operation(
            summary = "读取 Requirement Delivery Graph",
            description = "逐类授权后，以深度 8、节点 500 的上限批量投影 Work Item、文档与关系。")
    public DeliveryGraph deliveryGraph(
            @PathVariable long workItemId,
            @RequestParam long workspaceId,
            @RequestParam long projectId,
            HttpServletRequest request) {
        return deliveryGraphQuery.get(
                AuthController.requireContext(request).userId(), workspaceId, projectId, workItemId);
    }

    public record CreateRelationRequest(
            /* 同一项目内的关系目标工作项。 */ @Positive long targetId,
            /* 非树状关系的固定业务语义。 */ @NotNull WorkItemRelationType relationType) {}

    public record CreateCommentRequest(
            /* 写入统一 Activity 的评论正文。 */ @NotBlank @Size(max = 4000) String body) {}

    public record AddLabelRequest(
            /* 服务端白名单内、可用于 UX 跳过判断的分类。 */ @NotNull WorkItemLabel label) {}

    public record CreateWorkItemRequest(
            /* 所属 Workspace；服务端会与项目和当前权限重新比对。 */
            @NotNull @Positive Long workspaceId,
            /* 所属 Project；编号序列和所有查询均限制在该范围。 */
            @NotNull @Positive Long projectId,
            /* 本轮允许创建的 Requirement 或角色 Task 类型。 */
            @NotNull WorkItemType type,
            /* 工作项标题；服务端去除首尾空白后仍必须非空。 */
            @NotBlank @Size(max = 255) String title,
            /* 可选详细说明；空值由服务端规范为空字符串。 */
            String description,
            /* 工作项业务优先级，不能与 Tool 风险等级混用。 */
            @NotNull WorkItemPriority priority,
            /* 可选负责人；若存在则必须是当前项目的有效成员。 */
            @Positive Long assigneeUserId,
            /* 可选 UTC 截止时间。 */
            Instant dueAt) {}

    public record UpdateWorkItemRequest(
            /* 可选新标题；存在时去除首尾空白后仍必须非空。 */
            @Size(min = 1, max = 255) String title,
            /* 可选新说明；空值表示保留当前值。 */
            String description,
            /* 可选新业务优先级；空值表示保留当前值。 */
            WorkItemPriority priority,
            /* 可选新负责人；存在时必须是当前项目的有效成员。 */
            @Positive Long assigneeUserId,
            /* 可选新 UTC 截止时间；空值表示保留当前值。 */
            Instant dueAt,
            /* 客户端读取到的聚合版本；写入时必须精确匹配。 */
            @PositiveOrZero long expectedVersion) {}

    public record TransitionRequest(
            /* 请求执行的固定工作流动作；请求不接受任意目标状态。 */
            @NotNull WorkflowAction action,
            /* 客户端读取到的 Work Item 聚合版本。 */
            @PositiveOrZero long expectedVersion,
            /* 同一 Work Item 内唯一的稳定重试键。 */
            @NotBlank @Size(max = 128) String idempotencyKey,
            /* 退回等动作要求的审计原因；无需原因时可为空。 */
            @Size(max = 1000) String reason,
            /* UX 阶段提交时确认的交付物清单；服务端 Guard 固定核验必需项。 */
            List<@NotBlank String> checklist) {}

    public record RequirementDetailsRequest(
            /* 业务目标；Guard 要求非空。 */ @NotNull String goal,
            /* 纳入范围；Guard 要求非空。 */ @NotNull String inScope,
            /* 排除范围；允许为空。 */ @NotNull String outOfScope,
            /* 验收标准；Guard 要求至少一条非空值。 */ @NotNull List<@NotBlank String> acceptanceCriteria,
            /* 业务价值；允许为空。 */ @NotNull String businessValue,
            /* 客户端读取到的材料版本。 */ @PositiveOrZero long expectedVersion) {}

    public record WorkItemDetailResponse(
            /* 工作项稳定标识。 */ long id,
            /* 所属工作区。 */ long workspaceId,
            /* 所属项目。 */ long projectId,
            /* 项目内展示编号。 */ String itemKey,
            /* 工作项类型。 */ WorkItemType type,
            /* 工作项标题。 */ String title,
            /* 工作项说明。 */ String description,
            /* 服务端当前状态。 */ WorkItemStatus status,
            /* 业务优先级。 */ WorkItemPriority priority,
            /* 可选负责人。 */ Long assigneeUserId,
            /* 创建者。 */ long reporterUserId,
            /* 可选截止时间。 */ Instant dueAt,
            /* 聚合乐观锁版本。 */ long version,
            /* 创建时间。 */ Instant createdAt,
            /* 更新时间。 */ Instant updatedAt,
            /* 当前身份与状态下可尝试的动作，不替代执行时授权。 */ List<WorkflowAction> availableActions,
            /* 动作对应的机器可读缺失材料。 */ java.util.Map<WorkflowAction, List<String>> guardHints) {
        static WorkItemDetailResponse from(WorkItem item, RequirementTransitionService.ActionHints hints) {
            return new WorkItemDetailResponse(item.id(), item.workspaceId(), item.projectId(), item.itemKey(),
                    item.type(), item.title(), item.description(), item.status(), item.priority(), item.assigneeUserId(),
                    item.reporterUserId(), item.dueAt(), item.version(), item.createdAt(), item.updatedAt(),
                    hints.availableActions(), hints.guardHints());
        }
    }
}
