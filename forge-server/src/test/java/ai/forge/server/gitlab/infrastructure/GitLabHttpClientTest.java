package ai.forge.server.gitlab.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.gitlab.application.GitLabRemoteException;
import ai.forge.server.gitlab.application.PipelineContext;
import ai.forge.server.gitlab.application.RepositoryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitLabHttpClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void testsIdentityAndReadsNormalizedRepositoryWithoutReturningToken() {
        server.createContext("/api/v4/user", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN")).isEqualTo("glpat-sensitive");
            respond(exchange, 200, "{\"id\":42,\"username\":\"forge-admin\"}");
        });
        server.createContext("/api/v4/projects/123", exchange -> respond(exchange, 200,
                "{\"id\":123,\"path_with_namespace\":\"team/repo\","
                        + "\"http_url_to_repo\":\"https://gitlab.example/team/repo.git\","
                        + "\"default_branch\":\"main\"}"));
        GitLabHttpClient client = client(Duration.ofSeconds(1));

        assertThat(client.test(baseUrl, "glpat-sensitive").username()).isEqualTo("forge-admin");
        RepositoryDto repository = client.getRepository(baseUrl, "glpat-sensitive", "123");
        assertThat(repository).isEqualTo(new RepositoryDto(
                "123", "team/repo", "https://gitlab.example/team/repo.git", "main"));
        assertThat(repository.toString()).doesNotContain("glpat-sensitive");
    }

    @Test
    void mapsAuthorizationTimeoutAndRedirectWithoutLeakingRemoteBody() {
        server.createContext("/api/v4/user", exchange -> respond(exchange, 401, "glpat-sensitive remote body"));
        assertRemoteCode(() -> client(Duration.ofSeconds(1)).test(baseUrl, "glpat-sensitive"),
                "GITLAB_UNAUTHORIZED");

        server.removeContext("/api/v4/user");
        server.createContext("/api/v4/user", exchange -> respond(exchange, 403, "forbidden"));
        assertRemoteCode(() -> client(Duration.ofSeconds(1)).test(baseUrl, "glpat-sensitive"),
                "GITLAB_FORBIDDEN");

        server.removeContext("/api/v4/user");
        server.createContext("/api/v4/user", exchange -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{}");
        });
        assertRemoteCode(() -> client(Duration.ofMillis(30)).test(baseUrl, "glpat-sensitive"),
                "GITLAB_TIMEOUT");

        server.removeContext("/api/v4/user");
        server.createContext("/api/v4/user", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data");
            respond(exchange, 302, "redirect");
        });
        assertRemoteCode(() -> client(Duration.ofSeconds(1)).test(baseUrl, "glpat-sensitive"),
                "GITLAB_INVALID_RESPONSE");
    }

    @Test
    void triggersPipelineAndReadsBoundedJobTrace() {
        server.createContext("/api/v4/projects/123/pipeline", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN")).isEqualTo("glpat-sensitive");
            respond(exchange, 201, "{\"id\":81,\"ref\":\"main\",\"sha\":\"abc\","
                    + "\"status\":\"pending\",\"web_url\":\"https://gitlab.example/pipelines/81\","
                    + "\"created_at\":\"2026-09-06T10:00:00Z\"}");
        });
        server.createContext("/api/v4/projects/123/jobs/99/trace", exchange ->
                respond(exchange, 200, "0123456789"));
        PipelineContext context = new PipelineContext(7L, 9L, 41L, 21L, baseUrl, "123", "glpat-sensitive");
        GitLabHttpClient client = client(Duration.ofSeconds(1));

        assertThat(client.triggerPipeline(context, "main").remotePipelineId()).isEqualTo(81L);
        assertThat(client.getJobLog(context, 81L, 99L, 6))
                .asString()
                .isEqualTo("012345");
    }

    private GitLabHttpClient client(Duration timeout) {
        GitLabUrlPolicy policy = new GitLabUrlPolicy(true, List.of(), java.net.InetAddress::getAllByName);
        return new GitLabHttpClient(
                policy,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                new ObjectMapper(),
                timeout,
                64 * 1024);
    }

    private static void assertRemoteCode(ThrowingCall call, String expectedCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(GitLabRemoteException.class)
                .extracting(exception -> ((GitLabRemoteException) exception).code())
                .isEqualTo(expectedCode);
        assertThatThrownBy(call::run).hasMessageNotContaining("glpat-sensitive");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) {
        try (exchange) {
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (java.io.IOException ignored) {
            // 客户端超时关闭连接是该测试的预期路径。
        }
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
