package ai.forge.server.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiWebTest.ValidationProbeController.class)
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test-unit")
class ApiWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void systemStatusUsesControllerApplicationDomainChain() throws Exception {
        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER))
                .andExpect(jsonPath("$.application").value("forge-server"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void acceptedRequestIdIsReturnedInHeaderAndJsonLog(CapturedOutput output) throws Exception {
        String requestId = "req_client-123";

        mockMvc.perform(get("/api/v1/system/status")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, requestId))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, requestId));

        assertThat(Arrays.stream(output.getOut().split("\\R"))
                        .filter(line -> line.startsWith("{"))
                        .map(this::readJson)
                        .anyMatch(json -> requestId.equals(json.path("requestId").asText())
                                && json.path("message").asText().contains("HTTP request completed")))
                .isTrue();
    }

    @Test
    void unsafeRequestIdIsReplaced() throws Exception {
        String responseRequestId = mockMvc.perform(get("/api/v1/system/status")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "line-one\nline-two"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(RequestIdFilter.REQUEST_ID_HEADER);

        assertThat(responseRequestId).startsWith("req_").doesNotContain("line-one");
    }

    @Test
    void missingRouteReturnsStableErrorWithoutStackTrace() throws Exception {
        mockMvc.perform(get("/api/v1/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void invalidBodyReturnsFieldDetails() throws Exception {
        mockMvc.perform(post("/api/v1/test-support/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.details.name").value("must not be blank"));
    }

    private JsonNode readJson(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (Exception exception) {
            throw new AssertionError("Expected a JSON log line", exception);
        }
    }

    @RestController
    @RequestMapping("/api/v1/test-support")
    static class ValidationProbeController {

        @PostMapping("/validation")
        ValidationProbeRequest validate(@Valid @RequestBody ValidationProbeRequest request) {
            return request;
        }
    }

    record ValidationProbeRequest(@NotBlank String name) {}
}
