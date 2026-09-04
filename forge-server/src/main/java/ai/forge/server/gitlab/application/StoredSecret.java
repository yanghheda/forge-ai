package ai.forge.server.gitlab.application;

public record StoredSecret(
        /* 凭据的内部数据库标识。 */ long id,
        /* 凭据所属工作区。 */ long workspaceId,
        /* 凭据用途类型。 */ String type,
        /* 加密后的凭据材料。 */ EncryptedSecret encrypted) {}
