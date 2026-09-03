package ai.forge.server.workitem.domain;

import java.util.List;

public class WorkflowGuardFailedException extends RuntimeException {

    /* 未满足条件的稳定机器编码，供客户端逐项引导用户补齐。 */
    private final List<String> missing;

    public WorkflowGuardFailedException(List<String> missing) {
        super("Workflow guard failed");
        this.missing = List.copyOf(missing);
    }

    public List<String> missing() {
        return missing;
    }
}
