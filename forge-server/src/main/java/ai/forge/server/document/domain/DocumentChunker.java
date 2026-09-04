package ai.forge.server.document.domain;

import java.util.ArrayList;
import java.util.List;

/** 将文档规范化纯文本切分为目标大小、带重叠的检索切片。 */
public final class DocumentChunker {

    /* 切片目标长度（字符）；按英文 1 token ≈ 4 字符近似 600–900 tokens 目标的上限。 */
    public static final int TARGET_CHUNK_CHARS = 2400;
    /* 单个切片硬上限（字符）；超出目标后仅允许再并入一个段落，防止无界增长。 */
    public static final int MAX_CHUNK_CHARS = 3600;
    /* 相邻切片的重叠长度（字符）；近似 80–120 tokens 的上下文重叠，缓解边界语义断裂。 */
    public static final int OVERLAP_CHARS = 400;

    private DocumentChunker() {
    }

    public static List<DocumentChunk> chunk(String plainText) {
        List<String> paragraphs = splitParagraphs(plainText);
        if (paragraphs.isEmpty()) {
            return List.of();
        }
        List<String> units = splitOversizedParagraphs(paragraphs);
        return packUnits(units);
    }

    private static List<String> splitParagraphs(String plainText) {
        List<String> paragraphs = new ArrayList<>();
        if (plainText == null) {
            return paragraphs;
        }
        for (String line : plainText.split("\n", -1)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    private static List<String> splitOversizedParagraphs(List<String> paragraphs) {
        List<String> units = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph.length() <= MAX_CHUNK_CHARS) {
                units.add(paragraph);
                continue;
            }
            int position = 0;
            while (position < paragraph.length()) {
                int end = Math.min(position + MAX_CHUNK_CHARS, paragraph.length());
                units.add(paragraph.substring(position, end));
                if (end >= paragraph.length()) {
                    break;
                }
                position = end - OVERLAP_CHARS;
            }
        }
        return units;
    }

    private static List<DocumentChunk> packUnits(List<String> units) {
        List<DocumentChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        /* 最近一个已关闭切片的文本；用于在新切片头部重建重叠上下文。 */
        String previousChunkText = null;
        for (String unit : units) {
            if (current.length() > 0 && current.length() + unit.length() + 1 > MAX_CHUNK_CHARS) {
                previousChunkText = current.toString();
                chunks.add(new DocumentChunk(chunks.size(), previousChunkText));
                current = new StringBuilder();
            }
            if (current.length() == 0 && previousChunkText != null) {
                current.append(overlapTail(previousChunkText));
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(unit);
            if (current.length() >= TARGET_CHUNK_CHARS) {
                previousChunkText = current.toString();
                chunks.add(new DocumentChunk(chunks.size(), previousChunkText));
                current = new StringBuilder();
            }
        }
        if (current.length() > 0) {
            chunks.add(new DocumentChunk(chunks.size(), current.toString()));
        }
        return chunks;
    }

    private static String overlapTail(String closedChunk) {
        return closedChunk.substring(Math.max(0, closedChunk.length() - OVERLAP_CHARS));
    }
}
