package ai.forge.server.workitem.application;

import java.util.List;

public record RequirementMemberView(
        /* 可被关联成员的稳定用户标识。 */ long userId,
        /* 成员在人员选择器中的展示名称。 */ String displayName,
        /* 成员的联系邮箱。 */ String email,
        /* 成员在当前组织持有的业务或管理角色。 */ List<String> roles) {}
