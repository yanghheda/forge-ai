package ai.forge.server.release.application;

import ai.forge.server.release.domain.PrecheckResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PrecheckSnapshot(
        /* 不可变快照标识。 */ long id,
        /* 六项规则聚合状态：PASS 或 FAIL。 */ String status,
        /* 后端按固定规则计算的逐项结果。 */ List<PrecheckResult> checks,
        /* 检查时关键资源的版本映射。 */ Map<String, Long> resourceVersions,
        /* 发起检查的主体类型。 */ String checkedByType,
        /* 发起检查的主体标识。 */ long checkedById,
        /* 快照写入时间。 */ Instant checkedAt,
        /* 当前事实是否仍与快照版本一致。 */ boolean current) {

    public PrecheckSnapshot {
        checks = List.copyOf(checks);
        resourceVersions = Map.copyOf(resourceVersions);
    }
}
