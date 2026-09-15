package ai.forge.server.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.forge.server.document.application.DocumentContent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DocumentContentTest {

    @Test
    void canonicalizesProseMirrorJsonAndHashesItsSearchableText() throws Exception {
        DocumentContent content = DocumentContent.from(
                new ObjectMapper().readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"  ForgeAI 文档  \"}]}]}"));

        assertThat(content.plainText()).isEqualTo("ForgeAI 文档");
        assertThat(content.hash()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void convertsGeneratedMarkdownIntoEditableProseMirrorContent() {
        DocumentContent content = DocumentContent.fromMarkdown("""
                # 1 背景与目标

                为团队提供真实可编辑的 PRD 正文。

                ## 2 验收标准

                - 页面展示 Agent 生成的内容
                - 刷新后内容仍然存在
                """);

        assertThat(content.proseMirrorJson().path("type").asText()).isEqualTo("doc");
        assertThat(content.proseMirrorJson().path("content").get(0).path("type").asText())
                .isEqualTo("heading");
        assertThat(content.proseMirrorJson().path("content").get(3).path("type").asText())
                .isEqualTo("bulletList");
        assertThat(content.plainText()).contains(
                "1 背景与目标",
                "为团队提供真实可编辑的 PRD 正文。",
                "页面展示 Agent 生成的内容",
                "刷新后内容仍然存在");
    }
}
