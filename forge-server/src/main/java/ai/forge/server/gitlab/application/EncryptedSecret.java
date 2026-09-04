package ai.forge.server.gitlab.application;

public record EncryptedSecret(
        /* 使用当前数据密钥加密并包含 GCM 认证标签的 Base64 密文。 */ String ciphertext,
        /* 每次加密随机生成且可公开存储的 96 位 Base64 IV。 */ String iv,
        /* 标识解密所需部署主密钥版本的正整数。 */ int keyVersion,
        /* 使用主密钥 HMAC 生成、仅供管理员识别轮换结果的短指纹。 */ String fingerprint) {}
