package ai.forge.server.release.infrastructure.persistence;

import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.common.domain.VersionConflictException;
import ai.forge.server.release.application.PrecheckSnapshot;
import ai.forge.server.release.application.ReleaseStore;
import ai.forge.server.release.application.ReleaseView;
import ai.forge.server.release.domain.PrecheckDecision;
import ai.forge.server.release.domain.PrecheckFacts;
import ai.forge.server.release.domain.PrecheckResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!test-unit")
public class MybatisReleaseStore implements ReleaseStore {

    /* 执行所有显式携带公司作用域的发布 SQL。 */
    private final ReleaseMapper mapper;
    /* 序列化策略、规则结果与资源版本快照。 */
    private final ObjectMapper objectMapper;

    public MybatisReleaseStore(ReleaseMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ReleaseView create(long organizationId, long userId, String versionName,
            String environment, List<Long> itemIds, long approvalTtlMinutes) {
        List<Long> scoped = mapper.findScopedRequirementIds(organizationId, itemIds);
        if (scoped.size() != itemIds.size()) {
            throw new ResourceNotFoundException();
        }
        Map<String, Object> generated = new LinkedHashMap<>();
        mapper.insert(generated, organizationId, versionName, environment,
                write(Map.of("approvalTtlMinutes", approvalTtlMinutes)), userId);
        long releaseId = number(generated.get("id"));
        scoped.forEach(itemId -> mapper.insertItem(organizationId, releaseId, itemId));
        return find(organizationId, releaseId).orElseThrow();
    }

    @Override
    public Optional<ReleaseView> find(long organizationId, long releaseId) {
        return mapper.find(organizationId, releaseId).stream().findFirst()
                .map(row -> view(organizationId, row));
    }

    @Override
    public List<ReleaseView> list(long organizationId) {
        return mapper.list(organizationId).stream()
                .map(row -> view(organizationId, row)).toList();
    }

    @Override
    public ReleaseView updateNote(long organizationId, long releaseId, String note,
            long expectedVersion) {
        if (mapper.updateNote(organizationId, releaseId, note, expectedVersion) != 1) {
            throw new VersionConflictException();
        }
        return find(organizationId, releaseId).orElseThrow();
    }

    @Override
    public PrecheckFacts loadFacts(long organizationId, long releaseId) {
        Map<String, Object> release = mapper.find(organizationId, releaseId).stream()
                .findFirst().orElseThrow(ResourceNotFoundException::new);
        List<PrecheckFacts.Item> items = mapper.findItemFacts(organizationId, releaseId).stream()
                .map(row -> new PrecheckFacts.Item(text(row.get("item_key")), text(row.get("status")),
                        number(row.get("version"))))
                .toList();
        List<PrecheckFacts.Pipeline> pipelines = mapper.findPipelineFacts(organizationId, releaseId).stream()
                .map(row -> new PrecheckFacts.Pipeline(text(row.get("item_key")), text(row.get("mr_ref")),
                        text(row.get("head_sha")), text(row.get("pipeline_sha")),
                        text(row.get("pipeline_status")), number(row.get("pipeline_version"))))
                .toList();
        List<PrecheckFacts.QaRun> runs = mapper.findQaFacts(organizationId, releaseId).stream()
                .map(row -> new PrecheckFacts.QaRun(text(row.get("item_key")), number(row.get("run_id")),
                        text(row.get("run_status")), integer(row.get("passed")), integer(row.get("failed")),
                        integer(row.get("blocked")), number(row.get("run_version"))))
                .toList();
        List<PrecheckFacts.Bug> bugs = mapper.findBugFacts(organizationId, releaseId).stream()
                .map(row -> new PrecheckFacts.Bug(text(row.get("item_key")), text(row.get("severity")),
                        text(row.get("status")), number(row.get("version"))))
                .toList();
        Map<String, Object> policy = read(text(release.get("policy_snapshot_json")), new TypeReference<>() {});
        long ttl = number(policy.getOrDefault("approvalTtlMinutes", 0));
        return new PrecheckFacts(number(release.get("version")), items, pipelines, runs, bugs,
                !text(release.get("release_note")).isBlank(), mapper.approverAvailable(organizationId), ttl,
                Optional.ofNullable(mapper.policyVersion(organizationId)).orElse(0L));
    }

    @Override
    public PrecheckSnapshot appendPrecheck(long organizationId, long releaseId, long checkedById,
            String checkedByType, PrecheckDecision decision, PrecheckFacts facts) {
        Map<String, Long> versions = versions(facts);
        Map<String, Object> generated = new LinkedHashMap<>();
        mapper.insertPrecheck(generated, organizationId, releaseId, decision.passed() ? "PASS" : "FAIL",
                write(decision.checks()), checkedByType, checkedById, write(versions));
        mapper.markPrechecked(organizationId, releaseId);
        return latest(organizationId, releaseId, versions).orElseThrow();
    }

    private ReleaseView view(long organizationId, Map<String, Object> row) {
        long releaseId = number(row.get("id"));
        Map<String, Long> currentVersions = versions(loadFacts(organizationId, releaseId));
        PrecheckSnapshot latest = latest(organizationId, releaseId, currentVersions).orElse(null);
        return new ReleaseView(releaseId, organizationId, text(row.get("version_name")),
                text(row.get("environment")), text(row.get("status")), text(row.get("release_note")),
                mapper.findItemIds(organizationId, releaseId), latest, number(row.get("version")),
                instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private Optional<PrecheckSnapshot> latest(long organizationId, long releaseId,
            Map<String, Long> currentVersions) {
        return mapper.findLatestPrecheck(organizationId, releaseId).stream().findFirst().map(row -> {
            Map<String, Long> stored = read(text(row.get("resource_versions_json")), new TypeReference<>() {});
            List<PrecheckResult> checks = read(text(row.get("checks_json")), new TypeReference<>() {});
            return new PrecheckSnapshot(number(row.get("id")), text(row.get("status")), checks, stored,
                    text(row.get("checked_by_type")), number(row.get("checked_by_id")),
                    instant(row.get("checked_at")), stored.equals(currentVersions));
        });
    }

    private Map<String, Long> versions(PrecheckFacts facts) {
        Map<String, Long> versions = new LinkedHashMap<>();
        versions.put("release", facts.releaseVersion());
        versions.put("policy", facts.policyVersion());
        facts.items().forEach(item -> versions.put("item:" + item.key(), item.version()));
        facts.pipelines().forEach(pipeline -> versions.put(
                "pipeline:" + pipeline.itemKey() + ":" + pipeline.mergeRequestRef(), pipeline.version()));
        facts.qaRuns().forEach(run -> versions.put("testRun:" + run.itemKey(), run.version()));
        facts.bugs().forEach(bug -> versions.put("bug:" + bug.key(), bug.version()));
        return versions;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
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
        if (value == null) {
            return 0;
        }
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private static int integer(Object value) {
        return Math.toIntExact(number(value));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static java.time.Instant instant(Object value) {
        return ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
    }
}
