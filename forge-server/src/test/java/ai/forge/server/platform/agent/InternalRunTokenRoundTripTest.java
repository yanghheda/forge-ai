package ai.forge.server.platform.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InternalRunTokenRoundTripTest {

    private static final String SECRET = "test-only-internal-jwt-secret-32-bytes-minimum";

    @Test
    void runTokenRoundTripCarriesScopeClaims() {
        AgentServiceTokenProvider provider = new AgentServiceTokenProvider(SECRET, Duration.ofSeconds(30));
        InternalRunTokenVerifier verifier = new InternalRunTokenVerifier(SECRET);
        Instant now = Instant.now();

        String token = provider.createRunToken(now, "01ARZ3NDEKTSV4RRFFQ69G5FAV", 7L, 11L);
        InternalRunTokenVerifier.RunCredential credential = verifier.verify(token);

        assertThat(credential.runId()).isEqualTo("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        assertThat(credential.workspaceId()).isEqualTo(7L);
        assertThat(credential.projectId()).isEqualTo(11L);
    }

    @Test
    void expiredRunTokenIsRejected() {
        AgentServiceTokenProvider provider = new AgentServiceTokenProvider(SECRET, Duration.ofSeconds(30));
        InternalRunTokenVerifier verifier = new InternalRunTokenVerifier(
                SECRET, Clock.fixed(Instant.now().plus(Duration.ofMinutes(5)), ZoneOffset.UTC));

        String token = provider.createRunToken(Instant.now(), "01ARZ3NDEKTSV4RRFFQ69G5FAV", 7L, 11L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
