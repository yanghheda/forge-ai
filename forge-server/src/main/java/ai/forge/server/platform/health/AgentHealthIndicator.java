package ai.forge.server.platform.health;

import ai.forge.server.platform.agent.AgentServiceTokenProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("agent")
@ConditionalOnProperty(name = "forge.infrastructure.agent.health-enabled", matchIfMissing = true)
public class AgentHealthIndicator implements HealthIndicator {

    /* Agent 内部就绪地址；Compose 中只允许通过内部网络访问。 */
    private final URI readyEndpoint;

    /* 单次探测超时，防止可选 Agent 能力阻塞业务健康接口。 */
    private final Duration timeout;

    /* JDK HTTP 客户端；仅执行无业务副作用的健康请求。 */
    private final HttpClient httpClient;

    /* 短时服务令牌签发器；每次探测使用新令牌缩短重放窗口。 */
    private final AgentServiceTokenProvider tokenProvider;

    public AgentHealthIndicator(
            @Value("${forge.infrastructure.agent.base-url:http://forge-agent:8000}") String baseUrl,
            @Value("${forge.infrastructure.agent.health-timeout:1s}") Duration timeout,
            AgentServiceTokenProvider tokenProvider) {
        this.readyEndpoint = URI.create(baseUrl + "/internal/v1/health/ready");
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Health health() {
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest request = HttpRequest.newBuilder(readyEndpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + tokenProvider.createToken(Instant.now()))
                .header("X-Request-ID", requestId)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Health.up().withDetail("role", "optional-agent-runtime").build();
            }
            return degraded("HTTP_" + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return degraded(exception.getClass().getSimpleName());
        } catch (Exception exception) {
            return degraded(exception.getClass().getSimpleName());
        }
    }

    private Health degraded(String reason) {
        return Health.status(new Status("DEGRADED"))
                .withDetail("role", "optional-agent-runtime")
                .withDetail("reason", reason)
                .build();
    }
}
