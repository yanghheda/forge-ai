package ai.forge.server.document.infrastructure.vector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QdrantVectorIndexClientTest {

    @Test
    void remoteErrorBodyCannotEnterTheStableFailureMessage() {
        String secret = "qdrant-echoed-secret";

        String message = QdrantVectorIndexClient.remoteFailureMessage("search", 500);

        assertThat(message).isEqualTo("search returned HTTP_500").doesNotContain(secret);
    }
}
