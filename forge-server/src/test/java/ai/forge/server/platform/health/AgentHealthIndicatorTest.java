package ai.forge.server.platform.health;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.platform.agent.AgentServiceTokenProvider;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class AgentHealthIndicatorTest {

    private static final String SECRET = "test-only-internal-jwt-secret-32-bytes-minimum";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsBearerCredentialAndRequestId() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/health/ready", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        Health health = indicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(authorization.get()).startsWith("Bearer ");
        assertThat(requestId.get()).startsWith("req_");
    }

    @Test
    void authenticationFailureIsDegradedInsteadOfCoreReadinessFailure() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/health/ready", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();

        Health health = indicator().health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsEntry("reason", "HTTP_401");
    }

    private AgentHealthIndicator indicator() {
        AgentServiceTokenProvider provider = new AgentServiceTokenProvider(SECRET, Duration.ofSeconds(30));
        return new AgentHealthIndicator(
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(1), provider);
    }
}
