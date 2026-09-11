package ai.forge.server.release.infrastructure;

import ai.forge.server.release.application.DeploymentStore;
import ai.forge.server.release.application.DeploymentView;
import ai.forge.server.release.domain.SimulatedDeploymentExecutor;
import ai.forge.server.release.domain.SimulatedDeploymentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test-unit")
public class SimulatedDeploymentWorker {

    /* 待执行部署、Release 与 Requirement 的本地事务端口。 */
    private final DeploymentStore deployments;
    /* 无外部系统副作用的确定性模拟器。 */
    private final SimulatedDeploymentExecutor executor;

    public SimulatedDeploymentWorker(DeploymentStore deployments) {
        this.deployments = deployments;
        this.executor = new SimulatedDeploymentExecutor();
    }

    @Scheduled(fixedDelayString = "${forge.release.simulated-worker-delay-ms:1000}")
    public void poll() {
        deployments.approved().forEach(this::execute);
    }

    @Transactional
    public void execute(DeploymentView deployment) {
        if (!deployments.claim(deployment.id(), deployment.version())) {
            return;
        }
        deployments.updateReleaseStatus(deployment.organizationId(),
                deployment.releaseId(), "DEPLOYING");
        SimulatedDeploymentResult result = executor.execute(deployment.simulateFailure());
        deployments.complete(deployment.id(), result.succeeded(), result.code(), result.summary());
        deployments.updateReleaseStatus(deployment.organizationId(), deployment.releaseId(),
                result.succeeded() ? "RELEASED" : "FAILED");
        if (result.succeeded()) {
            deployments.completeRequirements(deployment.organizationId(),
                    deployment.releaseId(), deployment.requestedBy(), deployment.id());
        }
        deployments.audit(deployment.organizationId(), "SYSTEM", deployment.requestedBy(),
                "deployment.simulated.completed", deployment.id(), result.code(), "deployment:" + deployment.id(),
                null);
    }
}
