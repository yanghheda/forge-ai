package ai.forge.server.organization.domain;

import java.time.Instant;
import java.util.List;

public record OrganizationMember(
        /* 成员对应的用户标识。 */ long userId,
        /* 成员展示名称。 */ String displayName,
        /* 成员联系与登录邮箱。 */ String email,
        /* 成员审核和启停状态。 */ String status,
        /* 成员拥有的公司级角色代码。 */ List<String> roles,
        /* 最近一次成功登录时间；从未登录时为空。 */ Instant lastLoginAt,
        /* 成员关系乐观锁版本。 */ long version) {}
