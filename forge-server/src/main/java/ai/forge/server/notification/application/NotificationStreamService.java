package ai.forge.server.notification.application;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/*
 * 用户级通知事件流：从 MySQL 按游标补发本人本公司通知并持续轮询；写失败只结束连接，
 * 不改变任何业务状态。该流没有终态，仅在超时、客户端断开或发生异常时结束。
 */
@Service
@Profile("!test-unit")
public class NotificationStreamService {

    /* SSE 写失败只结束连接，不改变通知权威状态。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationStreamService.class);

    /* 单次数据库补发的最大通知数量。 */
    private static final int REPLAY_BATCH_SIZE = 100;

    /* 没有新通知时的短轮询间隔。 */
    private static final long POLL_INTERVAL_MILLIS = 2000;

    /* 空闲时发送心跳事件的最小间隔，用于保活并让客户端感知连接仍在。 */
    private static final long HEARTBEAT_INTERVAL_MILLIS = 15_000;

    /* 连接最长空闲时间；客户端可从最后连续事件 id 重连续传。 */
    private static final long STREAM_TIMEOUT_MILLIS = 120_000;

    /* 提供按游标读取本人通知的只读查询。 */
    private final NotificationService notificationService;

    /* 在请求线程之外执行可取消的轮询循环。 */
    private final TaskExecutor taskExecutor;

    public NotificationStreamService(
            NotificationService notificationService,
            /* 显式选择应用执行器，避免启用调度后出现多个 TaskExecutor 候选。 */
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.notificationService = notificationService;
        this.taskExecutor = taskExecutor;
    }

    /* 建立本人本公司的 SSE 连接；afterId 为补发游标，负数视为非法游标。 */
    public SseEmitter stream(long userId, long organizationId, long afterId) {
        if (afterId < 0) {
            throw new IllegalArgumentException("afterId must not be negative");
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        taskExecutor.execute(() -> pump(emitter, closed, userId, organizationId, afterId));
        return emitter;
    }

    private void pump(
            SseEmitter emitter,
            AtomicBoolean closed,
            long userId,
            long organizationId,
            long afterId) {
        long cursor = afterId;
        long lastHeartbeat = System.currentTimeMillis();
        try {
            while (!closed.get()) {
                List<NotificationView> events = notificationService.eventsAfter(
                        organizationId, userId, cursor, REPLAY_BATCH_SIZE);
                for (NotificationView event : events) {
                    emitter.send(SseEmitter.event()
                            .id(Long.toString(event.id()))
                            .name("notification")
                            .data(event));
                    cursor = event.id();
                }
                if (events.isEmpty()) {
                    long now = System.currentTimeMillis();
                    if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MILLIS) {
                        emitter.send(SseEmitter.event()
                                .name("heartbeat")
                                .data(Map.of("userId", userId, "timestamp", Instant.now().toString())));
                        lastHeartbeat = now;
                    }
                    Thread.sleep(POLL_INTERVAL_MILLIS);
                }
            }
        } catch (IOException | IllegalStateException exception) {
            LOGGER.debug("Notification SSE connection closed: userId={}", userId, exception);
            emitter.complete();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException exception) {
            LOGGER.warn("Notification SSE replay failed: userId={}", userId, exception);
            emitter.completeWithError(exception);
        }
    }
}
