package ai.forge.server.qa.infrastructure.persistence;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.qa.application.BugStore;
import ai.forge.server.qa.application.BugView;
import ai.forge.server.qa.domain.BugAction;
import ai.forge.server.qa.domain.BugSeverity;
import ai.forge.server.workitem.domain.WorkItem;
import ai.forge.server.workitem.domain.WorkItemStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisBugStore implements BugStore {

    /* 执行显式公司作用域 SQL。 */
    private final BugMapper mapper;

    /* 序列化复现步骤与修复证据。 */
    private final ObjectMapper objectMapper;

    public MybatisBugStore(BugMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public BugView create(WorkItem item, BugSeverity severity, long requirementId, Long testRunId,
            Long testResultId, List<String> reproductionSteps, String expectedResult, String actualResult,
            Long devTaskId) {
        if (mapper.countRequirement(item.organizationId(), requirementId) != 1) {
            throw new ResourceNotFoundException();
        }
        if ((testRunId == null) != (testResultId == null)) {
            throw new IllegalArgumentException("testRunId and testResultId must be supplied together");
        }
        if (testResultId != null && mapper.countFailedResult(item.organizationId(), requirementId,
                testRunId, testResultId) != 1) {
            throw new IllegalArgumentException("bug source must be a FAIL or BLOCKED result in the requirement");
        }
        if (devTaskId != null && mapper.countDevTask(item.organizationId(), devTaskId) != 1) {
            throw new ResourceNotFoundException();
        }
        mapper.setSeverity(item.organizationId(), item.id(), severity.name());
        mapper.insertDetails(item.organizationId(), item.id(), requirementId, testRunId,
                testResultId, write(reproductionSteps), expectedResult, actualResult);
        mapper.insertRelation(item.organizationId(), item.id(), requirementId,
                "FOUND_IN", item.reporterUserId());
        if (devTaskId != null) {
            mapper.insertRelation(item.organizationId(), item.id(), devTaskId,
                    "FIXED_BY", item.reporterUserId());
        }
        return find(item.organizationId(), item.id()).orElseThrow();
    }

    @Override
    public Optional<BugView> find(long organizationId, long bugId) {
        return mapper.find(organizationId, bugId).stream().findFirst().map(this::view);
    }

    @Override
    @Transactional
    public BugView transition(long organizationId, long bugId, long userId, BugAction action,
            String toStatus, String reason, List<String> fixEvidence, long expectedVersion, String idempotencyKey) {
        BugView current = find(organizationId, bugId).orElseThrow(ResourceNotFoundException::new);
        if (mapper.transition(organizationId, bugId, current.status().name(), toStatus,
                expectedVersion) != 1) {
            throw new VersionConflictException();
        }
        if (action == BugAction.RESOLVE) {
            mapper.resolveDetails(organizationId, bugId, reason, write(fixEvidence));
        } else if (action == BugAction.VERIFY) {
            mapper.verifyDetails(organizationId, bugId, userId);
        } else if (action == BugAction.REOPEN) {
            mapper.reopenDetails(organizationId, bugId);
        }
        mapper.insertEvent(organizationId, bugId, action.name(), current.status().name(), toStatus,
                userId, reason, idempotencyKey);
        return find(organizationId, bugId).orElseThrow();
    }

    @Override
    public List<BugView> findByRequirement(long organizationId, long requirementId) {
        return mapper.findByRequirement(organizationId, requirementId).stream().map(this::view).toList();
    }

    private BugView view(Map<String, Object> row) {
        return new BugView(number(row.get("id")), text(row.get("item_key")), text(row.get("title")),
                WorkItemStatus.valueOf(text(row.get("status"))), BugSeverity.valueOf(text(row.get("severity"))),
                number(row.get("requirement_id")), nullableNumber(row.get("test_run_id")),
                nullableNumber(row.get("test_result_id")), readList(row.get("reproduction_steps_json")),
                text(row.get("expected_result")), text(row.get("actual_result")),
                nullableText(row.get("fix_note")), readList(row.get("fix_evidence_json")),
                number(row.get("version")));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<String> readList(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static Long nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private static String text(Object value) {
        return value.toString();
    }

    private static String nullableText(Object value) {
        return value == null ? null : value.toString();
    }
}
