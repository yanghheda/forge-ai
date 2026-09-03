package ai.forge.server.workitem.domain;

import java.util.List;

public record GuardResult(
        /* Guard 未满足的机器可读条件；空列表表示允许转换。 */
        List<String> missing) {

    public GuardResult {
        missing = List.copyOf(missing);
    }

    public static GuardResult allowed() {
        return new GuardResult(List.of());
    }

    public boolean allowedTransition() {
        return missing.isEmpty();
    }
}
