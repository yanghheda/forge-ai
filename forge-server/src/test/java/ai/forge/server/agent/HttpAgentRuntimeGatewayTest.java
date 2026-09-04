package ai.forge.server.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.agent.application.AgentRunRequested;
import ai.forge.server.agent.application.ToolContractRegistry;
import ai.forge.server.agent.domain.MediumToolConfirmation;
import ai.forge.server.agent.infrastructure.HttpAgentRuntimeGateway;
import ai.forge.server.platform.agent.AgentServiceTokenProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.web.client.RestClient;

class HttpAgentRuntimeGatewayTest {

    /* 验证目标 HTTP 请求而不启动真实 Agent 进程。 */
    private final RestClient.Builder restClientBuilder = RestClient.builder();

    /* 捕获 RestClient 发出的请求。 */
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();

    /* 加载仓库契约，为 Manifest 提供 Skill 白名单与上限。 */
    private final ToolContractRegistry toolContractRegistry = new ToolContractRegistry();

    @Test
    void startsRunWithServiceJwtAndBackendGeneratedManifest() throws Exception {
        AgentServiceTokenProvider tokens = new AgentServiceTokenProvider(
                "test-only-internal-jwt-secret-32-bytes-minimum", Duration.ofMinutes(5));
        HttpAgentRuntimeGateway gateway = new HttpAgentRuntimeGateway(
                restClientBuilder, "http://forge-agent:8000", tokens, toolContractRegistry);
        server.expect(request -> {
                    assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
                    assertThat(request.getURI().toString())
                            .isEqualTo("http://forge-agent:8000/internal/v1/runs/01JTEST0000000000000000000/start");
                    assertThat(request.getHeaders().getFirst("Authorization")).startsWith("Bearer ");
                })
                .andExpect(MockRestRequestMatchers.content().json("""
                        {
                          "manifest": {
                            "runId": "01JTEST0000000000000000000",
                            "skill": "UX",
                            "effectiveToolNames": [
                                "get_project", "get_work_item", "get_delivery_graph", "search_documents",
                                "create_ux_task", "create_ux_document"
                            ],
                            "policy": {"mediumConfirmation": "ASK", "maxToolCalls": 15}
                          }
                        }
                        """, false))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"status\":\"SUCCEEDED\",\"plan\":[\"plan\"],\"answer\":\"done\",\"state_version\":3}",
                        MediaType.APPLICATION_JSON));

        var result = gateway.start(new AgentRunRequested(
                "01JTEST0000000000000000000",
                2,
                10,
                1024L,
                12,
                "UX",
                MediumToolConfirmation.ASK,
                "整理 UX 计划",
                "request-1"));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.answer()).isEqualTo("done");
        server.verify();
    }
}
