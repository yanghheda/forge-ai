package ai.forge.server.workitem.application;

import ai.forge.server.workitem.domain.ReasonRequiredGuard;
import ai.forge.server.workitem.domain.RequirementWorkflowRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test-unit")
public class WorkflowConfiguration {

    @Bean
    RequirementWorkflowRegistry requirementWorkflowRegistry(RequirementMaterialStore materialStore) {
        return new RequirementWorkflowRegistry(
                new RequirementMaterialGuard(materialStore),
                new PublishedPrdGuard(materialStore),
                new PublishedUxSpecGuard(materialStore),
                new UxChecklistGuard(),
                new ReasonRequiredGuard());
    }
}
