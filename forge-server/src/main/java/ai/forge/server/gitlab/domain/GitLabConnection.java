package ai.forge.server.gitlab.domain;

import java.time.LocalDateTime;

public record GitLabConnection(
        /* 连接的本地稳定标识。 */ long id,
        /* 连接所属工作区。 */ long organizationId,
        /* 管理员可识别的连接名称。 */ String name,
        /* 已规范化并通过 SSRF 校验的 GitLab 根地址。 */ String baseUrl,
        /* 不可逆的当前 Token 短指纹。 */ String tokenFingerprint,
        /* 最近测试形成的连接状态。 */ String status,
        /* 最近测试完成时间；未测试时为空。 */ LocalDateTime lastTestedAt,
        /* 乐观锁版本。 */ long version) {}
