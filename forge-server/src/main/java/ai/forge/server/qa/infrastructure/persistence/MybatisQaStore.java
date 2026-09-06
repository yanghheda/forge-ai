package ai.forge.server.qa.infrastructure.persistence;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.qa.application.QaStateException;
import ai.forge.server.qa.application.QaStore;
import ai.forge.server.qa.application.TestCaseView;
import ai.forge.server.qa.application.TestResultView;
import ai.forge.server.qa.application.TestRunView;
import ai.forge.server.qa.domain.QaGuardDecision;
import ai.forge.server.qa.domain.TestCasePriority;
import ai.forge.server.qa.domain.TestResultStatus;
import ai.forge.server.qa.domain.TestRunStatus;
import ai.forge.server.qa.domain.TestRunSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisQaStore implements QaStore {

    /* 执行显式租户范围 SQL 的 QA Mapper。 */
    private final QaMapper mapper;

    /* 序列化步骤、证据与完成统计 JSON。 */
    private final ObjectMapper objectMapper;

    public MybatisQaStore(QaMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public TestCaseView createCase(long workspaceId, long projectId, long requirementId, long userId,
            String title, String preconditions, List<String> steps, String expectedResult, TestCasePriority priority) {
        if (mapper.countQaRequirement(workspaceId, projectId, requirementId) == 0) {
            throw new ResourceNotFoundException();
        }
        Map<String, Object> generated = new LinkedHashMap<>();
        mapper.insertCase(generated, workspaceId, projectId, requirementId, title, preconditions,
                write(steps), expectedResult, priority.name(), userId);
        return new TestCaseView(number(generated.get("id")), requirementId, title, preconditions,
                List.copyOf(steps), expectedResult, priority, 0L);
    }

    @Override
    public List<TestCaseView> findCases(long workspaceId, long projectId, long requirementId) {
        return mapper.findCases(workspaceId, projectId, requirementId).stream().map(this::caseView).toList();
    }

    @Override
    @Transactional
    public TestRunView createRun(long workspaceId, long projectId, long requirementId, long userId,
            String environment) {
        if (mapper.countQaRequirement(workspaceId, projectId, requirementId) == 0) {
            throw new ResourceNotFoundException();
        }
        Map<String, Object> generated = new LinkedHashMap<>();
        mapper.insertRun(generated, workspaceId, projectId, requirementId, environment, userId);
        long runId = number(generated.get("id"));
        if (mapper.snapshotResults(workspaceId, projectId, requirementId, runId) == 0) {
            throw new QaStateException();
        }
        return findRun(workspaceId, projectId, runId).orElseThrow();
    }

    @Override
    public Optional<TestRunView> findRun(long workspaceId, long projectId, long runId) {
        return mapper.findRun(workspaceId, projectId, runId).stream().findFirst()
                .map(row -> runView(row, mapper.findResults(workspaceId, projectId, runId)));
    }

    @Override
    public Optional<TestRunView> findLatestRun(long workspaceId, long projectId, long requirementId) {
        return mapper.findLatestRun(workspaceId, projectId, requirementId).stream().findFirst()
                .map(row -> runView(row, mapper.findResults(workspaceId, projectId, number(row.get("id")))));
    }

    @Override
    @Transactional
    public TestResultView updateResult(long workspaceId, long projectId, long runId, long resultId, long userId,
            TestResultStatus status, String actualResult, List<String> evidence, long expectedVersion) {
        if (status == TestResultStatus.NOT_RUN) {
            throw new IllegalArgumentException("NOT_RUN cannot be submitted as an execution result");
        }
        if (mapper.updateResult(workspaceId, projectId, runId, resultId, userId, status.name(), actualResult,
                write(evidence), expectedVersion) == 0) {
            throw new VersionConflictException();
        }
        return mapper.findResults(workspaceId, projectId, runId).stream()
                .filter(row -> number(row.get("id")) == resultId)
                .findFirst()
                .map(this::resultView)
                .orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    @Transactional
    public TestRunView completeRun(long workspaceId, long projectId, long runId, long userId,
            long expectedVersion) {
        TestRunSummary summary = summary(mapper.calculateSummary(workspaceId, projectId, runId));
        if (summary.total() == 0 || summary.notRun() > 0) {
            throw new QaStateException();
        }
        if (mapper.completeRun(workspaceId, projectId, runId, write(summary), expectedVersion) != 1) {
            throw new VersionConflictException();
        }
        return findRun(workspaceId, projectId, runId).orElseThrow();
    }

    @Override
    @Transactional
    public TestRunView reopenRun(long workspaceId, long projectId, long runId, long userId, long expectedVersion,
            String reason, String requestId) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reopen reason is required");
        }
        if (mapper.reopenRun(workspaceId, projectId, runId, expectedVersion) != 1) {
            throw new VersionConflictException();
        }
        mapper.insertReopenAudit(workspaceId, projectId, runId, userId, reason.trim(), requestId);
        return findRun(workspaceId, projectId, runId).orElseThrow();
    }

    @Override
    public Optional<TestRunSummary> findLatestCompletedSummary(
            long workspaceId, long projectId, long requirementId) {
        return mapper.findLatestCompletedSummary(workspaceId, projectId, requirementId).stream()
                .filter(row -> "COMPLETED".equals(text(row.get("status"))) && row.get("summary_json") != null)
                .findFirst()
                .map(row -> read(text(row.get("summary_json")), TestRunSummary.class));
    }

    private TestCaseView caseView(Map<String, Object> row) {
        return new TestCaseView(number(row.get("id")), number(row.get("work_item_id")),
                text(row.get("title")), text(row.get("preconditions")),
                read(text(row.get("steps_json")), new TypeReference<>() {}), text(row.get("expected_result")),
                TestCasePriority.valueOf(text(row.get("priority"))), number(row.get("version")));
    }

    private TestResultView resultView(Map<String, Object> row) {
        return new TestResultView(number(row.get("id")), number(row.get("test_case_id")), text(row.get("title")),
                TestCasePriority.valueOf(text(row.get("priority"))),
                TestResultStatus.valueOf(text(row.get("status"))), text(row.get("actual_result")),
                read(text(row.get("evidence_json")), new TypeReference<>() {}), nullableNumber(row.get("executed_by")),
                (LocalDateTime) row.get("executed_at"), number(row.get("version")));
    }

    private TestRunView runView(Map<String, Object> row, List<Map<String, Object>> resultRows) {
        TestRunSummary storedSummary = row.get("summary_json") == null
                ? null
                : read(text(row.get("summary_json")), TestRunSummary.class);
        TestRunStatus status = TestRunStatus.valueOf(text(row.get("status")));
        QaGuardDecision decision = status == TestRunStatus.COMPLETED
                ? QaGuardDecision.from(storedSummary)
                : QaGuardDecision.noCompletedRun();
        return new TestRunView(number(row.get("id")), number(row.get("requirement_id")),
                text(row.get("environment")), status, storedSummary, decision,
                resultRows.stream().map(this::resultView).toList(), (LocalDateTime) row.get("started_at"),
                (LocalDateTime) row.get("finished_at"), number(row.get("version")));
    }

    private TestRunSummary summary(Map<String, Object> row) {
        return new TestRunSummary(integer(row.get("total")), integer(row.get("passed")),
                integer(row.get("failed")), integer(row.get("blocked")), integer(row.get("skipped")),
                integer(row.get("not_run")), integer(row.get("mandatory_skipped")));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static Long nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private static int integer(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
