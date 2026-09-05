package ai.forge.server.gitlab.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WebhookParser {

    /* 只解析已通过大小限制的 JSON，不保留或记录原始内容。 */
    private final ObjectMapper objectMapper;

    public WebhookParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<WebhookChange> parse(String eventType, String payload) {
        JsonNode root = json(payload);
        return switch (eventType) {
            case "Merge Request Hook" -> Optional.of(mergeRequest(root));
            case "Pipeline Hook" -> Optional.of(pipeline(root));
            default -> Optional.empty();
        };
    }

    public String objectId(String payload) {
        JsonNode root = json(payload);
        return root.path("object_attributes").path("id").asText("unknown");
    }

    public String updatedAt(String payload) {
        JsonNode attributes = json(payload).path("object_attributes");
        return attributes.path("updated_at").asText(attributes.path("finished_at").asText("unknown"));
    }

    private WebhookChange.MergeRequestChanged mergeRequest(JsonNode root) {
        JsonNode value = root.path("object_attributes");
        return new WebhookChange.MergeRequestChanged(
                required(root.path("project"), "id"),
                requiredLong(value, "iid"),
                required(value, "title"),
                required(value, "source_branch"),
                required(value, "target_branch"),
                required(value, "state"),
                required(value, "url"),
                nullable(root.path("user"), "id"),
                nullable(value.path("last_commit"), "id"),
                nullable(value, "merge_status"),
                date(required(value, "updated_at")));
    }

    private WebhookChange.PipelineChanged pipeline(JsonNode root) {
        JsonNode value = root.path("object_attributes");
        return new WebhookChange.PipelineChanged(
                required(root.path("project"), "id"),
                requiredLong(value, "id"),
                required(value, "ref"),
                required(value, "sha"),
                required(value, "status"),
                required(value, "url"),
                nullableDate(value, "started_at"),
                nullableDate(value, "finished_at"),
                date(required(value, "updated_at")));
    }

    private JsonNode json(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid webhook json", exception);
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing webhook field");
        }
        return value;
    }

    private static long requiredLong(JsonNode node, String field) {
        if (!node.path(field).canConvertToLong()) {
            throw new IllegalArgumentException("invalid webhook field");
        }
        return node.path(field).asLong();
    }

    private static String nullable(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static LocalDateTime nullableDate(JsonNode node, String field) {
        String value = nullable(node, field);
        return value == null ? null : date(value);
    }

    private static LocalDateTime date(String value) {
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("invalid webhook timestamp", exception);
        }
    }
}
