package ai.forge.server.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.agent.domain.AgentSkill;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ToolContractRegistryTest {

    /* 加载 classpath 中的仓库契约；构造失败即启动门禁失败。 */
    private final ToolContractRegistry registry = new ToolContractRegistry();

    @Test
    void firstBatchToolsAreRegisteredWithContractMetadata() {
        Optional<ToolContract> getProject = registry.findTool("get_project");
        Optional<ToolContract> createUxTask = registry.findTool("create_ux_task");

        assertThat(getProject).isPresent();
        assertThat(getProject.get().riskLevel()).isEqualTo("LOW");
        assertThat(getProject.get().requiredPermission()).isEqualTo("project.read");
        assertThat(getProject.get().idempotencyRequired()).isFalse();
        assertThat(createUxTask).isPresent();
        assertThat(createUxTask.get().riskLevel()).isEqualTo("MEDIUM");
        assertThat(createUxTask.get().requiredPermission()).isEqualTo("ux.create");
        assertThat(createUxTask.get().idempotencyRequired()).isTrue();
    }

    @Test
    void inputSchemaRejectsScopeFieldsByContract() {
        ToolContract getWorkItem = registry.findTool("get_work_item").orElseThrow();

        assertThat(getWorkItem.inputSchema())
                .containsEntry("additionalProperties", false);
        assertThat(getWorkItem.inputSchema().get("properties"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .doesNotContainKeys("workspaceId", "projectId");
    }

    @Test
    void effectiveToolNamesMatchSkillAllowlist() {
        List<String> product = registry.effectiveToolNames(AgentSkill.PRODUCT);
        List<String> ux = registry.effectiveToolNames(AgentSkill.UX);

        assertThat(product).containsExactly(
                "get_project", "get_work_item", "get_delivery_graph", "search_documents",
                "create_requirement", "create_prd_document");
        assertThat(ux).containsExactly(
                "get_project", "get_work_item", "get_delivery_graph", "search_documents",
                "create_ux_task", "create_ux_document");
    }

    @Test
    void unknownToolAndSkillAreAbsent() {
        assertThat(registry.findTool("deploy_release")).isEmpty();
        assertThat(registry.findSkill("developer")).isEmpty();
    }
}
