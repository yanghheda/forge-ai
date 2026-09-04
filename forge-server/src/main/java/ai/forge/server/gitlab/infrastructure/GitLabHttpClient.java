package ai.forge.server.gitlab.infrastructure;

import ai.forge.server.gitlab.application.ConnectionTestResult;
import ai.forge.server.gitlab.application.GitLabRemoteException;
import ai.forge.server.gitlab.application.RepositoryDto;
import ai.forge.server.gitlab.application.SourceControlProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GitLabHttpClient implements SourceControlProvider {

    /* 每次请求前重新执行 DNS 与网段校验的 Base URL 策略。 */
    private final GitLabUrlPolicy urlPolicy;

    /* 禁止自动重定向的 JDK HTTP 客户端。 */
    private final HttpClient httpClient;

    /* 仅解析受大小限制的 GitLab 标准 JSON 响应。 */
    private final ObjectMapper objectMapper;

    /* 单次外部读取的总超时。 */
    private final Duration requestTimeout;

    /* 可进入内存和 JSON 解析器的最大响应字节数。 */
    private final int maxResponseBytes;

    @Autowired
    public GitLabHttpClient(
            GitLabUrlPolicy urlPolicy,
            ObjectMapper objectMapper,
            @Value("${forge.gitlab.request-timeout:5s}") Duration requestTimeout,
            @Value("${forge.gitlab.max-response-bytes:1048576}") int maxResponseBytes) {
        this(
                urlPolicy,
                HttpClient.newBuilder()
                        .connectTimeout(requestTimeout)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                objectMapper,
                requestTimeout,
                maxResponseBytes);
    }

    GitLabHttpClient(
            GitLabUrlPolicy urlPolicy,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Duration requestTimeout,
            int maxResponseBytes) {
        this.urlPolicy = urlPolicy;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public ConnectionTestResult test(String baseUrl, String token) {
        GitLabUserResponse response = get(baseUrl, token, "/api/v4/user", GitLabUserResponse.class);
        if (response.id() == null || response.username() == null || response.username().isBlank()) {
            throw new GitLabRemoteException("GITLAB_INVALID_RESPONSE");
        }
        return new ConnectionTestResult(response.id().toString(), response.username());
    }

    @Override
    public RepositoryDto getRepository(String baseUrl, String token, String remoteProjectId) {
        String encodedProjectId = URLEncoder.encode(remoteProjectId, StandardCharsets.UTF_8).replace("+", "%20");
        GitLabRepositoryResponse response = get(
                baseUrl, token, "/api/v4/projects/" + encodedProjectId, GitLabRepositoryResponse.class);
        if (response.id() == null
                || response.pathWithNamespace() == null
                || response.httpUrl() == null) {
            throw new GitLabRemoteException("GITLAB_INVALID_RESPONSE");
        }
        return new RepositoryDto(
                response.id().toString(),
                response.pathWithNamespace(),
                response.httpUrl(),
                response.defaultBranch());
    }

    private <T> T get(String rawBaseUrl, String token, String path, Class<T> responseType) {
        URI baseUrl = urlPolicy.validate(rawBaseUrl);
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve(baseUrl.getPath() + path))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            validateStatus(response.statusCode());
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(maxResponseBytes + 1);
                if (bytes.length > maxResponseBytes) {
                    throw new GitLabRemoteException("GITLAB_INVALID_RESPONSE");
                }
                return objectMapper.readValue(bytes, responseType);
            }
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new GitLabRemoteException("GITLAB_TIMEOUT", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GitLabRemoteException("GITLAB_UNAVAILABLE", exception);
        } catch (IOException exception) {
            throw new GitLabRemoteException("GITLAB_UNAVAILABLE", exception);
        }
    }

    private static void validateStatus(int status) {
        if (status >= 200 && status < 300) {
            return;
        }
        String code = switch (status) {
            case 401 -> "GITLAB_UNAUTHORIZED";
            case 403 -> "GITLAB_FORBIDDEN";
            case 404 -> "GITLAB_NOT_FOUND";
            case 409 -> "GITLAB_CONFLICT";
            case 429 -> "GITLAB_RATE_LIMITED";
            default -> status >= 500 ? "GITLAB_UNAVAILABLE" : "GITLAB_INVALID_RESPONSE";
        };
        throw new GitLabRemoteException(code);
    }

    private record GitLabUserResponse(
            /* GitLab 当前身份数字标识。 */ Long id,
            /* GitLab 当前身份用户名。 */ String username) {}

    private record GitLabRepositoryResponse(
            /* GitLab 项目数字标识。 */ Long id,
            /* GitLab 项目的完整命名空间路径。 */ @JsonProperty("path_with_namespace") String pathWithNamespace,
            /* GitLab 返回的不含凭据 HTTP 仓库地址。 */ @JsonProperty("http_url_to_repo") String httpUrl,
            /* GitLab 当前默认分支。 */ @JsonProperty("default_branch") String defaultBranch) {}
}
