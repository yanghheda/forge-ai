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
        Optional<ToolContract> getProject = registry.findTool("get_organization");
        Optional<ToolContract> createUxTask = registry.findTool("create_ux_task");

        assertThat(getProject).isPresent();
        assertThat(getProject.get().riskLevel()).isEqualTo("LOW");
        assertThat(getProject.get().requiredPermission()).isEqualTo("requirement.read");
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
                .doesNotContainKeys("organizationId", "organizationId");
    }

    @Test
    void effectiveToolNamesMatchSkillAllowlist() {
        List<String> product = registry.effectiveToolNames(AgentSkill.PRODUCT);
        List<String> ux = registry.effectiveToolNames(AgentSkill.UX);
        List<String> developer = registry.effectiveToolNames(AgentSkill.DEVELOPER);
        List<String> qa = registry.effectiveToolNames(AgentSkill.QA);
        List<String> release = registry.effectiveToolNames(AgentSkill.RELEASE);

        assertThat(product).containsExactly(
                "get_organization", "get_work_item", "list_requirements", "list_tasks", "list_bugs",
                "list_members", "get_delivery_graph", "search_documents",
                "create_requirement", "create_prd_document", "publish_document", "update_requirement",
                "update_requirement_details", "delete_requirement",
                "assign_executors", "assign_participants", "advance_requirement");
        assertThat(ux).containsExactly(
                "get_organization", "get_work_item", "list_requirements", "list_tasks",
                "list_members", "get_delivery_graph", "search_documents",
                "create_ux_task", "create_ux_document", "publish_ux_document",
                "update_task", "advance_task", "advance_requirement");
        assertThat(developer).containsExactly(
                "get_organization", "get_work_item", "list_requirements", "list_tasks", "list_bugs",
                "list_members", "get_delivery_graph", "search_documents",
                "create_tech_design", "create_dev_task", "start_development", "get_pipeline_log",
                "update_task", "advance_task", "update_bug", "advance_bug", "advance_requirement")
                .doesNotContain("deploy_release", "create_test_case");
        assertThat(qa).containsExactly(
                "get_organization", "get_work_item", "list_requirements", "list_tasks", "list_bugs",
                "list_members", "get_test_run", "get_delivery_graph", "search_documents",
                "create_test_case", "create_qa_task", "create_bug", "update_task", "advance_task", "update_bug",
                "advance_bug", "start_test_run", "record_test_result", "complete_test_run",
                "reopen_test_run", "advance_requirement")
                .doesNotContain("update_test_result", "deploy_release");
        assertThat(release).containsExactly(
                "get_organization", "get_work_item", "list_requirements", "list_tasks", "list_bugs",
                "list_members", "get_delivery_graph", "create_release", "run_release_precheck",
                "get_release_precheck", "update_release_note", "deploy_release");
    }

    @Test
    void forcedConfirmationToolsAreMediumRiskButNeverAutoExecuted() {
        for (String name : List.of(
                "assign_participants", "record_test_result", "complete_test_run", "delete_requirement")) {
            ToolContract contract = registry.findTool(name).orElseThrow();
            assertThat(contract.requiresConfirmation())
                    .as("%s must declare requires_confirmation", name)
                    .isTrue();
            assertThat(contract.mediumRisk()).isTrue();
            assertThat(contract.forcedConfirmation()).isTrue();
        }
        ToolContract plainMedium = registry.findTool("update_task").orElseThrow();
        assertThat(plainMedium.requiresConfirmation()).isFalse();
        assertThat(plainMedium.forcedConfirmation()).isFalse();
    }

    @Test
    void everySkillDeclaresStageGuidance() {
        for (AgentSkill skill : AgentSkill.values()) {
            SkillContract contract = registry.findSkill(skill.name()).orElseThrow();
            assertThat(contract.guidance())
                    .as("%s must declare guidance", skill)
                    .isNotNull()
                    .contains("人工专属");
        }
    }

    @Test
    void requirementReadToolsAreLowRiskAndScopedByContract() {
        for (String name : List.of(
                "list_requirements", "list_tasks", "list_bugs", "list_members", "get_test_run")) {
            ToolContract contract = registry.findTool(name).orElseThrow();
            assertThat(contract.riskLevel()).isEqualTo("LOW");
            assertThat(contract.idempotencyRequired()).isFalse();
            assertThat(contract.inputSchema()).containsEntry("additionalProperties", false);
            assertThat(contract.inputSchema().get("properties"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .doesNotContainKeys("organizationId", "userId");
        }
    }

    @Test
    void unknownToolAndSkillAreAbsent() {
        ToolContract deploy = registry.findTool("deploy_release").orElseThrow();
        assertThat(deploy.highRisk()).isTrue();
        assertThat(deploy.requiredPermission()).isEqualTo("release.deploy");
        assertThat(registry.findSkill("unknown")).isEmpty();
    }
}
