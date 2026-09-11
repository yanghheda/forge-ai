package ai.forge.server.platform.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class AgentServiceTokenProviderTest {

    private static final String SECRET = "test-only-internal-jwt-secret-32-bytes-minimum";

    @Test
    void createsShortLivedTokenScopedToAgentService() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        AgentServiceTokenProvider provider = new AgentServiceTokenProvider(SECRET, Duration.ofSeconds(30));

        Jwt jwt = decoder().decode(provider.createToken(issuedAt));

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("forge-server");
        assertThat(jwt.getSubject()).isEqualTo("forge-server");
        assertThat(jwt.getAudience()).containsExactly("forge-agent");
        assertThat(jwt.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(jwt.getExpiresAt()).isEqualTo(issuedAt.plusSeconds(30));
    }

    @Test
    void createsRunTokenBoundToBackendScope() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        AgentServiceTokenProvider provider = new AgentServiceTokenProvider(SECRET, Duration.ofMinutes(5));

        Jwt jwt = decoder().decode(provider.createRunToken(issuedAt, "run-1", 2));

        assertThat(jwt.getClaimAsString("run_id")).isEqualTo("run-1");
        assertThat(((Number) jwt.getClaim("organization_id")).longValue()).isEqualTo(2);
        assertThat(jwt.getExpiresAt()).isEqualTo(issuedAt.plus(Duration.ofMinutes(5)));
    }

    @Test
    void rejectsWeakSecret() {
        assertThatThrownBy(() -> new AgentServiceTokenProvider("too-short", Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    private NimbusJwtDecoder decoder() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
