package ai.forge.server.agent.application;

import java.util.List;

public record SkillContract(
        /* Skill 契约的稳定名称，与 AgentSkill 枚举的小写形式一一对应。 */
        String name,
        /* Skill 契约版本。 */
        int version,
        /* 该 Skill 允许调用的 Tool 名称白名单；是入口上限而非执行授权。 */
        List<String> allowedTools,
        /* 单次图执行的 Tool 调用硬上限。 */
        int maxToolCalls) {
}
