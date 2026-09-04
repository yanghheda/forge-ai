package ai.forge.server.document.domain;

/** 文档纯文本的一个检索切片；索引稳定 ID 由 documentId、versionId 与 chunkIndex 组成。 */
public record DocumentChunk(
        /* 切片在版本内的顺序号；从 0 开始且连续。 */ int chunkIndex,
        /* 切片规范化文本；相邻切片之间有受控重叠。 */ String text) {}
