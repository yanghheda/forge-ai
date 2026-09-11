package ai.forge.server.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolArgumentValidatorTest {

    /* 解析测试 JSON 参数。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validArgumentsPassWithoutErrors() throws Exception {
        Map<String, Object> schema = schema(
                List.of("workItemId"),
                Map.of("workItemId", Map.of("type", "integer", "minimum", 1)));

        List<String> errors = ToolArgumentValidator.validate(
                schema, objectMapper.readTree("{\"workItemId\": 42}"));

        assertThat(errors).isEmpty();
    }

    @Test
    void missingRequiredFieldIsReported() throws Exception {
        Map<String, Object> schema = schema(
                List.of("requirementId", "title"),
                Map.of("title", Map.of("type", "string", "minLength", 1)));

        List<String> errors = ToolArgumentValidator.validate(
                schema, objectMapper.readTree("{\"title\": \"UX 重设计\"}"));

        assertThat(errors).singleElement().asString().contains("requirementId").contains("必填");
    }

    @Test
    void scopeInjectionFieldIsRejectedByAdditionalProperties() throws Exception {
        Map<String, Object> schema = schema(
                List.of("title"),
                Map.of("title", Map.of("type", "string", "minLength", 1)));

        List<String> errors = ToolArgumentValidator.validate(
                schema, objectMapper.readTree("{\"title\": \"x\", \"organizationId\": 999}"));

        assertThat(errors).singleElement().asString().contains("organizationId").contains("未定义");
    }

    @Test
    void typeMismatchAndBoundsAreReported() throws Exception {
        Map<String, Object> schema = schema(
                List.of("workItemId", "topK"),
                Map.of(
                        "workItemId", Map.of("type", "integer", "minimum", 1),
                        "topK", Map.of("type", List.of("integer", "null"), "minimum", 1, "maximum", 30)));

        List<String> errors = ToolArgumentValidator.validate(
                schema, objectMapper.readTree("{\"workItemId\": \"abc\", \"topK\": 99}"));

        assertThat(errors).anySatisfy(error -> assertThat(error).contains("workItemId").contains("类型"));
        assertThat(errors).anySatisfy(error -> assertThat(error).contains("topK").contains("30"));
    }

    @Test
    void nullableUnionTypeAcceptsExplicitNull() throws Exception {
        Map<String, Object> schema = schema(
                List.of("title"),
                Map.of(
                        "title", Map.of("type", "string", "minLength", 1),
                        "assigneeId", Map.of("type", List.of("integer", "null"), "minimum", 1)));

        List<String> errors = ToolArgumentValidator.validate(
                schema, objectMapper.readTree("{\"title\": \"t\", \"assigneeId\": null}"));

        assertThat(errors).isEmpty();
    }

    @Test
    void enumValueOutsideContractIsRejected() throws Exception {
        Map<String, Object> schema = schema(
                List.of("priority"),
                Map.of("priority", Map.of(
                        "type", List.of("string", "null"),
                        "enum", Arrays.asList("LOW", "MEDIUM", "HIGH", "URGENT", null))));

        List<String> errors = ToolArgumentValidator.validate(
                schema, objectMapper.readTree("{\"priority\": \"CRITICAL\"}"));

        assertThat(errors).singleElement().asString().contains("CRITICAL").contains("之一");
    }

    @Test
    void nonObjectArgumentsAreRejected() throws Exception {
        Map<String, Object> schema = schema(List.of(), Map.of());

        List<String> errors = ToolArgumentValidator.validate(
                schema, objectMapper.readTree("[1, 2]"));

        assertThat(errors).singleElement().asString().contains("object");
    }

    private Map<String, Object> schema(List<String> required, Map<String, Object> properties) {
        return Map.of(
                "type", "object",
                "required", required,
                "properties", properties,
                "additionalProperties", false);
    }
}
