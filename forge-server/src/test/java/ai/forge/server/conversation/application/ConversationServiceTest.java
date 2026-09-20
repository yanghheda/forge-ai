package ai.forge.server.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConversationServiceTest {

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
