package ai.forge.server.document.application;

import java.util.List;

/** 文档向量索引端口；实现必须保证按稳定切片 ID 幂等写入。 */
public interface VectorIndexClient {

    /* 确保集合存在；集合名由 embedding 模型版本派生，创建参数必须携带维度。 */
    void ensureCollection(String collectionName, int dimension);

    /* 以稳定切片 ID 覆盖写入一批切片；重复投递不得产生重复数据。 */
    void upsertChunks(String collectionName, List<ChunkPoint> points);

    /* 删除同一文档中除保留版本外的全部切片；用于版本切换后的旧版本失效。 */
    void deleteStaleVersions(String collectionName, long documentId, long keepVersionId);

    /* 统计指定文档版本的切片数量；用于索引后校验。 */
    long countVersionChunks(String collectionName, long documentId, long versionId);

    /* 在强制过滤器约束下做稠密检索；过滤器由服务端构造，调用方不可移除。 */
    List<ScoredChunk> search(String collectionName, float[] queryVector, SearchFilter filter, int limit);

    /** 待写入向量索引的一个切片点。 */
    record ChunkPoint(
            /* 切片向量；与集合维度一致。 */ float[] vector,
            /* 切片完整载荷。 */ IndexedChunkPayload payload) {}

    /** 服务端构造的强制检索过滤器。 */
    record SearchFilter(
            /* 公司强制范围；来自服务端会话或 Run 上下文，不是 Agent 输入。 */ long organizationId,
            /* 可选文档类型收窄；Agent 允许提供的唯一过滤维度之一。 */ String documentType) {}

    /** 检索命中的切片及其相似度得分。 */
    record ScoredChunk(
            /* 相似度得分；用于排序与调试，不作为权限依据。 */ float score,
            /* 命中切片的完整载荷。 */ IndexedChunkPayload payload) {}
}
