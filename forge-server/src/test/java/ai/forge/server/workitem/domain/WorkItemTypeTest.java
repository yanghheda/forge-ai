package ai.forge.server.workitem.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkItemTypeTest {

    @Test
    void requirementAndTasksChooseTheirFixedCreationStatus() {
        assertThat(WorkItemType.REQUIREMENT.initialStatus()).isEqualTo(WorkItemStatus.DRAFT);
        assertThat(WorkItemType.UX_TASK.initialStatus()).isEqualTo(WorkItemStatus.TODO);
        assertThat(WorkItemType.DEV_TASK.initialStatus()).isEqualTo(WorkItemStatus.TODO);
        assertThat(WorkItemType.QA_TASK.initialStatus()).isEqualTo(WorkItemStatus.TODO);
        assertThat(WorkItemType.BUG.initialStatus()).isEqualTo(WorkItemStatus.OPEN);
    }

    @Test
    void eachTypeMapsToItsStablePermissionResource() {
        assertThat(WorkItemType.REQUIREMENT.permissionResource()).isEqualTo("requirement");
        assertThat(WorkItemType.UX_TASK.permissionResource()).isEqualTo("ux");
        assertThat(WorkItemType.DEV_TASK.permissionResource()).isEqualTo("task");
        assertThat(WorkItemType.QA_TASK.permissionResource()).isEqualTo("task");
        assertThat(WorkItemType.BUG.permissionResource()).isEqualTo("bug");
    }
}
