package ai.forge.server.quality;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionJavaMemberCommentTest {

    @Test
    void allProductionMembersHaveIndividualChineseBlockComments() throws Exception {
        var violations = new JavaMemberCommentChecker().check(Path.of("src/main/java"));

        assertThat(violations)
                .as("生产 Java 成员必须具有紧邻声明的中文普通块注释")
                .isEmpty();
    }
}
