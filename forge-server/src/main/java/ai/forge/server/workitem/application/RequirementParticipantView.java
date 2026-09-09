package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.RequirementParticipantRole;

public record RequirementParticipantView(
        /* 在当前需求承担的产品、UX、开发或测试角色。 */ RequirementParticipantRole role,
        /* 被关联组织成员的稳定用户标识。 */ long userId,
        /* 参与人在界面显示的名称。 */ String displayName,
        /* 参与人的登录与联系邮箱。 */ String email) {}
