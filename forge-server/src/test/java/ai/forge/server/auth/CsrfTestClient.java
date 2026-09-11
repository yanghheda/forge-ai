package ai.forge.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class CsrfTestClient {

    /* 测试环境配置允许的浏览器 Origin。 */
    private static final String ORIGIN = "http://localhost";

    /* 对随机端口测试服务发起真实 HTTP 请求的客户端。 */
    private final TestRestTemplate restTemplate;

    /* 解析 CSRF 端点的 JSON Token 响应。 */
    private final ObjectMapper objectMapper;

    public CsrfTestClient(TestRestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> ResponseEntity<T> post(String path, Object body, String existingCookie, Class<T> responseType) {
        return exchange(HttpMethod.POST, path, body, existingCookie, responseType);
    }

    public <T> ResponseEntity<T> patch(String path, Object body, String existingCookie, Class<T> responseType) {
        return exchange(HttpMethod.PATCH, path, body, existingCookie, responseType);
    }

    public <T> ResponseEntity<T> put(String path, Object body, String existingCookie, Class<T> responseType) {
        return exchange(HttpMethod.PUT, path, body, existingCookie, responseType);
    }

    public <T> ResponseEntity<T> get(String path, String existingCookie, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (existingCookie != null) {
            headers.set(HttpHeaders.COOKIE, existingCookie);
        }
        return unwrapSuccess(restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), responseType), responseType);
    }

    public <T> ResponseEntity<T> delete(String path, String existingCookie, Class<T> responseType) {
        return exchange(HttpMethod.DELETE, path, null, existingCookie, responseType);
    }

    private <T> ResponseEntity<T> exchange(
            HttpMethod method, String path, Object body, String existingCookie, Class<T> responseType) {
        CsrfSession csrf = fetch(existingCookie);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ORIGIN, ORIGIN);
        headers.set(HttpHeaders.COOKIE, csrf.cookie());
        headers.set("X-CSRF-TOKEN", csrf.token());
        ResponseEntity<T> response = restTemplate.exchange(path, method, new HttpEntity<>(body, headers), responseType);
        return unwrapSuccess(response, responseType);
    }

    private CsrfSession fetch(String existingCookie) {
        HttpHeaders headers = new HttpHeaders();
        if (existingCookie != null) {
            headers.set(HttpHeaders.COOKIE, existingCookie);
        }
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/auth/csrf", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        try {
            JsonNode body = objectMapper.readTree(response.getBody()).path("data");
            String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
            String cookie = existingCookie != null ? existingCookie : cookiePair(setCookie);
            return new CsrfSession(cookie, body.get("token").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read test CSRF response", exception);
        }
    }

    public ResponseEntity<String> unwrapSuccess(ResponseEntity<String> response) {
        return unwrapSuccess(response, String.class);
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> unwrapSuccess(ResponseEntity<T> response, Class<T> responseType) {
        if (responseType != String.class || response.getBody() == null || !response.getStatusCode().is2xxSuccessful()) {
            return response;
        }
        try {
            JsonNode envelope = objectMapper.readTree((String) response.getBody());
            if (envelope.path("code").asInt(-1) != 0 || !envelope.has("data")) {
                return response;
            }
            T data = (T) objectMapper.writeValueAsString(envelope.get("data"));
            return new ResponseEntity<>(data, response.getHeaders(), response.getStatusCode());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to unwrap successful test response", exception);
        }
    }

    private String cookiePair(String setCookie) {
        assertThat(setCookie).isNotBlank();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private record CsrfSession(
            /* 与测试 Token 绑定的 Redis Session Cookie。 */
            String cookie,
            /* 修改请求通过 Header 回传的随机 Token。 */
            String token) {}
}
