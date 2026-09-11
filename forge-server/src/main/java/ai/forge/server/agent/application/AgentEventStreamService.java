package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentEvent;
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

@Service
@Profile("!test-unit")
public class AgentEventStreamService {

    /* SSE 写失败只结束连接，不改变 Run 权威状态。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentEventStreamService.class);

    /* 单次数据库补发的最大事件数量。 */
    private static final int REPLAY_BATCH_SIZE = 100;

    /* 没有新事件时的短轮询间隔。 */
    private static final long POLL_INTERVAL_MILLIS = 200;

    /* 连接最长空闲时间；客户端可从最后连续序号重连。 */
    private static final long STREAM_TIMEOUT_MILLIS = 30_000;

    /* 提供 scoped 快照和 MySQL 持久事件查询。 */
    private final AgentRunService runService;

    /* 在请求线程之外执行可取消的连接循环。 */
    private final TaskExecutor taskExecutor;

    public AgentEventStreamService(
            AgentRunService runService,
            /* 显式选择应用执行器，避免启用调度后出现多个 TaskExecutor 候选。 */
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.runService = runService;
        this.taskExecutor = taskExecutor;
    }

    public SseEmitter stream(
            long userId, long organizationId, String runId, long afterSequence) {
        AgentRunSnapshot initial = runService.get(userId, organizationId, runId);
        if (afterSequence < 0 || afterSequence > initial.lastSequence()) {
            throw new IllegalArgumentException("afterSequence is outside the persisted Run sequence");
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        taskExecutor.execute(() -> pump(
                emitter, closed, userId, organizationId, runId, afterSequence));
        return emitter;
    }

    private void pump(
            SseEmitter emitter,
            AtomicBoolean closed,
            long userId,
            long organizationId,
            String runId,
            long afterSequence) {
        long cursor = afterSequence;
        try {
            while (!closed.get()) {
                List<AgentEvent> events = runService.eventsAfter(
                        userId, organizationId, runId, cursor, REPLAY_BATCH_SIZE);
                for (AgentEvent event : events) {
                    emitter.send(SseEmitter.event()
                            .id(Long.toString(event.sequence()))
                            .name(event.type())
                            .data(event));
                    cursor = event.sequence();
                }
                AgentRunSnapshot snapshot = runService.get(userId, organizationId, runId);
                if (snapshot.terminal() && cursor >= snapshot.lastSequence()) {
                    emitter.complete();
                    return;
                }
                if (events.isEmpty()) {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(Map.of("runId", runId, "timestamp", Instant.now().toString())));
                    Thread.sleep(POLL_INTERVAL_MILLIS);
                }
            }
        } catch (IOException | IllegalStateException exception) {
            LOGGER.debug("Agent SSE connection closed: runId={}", runId, exception);
            emitter.complete();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException exception) {
            LOGGER.warn("Agent SSE replay failed: runId={}", runId, exception);
            emitter.completeWithError(exception);
        }
    }
}
