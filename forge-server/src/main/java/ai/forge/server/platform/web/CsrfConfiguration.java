package ai.forge.server.platform.web;

import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
@Profile("!test-unit")
public class CsrfConfiguration {

    @Bean
    public HttpSessionCsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    @Bean
    public FilterRegistrationBean<CsrfFilter> csrfFilterRegistration(
            HttpSessionCsrfTokenRepository repository,
            SecurityErrorWriter errorWriter) {
        CsrfFilter filter = new CsrfFilter(repository);
        filter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        filter.setRequireCsrfProtectionMatcher(csrfProtectedMatcher());
        filter.setAccessDeniedHandler((request, response, exception) ->
                errorWriter.write(request, response, ErrorCode.CSRF_REJECTED, "CSRF validation failed"));
        FilterRegistrationBean<CsrfFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 75);
        return registration;
    }

    /* 需要 CSRF 校验的是非安全方法，且排除只依赖服务 JWT 的内部端点。 */
    private RequestMatcher csrfProtectedMatcher() {
        RequestMatcher internal = new AntPathRequestMatcher("/internal/**");
        RequestMatcher nonSafe = request -> !HttpMethod.GET.matches(request.getMethod())
                && !HttpMethod.HEAD.matches(request.getMethod())
                && !HttpMethod.OPTIONS.matches(request.getMethod())
                && !HttpMethod.TRACE.matches(request.getMethod());
        return request -> !internal.matches(request) && nonSafe.matches(request);
    }
}
