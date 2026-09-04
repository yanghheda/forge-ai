package ai.forge.server.agent.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 契约输入 JSON Schema 的最小校验器，覆盖首批 Tool 契约使用的关键字。
 */
final class ToolArgumentValidator {

    private ToolArgumentValidator() {
    }

    /* 返回按字段排序的错误列表；空列表表示参数满足契约。 */
    static List<String> validate(Map<String, Object> schema, JsonNode arguments) {
        List<String> errors = new ArrayList<>();
        if (!arguments.isObject()) {
            return List.of("arguments 必须是 JSON object");
        }
        validateObject(schema, arguments, "", errors);
        return List.copyOf(errors);
    }

    private static void validateObject(
            Map<String, Object> schema, JsonNode value, String path, List<String> errors) {
        Set<String> allowedProperties = schemaProperties(schema);
        for (String required : requiredFields(schema)) {
            JsonNode field = value.get(required);
            if (field == null || field.isNull() && !isNullable(schema, required)) {
                errors.add(fieldPath(path, required) + " 是必填字段");
            }
        }
        Set<String> unknown = new LinkedHashSet<>();
        value.fieldNames().forEachRemaining(field -> {
            if (!allowedProperties.contains(field)) {
                unknown.add(field);
            }
        });
        if (!unknown.isEmpty() && Boolean.FALSE.equals(schema.get("additionalProperties"))) {
            errors.add("存在契约未定义的字段: " + String.join(", ", unknown));
            return;
        }
        value.fields().forEachRemaining(entry -> {
            Object propertySchema = propertySchemas(schema).get(entry.getKey());
            if (propertySchema instanceof Map<?, ?> rawSchema && !entry.getValue().isNull()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedSchema = (Map<String, Object>) rawSchema;
                validateValue(typedSchema, entry.getValue(), fieldPath(path, entry.getKey()), errors);
            }
        });
    }

    private static void validateValue(Map<String, Object> schema, JsonNode value, String path, List<String> errors) {
        String typeError = typeError(schema, value, path);
        if (typeError != null) {
            errors.add(typeError);
            return;
        }
        if (value.isTextual()) {
            validateText(schema, value, path, errors);
        } else if (value.isNumber()) {
            validateNumber(schema, value, path, errors);
        }
        if (schema.get("enum") instanceof List<?> allowedValues && !enumContains(allowedValues, value)) {
            errors.add(path + " 的取值 " + value.asText() + " 必须是 " + allowedValues + " 之一");
        }
        if ("object".equals(schema.get("type")) && value.isObject()) {
            validateObject(schema, value, path, errors);
        }
    }

    private static boolean enumContains(List<?> allowedValues, JsonNode value) {
        for (Object allowed : allowedValues) {
            if (allowed == null) {
                if (value.isNull()) {
                    return true;
                }
                continue;
            }
            if (String.valueOf(allowed).equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private static String typeError(Map<String, Object> schema, JsonNode value, String path) {
        Object type = schema.get("type");
        List<String> candidates = type instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : type == null ? List.of() : List.of(String.valueOf(type));
        if (candidates.isEmpty()) {
            return null;
        }
        for (String candidate : candidates) {
            if (matchesType(candidate, value)) {
                return null;
            }
        }
        return path + " 的类型必须是 " + candidates + " 之一";
    }

    private static boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private static void validateText(Map<String, Object> schema, JsonNode value, String path, List<String> errors) {
        String text = value.asText();
        Object minLength = schema.get("minLength");
        Object maxLength = schema.get("maxLength");
        if (minLength instanceof Number minimum && text.length() < minimum.intValue()) {
            errors.add(path + " 的长度不能小于 " + minimum.intValue());
        }
        if (maxLength instanceof Number maximum && text.length() > maximum.intValue()) {
            errors.add(path + " 的长度不能大于 " + maximum.intValue());
        }
    }

    private static void validateNumber(Map<String, Object> schema, JsonNode value, String path, List<String> errors) {
        double number = value.asDouble();
        Object minimum = schema.get("minimum");
        Object maximum = schema.get("maximum");
        if (minimum instanceof Number min && number < min.doubleValue()) {
            errors.add(path + " 不能小于 " + min);
        }
        if (maximum instanceof Number max && number > max.doubleValue()) {
            errors.add(path + " 不能大于 " + max);
        }
    }

    private static List<String> requiredFields(Map<String, Object> schema) {
        return schema.get("required") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }

    private static Set<String> schemaProperties(Map<String, Object> schema) {
        return propertySchemas(schema).keySet();
    }

    private static Map<String, Object> propertySchemas(Map<String, Object> schema) {
        if (schema.get("properties") instanceof Map<?, ?> properties) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) properties;
            return result;
        }
        return Map.of();
    }

    private static boolean isNullable(Map<String, Object> schema, String field) {
        Object property = propertySchemas(schema).get(field);
        if (property instanceof Map<?, ?> propertySchema) {
            Object type = propertySchema.get("type");
            if (type instanceof List<?> list) {
                return list.stream().anyMatch("null"::equals);
            }
            return "null".equals(type);
        }
        return false;
    }

    private static String fieldPath(String path, String field) {
        return path.isEmpty() ? field : path + "." + field;
    }
}
