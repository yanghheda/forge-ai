package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentSkill;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class ToolContractRegistry {

    /* 以 Tool 名称为键的不可变契约集合；启动时加载并校验。 */
    private final Map<String, ToolContract> tools;

    /* 以 Skill 小写名称为键的不可变契约集合。 */
    private final Map<String, SkillContract> skills;

    public ToolContractRegistry() {
        this.tools = loadTools();
        this.skills = loadSkills();
        validateSkillReferences();
    }

    public java.util.Optional<ToolContract> findTool(String name) {
        return java.util.Optional.ofNullable(tools.get(name));
    }

    public java.util.Optional<SkillContract> findSkill(String name) {
        return java.util.Optional.ofNullable(skills.get(name == null ? "" : name.toLowerCase(Locale.ROOT)));
    }

    /* 返回指定 Agent Skill 的 Tool 名称白名单；作为 Manifest 入口上限下发给 Agent。 */
    public List<String> effectiveToolNames(AgentSkill skill) {
        return findSkill(skill.name()).map(SkillContract::allowedTools).orElse(List.of());
    }

    private Map<String, ToolContract> loadTools() {
        Map<String, ToolContract> result = new LinkedHashMap<>();
        for (Map<String, Object> document : yamlDocuments("classpath*:tools/*.yaml")) {
            ToolContract contract = toolContract(document);
            if (result.put(contract.name(), contract) != null) {
                throw new IllegalStateException("重复的 Tool 契约名称: " + contract.name());
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("classpath 中没有可用的 Tool 契约");
        }
        return Map.copyOf(result);
    }

    private Map<String, SkillContract> loadSkills() {
        Map<String, SkillContract> result = new LinkedHashMap<>();
        for (Map<String, Object> document : yamlDocuments("classpath*:skills/*.yaml")) {
            SkillContract contract = skillContract(document);
            if (result.put(contract.name(), contract) != null) {
                throw new IllegalStateException("重复的 Skill 契约名称: " + contract.name());
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("classpath 中没有可用的 Skill 契约");
        }
        return Map.copyOf(result);
    }

    private void validateSkillReferences() {
        for (SkillContract skill : skills.values()) {
            for (String toolName : skill.allowedTools()) {
                if (!tools.containsKey(toolName)) {
                    throw new IllegalStateException("Skill '" + skill.name() + "' 引用了不存在的 Tool: " + toolName);
                }
            }
        }
    }

    private ToolContract toolContract(Map<String, Object> document) {
        String name = text(document, "name");
        Object rawVersion = document.get("version");
        Object idempotency = document.get("idempotency");
        @SuppressWarnings("unchecked")
        Map<String, Object> backend = requireMap(document.get("backend_mapping"), "backend_mapping");
        Set<String> risks = Set.of("LOW", "MEDIUM", "HIGH");
        String riskLevel = text(document, "risk_level");
        if (!risks.contains(riskLevel)) {
            throw new IllegalStateException("Tool '" + name + "' 的 risk_level 非法: " + riskLevel);
        }
        String backendPath = text(backend, "path");
        if (!backendPath.startsWith("/internal/v1/")) {
            throw new IllegalStateException("Tool '" + name + "' 的 backend_mapping.path 必须位于 /internal/v1/ 下");
        }
        if (!(rawVersion instanceof Number version) || version.intValue() < 1) {
            throw new IllegalStateException("Tool '" + name + "' 的 version 必须为正整数");
        }
        boolean idempotencyRequired = idempotency instanceof Map<?, ?> mapping
                && Boolean.TRUE.equals(mapping.get("required"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = requireMap(document.get("input_schema"), "input_schema");
        return new ToolContract(
                name,
                version.intValue(),
                text(document, "description"),
                text(document, "required_permission"),
                riskLevel,
                idempotencyRequired,
                inputSchema,
                text(backend, "method"),
                backendPath);
    }

    private SkillContract skillContract(Map<String, Object> document) {
        Object rawVersion = document.get("version");
        if (!(rawVersion instanceof Number version) || version.intValue() < 1) {
            throw new IllegalStateException("Skill '" + text(document, "name") + "' 的 version 必须为正整数");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> limits = requireMap(document.get("limits"), "limits");
        Object maxToolCalls = limits.get("max_tool_calls");
        if (!(maxToolCalls instanceof Number calls) || calls.intValue() < 0) {
            throw new IllegalStateException("Skill '" + text(document, "name") + "' 的 max_tool_calls 非法");
        }
        Object allowed = document.get("allowed_tools");
        if (!(allowed instanceof List<?> toolNames)) {
            throw new IllegalStateException("Skill '" + text(document, "name") + "' 缺少 allowed_tools");
        }
        return new SkillContract(
                text(document, "name"),
                version.intValue(),
                nullableText(document, "prompt_version"),
                nullableText(document, "context_template"),
                toolNames.stream().map(String::valueOf).toList(),
                calls.intValue());
    }

    private String nullableText(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? null : value.toString();
    }

    private List<Map<String, Object>> yamlDocuments(String location) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Yaml yaml = new Yaml();
        try {
            Resource[] resources = resolver.getResources(location);
            List<Map<String, Object>> documents = new java.util.ArrayList<>();
            for (Resource resource : resources) {
                try (InputStream input = resource.getInputStream()) {
                    Map<String, Object> document = yaml.load(input);
                    if (document != null && document.get("name") instanceof String) {
                        documents.add(document);
                    }
                }
            }
            return List.copyOf(documents);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载契约资源 " + location, exception);
        }
    }

    private Map<String, Object> requireMap(Object value, String field) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) map;
            return result;
        }
        throw new IllegalStateException("契约缺少必填字段 " + field);
    }

    private String text(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalStateException("契约缺少必填字段 " + key);
    }
}
