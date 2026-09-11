package ai.forge.server.document.application;

/** 单个文档切片在向量索引中的完整载荷；字段构成 Qdrant payload 的事实契约。 */
public record IndexedChunkPayload(
        /* 所属工作区；检索时服务端强制过滤，不允许调用方移除。 */ long organizationId,
        /* 可选关联工作项；为空表示切片不挂接具体条目。 */ Long workItemId,
        /* 所属文档标识。 */ long documentId,
        /* 切片所属的不可变文档版本标识；引用与失效都以它为准。 */ long versionId,
        /* 文档业务类型；如 PRD、UX_SPEC。 */ String documentType,
        /* 文档可见性；当前固定为公司范围。 */ String visibility,
        /* 版本规范化纯文本的 SHA-256；用于派生索引重建时去重。 */ String contentHash,
        /* 切片在版本内的顺序号。 */ int chunkIndex,
        /* 文档标题快照；文档无改名通道，载荷与事实一致。 */ String title,
        /* 切片文本本体；检索返回时不回源 MySQL。 */ String text) {}
