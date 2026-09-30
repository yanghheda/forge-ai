package ai.forge.server.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.auth.CsrfTestClient;
import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import ai.forge.server.notification.application.NotificationService;
import ai.forge.server.notification.application.NotificationView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

/*
 * 用户级通知 SSE 事件流集成测试：覆盖游标补发、鉴权隔离与真实 HTTP 流式读取。
 * 不使用测试事务，保证 JdbcTemplate 造数对服务端 SSE 请求可见。
 */
class NotificationStreamIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 通过真实 HTTP、Session 链路验证通知事件流端点。 */
    @Autowired private TestRestTemplate restTemplate;

    /* 解析 CSRF 与初始化响应信封。 */
    @Autowired private ObjectMapper objectMapper;

    /* 仅用于搭建身份事实与断言持久化结果。 */
    @Autowired private JdbcTemplate jdbcTemplate;

    /* 随机端口测试服务的本地端口，用于构造 SSE 绝对地址。 */
    @LocalServerPort private int port;

    /* 清理跨测试共享的登录限流状态，避免用例数量增长后相互污染。 */
    @Autowired private StringRedisTemplate redisTemplate;

    /* 直接验证游标补发查询的过滤与排序语义。 */
    @Autowired private NotificationService notificationService;

    /* 登录成员 Cookie，用于建立与验证本人事件流。 */
    private String ownerCookie;
    /* 初始化后默认公司标识，作为通知公司范围。 */
    private long organizationId;
    /* 初始化创建的公司拥有者用户标识，作为事件流接收者。 */
    private long ownerUserId;

    @BeforeEach
    void initializeIdentity() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
                "notifications", "outbox_events", "agent_messages", "agent_conversations", "agent_tool_calls",
                "approvals", "agent_events", "agent_steps", "agent_runs", "comments", "work_item_relations",
                "work_item_labels", "organization_policies", "work_item_events", "review_records",
                "requirement_details", "work_items", "organization_item_sequences", "audit_logs", "member_roles",
                "organization_members", "organizations", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        ResponseEntity<String> initialized = csrf().post(
                "/api/v1/setup/initialize",
                Map.of(
                        "adminEmail", "owner@example.com",
                        "adminDisplayName", "Forge Owner",
                        "password", "correct-horse-42",
                        "organizationName", "Forge",
                        "organizationSlug", "forge"),
                null,
                String.class);
        assertThat(initialized.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ownerCookie = loginOwner();
        organizationId = jdbcTemplate.queryForObject(
                "SELECT default_organization_id FROM instance_settings WHERE id = 1", Long.class);
        ownerUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'owner@example.com'", Long.class);
    }

    @Test
    void eventsAfterReturnsOnlyOwnAscendingNotificationsWithCursorAndLimit() {
        long first = insertNotification("WORK_ITEM_ASSIGNED", "第一条", "REQUIREMENT", "1", ownerUserId);
        long second = insertNotification("REVIEW_REQUESTED", "第二条", "REQUIREMENT", "2", ownerUserId);
        long otherUser = insertUser("stream-other@example.com");
        insertNotification("WORK_ITEM_ASSIGNED", "他人通知", null, null, otherUser);
        long otherOrg = insertOrganization("notify-stream-other", ownerUserId);
        insertNotification("WORK_ITEM_ASSIGNED", "跨公司通知", null, null, ownerUserId, otherOrg);

        List<NotificationView> all = notificationService.eventsAfter(organizationId, ownerUserId, 0L, 100);

        /* 只返回本公司 + 本人的通知；按 id 升序；他用户与他公司通知不出现。 */
        assertThat(all).extracting(NotificationView::title).containsExactly("第一条", "第二条");
        assertThat(all).extracting(NotificationView::id).containsExactly(first, second);

        /* 游标只返回 id 大于 afterId 的通知。 */
        assertThat(notificationService.eventsAfter(organizationId, ownerUserId, first, 100))
                .extracting(NotificationView::id).containsExactly(second);

        /* limit 生效。 */
        assertThat(notificationService.eventsAfter(organizationId, ownerUserId, 0L, 1))
                .extracting(NotificationView::id).containsExactly(first);

        NotificationView view = all.get(0);
        assertThat(view.type()).isEqualTo("WORK_ITEM_ASSIGNED");
        assertThat(view.resourceType()).isEqualTo("REQUIREMENT");
        assertThat(view.resourceId()).isEqualTo("1");
        assertThat(view.readAt()).isNull();
        assertThat(view.createdAt()).isNotNull();
    }

    @Test
    void eventStreamRequiresAuthentication() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/notifications/events", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("UNAUTHENTICATED");
    }

    @Test
    void eventStreamReplaysNewNotificationAsSseFrame() {
        long id = insertNotification("WORK_ITEM_ASSIGNED", "流式通知标题", "DEFECT", "77", ownerUserId);

        String body = readEventStream("/api/v1/notifications/events?afterId=0", ownerCookie, null);

        assertThat(body).contains("id:" + id + "\n", "event:notification", "流式通知标题");
        /* 事件帧带 JSON 数据体，正文由 SSE 的 data: 字段承载。 */
        assertThat(body).contains("\"id\":" + id, "\"title\":\"流式通知标题\"");
    }

    @Test
    void eventStreamResumesAfterLastEventIdWithoutRepeating() {
        long first = insertNotification("WORK_ITEM_ASSIGNED", "已推送", "REQUIREMENT", "1", ownerUserId);
        long second = insertNotification("REVIEW_REQUESTED", "续传通知", "REQUIREMENT", "2", ownerUserId);

        String body = readEventStream("/api/v1/notifications/events", ownerCookie, Long.toString(first));

        assertThat(body).contains("id:" + second + "\n", "event:notification", "续传通知");
        assertThat(body).doesNotContain("id:" + first + "\n", "已推送");
    }

    /*
     * 以流式方式读取 SSE 响应体：带 8 秒截止时间，读到完整事件帧（含 data: 载荷）
     * 立即提前结束，读取循环不会挂死；同时校验响应 Content-Type 为 text/event-stream。
     */
    private String readEventStream(String url, String cookie, String lastEventId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(8));
        AtomicReference<MediaType> contentType = new AtomicReference<>();
        /* SSE 客户端使用短读超时：既界定单次阻塞读，也避免连接关闭时阻塞等待服务端超时。 */
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        RestTemplate client = new RestTemplate(factory);
        String body = client.execute("http://localhost:" + port + url, HttpMethod.GET, request -> {
            request.getHeaders().add(HttpHeaders.COOKIE, cookie);
            request.getHeaders().setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
            if (lastEventId != null) {
                request.getHeaders().set("Last-Event-ID", lastEventId);
            }
        }, response -> {
            contentType.set(response.getHeaders().getContentType());
            StringBuilder collected = new StringBuilder();
            InputStream stream = response.getBody();
            byte[] buffer = new byte[1024];
            while (Instant.now().isBefore(deadline)) {
                int read;
                try {
                    read = stream.read(buffer);
                } catch (SocketTimeoutException idle) {
                    /* 空闲心跳间隔内的读超时不算错误，继续等待直到截止时间。 */
                    continue;
                }
                if (read < 0) {
                    break;
                }
                collected.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                /* 读到空行即代表一个完整事件帧（含 data: 载荷）已读出。 */
                if (collected.indexOf("\n\n") >= 0) {
                    break;
                }
            }
            return collected.toString();
        });
        assertThat(contentType.get()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        return body;
    }

    private long insertNotification(String type, String title, String resourceType, String resourceId, long userId) {
        return insertNotification(type, title, resourceType, resourceId, userId, organizationId);
    }

    private long insertNotification(
            String type, String title, String resourceType, String resourceId, long userId, long orgId) {
        jdbcTemplate.update(
                "INSERT INTO notifications(organization_id,user_id,type,title,body,resource_type,resource_id,"
                        + "read_at,created_at) VALUES(?,?,?,?,'',?,?,NULL,UTC_TIMESTAMP(6))",
                orgId, userId, type, title, resourceType, resourceId);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM notifications WHERE organization_id=? AND user_id=?", Long.class, orgId, userId);
    }

    private long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users(email,normalized_email,display_name,password_hash,status,created_at,updated_at) "
                        + "VALUES(?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                email, email, "Stream Member", "$2a$10$placeholder");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE normalized_email=?", Long.class, email);
    }

    private long insertOrganization(String slug, long ownerId) {
        jdbcTemplate.update(
                "INSERT INTO organizations(name,slug,owner_user_id,created_at,updated_at) "
                        + "VALUES(?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                "Other Stream Org", slug, ownerId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM organizations WHERE slug=?", Long.class, slug);
    }

    private String loginOwner() {
        ResponseEntity<String> response = csrf().post(
                "/api/v1/auth/login",
                Map.of("email", "owner@example.com", "password", "correct-horse-42"),
                null,
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private CsrfTestClient csrf() {
        return new CsrfTestClient(restTemplate, objectMapper);
    }
}
