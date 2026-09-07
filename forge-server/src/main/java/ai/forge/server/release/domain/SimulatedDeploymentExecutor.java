package ai.forge.server.release.domain;

public class SimulatedDeploymentExecutor {

    public SimulatedDeploymentResult execute(boolean simulateFailure) {
        if (simulateFailure) {
            return new SimulatedDeploymentResult(
                    false,
                    "SIMULATED_FAILURE",
                    "Simulated deployment failed; no production environment was changed.");
        }
        return new SimulatedDeploymentResult(
                true,
                "SIMULATED_SUCCESS",
                "Simulated deployment succeeded; no production environment was changed.");
    }
}
