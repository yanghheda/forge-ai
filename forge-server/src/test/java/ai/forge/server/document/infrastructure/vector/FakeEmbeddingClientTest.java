package ai.forge.server.document.infrastructure.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class FakeEmbeddingClientTest {

    @Test
    void embeddingIsDeterministicAndUnitLength() {
        FakeEmbeddingClient client = new FakeEmbeddingClient(64);

        float[] first = client.embed("release pipeline retry policy");
        float[] second = client.embed("release pipeline retry policy");

        assertThat(first).containsExactly(second);
        double squaredSum = 0.0;
        for (float value : first) {
            squaredSum += (double) value * value;
        }
        assertThat(squaredSum).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void sharedTokensAreCloserThanDisjointTexts() {
        FakeEmbeddingClient client = new FakeEmbeddingClient(64);

        float[] base = client.embed("payment retry policy");
        float[] similar = client.embed("payment retry policy timeout");
        float[] disjoint = client.embed("zebra quantum xylophone");

        assertThat(cosine(base, similar)).isGreaterThan(cosine(base, disjoint));
    }

    @Test
    void chineseTextMatchesOverSharedCharacters() {
        FakeEmbeddingClient client = new FakeEmbeddingClient(64);

        float[] query = client.embed("发布流程");
        float[] related = client.embed("文档发布流程说明");
        float[] unrelated = client.embed("登录密码重置");

        assertThat(cosine(query, related)).isGreaterThan(cosine(query, unrelated));
    }

    @Test
    void invalidDimensionIsRejected() {
        try {
            new FakeEmbeddingClient(0);
            throw new AssertionError("dimension must be validated");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("positive");
        }
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
        }
        return dot;
    }
}
