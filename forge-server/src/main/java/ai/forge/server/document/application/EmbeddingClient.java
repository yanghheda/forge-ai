package ai.forge.server.document.application;

/** 文本向量化端口；Fake 实现用于本地与测试，HTTP 实现对接真实模型服务。 */
public interface EmbeddingClient {

    /* 向量维度；必须与 Qdrant Collection 创建参数一致。 */
    int dimension();

    /* 模型版本标识；用于派生 Qdrant Collection 名称，切换模型需重建集合。 */
    String modelVersion();

    /* 将文本转为单位长度向量；相同输入必须产生相同输出。 */
    float[] embed(String text);
}
