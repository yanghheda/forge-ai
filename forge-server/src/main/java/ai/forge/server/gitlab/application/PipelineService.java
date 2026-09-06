package ai.forge.server.gitlab.application;

import ai.forge.server.authorization.application.PermissionEvaluator;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.gitlab.domain.PipelineRun;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test-unit")
public class PipelineService {

    /* 常见 GitLab 凭据头、Bearer 与个人访问令牌；返回任何调用方前统一清洗。 */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(PRIVATE-TOKEN\\s*[:=]\\s*)\\S+|(Authorization\\s*[:=]\\s*Bearer\\s+)\\S+|glpat-[A-Za-z0-9_-]{8,}");

    /* 所有触发与读取操作的最终项目授权边界。 */
    private final PermissionEvaluator permissions;

    /* Pipeline 上下文和本地事实快照的短事务端口。 */
    private final PipelineStore store;

    /* GitLab Pipeline 与日志调用的外部系统端口。 */
    private final SourceControlProvider sourceControl;

    /* 单次日志响应允许返回给 UI 的最大 UTF-8 字节数。 */
    private final int maxLogBytes;

    public PipelineService(
            PermissionEvaluator permissions,
            PipelineStore store,
            SourceControlProvider sourceControl,
            @Value("${forge.gitlab.max-log-tail-bytes:65536}") int maxLogBytes) {
        this.permissions = permissions;
        this.store = store;
        this.sourceControl = sourceControl;
        this.maxLogBytes = maxLogBytes;
    }

    public PipelineRun trigger(long userId, long workspaceId, long projectId, String ref) {
        permissions.requireProject(userId, workspaceId, projectId, "repo.write");
        PipelineContext context = store.loadContext(workspaceId, projectId);
        PipelineRun remote = sourceControl.triggerPipeline(context, requireRef(ref));
        return store.save(remote);
    }

    public List<PipelineRun> list(long userId, long workspaceId, long projectId) {
        permissions.requireProject(userId, workspaceId, projectId, "repo.read");
        return store.list(workspaceId, projectId, 50);
    }

    public PipelineLogTail logTail(
            long userId, long workspaceId, long projectId, long pipelineId, long jobId) {
        permissions.requireProject(userId, workspaceId, projectId, "repo.read");
        PipelineRun pipeline = store.find(workspaceId, projectId, pipelineId)
                .orElseThrow(ResourceNotFoundException::new);
        PipelineContext context = store.loadContext(workspaceId, projectId);
        byte[] remote = sourceControl.getJobLog(context, pipeline.remotePipelineId(), jobId, maxLogBytes + 1);
        boolean truncated = remote.length > maxLogBytes;
        int start = Math.max(0, remote.length - maxLogBytes);
        String raw = new String(remote, start, remote.length - start, StandardCharsets.UTF_8);
        String sanitized = redactSecrets(raw);
        return new PipelineLogTail(pipeline.id(), jobId, sanitized, truncated, !sanitized.equals(raw));
    }

    private static String requireRef(String ref) {
        if (ref == null || ref.isBlank() || ref.length() > 255) {
            throw new IllegalArgumentException("invalid pipeline ref");
        }
        return ref.trim();
    }

    private static String redactSecrets(String content) {
        return SECRET_PATTERN.matcher(content).replaceAll(match -> {
            if (match.group(1) != null) {
                return match.group(1) + "[REDACTED]";
            }
            if (match.group(2) != null) {
                return match.group(2) + "[REDACTED]";
            }
            return "[REDACTED]";
        });
    }

    public record PipelineLogTail(
            /* 本地 Pipeline 快照标识。 */ long pipelineId,
            /* GitLab Pipeline Job 标识。 */ long jobId,
            /* 最多为配置上限的日志尾部文本。 */ String content,
            /* 远端日志是否因本地上限被截断。 */ boolean truncated,
            /* 返回内容是否命中过凭据清洗规则。 */ boolean redacted) {}
}
