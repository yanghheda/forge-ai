package ai.forge.server.agent.application;

import java.util.Map;

public record ToolContract(
        /* Tool 契约的稳定名称；Registry、Skill 白名单与内部执行端点都以它寻址。 */
        String name,
        /* Tool 契约版本；参数或行为变更时递增，用于审计与兼容判断。 */
        int version,
        /* 供模型理解用途的说明文字，不构成授权或执行语义。 */
        String description,
        /* 契约声明的入口权限代码；服务端执行前仍会按最新事实重新校验。 */
        String requiredPermission,
        /* 风险等级：LOW 直接执行、MEDIUM 受确认策略约束、HIGH 必须人工审批。 */
        String riskLevel,
        /* 是否声明写副作用并要求幂等；为 true 时成功结果按幂等键重放。 */
        boolean idempotencyRequired,
        /* 契约输入 JSON Schema；服务端在执行前用它拒绝非法参数。 */
        Map<String, Object> inputSchema,
        /* 内部执行端点的 HTTP 方法。 */
        String backendMethod,
        /* 内部执行端点路径；形如 /internal/v1/tools/{name}:execute。 */
        String backendPath) {

    /* MEDIUM 风险工具受 Run 确认策略约束。 */
    public boolean mediumRisk() {
        return "MEDIUM".equals(riskLevel);
    }

    /* HIGH 风险工具无条件进入人工审批，不受 Run 的 MEDIUM 策略影响。 */
    public boolean highRisk() {
        return "HIGH".equals(riskLevel);
    }
}
