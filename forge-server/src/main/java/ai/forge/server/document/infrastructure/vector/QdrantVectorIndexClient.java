package ai.forge.server.document.infrastructure.vector;

import ai.forge.server.document.application.IndexedChunkPayload;
import ai.forge.server.document.application.VectorIndexClient;
import ai.forge.server.document.application.VectorIndexUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test-unit")
public class QdrantVectorIndexClient implements VectorIndexClient {

    /* 稳定错误码：连接失败、超时或非预期响应。 */
    private static final String ERROR_CODE = "QDRANT_UNAVAILABLE";

    /* Qdrant REST 根地址；来自部署配置，不承载业务事实。 */
    private final URI baseUrl;

    /* 单次请求超时；防止外部故障长期占用索引线程。 */
    private final Duration requestTimeout;

    /* 复用连接的 JDK HTTP 客户端。 */
    private final HttpClient httpClient;

    /* 构造与解析 Qdrant JSON。 */
    private final ObjectMapper objectMapper;

    public QdrantVectorIndexClient(
            @Value("${forge.infrastructure.qdrant.base-url}") String baseUrl,
            @Value("${forge.rag.qdrant.request-timeout:10s}") Duration requestTimeout) {
        this.baseUrl = URI.create(baseUrl);
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void ensureCollection(String collectionName, int dimension) {
        QdrantResponse existing = get("/collections/" + collectionName);
        if (existing.status() == 200) {
            return;
        }
        if (existing.status() != 404) {
            throw unavailable("collection lookup returned HTTP_" + existing.status());
        }
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode vectors = body.putObject("vectors");
        vectors.put("size", dimension);
        vectors.put("distance", "Cosine");
        QdrantResponse created = put("/collections/" + collectionName, body);
        if (created.status() != 200) {
            /* 并发创建冲突时允许最终以集合已存在判定成功。 */
            QdrantResponse rechecked = get("/collections/" + collectionName);
            if (rechecked.status() != 200) {
                throw unavailable("collection create returned HTTP_" + created.status());
            }
        }
    }

    @Override
    public void upsertChunks(String collectionName, List<ChunkPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode jsonPoints = body.putArray("points");
        for (ChunkPoint point : points) {
            ObjectNode jsonPoint = jsonPoints.addObject();
            jsonPoint.put("id", stablePointId(point.payload()).toString());
            ArrayNode vector = jsonPoint.putArray("vector");
            for (float value : point.vector()) {
                vector.add(value);
            }
            jsonPoint.set("payload", payloadJson(point.payload()));
        }
        /* upsert 语义在 Qdrant REST 中是 PUT /points；POST /points 是按 ids/filter 改 payload。 */
        QdrantResponse response = put("/collections/" + collectionName + "/points?wait=true", body);
        requireOk(response, "upsert");
    }

    @Override
    public void deleteStaleVersions(String collectionName, long documentId, long keepVersionId) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode filter = body.putObject("filter");
        ArrayNode must = filter.putArray("must");
        matchLong(must.addObject(), "document_id", documentId);
        ArrayNode mustNot = filter.putArray("must_not");
        matchLong(mustNot.addObject(), "version_id", keepVersionId);
        QdrantResponse response =
                post("/collections/" + collectionName + "/points/delete?wait=true", body);
        requireOk(response, "delete");
    }

