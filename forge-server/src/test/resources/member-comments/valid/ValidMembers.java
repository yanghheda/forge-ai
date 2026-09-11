package fixture.valid;

public class ValidMembers {
    /* 所属公司，用于服务端租户范围校验。 */
    private long organizationId;
}

interface ValidContract {
    /* 默认页容量，用于限制单次查询返回规模。 */
    int DEFAULT_PAGE_SIZE = 20;
}

record ValidRecord(
        /* 资源唯一标识，用于定位当前响应对象。 */
        long id,
        /* 资源标题，用于调用方展示。 */
        String title) {}

enum ValidStatus {
    /* 草稿状态，允许继续修改。 */
    DRAFT("draft"),
    /* 完成状态，表示流程已经结束。 */
    COMPLETED("completed");

    /* 持久化编码，写入数据库时保持向后兼容。 */
    private final String persistedCode;

    ValidStatus(String persistedCode) {
        this.persistedCode = persistedCode;
    }
}
