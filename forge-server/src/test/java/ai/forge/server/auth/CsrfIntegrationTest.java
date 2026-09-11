package ai.forge.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.infrastructure.InfrastructureIntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(OutputCaptureExtension.class)
class CsrfIntegrationTest extends InfrastructureIntegrationTestBase {

    /* 测试环境中唯一允许发起修改请求的浏览器来源。 */
    private static final String ALLOWED_ORIGIN = "http://localhost";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetBootstrapFacts() {
        jdbcTemplate.update(
                "UPDATE instance_settings SET initialized_at = NULL, default_organization_id = NULL, version = 0 WHERE id = 1");
        for (String table : List.of(
                "work_item_events", "review_records", "requirement_details", "work_items", "organization_item_sequences", "organization_policies", "audit_logs", "member_roles",
                "organization_members", "organizations", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @Test
    void validOriginAndTokenAllowMutationWithoutPuttingTokenInUrlResponseOrLogs(CapturedOutput output) throws Exception {
        CsrfSession csrf = csrfSession();
        ResponseEntity<String> response = initialize(headers(csrf, ALLOWED_ORIGIN, csrf.token()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).doesNotContain(csrf.token());
        assertThat(output.getAll()).doesNotContain(csrf.token());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isOne();
    }

    @Test
    void missingOrIncorrectTokenRejectMutationBeforeBusinessWrites() throws Exception {
        CsrfSession csrf = csrfSession();

        ResponseEntity<String> missing = initialize(headers(csrf, ALLOWED_ORIGIN, null));
        ResponseEntity<String> incorrect = initialize(headers(csrf, ALLOWED_ORIGIN, "incorrect-token"));

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(incorrect.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(missing.getBody()).contains("CSRF_REJECTED").contains("requestId");
        assertThat(incorrect.getBody()).contains("CSRF_REJECTED").doesNotContain(csrf.token());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void missingOrCrossOriginRejectMutationEvenWithValidToken() throws Exception {
        CsrfSession csrf = csrfSession();

        ResponseEntity<String> missing = initialize(headers(csrf, null, csrf.token()));
        ResponseEntity<String> crossOrigin = initialize(headers(csrf, "https://attacker.example", csrf.token()));

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(crossOrigin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(missing.getBody()).contains("ORIGIN_REJECTED");
        assertThat(crossOrigin.getBody()).contains("ORIGIN_REJECTED").doesNotContain("attacker.example");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void safeStatusReadsNeedNoTokenAndHaveNoSideEffects() {
        ResponseEntity<String> first = restTemplate.getForEntity("/api/v1/setup/status", String.class);
        ResponseEntity<String> second = restTemplate.getForEntity("/api/v1/setup/status", String.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).contains("\"code\":0", "\"message\":\"success\"", "\"data\"");
        assertThat(first.getBody()).contains("\"initialized\":false");
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    private CsrfSession csrfSession() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/auth/csrf", String.class);
        JsonNode body = objectMapper.readTree(response.getBody()).path("data");
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        return new CsrfSession(setCookie.substring(0, setCookie.indexOf(';')), body.get("token").asText());
    }

    private ResponseEntity<String> initialize(HttpHeaders headers) {
        return restTemplate.exchange(
                "/api/v1/setup/initialize",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "adminEmail", "Owner@Example.COM",
                        "adminDisplayName", "Forge Owner",
                        "password", "correct-horse-42",
                        "organizationName", "Forge",
                        "organizationSlug", "forge",
                        "logoFileName", "logo.webp",
                        "logoMediaType", "image/webp",
                        "logoBase64", "UklGRgAAAABXRUJQVlA4WAAAAAAAAAAAGwAAGwAA"), headers),
                String.class);
    }

    private HttpHeaders headers(CsrfSession csrf, String origin, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE, csrf.cookie());
        if (origin != null) {
            headers.set(HttpHeaders.ORIGIN, origin);
        }
        if (token != null) {
            headers.set("X-CSRF-TOKEN", token);
        }
        return headers;
    }

    private record CsrfSession(
            /* 与测试 Token 对应的 Redis Session Cookie。 */
            String cookie,
            /* 测试修改请求必须通过 Header 回传的随机 Token。 */
            String token) {}
}
