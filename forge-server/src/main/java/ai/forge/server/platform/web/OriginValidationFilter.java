package ai.forge.server.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("!test-unit")
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class OriginValidationFilter extends OncePerRequestFilter {

    /* 当前部署明确允许发起浏览器修改请求的 Origin 集合。 */
    private final Set<String> allowedOrigins;

    /* 输出统一安全错误响应且不暴露 Token 或来源配置。 */
    private final SecurityErrorWriter errorWriter;

    public OriginValidationFilter(
            @Value("${forge.web.allowed-origins}") String allowedOrigins,
            SecurityErrorWriter errorWriter) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isStateChanging(request.getMethod())) {
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (origin == null || !allowedOrigins.contains(origin)) {
                errorWriter.write(request, response, ErrorCode.ORIGIN_REJECTED, "Request origin is not allowed");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isStateChanging(String method) {
        return !Set.of("GET", "HEAD", "OPTIONS").contains(method);
    }
}
