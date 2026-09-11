package ai.forge.server.agent.infrastructure;

import ai.forge.server.agent.application.AgentRunRequested;
import ai.forge.server.agent.application.AgentRuntimeGateway;
import ai.forge.server.agent.application.SkillContract;
import ai.forge.server.agent.application.ToolContractRegistry;
import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.platform.agent.AgentServiceTokenProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("!test-unit")
@ConditionalOnProperty(name = "forge.infrastructure.agent.dispatch-enabled", matchIfMissing = true)
public class HttpAgentRuntimeGateway implements AgentRuntimeGateway {

    /* 只访问配置的内部 forge-agent 地址。 */
    private final RestClient restClient;

    /* 为每次内部请求签发短时服务身份。 */
    private final AgentServiceTokenProvider tokenProvider;

    /* 提供该 Skill 允许的 Tool 白名单与调用上限。 */
    private final ToolContractRegistry toolContractRegistry;

    public HttpAgentRuntimeGateway(
            RestClient.Builder builder,
            @Value("${forge.infrastructure.agent.base-url}") String baseUrl,
            AgentServiceTokenProvider tokenProvider,
            ToolContractRegistry toolContractRegistry) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.tokenProvider = tokenProvider;
        this.toolContractRegistry = toolContractRegistry;
    }

    @Override
    public RunResult start(AgentRunRequested requested) {
        Instant issuedAt = Instant.now();
        StartResponse response = restClient
                .post()
                .uri("/internal/v1/runs/{runId}/start", requested.runId())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(tokenProvider.createRunToken(
                        issuedAt,
                        requested.runId(),
                        requested.organizationId())))
                .body(new StartRequest(manifest(requested, issuedAt), requested.message()))
                .retrieve()
                .body(StartResponse.class);
        if (response == null || (!"SUCCEEDED".equals(response.status())
                && !"WAITING_APPROVAL".equals(response.status()))) {
            throw new IllegalStateException("Agent Runtime returned an unsupported result");
        }
        return new RunResult(response.status(), response.plan(), response.answer(), response.stateVersion());
    }

    private ContextManifest manifest(AgentRunRequested requested, Instant issuedAt) {
        Scope scope = new Scope(requested.organizationId(), requested.workItemId());
        List<ResourceRef> refs = requested.workItemId() == null
                ? List.of()
                : List.of(new ResourceRef("WORK_ITEM", requested.workItemId().toString(), 0));
        AgentSkill skill = AgentSkill.valueOf(requested.skill());
        SkillContract contract = toolContractRegistry.findSkill(requested.skill()).orElseThrow(
                () -> new IllegalStateException("Skill contract missing for " + requested.skill()));
        return new ContextManifest(
                requested.runId(),
                new Subject(requested.userId()),
                scope,
                requested.skill(),
                contract.promptVersion(),
                contract.contextTemplate(),
                toolContractRegistry.effectiveToolNames(skill),
                new Policy(
                        requested.mediumToolConfirmation().name(),
                        contract.maxToolCalls(),
                        50_000),
                refs,
                issuedAt.plus(5, ChronoUnit.MINUTES));
    }

    private record StartRequest(
            /* Backend 生成且受服务 JWT 保护的上下文清单。 */
            ContextManifest manifest,
            /* 仅在受保护的内部调用中传递，不进入 Backend 业务表或日志。 */
            String message) {
    }

    private record ContextManifest(
            /* 幂等执行与 Checkpoint thread 共用的 Run 标识。 */
            String runId,
            /* 已认证的发起主体。 */
            Subject subject,
            /* 已校验的租户和资源范围。 */
            Scope scope,
            /* 本次固定的 Skill 类型。 */
            String skill,
            /* 可回归的系统 Prompt 版本。 */
            String promptVersion,
            /* 最小上下文模板版本。 */
            String contextTemplate,
            /* Skill 白名单与运行时策略共同裁剪后的 Tool 名称。 */
            List<String> effectiveToolNames,
            /* 图执行预算，不代表 Backend 业务授权。 */
            Policy policy,
            /* 可回溯但不包含正文的资源引用。 */
            List<ResourceRef> resourceRefs,
            /* Manifest 的短时失效时间。 */
            Instant expiresAt) {
    }

    private record Subject(
            /* Backend 已认证的用户主键。 */
            long userId) {
    }

    private record Scope(
            /* Run 所属工作区。 */
            long organizationId,
            /* 可选工作项。 */
            Long workItemId) {
    }

    private record Policy(
            /* MEDIUM 风险 Tool 的确认策略：ASK 需确认、ALLOW 直接执行、DENY 拒绝。 */
            String mediumConfirmation,
            /* Skill 契约声明的 Tool 调用硬上限。 */
            int maxToolCalls,
            /* 模型令牌预算上限。 */
            int tokenBudget) {
    }

    private record ResourceRef(
            /* Backend 资源类型。 */
            String type,
            /* Backend 资源主键的字符串表示。 */
            String id,
            /* 当前最小清单的资源版本占位。 */
            int version) {
    }

    private record StartResponse(
            /* Agent 图终态。 */
            String status,
            /* 图生成的计划。 */
            List<String> plan,
            /* 图生成的完成摘要。 */
            String answer,
            /* Checkpoint 中的状态版本。 */
            @JsonProperty("state_version") int stateVersion) {
    }
}
