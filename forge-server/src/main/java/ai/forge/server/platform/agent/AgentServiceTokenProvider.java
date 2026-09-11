package ai.forge.server.platform.agent;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class AgentServiceTokenProvider {

    /* 服务令牌签发器；只使用进程配置中的对称密钥，不读取业务数据。 */
    private final JwtEncoder jwtEncoder;

    /* 单个健康探测令牌的有效期，限制凭据泄露后的可重放窗口。 */
    private final Duration tokenTtl;

    public AgentServiceTokenProvider(
            @Value("${forge.infrastructure.agent.jwt-secret}") String jwtSecret,
            @Value("${forge.infrastructure.agent.token-ttl:30s}") Duration tokenTtl) {
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Agent internal JWT secret must be at least 32 bytes");
        }
        if (tokenTtl.isZero() || tokenTtl.isNegative() || tokenTtl.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Agent service token TTL must be between 1ns and 5m");
        }
        SecretKey secretKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        this.tokenTtl = tokenTtl;
    }

    public String createToken(Instant issuedAt) {
        return encode(baseClaims(issuedAt).build());
    }

    public String createRunToken(Instant issuedAt, String runId, long organizationId) {
        JwtClaimsSet claims = baseClaims(issuedAt)
                .claim("run_id", runId)
                .claim("organization_id", organizationId)
                .build();
        return encode(claims);
    }

    private JwtClaimsSet.Builder baseClaims(Instant issuedAt) {
        return JwtClaimsSet.builder()
                .issuer("forge-server")
                .subject("forge-server")
                .audience(List.of("forge-agent"))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(tokenTtl));
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
