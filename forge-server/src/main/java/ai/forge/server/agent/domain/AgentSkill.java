package ai.forge.server.agent.domain;

public enum AgentSkill {
    /* 使用 Requirement 与 PRD 范围执行产品工作。 */
    PRODUCT("requirement.create"),
    /* 使用 UX Task 与 UX 文档范围执行体验工作。 */
    UX("ux.create");

    /* 启动该 Skill 前必须具备的项目权限。 */
    private final String requiredPermission;

    AgentSkill(String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    public String requiredPermission() {
        return requiredPermission;
    }
}
