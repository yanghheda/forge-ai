package ai.forge.server.document.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 可读的不可变文档版本。 */
public record DocumentVersion(
        /* 版本稳定标识。 */ long id,
        /* 版本所属文档。 */ long documentId,
        /* 文档内单调版本号。 */ long versionNo,
        /* ProseMirror JSON 正文。 */ JsonNode content,
        /* 规范化纯文本摘要来源。 */ String plainText,
        /* 规范化正文的 SHA-256。 */ String contentHash,
        /* 创建人。 */ long createdBy,
        /* 创建时间。 */ Instant createdAt) {}
