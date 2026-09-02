package ai.forge.server.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /* 请求与响应共同使用的关联标识 Header 名称。 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /* Servlet 请求属性中的关联标识键，供异常处理器读取。 */
    public static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    /* MDC 中的关联标识键，结构化日志通过该键输出 requestId。 */
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    /* 允许透传的请求标识格式，限制长度与字符以阻止日志注入。 */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("req_[A-Za-z0-9._-]{1,64}");

    /* 记录请求完成状态的日志入口，便于从 requestId 追踪单次调用。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable(REQUEST_ID_MDC_KEY, requestId)) {
            filterChain.doFilter(request, response);
            LOGGER.info(
                    "HTTP request completed: method={}, path={}, status={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus());
        }
    }

    private String resolveRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
}
