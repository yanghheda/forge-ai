package ai.forge.server.platform.agent;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class InternalRunTokenVerifier {

    /* 验证 forge-agent 回调 Tool 执行时携带的短时 run-scoped credential。 */
    private final JwtDecoder jwtDecoder;

    /* 决定 token 过期判定的时钟；便于测试注入。 */
    private final Clock clock;

    @Autowired
    public InternalRunTokenVerifier(
            @Value("${forge.infrastructure.agent.jwt-secret}") String jwtSecret) {
        this(jwtSecret, Clock.systemUTC());
    }

    InternalRunTokenVerifier(String jwtSecret, Clock clock) {
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Agent internal JWT secret must be at least 32 bytes");
        }
        SecretKey secretKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        this.clock = clock;
    }

    /* 校验签名与声明并返回 run 上下文；任何缺失字段都视为不可信凭据。 */
    public RunCredential verify(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt == null || !Instant.now(clock).isBefore(expiresAt)) {
                throw new IllegalArgumentException("Run credential is expired");
            }
            if (!"forge-server".equals(jwt.getClaimAsString("iss"))
                    || jwt.getAudience() == null
                    || !jwt.getAudience().contains("forge-agent")) {
                throw new IllegalArgumentException("Run credential has unexpected issuer or audience");
            }
            String runId = jwt.getClaimAsString("run_id");
            Long organizationId = longClaim(jwt, "organization_id");
            if (runId == null || runId.isBlank() || organizationId == null) {
                throw new IllegalArgumentException("Run credential lacks scope claims");
            }
            return new RunCredential(runId, organizationId);
        } catch (JwtException | NumberFormatException exception) {
            throw new IllegalArgumentException("Run credential is invalid", exception);
        }
    }

    private Long longClaim(Jwt jwt, String claim) {
        String value = jwt.getClaimAsString(claim);
        return value == null ? null : Long.parseLong(value);
    }

    /* 只携带上下文标识的已验证凭据；不代表任何权限集合。 */
    public record RunCredential(
            /* 凭据绑定的 Run 标识。 */
            String runId,
            /* 凭据绑定的工作区标识。 */
            long organizationId) {
    }
}
