package ai.forge.server.quality;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaMemberCommentCheckerTest {

    private final JavaMemberCommentChecker checker = new JavaMemberCommentChecker();

    @Test
    void acceptsIndividuallyDocumentedFieldsRecordComponentsAndEnumMembers() throws Exception {
        assertThat(checker.check(fixture("member-comments/valid"))).isEmpty();
    }

    @Test
    void rejectsEveryRequiredNegativeShape() throws Exception {
        List<JavaMemberCommentChecker.Violation> violations =
                checker.check(fixture("member-comments/invalid"));

        assertThat(violations)
                .extracting(JavaMemberCommentChecker.Violation::member)
                .contains(
                        "missingField",
                        "javadocField",
                        "englishOnlyField",
                        "separatedField",
                        "firstGroupedField",
                        "secondGroupedField",
                        "missingComponent",
                        "SECOND",
                        "persistedCode");
        assertThat(violations)
                .extracting(JavaMemberCommentChecker.Violation::reason)
                .contains(
                        "缺少紧邻声明的普通块注释",
                        "Javadoc 不能替代成员普通块注释",
                        "普通块注释必须包含中文语义说明",
                        "普通块注释与声明之间不能存在空行",
                        "一个声明包含多个字段，无法为每个字段提供独立注释");
    }

    private Path fixture(String name) throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource(name).toURI());
    }
}
