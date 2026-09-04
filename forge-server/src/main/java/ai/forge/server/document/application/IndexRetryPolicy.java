package ai.forge.server.document.application;

import java.time.Duration;

/** 索引任务的退避重试策略；由部署配置构造，测试可注入更激进的参数。 */
public record IndexRetryPolicy(
        /* 自动重试上限；达到后任务转 DEAD 等待人工处理。 */ int maxAttempts,
        /* 指数退避基数；第 n 次失败后等待 base * 2^(n-1)。 */ Duration backoffBase) {

    /* 计算第 givenAttempts 次失败后的退避时长；封顶 1 小时防止长期积压。 */
    public Duration backoffAfter(int givenAttempts) {
        long cappedExponent = Math.min(givenAttempts - 1, 12);
        long millis = backoffBase.toMillis() * (1L << cappedExponent);
        return Duration.ofMillis(Math.min(millis, Duration.ofHours(1).toMillis()));
    }
}
