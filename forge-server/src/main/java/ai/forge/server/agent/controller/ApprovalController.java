package ai.forge.server.agent.controller;

import ai.forge.server.agent.application.ApprovalService;
import ai.forge.server.agent.application.ApprovalSnapshot;
import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.auth.domain.AuthContext;
import ai.forge.server.organization.application.OrganizationAccessService;
import ai.forge.server.platform.web.RequestIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/approvals")
@Tag(name = "Agent Approval", description = "读取并决定持久化 Agent Tool 审批")
public class ApprovalController {

    /* 执行审批 scope、权限、自批与乐观锁规则。 */
    private final ApprovalService approvalService;
    /* 从登录成员解析唯一公司范围。 */ private final OrganizationAccessService organizationAccess;

    public ApprovalController(ApprovalService approvalService, OrganizationAccessService organizationAccess) {
        this.approvalService = approvalService;
        this.organizationAccess = organizationAccess;
    }

    @GetMapping("/{approvalId}")
    @Operation(summary = "读取审批卡片", description = "只返回参数摘要与资源版本，不返回加密前完整参数。")
    public ApprovalSnapshot get(@PathVariable String approvalId,
            HttpServletRequest request) {
        long userId = AuthController.requireContext(request).userId();
        return approvalService.get(userId, organizationAccess.requireContext(userId).organizationId(), approvalId);
    }

    @PostMapping("/{approvalId}:decide")
    @Operation(summary = "批准或拒绝审批", description = "决策使用 expectedVersion 乐观锁；发起人不得自批。")
    public ApprovalSnapshot decide(@PathVariable String approvalId, @Valid @RequestBody DecisionRequest body,
            HttpServletRequest request) {
        AuthContext context = AuthController.requireContext(request);
        return approvalService.decide(context.userId(), organizationAccess.requireContext(context.userId()).organizationId(), approvalId,
                body.decision() == Decision.APPROVE, body.expectedVersion(),
                (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
    }

    @GetMapping("/by-run/{runId}")
    @Operation(summary = "读取 Run 当前审批", description = "页面刷新后从 MySQL 恢复审批卡片。")
    public ApprovalSnapshot getForRun(@PathVariable String runId,
            HttpServletRequest request) {
        long userId = AuthController.requireContext(request).userId();
        return approvalService.getForRun(userId, organizationAccess.requireContext(userId).organizationId(), runId);
    }

    @PostMapping("/{approvalId}:cancel")
    @Operation(summary = "取消待审批调用", description = "只有 Run 发起人可以取消尚未决定的审批。")
    public ApprovalSnapshot cancel(@PathVariable String approvalId, @Valid @RequestBody CancelRequest body,
            HttpServletRequest request) {
        long userId = AuthController.requireContext(request).userId();
        return approvalService.cancel(userId, organizationAccess.requireContext(userId).organizationId(), approvalId, body.expectedVersion(),
                (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
    }

    public enum Decision {
        /* 批准被冻结的 Tool Call。 */
        APPROVE,
        /* 拒绝被冻结的 Tool Call。 */
        REJECT
    }

    public record DecisionRequest(
            /* 明确批准或拒绝。 */ @NotNull Decision decision,
            /* 审批卡片读取到的乐观锁版本。 */ @PositiveOrZero long expectedVersion) {
    }

    public record CancelRequest(
            /* 审批卡片读取到的乐观锁版本。 */ @PositiveOrZero long expectedVersion) {
    }
}
