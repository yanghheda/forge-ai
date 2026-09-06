package ai.forge.server.release.domain;

import java.util.List;

public record PrecheckDecision(
        /* 六项规则全部通过时为 true。 */ boolean passed,
        /* 按固定注册顺序返回的六项规则结果。 */ List<PrecheckResult> checks) {

    public PrecheckDecision {
        checks = List.copyOf(checks);
    }

    public PrecheckResult result(PrecheckRule rule) {
        return checks.stream().filter(result -> result.rule() == rule).findFirst().orElseThrow();
    }
}
