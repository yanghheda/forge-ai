package ai.forge.server.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test-unit")
class PrometheusEndpointTest {

    /* 通过真实 Actuator MVC 映射验证指标抓取入口。 */
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesPrometheusMetricsInsideTheServerNetwork() throws Exception {
        mockMvc.perform(get("/api/v1/system/status")).andExpect(status().isOk());

        String metrics = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(metrics)
                .contains("http_server_requests_seconds")
                .contains("http_server_requests_seconds_bucket")
                .contains("jvm_memory_used_bytes");
    }
}
