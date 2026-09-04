package ai.forge.server.document.infrastructure.vector;

import ai.forge.server.document.application.EmbeddingClient;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 特征哈希 Fake 向量化；无外部依赖且确定性输出，仅用于本地开发与测试。 */
@Component
public class FakeEmbeddingClient implements EmbeddingClient {

    /* 向量维度；必须与 Qdrant Collection 创建参数一致且不得在运行中变化。 */
    private final int dimension;

    public FakeEmbeddingClient(@Value("${forge.rag.embedding.dimension:64}") int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("embedding dimension must be positive");
        }
        this.dimension = dimension;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String modelVersion() {
        return "fake-hash-v1";
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        for (String token : tokenize(text)) {
            vector[Math.floorMod(token.hashCode(), dimension)] += 1.0f;
        }
        normalize(vector);
        return vector;
    }

    /* 小写化并按非字母数字边界切词；CJK 字符额外产出单字特征以支持子词匹配。 */
    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                current.append(Character.toLowerCase(character));
                if (character >= 0x2E80) {
                    tokens.add(String.valueOf(Character.toLowerCase(character)));
                }
            } else if (current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /* 归一化为单位向量，使余弦相似度等价于内积；空文本保持零向量。 */
    private static void normalize(float[] vector) {
        double squaredSum = 0.0;
        for (float value : vector) {
            squaredSum += (double) value * value;
        }
        if (squaredSum == 0.0) {
            return;
        }
        double norm = Math.sqrt(squaredSum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
