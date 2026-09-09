package ai.forge.server.workitem.domain;

public enum RequirementParticipantRole {
    /* 负责需求定义、产品材料和产品评审的人员。 */
    PRODUCT,
    /* 负责用户体验方案、UX 文档和体验评审的人员。 */
    UX,
    /* 负责实现、代码评审和持续集成结果的人员。 */
    DEVELOPER,
    /* 负责测试设计、执行、缺陷验证和质量结论的人员。 */
    QA
}
