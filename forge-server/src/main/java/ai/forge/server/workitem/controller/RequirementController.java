package ai.forge.server.workitem.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.workitem.application.OrganizationRequirementPage;
import ai.forge.server.workitem.application.OrganizationRequirementService;
import ai.forge.server.workitem.application.OrganizationRequirementStore;
import ai.forge.server.workitem.application.OrganizationRequirementView;
import ai.forge.server.workitem.application.RequirementMemberView;
import ai.forge.server.workitem.application.RequirementOverview;
import ai.forge.server.workitem.application.RequirementParticipantView;
import ai.forge.server.workitem.domain.RequirementParticipantRole;
import ai.forge.server.workitem.domain.WorkItemPriority;
import ai.forge.server.workitem.domain.WorkItemStatus;
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
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("!test-unit")
@RequestMapping("/api/v1/requirements")
@Tag(name = "需求", description = "实例唯一公司的需求创建、查询与协作入口")
public class RequirementController {

    /* 解析当前公司 scope，并复用既有需求权限、状态与编号规则。 */
    private final OrganizationRequirementService service;

    public RequirementController(OrganizationRequirementService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "创建需求", description = "权限：Owner 或 Product；scope 由当前 Session 在服务端解析。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "需求已创建"),
        @ApiResponse(responseCode = "404", description = "当前账号没有创建需求的公司角色")
    })
    public ResponseEntity<OrganizationRequirementView> create(
            @Valid @RequestBody CreateRequirementRequest body, HttpServletRequest request) {
        OrganizationRequirementView created = service.create(
                AuthController.requireContext(request).userId(), body.title(), body.description(), body.priority());
        return ResponseEntity.created(URI.create("/api/v1/requirements/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "搜索需求", description = "支持标题、编号、描述、状态和我的参与需求筛选。Owner 的我的需求等同全部需求。")
    public OrganizationRequirementPage list(
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(required = false) WorkItemStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        return service.list(
                AuthController.requireContext(request).userId(), mine, q, status, page, pageSize);
    }

    @GetMapping("/overview")
    @Operation(summary = "读取当前需求概览", description = "返回全部、进行中和已完成 Requirement 数量。")
    public RequirementOverview overview(HttpServletRequest request) {
        return service.overview(AuthController.requireContext(request).userId());
    }

    @GetMapping("/members")
    @Operation(summary = "读取需求可选成员", description = "返回当前公司的有效成员及其业务角色。")
    public List<RequirementMemberView> members(HttpServletRequest request) {
        return service.members(AuthController.requireContext(request).userId());
    }

    @GetMapping("/{requirementId}")
    public OrganizationRequirementView get(@PathVariable long requirementId, HttpServletRequest request) {
        return service.get(AuthController.requireContext(request).userId(), requirementId);
    }

    @GetMapping("/{requirementId}/participants")
    public List<RequirementParticipantView> participants(
            @PathVariable long requirementId, HttpServletRequest request) {
        return service.participants(AuthController.requireContext(request).userId(), requirementId);
    }

    @PutMapping("/{requirementId}/participants")
    @Operation(summary = "设置需求参与人", description = "权限：Owner 或 Product；每个业务角色最多关联一名持有该角色的成员。")
    public List<RequirementParticipantView> replaceParticipants(
            @PathVariable long requirementId,
            @Valid @RequestBody ReplaceParticipantsRequest body,
            HttpServletRequest request) {
        return service.replaceParticipants(
                AuthController.requireContext(request).userId(),
                requirementId,
                body.participants().stream()
                        .map(item -> new OrganizationRequirementStore.ParticipantAssignment(item.role(), item.userId()))
                        .toList());
    }

    public record CreateRequirementRequest(
            /* 需求列表与详情展示的简短标题。 */ @NotBlank @Size(max = 255) String title,
            /* 可搜索的需求背景与业务说明。 */ @Size(max = 10000) String description,
            /* 需求业务处理优先级。 */ @NotNull WorkItemPriority priority) {}

    public record ReplaceParticipantsRequest(
            /* 覆盖当前需求全部角色参与关系，最多产品、UX、开发、测试各一名。 */
            @NotNull @Size(max = 4) List<@Valid ParticipantRequest> participants) {}

    public record ParticipantRequest(
            /* 当前需求中的固定业务角色。 */ @NotNull RequirementParticipantRole role,
            /* 被关联且持有对应角色的有效成员标识。 */ @Positive long userId) {}
}
