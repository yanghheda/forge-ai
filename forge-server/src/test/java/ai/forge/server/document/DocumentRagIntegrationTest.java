package ai.forge.server.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.forge.server.auth.CsrfTestClient;
import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.document.application.DocumentIndexingService;
import ai.forge.server.document.application.EmbeddingClient;
import ai.forge.server.document.application.RagSearchService;
import ai.forge.server.document.application.SearchChunk;
import ai.forge.server.document.application.VectorIndexClient;
import ai.forge.server.document.domain.RagUnavailableException;
import ai.forge.server.document.infrastructure.DocumentIndexingWorker;
import ai.forge.server.document.infrastructure.vector.QdrantVectorIndexClient;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class DocumentRagIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 通过真实 HTTP、Session 与 CSRF 链路调用文档 API。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /* 解析 API 响应与构造 ProseMirror 正文。 */
    @Autowired
    private ObjectMapper objectMapper;

    /* 搭建跨工作区测试事实并检查索引任务表状态。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /* 被测索引 Worker；测试手动驱动避免定时器竞态。 */
    @Autowired
    private DocumentIndexingWorker indexingWorker;

    /* 事务边界服务；用于构造指向故障端点的 Worker。 */
    @Autowired
    private DocumentIndexingService indexingService;

    /* 检索门面；过滤器由服务端构造。 */
    @Autowired
    private RagSearchService ragSearchService;

    /* 授权计算器；用于构造降级场景的门面。 */
    @Autowired
    private PermissionEvaluator permissions;

    /* 向量化端口；用于派生集合名。 */
    @Autowired
    private EmbeddingClient embeddingClient;

    /* 真实 Qdrant 客户端；用于断言派生索引内容。 */
    @Autowired
    private VectorIndexClient vectorIndexClient;

    private String ownerCookie;
    private long ownerId;
    private long workspaceId;
    private long projectId;
    private long requirementId;

    @BeforeEach
    void initializeWorkspaceAndProject() throws Exception {
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of("document_index_jobs", "outbox_events")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        /* documents 与 document_versions 存在相互外键，先解除指针再按依赖顺序清空。 */
        jdbcTemplate.update("UPDATE documents SET current_version_id = NULL");
        jdbcTemplate.update("DELETE FROM document_versions");
        jdbcTemplate.update("DELETE FROM documents");
        for (String table : List.of(
                "comments", "work_item_relations", "work_item_labels", "project_policies", "work_item_events",
                "review_records", "requirement_details", "work_items", "project_item_sequences",
                "project_members", "projects", "audit_logs", "member_roles",
                "workspace_members", "workspaces", "organizations", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        ResponseEntity<String> initialized = csrf().post(
                "/api/v1/setup/initialize",
                Map.of(
                        "adminEmail", "owner@example.com",
                        "adminDisplayName", "Forge Owner",
                        "password", "correct-horse-42",
                        "organizationName", "Forge",
                        "organizationSlug", "forge",
                        "workspaceName", "Engineering",
                        "workspaceSlug", "engineering"),
                null,
                String.class);
        assertThat(initialized.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ownerCookie = login("owner@example.com");
        ownerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email = 'owner@example.com'", Long.class);
        workspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE slug = 'engineering'", Long.class);
        ResponseEntity<String> project = csrf().post(
                "/api/v1/projects",
                Map.of("workspaceId", workspaceId, "key", "FORGE", "name", "ForgeAI", "description", "会话 20"),
                ownerCookie,
                String.class);
        assertThat(project.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        projectId = objectMapper.readTree(project.getBody()).get("id").asLong();
        ResponseEntity<String> requirement = csrf().post(
                "/api/v1/work-items",
                Map.of(
                        "workspaceId", workspaceId,
                        "projectId", projectId,
                        "type", "REQUIREMENT",
                        "title", "RAG 基线",
                        "description", "document indexing baseline",
                        "priority", "MEDIUM"),
                ownerCookie,
                String.class);
        assertThat(requirement.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        requirementId = objectMapper.readTree(requirement.getBody()).get("id").asLong();
    }

    @Test
    void publishedDocumentIsIndexedAndSearchableWithVersionReference() throws Exception {
        Published published = publishDocument("索引基线", "release pipeline retry policy");
        assertThat(indexingWorker.dispatchOutbox()).isOne();
        assertThat(indexingWorker.processNextJob()).isTrue();
        assertThat(indexingWorker.processNextJob()).isFalse();

        Map<String, Object> job = jdbcTemplate.queryForMap(
                "SELECT status, chunk_count, indexed_at FROM document_index_jobs WHERE document_id = ? AND version_id = ?",
                published.documentId(), published.versionId());
        assertThat(job.get("status")).isEqualTo("SUCCEEDED");
        assertThat(((Number) job.get("chunk_count")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(job.get("indexed_at")).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT processed_at IS NOT NULL FROM outbox_events WHERE aggregate_id = ?",
                Boolean.class, published.documentId())).isTrue();

        List<SearchChunk> hits = ragSearchService.search(
                ownerId, workspaceId, "release pipeline retry", null, null);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).documentId()).isEqualTo(published.documentId());
        assertThat(hits.get(0).versionId()).isEqualTo(published.versionId());
        assertThat(hits.get(0).text()).contains("release pipeline");
        assertThat(hits.get(0).title()).isEqualTo("索引基线");
        assertThat(hits.get(0).documentType()).isEqualTo("PRD");

        assertThat(ragSearchService.search(
                ownerId, workspaceId, "release pipeline retry", "UX_SPEC", null)).isEmpty();
    }

    @Test
    void duplicateConsumptionDoesNotDuplicateChunks() throws Exception {
        Published published = publishDocument("幂等消费", "idempotent consumption baseline");
        assertThat(indexingWorker.dispatchOutbox()).isOne();
        assertThat(indexingWorker.processNextJob()).isTrue();
        long indexedCount = vectorIndexClient.countVersionChunks(
                collectionName(), published.documentId(), published.versionId());
        assertThat(indexedCount).isGreaterThanOrEqualTo(1L);

        /* 模拟事件重复投递：手工再插入一条同载荷 Outbox 事件。 */
        jdbcTemplate.update(
                "INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, created_at, processed_at) "
                        + "VALUES ('DOCUMENT', ?, 'DOCUMENT_VERSION_PUBLISHED', JSON_OBJECT("
                        + "'workspaceId', ?, 'projectId', ?, 'documentId', ?, 'versionId', ?), "
                        + "UTC_TIMESTAMP(6), NULL)",
                published.documentId(), workspaceId, projectId, published.documentId(), published.versionId());
        assertThat(indexingWorker.dispatchOutbox()).isOne();
        assertThat(indexingWorker.processNextJob()).isFalse();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_index_jobs WHERE document_id = ? AND version_id = ?",
                Long.class, published.documentId(), published.versionId())).isEqualTo(1L);
        assertThat(vectorIndexClient.countVersionChunks(
                collectionName(), published.documentId(), published.versionId())).isEqualTo(indexedCount);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE processed_at IS NULL", Long.class)).isZero();
    }

    @Test
    void versionSwitchRemovesStaleVersionChunks() throws Exception {
        Published first = publishDocument("版本切换", "alpha beta gamma first version");
        assertThat(indexingWorker.dispatchOutbox()).isOne();
        assertThat(indexingWorker.processNextJob()).isTrue();
        assertThat(vectorIndexClient.countVersionChunks(
                collectionName(), first.documentId(), first.versionId())).isEqualTo(1L);

        JsonNode secondSave = saveVersion(first.documentId(), "alpha beta gamma second version delta epsilon");
        long secondVersionId = secondSave.get("currentVersionId").asLong();
        publish(first.documentId(), secondVersionId, secondSave.get("version").asLong());
        assertThat(indexingWorker.dispatchOutbox()).isOne();
        assertThat(indexingWorker.processNextJob()).isTrue();

        assertThat(vectorIndexClient.countVersionChunks(
                collectionName(), first.documentId(), first.versionId())).isZero();
        assertThat(vectorIndexClient.countVersionChunks(
                collectionName(), first.documentId(), secondVersionId)).isEqualTo(1L);
        List<SearchChunk> hits = ragSearchService.search(
                ownerId, workspaceId, "delta epsilon", null, null);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).versionId()).isEqualTo(secondVersionId);
    }

    @Test
    void crossWorkspaceQueriesAreBlockedByForcedFilter() throws Exception {
        Published mine = publishDocument("本区文档", "quantum falcon workspace secret");
        assertThat(indexingWorker.dispatchOutbox()).isOne();
        assertThat(indexingWorker.processNextJob()).isTrue();

        /* 直接构造第二个工作区的事实与发布事件，绕过 API 只为把数据写进同一索引。 */
        jdbcTemplate.update(
                "INSERT INTO workspaces (organization_id, name, slug, status, settings_json, created_at, updated_at, version) "
                        + "SELECT organization_id, 'Other', 'other', 'ACTIVE', JSON_OBJECT(), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0 "
                        + "FROM workspaces WHERE id = ?",
                workspaceId);
        long otherWorkspaceId = jdbcTemplate.queryForObject(
                "SELECT id FROM workspaces WHERE slug = 'other'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO projects (workspace_id, `key`, name, description, status, created_by, created_at, updated_at, version) "
                        + "VALUES (?, 'OTHER', 'Other', '', 'ACTIVE', ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
                otherWorkspaceId, ownerId);
        long otherProjectId = jdbcTemplate.queryForObject(
                "SELECT id FROM projects WHERE workspace_id = ? AND `key` = 'OTHER'",
                Long.class, otherWorkspaceId);
        jdbcTemplate.update(
                "INSERT INTO documents (workspace_id, project_id, work_item_id, type, title, status, visibility, current_version_id, created_by, created_at, updated_at, deleted_at, version) "
                        + "VALUES (?, ?, NULL, 'PRD', '他区文档', 'PUBLISHED', 'PROJECT', NULL, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, 0)",
                otherWorkspaceId, otherProjectId, ownerId);
        long otherDocumentId = jdbcTemplate.queryForObject(
                "SELECT id FROM documents WHERE workspace_id = ? AND title = '他区文档'",
                Long.class, otherWorkspaceId);
        jdbcTemplate.update(
                "INSERT INTO document_versions (workspace_id, document_id, version_no, content_format, content, content_hash, plain_text, summary, created_by, created_at) "
                        + "VALUES (?, ?, 1, 'PROSEMIRROR_JSON', CAST('{}' AS JSON), 'other-hash', 'quantum falcon workspace secret', NULL, ?, UTC_TIMESTAMP(6))",
                otherWorkspaceId, otherDocumentId, ownerId);
        long otherVersionId = jdbcTemplate.queryForObject(
                "SELECT id FROM document_versions WHERE document_id = ?", Long.class, otherDocumentId);
        jdbcTemplate.update(
                "UPDATE documents SET current_version_id = ? WHERE id = ?", otherVersionId, otherDocumentId);
        jdbcTemplate.update(
                "INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, created_at, processed_at) "
                        + "VALUES ('DOCUMENT', ?, 'DOCUMENT_VERSION_PUBLISHED', JSON_OBJECT("
                        + "'workspaceId', ?, 'projectId', ?, 'documentId', ?, 'versionId', ?), UTC_TIMESTAMP(6), NULL)",
                otherDocumentId, otherWorkspaceId, otherProjectId, otherDocumentId, otherVersionId);

        assertThat(indexingWorker.dispatchOutbox()).isOne();
        assertThat(indexingWorker.processNextJob()).isTrue();

        /* 他区切片确实已写入同一 Collection；隔离只能来自强制过滤器。 */
        assertThat(vectorIndexClient.countVersionChunks(
                collectionName(), otherDocumentId, otherVersionId)).isEqualTo(1L);

        List<SearchChunk> hits = ragSearchService.search(
                ownerId, workspaceId, "quantum falcon workspace secret", null, null);
        assertThat(hits).isNotEmpty();
        assertThat(hits).allSatisfy(hit -> assertThat(hit.documentId()).isEqualTo(mine.documentId()));

        /* Owner 不属于第二个工作区，即使显式指定他区范围也拿不到任何结果。 */
        assertThat(ragSearchService.search(
                ownerId, otherWorkspaceId, "quantum falcon workspace secret", null, null)).isEmpty();
    }

    @Test
    void failedIndexingRetriesWithBackoffAndEventuallyDiesWithoutBlockingDocuments() throws Exception {
        Published published = publishDocument("故障重试", "retry policy backoff");
        assertThat(indexingWorker.dispatchOutbox()).isOne();

        QdrantVectorIndexClient deadClient =
                new QdrantVectorIndexClient("http://127.0.0.1:1", Duration.ofMillis(500));
        DocumentIndexingWorker brokenWorker = new DocumentIndexingWorker(
                indexingService, embeddingClient, deadClient, 3, Duration.ofSeconds(5), Duration.ofMinutes(5));

        assertThat(brokenWorker.processNextJob()).isTrue();
        Map<String, Object> job = jdbcTemplate.queryForMap(
                "SELECT status, attempts, error_code, next_attempt_at FROM document_index_jobs WHERE document_id = ?",
                published.documentId());
        assertThat(job.get("status")).isEqualTo("FAILED");
        assertThat(((Number) job.get("attempts")).intValue()).isOne();
        assertThat(job.get("error_code")).isEqualTo("QDRANT_UNAVAILABLE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_attempt_at > UTC_TIMESTAMP(6) FROM document_index_jobs WHERE document_id = ?",
                Boolean.class, published.documentId())).isTrue();

        /* 退避未到期时不能重复领取。 */
        assertThat(brokenWorker.processNextJob()).isFalse();

        for (int attempt = 2; attempt <= 3; attempt++) {
            jdbcTemplate.update(
                    "UPDATE document_index_jobs SET next_attempt_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND "
                            + "WHERE document_id = ?",
                    published.documentId());
            assertThat(brokenWorker.processNextJob()).isTrue();
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM document_index_jobs WHERE document_id = ?", String.class,
                    published.documentId());
            assertThat(status).isEqualTo(attempt == 3 ? "DEAD" : "FAILED");
        }

        /* 超过上限后真实 Worker 也不应自动领取；事件仍保持已消费状态。 */
        assertThat(indexingWorker.processNextJob()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE processed_at IS NULL", Long.class)).isZero();

        /* 索引故障不影响文档事实读取。 */
        ResponseEntity<String> readable = get(
                "/api/v1/documents/" + published.documentId() + "?workspaceId=" + workspaceId
                        + "&projectId=" + projectId, ownerCookie);
        assertThat(readable.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void qdrantOutageDegradesSearchToStableError() throws Exception {
        publishDocument("降级验证", "degradation probe content");
        assertThat(indexingWorker.dispatchOutbox()).isOne();
        assertThat(indexingWorker.processNextJob()).isTrue();

        QdrantVectorIndexClient deadClient =
                new QdrantVectorIndexClient("http://127.0.0.1:1", Duration.ofMillis(500));
        RagSearchService degraded = new RagSearchService(permissions, embeddingClient, deadClient);

        assertThatThrownBy(() -> degraded.search(ownerId, workspaceId, "degradation probe", null, null))
                .isInstanceOf(RagUnavailableException.class);
    }

    @Test
    void searchValidatesQueryAndTopKBounds() {
        assertThatThrownBy(() -> ragSearchService.search(ownerId, workspaceId, "  ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ragSearchService.search(ownerId, workspaceId, "valid", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ragSearchService.search(ownerId, workspaceId, "valid", null, 31))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* ---------- 测试辅助：文档发布链路与断言工具 ---------- */

    private record Published(
            /* 已发布文档标识。 */ long documentId,
            /* 已发布版本标识。 */ long versionId) {}

    private Published publishDocument(String title, String text) throws Exception {
        ResponseEntity<String> created = csrf().post(
                "/api/v1/documents",
                Map.of(
                        "workspaceId", workspaceId,
                        "projectId", projectId,
                        "workItemId", requirementId,
                        "type", "PRD",
                        "title", title),
                ownerCookie,
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long documentId = objectMapper.readTree(created.getBody()).get("id").asLong();
        JsonNode saved = saveVersion(documentId, text);
        long versionId = saved.get("currentVersionId").asLong();
        publish(documentId, versionId, saved.get("version").asLong());
        return new Published(documentId, versionId);
    }

    private JsonNode saveVersion(long documentId, String text) throws Exception {
        JsonNode content = objectMapper.readTree(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"" + text + "\"}]}]}");
        /* expectedVersion 从数据库读取当前值，同一文档多次保存不会触发乐观锁冲突。 */
        long expectedVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM documents WHERE id = ?", Long.class, documentId);
        ResponseEntity<String> response = csrf().post(
                "/api/v1/documents/" + documentId + "/versions?workspaceId=" + workspaceId
                        + "&projectId=" + projectId,
                Map.of("expectedVersion", expectedVersion, "content", content),
                ownerCookie,
                String.class);
        /* save 返回更新后的文档（含 currentVersionId），既有契约为 200 OK。 */
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private void publish(long documentId, long versionId, long expectedVersion) {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/documents/" + documentId + "/publish?workspaceId=" + workspaceId
                        + "&projectId=" + projectId,
                Map.of("versionId", versionId, "expectedVersion", expectedVersion),
                ownerCookie,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String collectionName() {
        return "documents_" + embeddingClient.modelVersion();
    }

    private String login(String email) {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/auth/login",
                Map.of("email", email, "password", "correct-horse-42"),
                null,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private ResponseEntity<String> get(String path, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        return csrf().unwrapSuccess(
                restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class));
    }

    private CsrfTestClient csrf() {
        return new CsrfTestClient(restTemplate, objectMapper);
    }
}
