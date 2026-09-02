package fixture.invalid;

public class InvalidMembers {
    private String missingField;

    /** 这是 Javadoc，不能替代普通块注释。 */
    private String javadocField;

    /* English-only text is not enough. */
    private String englishOnlyField;

    /* 注释与声明之间存在空行也不算紧邻。 */

    private String separatedField;

    /* 一条注释不能同时解释两个字段。 */
    private String firstGroupedField, secondGroupedField;
}

record InvalidRecord(
        /* 已正确说明的组件。 */
        long documentedComponent,
        String missingComponent) {}

enum InvalidStatus {
    /* 第一个状态具有独立说明。 */
    FIRST,
    SECOND;

    private final String persistedCode = "invalid";
}
