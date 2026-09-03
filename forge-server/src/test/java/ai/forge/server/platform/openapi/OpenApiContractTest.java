package ai.forge.server.platform.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test-unit")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void actuatorHealthIsAvailableButNotPartOfPublicContract() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openApi = objectMapper.readTree(document);

        List<String> paths = new ArrayList<>();
        openApi.path("paths").fieldNames().forEachRemaining(paths::add);
        assertThat(paths)
                .containsExactly("/api/v1/system/status")
                .allMatch(path -> path.startsWith("/api/v1/"))
                .noneMatch(path -> path.startsWith("/actuator") || path.startsWith("/internal/"));
    }

    @Test
    void publicContractDeclaresMetadataAndFutureSessionSecuritySchemes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ForgeAI Public API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieSession.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieSession.name").value("FORGE_SESSION"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfHeader.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfHeader.name").value("X-CSRF-TOKEN"));
    }

    @Test
    void swaggerUiIsDisabledOutsideDevelopmentProfile() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
