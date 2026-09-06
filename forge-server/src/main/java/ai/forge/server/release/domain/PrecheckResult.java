package ai.forge.server.release.domain;

import java.util.List;

public record PrecheckResult(
        /* 本项规则的稳定标识。 */ PrecheckRule rule,
        /* 后端依据事实计算出的通过结论。 */ boolean passed,
        /* 失败时可定位资源的稳定引用；通过时为空。 */ List<String> details) {

    public PrecheckResult {
        details = List.copyOf(details);
    }
}
