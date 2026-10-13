package ai.forge.server.document.infrastructure.vector;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class QdrantVectorIndexClientTest {

    @Test
    void remoteErrorBodyCannotEnterTheStableFailureMessage() {
        String secret = "qdrant-echoed-secret";

        String message = QdrantVectorIndexClient.remoteFailureMessage("search", 500);

        assertThat(message).isEqualTo("search returned HTTP_500").doesNotContain(secret);
    }

    @Test
    void sendsConfiguredApiKeyOnRequests() throws Exception {
        AtomicReference<String> capturedApiKey = new AtomicReference<>();
        HttpServer server = stubCollectionsEndpoint(capturedApiKey);
        server.start();
        try {
            QdrantVectorIndexClient client = new QdrantVectorIndexClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2),
                    "shared-qdrant-key");

            client.ensureCollection("forge_ai_document", 64);

            assertThat(capturedApiKey.get()).isEqualTo("shared-qdrant-key");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void omitsApiKeyHeaderWhenNotConfigured() throws Exception {
        AtomicReference<String> capturedApiKey = new AtomicReference<>("unset");
        HttpServer server = stubCollectionsEndpoint(capturedApiKey);
        server.start();
        try {
            QdrantVectorIndexClient client = new QdrantVectorIndexClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2),
                    "");

            client.ensureCollection("forge_ai_document", 64);

            assertThat(capturedApiKey.get()).isNull();
        } finally {
            server.stop(0);
        }
    }

    private HttpServer stubCollectionsEndpoint(AtomicReference<String> capturedApiKey) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections", exchange -> {
            capturedApiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        return server;
    }
}
