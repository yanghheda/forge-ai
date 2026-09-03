package ai.forge.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "forge.auth.absolute-session-timeout=100ms")
class AuthenticationSessionExpiryIntegrationTest extends InfrastructureIntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void initializeIdentity() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
                "audit_logs", "member_roles", "roles", "workspace_members", "workspaces", "organizations", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        new CsrfTestClient(restTemplate, objectMapper).post(
                "/api/v1/setup/initialize",
                Map.of(
                        "adminEmail", "owner@example.com",
                        "adminDisplayName", "Forge Owner",
                        "password", "correct-horse-42",
                        "organizationName", "Forge",
                        "organizationSlug", "forge",
                        "workspaceName", "Engineering",
                        "workspaceSlug", "engineering"), null,
                String.class);
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void absoluteDeadlineInvalidatesOtherwiseActiveRedisSession() throws Exception {
        ResponseEntity<Void> login = new CsrfTestClient(restTemplate, objectMapper).post(
                "/api/v1/auth/login",
                Map.of("email", "owner@example.com", "password", "correct-horse-42"),
                null,
                Void.class);
        String setCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        String cookie = setCookie.substring(0, setCookie.indexOf(';'));
        Thread.sleep(150);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);

        ResponseEntity<String> expired = restTemplate.exchange(
                "/api/v1/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(redisTemplate.keys("forge:session:sessions:*")).isEmpty();
    }
}
