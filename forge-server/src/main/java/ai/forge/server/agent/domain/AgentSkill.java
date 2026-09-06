package ai.forge.server.agent.domain;

public enum AgentSkill {
    /* 使用 Requirement 与 PRD 范围执行产品工作。 */
    PRODUCT("requirement.create"),
    /* 使用 UX Task 与 UX 文档范围执行体验工作。 */
    UX("ux.create"),
    /* 使用 Requirement、交付文档和 GitLab 受控能力执行开发规划与启动。 */
    DEVELOPER("task.create"),
    /* 生成 Test Case 与 Bug 草稿，不写人工执行结果。 */
    QA("qa.manage"),
    /* 解释后端 Precheck 并起草 Release Note，不决定 PASS。 */
    RELEASE("release.read");

    /* 启动该 Skill 前必须具备的项目权限。 */
    private final String requiredPermission;

    AgentSkill(String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    public String requiredPermission() {
        return requiredPermission;
    }
}
