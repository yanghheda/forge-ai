package ai.forge.server.document.application;

/** 检索返回的单个片段；引用版本 ID 保证文档更新后引用不失真。 */
public record SearchChunk(
        /* 命中文档标识。 */ long documentId,
        /* 命中切片所属不可变版本标识；最终回答必须引用它。 */ long versionId,
        /* 切片顺序号；用于回溯定位。 */ int chunkIndex,
        /* 文档标题快照。 */ String title,
        /* 文档业务类型。 */ String documentType,
        /* 可选关联工作项。 */ Long workItemId,
        /* 相似度得分；不作为权限或事实依据。 */ float score,
        /* 切片文本本体。 */ String text) {}
