package ai.forge.server.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
}
