package ai.forge.server.platform.health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("qdrant")
@ConditionalOnProperty(name = "forge.infrastructure.qdrant.health-enabled", matchIfMissing = true)
public class QdrantHealthIndicator implements HealthIndicator {

    /* Qdrant 就绪端点；只用于派生索引健康探测，不承载业务事实。 */
    private final URI readyEndpoint;

    /* 单次探测的超时，防止健康接口被不可用的派生服务长期阻塞。 */
    private final Duration timeout;

    /* JDK HTTP 客户端；探测不引入 Qdrant 业务读写能力。 */
    private final HttpClient httpClient;

    public QdrantHealthIndicator(
            @Value("${forge.infrastructure.qdrant.base-url:http://qdrant:6333}") String baseUrl,
            @Value("${forge.infrastructure.qdrant.health-timeout:1s}") Duration timeout) {
        this.readyEndpoint = URI.create(baseUrl + "/readyz");
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public Health health() {
        HttpRequest request = HttpRequest.newBuilder(readyEndpoint).timeout(timeout).GET().build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Health.up().withDetail("role", "rebuildable-derived-index").build();
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
                .withDetail("role", "rebuildable-derived-index")
                .withDetail("reason", reason)
                .build();
    }
}
