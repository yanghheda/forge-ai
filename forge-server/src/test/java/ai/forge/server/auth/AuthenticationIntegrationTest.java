package ai.forge.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(OutputCaptureExtension.class)
class AuthenticationIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 集成测试登录使用的有效明文密码，只存在于测试进程。 */
    private static final String PASSWORD = "correct-horse-42";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void resetIdentityAndSessions() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
                "audit_logs", "member_roles", "roles", "workspace_members", "workspaces", "organizations", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        ResponseEntity<String> initialized = restTemplate.postForEntity(
                "/api/v1/setup/initialize",
                Map.of(
                        "adminEmail", "Owner@Example.COM",
                        "adminDisplayName", "Forge Owner",
                        "password", PASSWORD,
                        "organizationName", "Forge",
                        "organizationSlug", "forge",
                        "workspaceName", "Engineering",
                        "workspaceSlug", "engineering"),
                String.class);
        assertThat(initialized.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void loginCreatesRedisSessionAndMeReadsCurrentWorkspace(CapturedOutput output) {
        ResponseEntity<String> login = login("owner@example.com", PASSWORD, null);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String setCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains("FORGE_SESSION=")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Secure");
        String cookie = cookiePair(setCookie);
        ResponseEntity<String> me = get("/api/v1/me", cookie);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody())
                .contains("\"email\":\"Owner@Example.COM\"")
                .contains("\"slug\":\"engineering\"")
                .contains("\"roles\":[\"OWNER\"]")
                .doesNotContain("password");
        assertThat(redisTemplate.keys("forge:session:sessions:*")).isNotEmpty();
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain("$2b$12$");
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnSamePublicFailureAndSafeAudit() {
        ResponseEntity<String> wrong = login("owner@example.com", "incorrect-password-42", null);
        ResponseEntity<String> unknown = login("missing@example.com", "incorrect-password-42", null);

        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody()).contains("UNAUTHENTICATED").contains("Invalid email or password");
        assertThat(unknown.getBody()).contains("UNAUTHENTICATED").contains("Invalid email or password");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGIN' AND result = 'FAILURE'",
                        Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE actor_type = 'ANONYMOUS' AND actor_id IS NULL",
                        Integer.class))
                .isOne();
    }

    @Test
    void utf8PasswordBeyondBcryptLimitFailsWithoutPrefixTruncation() {
        ResponseEntity<String> response = login("owner@example.com", "密".repeat(25), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid email or password").doesNotContain("UTF-8");
    }

    @Test
    void fifthFailureLocksAccountAndSuccessAfterExpiryResetsCounter() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(login("owner@example.com", "incorrect-password-42", null).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT failed_login_count FROM users", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT locked_until > UTC_TIMESTAMP(6) FROM users", Boolean.class))
                .isTrue();
        Object lockedUntil = jdbcTemplate.queryForObject("SELECT locked_until FROM users", Object.class);
        assertThat(login("owner@example.com", PASSWORD, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbcTemplate.queryForObject("SELECT locked_until FROM users", Object.class)).isEqualTo(lockedUntil);

        jdbcTemplate.update("UPDATE users SET locked_until = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND)");
        assertThat(login("owner@example.com", PASSWORD, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT failed_login_count = 0 AND locked_until IS NULL FROM users", Boolean.class))
                .isTrue();
    }

    @Test
    void concurrentFailuresAreAllCountedAndLockAccount() {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<HttpStatus>> attempts = java.util.stream.IntStream.range(0, 5)
                .mapToObj(ignored -> CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return HttpStatus.valueOf(login(
                            "owner@example.com", "incorrect-password-42", null).getStatusCode().value());
                }))
                .toList();
        start.countDown();

        assertThat(attempts.stream().map(CompletableFuture::join))
                .containsOnly(HttpStatus.UNAUTHORIZED)
                .hasSize(5);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT failed_login_count = 5 AND locked_until > UTC_TIMESTAMP(6) FROM users", Boolean.class))
                .isTrue();
    }

    @Test
    void reauthenticationRotatesSessionIdAndOldIdCannotBeUsed() {
        String oldCookie = cookiePair(login("owner@example.com", PASSWORD, null)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        ResponseEntity<String> reauthenticated = login("owner@example.com", PASSWORD, oldCookie);
        String newCookie = cookiePair(reauthenticated.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        assertThat(newCookie).isNotEqualTo(oldCookie);
        assertThat(get("/api/v1/me", oldCookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/me", newCookie).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void openApiPublishesAuthContractWithoutPasswordHashOrSessionPayload() {
        String openApi = restTemplate.getForObject("/v3/api-docs", String.class);

        assertThat(openApi)
                .contains("/api/v1/auth/login")
                .contains("/api/v1/auth/logout")
                .contains("/api/v1/me")
                .doesNotContain("passwordHash")
                .doesNotContain("SessionPrincipal");
    }

    @Test
    void rateLimitUsesHashedRedisKeyAndRejectsAttemptBeyondWindowLimit() {
        for (int attempt = 0; attempt < 10; attempt++) {
            login("missing@example.com", "incorrect-password-42", null);
        }
        ResponseEntity<String> limited = login("missing@example.com", "incorrect-password-42", null);

        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getBody()).contains("LOGIN_RATE_LIMITED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGIN' AND result = 'FAILURE'",
                        Integer.class))
                .isEqualTo(11);
        Set<String> keys = redisTemplate.keys("forge:auth:login:*");
        assertThat(keys).hasSize(1).allMatch(key -> !key.contains("missing@example.com"));
    }

    @Test
    void logoutRevokesOnlyCurrentConcurrentSession() {
        String firstCookie = cookiePair(login("owner@example.com", PASSWORD, null)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        String secondCookie = cookiePair(login("owner@example.com", PASSWORD, null)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        assertThat(firstCookie).isNotEqualTo(secondCookie);

        ResponseEntity<String> logout = postWithoutBody("/api/v1/auth/logout", firstCookie);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(get("/api/v1/me", firstCookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/me", secondCookie).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE action = 'SESSION_REVOKED'", Integer.class))
                .isOne();
    }

    @Test
    void removingRedisSessionImmediatelyInvalidatesCookie() {
        String cookie = cookiePair(login("owner@example.com", PASSWORD, null)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        Set<String> sessionKeys = redisTemplate.keys("forge:session:sessions:*");
        assertThat(sessionKeys).isNotEmpty();
        redisTemplate.delete(sessionKeys);

        assertThat(get("/api/v1/me", cookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unauthenticatedMeAndLogoutReturnStableError() {
        assertThat(get("/api/v1/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postWithoutBody("/api/v1/auth/logout", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> login(String email, String password, String cookie) {
        HttpHeaders headers = headers(cookie);
        return restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password), headers),
                String.class);
    }

    private ResponseEntity<String> get(String path, String cookie) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(cookie)), String.class);
    }

    private ResponseEntity<String> postWithoutBody(String path, String cookie) {
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(headers(cookie)), String.class);
    }

    private HttpHeaders headers(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) {
            headers.set(HttpHeaders.COOKIE, cookie);
        }
        return headers;
    }

    private String cookiePair(String setCookie) {
        assertThat(setCookie).isNotBlank();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent authentication test interrupted", exception);
        }
    }
}
