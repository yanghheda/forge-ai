package ai.forge.server.document.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.document.domain.RagUnavailableException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class RagSearchService {

    /* 默认返回片段数；调用方未指定 Top-K 时使用。 */
    private static final int DEFAULT_TOP_K = 8;

    /* 单次检索片段数上限；防止 Agent 用超大 Top-K 绕过 Token 预算。 */
    private static final int MAX_TOP_K = 30;

    /* 服务端授权判断；检索范围由它派生，不信任调用方自报。 */
    private final PermissionEvaluator permissions;

    /* 查询向量化端口。 */
    private final EmbeddingClient embeddingClient;

    /* 向量索引端口；过滤器在服务端构造。 */
    private final VectorIndexClient vectorIndexClient;

    public RagSearchService(
            PermissionEvaluator permissions,
            EmbeddingClient embeddingClient,
            VectorIndexClient vectorIndexClient) {
        this.permissions = permissions;
        this.embeddingClient = embeddingClient;
        this.vectorIndexClient = vectorIndexClient;
    }

    /* 在强制范围内检索；调用方只能提供 query、documentType 与 topK，不能触碰范围过滤。 */
    public List<SearchChunk> search(long userId, long organizationId, String query,
            String documentType, Integer topK) {
        String normalizedQuery = normalize(query);
        int limit = normalizeTopK(topK);
        permissions.requireOrganization(userId, organizationId, "document.read");
        float[] queryVector = embeddingClient.embed(normalizedQuery);
        VectorIndexClient.SearchFilter filter =
                new VectorIndexClient.SearchFilter(organizationId, documentType);
        String collectionName = "documents_" + embeddingClient.modelVersion();
        List<VectorIndexClient.ScoredChunk> hits;
        try {
            hits = vectorIndexClient.search(collectionName, queryVector, filter, limit);
        } catch (VectorIndexUnavailableException exception) {
            throw new RagUnavailableException("vector index unavailable", exception);
        }
        return hits.stream().map(RagSearchService::toChunk).toList();
    }

    private static String normalize(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        String normalized = query.strip();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("query must not exceed 500 characters");
        }
        return normalized;
    }

    private static int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK must be between 1 and " + MAX_TOP_K);
        }
        return topK;
    }

    private static SearchChunk toChunk(VectorIndexClient.ScoredChunk hit) {
        IndexedChunkPayload payload = hit.payload();
        return new SearchChunk(payload.documentId(), payload.versionId(), payload.chunkIndex(),
                payload.title(), payload.documentType(), payload.workItemId(), hit.score(),
                payload.text());
    }
}
