package ai.forge.server.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import ai.forge.server.organization.application.OrganizationAccessService;
import ai.forge.server.common.domain.ResourceNotFoundException;

import org.junit.jupiter.api.Test;

class ConversationServiceTest {

    @Test
    void renameValidatesTitleAndScopesUpdateToOwner() {
        var access = mock(OrganizationAccessService.class);
        var store = mock(ConversationStore.class);
        var service = new ConversationService(access, store, null, null, null);
        when(access.requireContext(anyLong())).thenReturn(new ai.forge.server.organization.domain.OrganizationContext(3L, "测试公司", false));
        when(store.owns(3, 7, 12)).thenReturn(1);
        when(store.rename(3, 7, 12, "需求讨论")).thenReturn(1);
        service.rename(7, 12, " 需求讨论 ");
        verify(store).rename(3, 7, 12, "需求讨论");
        assertThatThrownBy(() -> service.rename(7, 12, "  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rename(7, 12, "a".repeat(256))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rename(8, 12, "其他标题")).isInstanceOf(ResourceNotFoundException.class);
        verify(store, times(1)).rename(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void generatedTitleCollapsesWhitespaceAndKeepsShortPrompt() {
        assertThat(ConversationService.generatedTitle("  请帮我   梳理登录流程\n并补充异常场景  "))
                .isEqualTo("请帮我 梳理登录流程 并补充异常场景");
    }

    @Test
    void generatedTitleTruncatesByUnicodeCodePoint() {
        String prompt = "😀".repeat(31);

        assertThat(ConversationService.generatedTitle(prompt))
                .isEqualTo("😀".repeat(30) + "…");
    }
}
