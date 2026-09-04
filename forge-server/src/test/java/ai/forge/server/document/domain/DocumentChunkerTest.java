package ai.forge.server.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {

    @Test
    void blankTextProducesNoChunks() {
        assertThat(DocumentChunker.chunk(null)).isEmpty();
        assertThat(DocumentChunker.chunk("   ")).isEmpty();
        assertThat(DocumentChunker.chunk("\n \n")).isEmpty();
    }

    @Test
    void shortTextProducesSingleNormalizedChunk() {
        List<DocumentChunk> chunks = DocumentChunker.chunk("alpha beta\n gamma");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chunkIndex()).isZero();
        assertThat(chunks.get(0).text()).isEqualTo("alpha beta\ngamma");
    }

    @Test
    void consecutiveChunksCarryOverlapAndStayWithinBudget() {
        String text = IntStream.rangeClosed(1, 60)
                .mapToObj(i -> "paragraph-" + i + " " + "x".repeat(120))
                .collect(Collectors.joining("\n"));

        List<DocumentChunk> chunks = DocumentChunker.chunk(text);

        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
            assertThat(chunks.get(i).text().length())
                    .isLessThanOrEqualTo(DocumentChunker.MAX_CHUNK_CHARS + DocumentChunker.OVERLAP_CHARS);
        }
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1).text();
            String current = chunks.get(i).text();
            int overlap = Math.min(DocumentChunker.OVERLAP_CHARS, previous.length());
            assertThat(current.startsWith(previous.substring(previous.length() - overlap)))
                    .as("chunk %d should start with the overlap tail of chunk %d", i, i - 1)
                    .isTrue();
        }
        String chunksText = chunks.stream().map(DocumentChunk::text).collect(Collectors.joining("\n"));
        assertThat(chunksText).contains("paragraph-1").contains("paragraph-60");
    }

    @Test
    void oversizedParagraphIsHardSplitWithoutLosingContent() {
        /* 用唯一编号令牌构造超长段落，允许通过“每个令牌都出现在某切片”验证不丢内容。 */
        String huge = IntStream.rangeClosed(0, 1550)
                .mapToObj(i -> String.format("%07d", i))
                .collect(Collectors.joining());
        assertThat(huge.length()).isGreaterThan(DocumentChunker.MAX_CHUNK_CHARS * 2);

        List<DocumentChunk> chunks = DocumentChunker.chunk(huge);

        assertThat(chunks.size()).isGreaterThanOrEqualTo(3);
        String chunksText = String.join("", chunks.stream().map(DocumentChunk::text).toList());
        for (int i = 0; i <= 1550; i++) {
            assertThat(chunksText).contains(String.format("%07d", i));
        }
        assertThat(chunksText).startsWith(String.format("%07d", 0));
        assertThat(chunksText).endsWith(String.format("%07d", 1550));
    }

    @Test
    void chunkingIsDeterministic() {
        String text = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> "line-" + i + " " + "y".repeat(150))
                .collect(Collectors.joining("\n"));

        assertThat(DocumentChunker.chunk(text)).isEqualTo(DocumentChunker.chunk(text));
    }
}
