package ai.forge.server.auth.controller;

import ai.forge.server.auth.application.AuthenticationService;
import ai.forge.server.auth.domain.AuthContext;
import ai.forge.server.auth.domain.AuthenticatedUser;
import ai.forge.server.auth.domain.LoginCommand;
import ai.forge.server.auth.domain.SessionPrincipal;
import ai.forge.server.auth.domain.UnauthenticatedException;
import ai.forge.server.platform.web.RequestIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/auth")
@Tag(name = "身份认证", description = "本地密码登录与服务端 Session 撤销")
public class AuthController {

    /* 执行凭据验证、锁定状态更新与认证审计的应用服务。 */
    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @GetMapping("/csrf")
    @Operation(summary = "获取 CSRF Token", description = "权限：公开；Token 仅通过 JSON 返回并绑定当前服务端 Session。")
    @ApiResponse(responseCode = "200", description = "返回修改请求必须携带的 CSRF Header 名称与 Token")
    public CsrfTokenResponse csrf(@RequestAttribute(name = "_csrf") CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "权限：公开；按 IP 与规范化邮箱限流，成功后轮换 Session ID。")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "凭据有效且 Redis Session 已建立"),
        @ApiResponse(responseCode = "401", description = "邮箱、密码或账户状态不可用于登录"),
        @ApiResponse(responseCode = "429", description = "登录尝试超过短期速率限制")
    })
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        AuthenticatedUser authenticated = authenticationService.authenticate(new LoginCommand(
                body.email(), body.password(), request.getRemoteAddr(), requestId(request)));
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        SessionPrincipal principal = new SessionPrincipal(authenticated.userId(), Instant.now());
        session.setAttribute(AuthenticationFilter.SESSION_PRINCIPAL_ATTRIBUTE, principal);
        session.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                Long.toString(authenticated.userId()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    @Operation(summary = "退出", description = "权限：已登录；撤销当前服务端 Session，不影响该用户的其他并发会话。")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "当前 Session 已撤销"),
        @ApiResponse(responseCode = "401", description = "当前请求没有有效 Session")
    })
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        AuthContext context = requireContext(request);
        authenticationService.recordLogout(context.userId(), requestId(request));
        request.getSession(false).invalidate();
        return ResponseEntity.noContent().build();
    }

    static AuthContext requireContext(HttpServletRequest request) {
        Object value = request.getAttribute(AuthenticationFilter.AUTH_CONTEXT_ATTRIBUTE);
        if (value instanceof AuthContext context) {
            return context;
        }
        throw new UnauthenticatedException();
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
    }

    public record LoginRequest(
            /* 用于查找本地身份且会在服务端规范化的电子邮箱。 */
            @NotBlank @Email @Size(max = 320) String email,
            /* 只参与本次 BCrypt 校验且禁止回显的密码。 */
            @NotBlank String password) {}

    public record CsrfTokenResponse(
            /* 修改请求携带 Token 时使用的固定 Header 名称。 */
            String headerName,
            /* 与当前服务端 Session 绑定且不得进入 URL 或日志的随机值。 */
            String token) {}
}
