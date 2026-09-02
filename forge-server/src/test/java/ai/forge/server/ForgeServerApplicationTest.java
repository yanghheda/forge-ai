package ai.forge.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test-unit")
class ForgeServerApplicationTest {

    @Test
    void contextLoads() {
        // Spring 上下文能够启动即证明最小应用装配成立。
    }
}