    @Override
    public long countVersionChunks(String collectionName, long documentId, long versionId) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode filter = body.putObject("filter");
        ArrayNode must = filter.putArray("must");
        matchLong(must.addObject(), "document_id", documentId);
        matchLong(must.addObject(), "version_id", versionId);
        body.put("exact", true);
        QdrantResponse response = post("/collections/" + collectionName + "/points/count", body);
        requireOk(response, "count");
        return response.json().path("result").path("count").asLong(-1);
    }

    @Override
    public List<ScoredChunk> search(
            String collectionName, float[] queryVector, SearchFilter filter, int limit) {
        if (filter.projectIds().isEmpty()) {
            return List.of();
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode vector = body.putArray("vector");
        for (float value : queryVector) {
            vector.add(value);
        }
        ObjectNode jsonFilter = body.putObject("filter");
        ArrayNode must = jsonFilter.putArray("must");
        matchLong(must.addObject(), "workspace_id", filter.workspaceId());
        /* project_id IN allowed 用 MatchAny 表达；字段名和取值都来自服务端授权结果。 */
        ObjectNode projectMatch = must.addObject();
        projectMatch.put("key", "project_id");
        ArrayNode any = projectMatch.putObject("match").putArray("any");
        filter.projectIds().forEach(any::add);
        if (filter.documentType() != null) {
            matchString(must.addObject(), "document_type", filter.documentType());
        }
        body.put("limit", limit);
        body.put("with_payload", true);
        QdrantResponse response = post("/collections/" + collectionName + "/points/search", body);
        if (response.status() == 404) {
            /* 集合尚不存在说明没有任何已索引文档，按空结果处理而不是故障。 */
            return List.of();
        }
        requireOk(response, "search");
        List<ScoredChunk> results = new ArrayList<>();
        for (JsonNode hit : response.json().path("result")) {
            JsonNode payload = hit.path("payload");
            if (payload.isMissingNode() || !payload.has("document_id")) {
                continue;
            }
            results.add(new ScoredChunk(hit.path("score").floatValue(), payload(payload)));
        }
        return results;
    }

    /* 切片稳定 ID：由 documentId、versionId 与 chunkIndex 派生的 UUID，保证重复投递覆盖写入。 */
    private UUID stablePointId(IndexedChunkPayload payload) {
        String logicalId = "doc:" + payload.documentId() + ":ver:" + payload.versionId()
                + ":chunk:" + payload.chunkIndex();
        return UUID.nameUUIDFromBytes(logicalId.getBytes(StandardCharsets.UTF_8));
    }

    private ObjectNode payloadJson(IndexedChunkPayload payload) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("workspace_id", payload.workspaceId());
        json.put("project_id", payload.projectId());
        if (payload.workItemId() == null) {
            json.putNull("work_item_id");
        } else {
            json.put("work_item_id", payload.workItemId());
        }
        json.put("document_id", payload.documentId());
        json.put("version_id", payload.versionId());
        json.put("document_type", payload.documentType());
        json.put("visibility", payload.visibility());
        json.put("content_hash", payload.contentHash());
        json.put("chunk_index", payload.chunkIndex());
        json.put("title", payload.title());
        json.put("text", payload.text());
        return json;
    }

    private IndexedChunkPayload payload(JsonNode json) {
        return new IndexedChunkPayload(
                json.path("workspace_id").asLong(),
                json.path("project_id").asLong(),
                json.path("work_item_id").isNumber() ? json.path("work_item_id").asLong() : null,
                json.path("document_id").asLong(),
                json.path("version_id").asLong(),
                json.path("document_type").asText(),
                json.path("visibility").asText(),
                json.path("content_hash").asText(),
                json.path("chunk_index").asInt(),
                json.path("title").asText(),
                json.path("text").asText());
    }

    private static void matchLong(ObjectNode condition, String key, long value) {
        condition.put("key", key);
        condition.putObject("match").put("value", value);
    }

    private static void matchString(ObjectNode condition, String key, String value) {
        condition.put("key", key);
        condition.putObject("match").put("value", value);
    }

    private void requireOk(QdrantResponse response, String operation) {
        if (response.status() < 200 || response.status() >= 300) {
            throw unavailable(operation + " returned HTTP_" + response.status()
                    + " body=" + response.json());
        }
    }

    private QdrantResponse get(String path) {
        return exchange(path, null, "GET");
    }

    private QdrantResponse put(String path, ObjectNode body) {
        return exchange(path, body, "PUT");
    }

    private QdrantResponse post(String path, ObjectNode body) {
        return exchange(path, body, "POST");
    }

    /* 单次 Qdrant 调用的不可变结果：状态码与已解析 JSON。 */
    private record QdrantResponse(
            /* HTTP 状态码；用于区分不可用与集合不存在等语义。 */ int status,
            /* 已解析的响应 JSON；空体解析为空对象。 */ JsonNode json) {}

    private QdrantResponse exchange(String path, ObjectNode body, String method) {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUrl.resolve(path))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json");
        String payload = body == null ? "{}" : stringify(body);
        HttpRequest built = switch (method) {
            case "GET" -> request.GET().build();
            case "PUT" -> request.PUT(HttpRequest.BodyPublishers.ofString(payload)).build();
            default -> request.POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        };
        try {
            HttpResponse<String> response = httpClient.send(built, HttpResponse.BodyHandlers.ofString());
            return new QdrantResponse(response.statusCode(), parseBody(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("request interrupted", exception);
        } catch (IOException exception) {
            throw unavailable("connection failed", exception);
        }
    }

    private JsonNode parseBody(String body) {
        try {
            return body == null || body.isEmpty() ? objectMapper.createObjectNode()
                    : objectMapper.readTree(body);
        } catch (IOException exception) {
            throw unavailable("response parse failed", exception);
        }
    }

    private String stringify(ObjectNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw unavailable("request serialization failed", exception);
        }
    }

    private VectorIndexUnavailableException unavailable(String message) {
        return new VectorIndexUnavailableException(ERROR_CODE, message);
    }

    private VectorIndexUnavailableException unavailable(String message, Throwable cause) {
        return new VectorIndexUnavailableException(ERROR_CODE, message, cause);
    }
}
