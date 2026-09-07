package ai.forge.server.release.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimulatedDeploymentExecutorTest {

    private final SimulatedDeploymentExecutor executor = new SimulatedDeploymentExecutor();

    @Test
    void alwaysLabelsSuccessAsSimulated() {
        SimulatedDeploymentResult result = executor.execute(false);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.code()).isEqualTo("SIMULATED_SUCCESS");
        assertThat(result.summary()).contains("no production environment was changed");
    }

    @Test
    void exposesDeterministicFailureWithoutClaimingProductionExecution() {
        SimulatedDeploymentResult result = executor.execute(true);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.code()).isEqualTo("SIMULATED_FAILURE");
        assertThat(result.summary()).contains("no production environment was changed");
    }
}
