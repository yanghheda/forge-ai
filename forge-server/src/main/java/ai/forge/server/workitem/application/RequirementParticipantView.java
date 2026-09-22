package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.RequirementParticipantRole;

public record RequirementParticipantView(
        /* 在当前需求承担的产品、UX、开发或测试角色。 */ RequirementParticipantRole role,
        /* 被关联组织成员的稳定用户标识。 */ long userId,
        /* 参与人在界面显示的名称。 */ String displayName,
        /* 参与人的登录与联系邮箱。 */ String email,
        /* 该领域已分配的执行人标识集合。 */ java.util.List<Long> executorUserIds,
        /* 当前评审轮次中，该领域是否已确认通过。 */ boolean reviewApproved) {
    public RequirementParticipantView(RequirementParticipantRole role, long userId, String displayName, String email) {
        this(role, userId, displayName, email, java.util.List.of(), false);
    }
}
