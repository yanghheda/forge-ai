package ai.forge.server.auth.controller;

import ai.forge.server.auth.domain.AuthContext;
import ai.forge.server.auth.domain.SessionPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("!test-unit")
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class AuthenticationFilter extends OncePerRequestFilter {

    /* Session 中最小认证主体的属性名。 */
    public static final String SESSION_PRINCIPAL_ATTRIBUTE = AuthenticationFilter.class.getName() + ".principal";

    /* 请求内认证上下文的属性名，不跨请求或线程保存。 */
    public static final String AUTH_CONTEXT_ATTRIBUTE = AuthenticationFilter.class.getName() + ".context";

    /* 会话从首次签发开始允许存活的最长时间。 */
    private final Duration absoluteSessionTimeout;

    public AuthenticationFilter(@Value("${forge.auth.absolute-session-timeout:12h}") Duration absoluteSessionTimeout) {
        this.absoluteSessionTimeout = absoluteSessionTimeout;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object value = session.getAttribute(SESSION_PRINCIPAL_ATTRIBUTE);
            if (value instanceof SessionPrincipal principal) {
                if (!Instant.now().isBefore(principal.issuedAt().plus(absoluteSessionTimeout))) {
                    session.invalidate();
                } else {
                    request.setAttribute(AUTH_CONTEXT_ATTRIBUTE, new AuthContext(principal.userId()));
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
