package ai.forge.server.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DocumentContent(
        /* 原样保存的 ProseMirror JSON，作为编辑器可恢复的正文。 */ JsonNode proseMirrorJson,
        /* 从正文抽取并规范空白后的搜索与摘要文本。 */ String plainText,
        /* 规范化纯文本的 SHA-256 十六进制摘要。 */ String hash) {
    public static DocumentContent from(JsonNode proseMirrorJson) {
        if (proseMirrorJson == null || !proseMirrorJson.isObject() || !"doc".equals(proseMirrorJson.path("type").asText())) {
            throw new IllegalArgumentException("content must be a ProseMirror doc JSON object");
        }
        String plainText = proseMirrorJson.findValuesAsText("text").stream().map(String::trim)
                .filter(value -> !value.isEmpty()).reduce((left, right) -> left + "\n" + right).orElse("");
        return new DocumentContent(proseMirrorJson, plainText, sha256(plainText));
    }

    public static DocumentContent fromMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("markdown content must not be blank");
        }
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode document = objectMapper.createObjectNode();
        document.put("type", "doc");
        ArrayNode blocks = document.putArray("content");
        Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$");
        Pattern bulletPattern = Pattern.compile("^[-*+]\\s+(.+)$");
        Pattern orderedPattern = Pattern.compile("^(\\d+)[.)]\\s+(.+)$");
        String[] lines = markdown.strip().split("\\R", -1);
        int index = 0;
        while (index < lines.length) {
            String line = lines[index].strip();
            if (line.isEmpty()) {
                index++;
                continue;
            }
            Matcher heading = headingPattern.matcher(line);
            if (heading.matches()) {
                blocks.add(textBlock(objectMapper, "heading", heading.group(2), heading.group(1).length()));
                index++;
                continue;
            }
            Matcher bullet = bulletPattern.matcher(line);
            Matcher ordered = orderedPattern.matcher(line);
            if (bullet.matches() || ordered.matches()) {
                boolean orderedList = ordered.matches();
                ArrayNode items = objectMapper.createArrayNode();
                while (index < lines.length) {
                    String itemLine = lines[index].strip();
                    Matcher item = (orderedList ? orderedPattern : bulletPattern).matcher(itemLine);
                    if (!item.matches()) {
                        break;
                    }
                    String itemText = item.group(orderedList ? 2 : 1);
                    ObjectNode listItem = objectMapper.createObjectNode().put("type", "listItem");
                    listItem.putArray("content").add(textBlock(objectMapper, "paragraph", itemText, null));
                    items.add(listItem);
                    index++;
                }
                ObjectNode list = objectMapper.createObjectNode()
                        .put("type", orderedList ? "orderedList" : "bulletList");
                if (orderedList) {
                    list.putObject("attrs").put("start", Integer.parseInt(ordered.group(1)));
                }
                list.set("content", items);
                blocks.add(list);
                continue;
            }
            StringBuilder paragraph = new StringBuilder(line);
            index++;
            while (index < lines.length && !lines[index].isBlank()
                    && !headingPattern.matcher(lines[index].strip()).matches()
                    && !bulletPattern.matcher(lines[index].strip()).matches()
                    && !orderedPattern.matcher(lines[index].strip()).matches()) {
                paragraph.append(' ').append(lines[index].strip());
                index++;
            }
            blocks.add(textBlock(objectMapper, "paragraph", paragraph.toString(), null));
        }
        return from(document);
    }

    private static ObjectNode textBlock(
            ObjectMapper objectMapper, String type, String text, Integer headingLevel) {
        ObjectNode block = objectMapper.createObjectNode().put("type", type);
        if (headingLevel != null) {
            block.putObject("attrs").put("level", headingLevel);
        }
        block.putArray("content").add(objectMapper.createObjectNode()
                .put("type", "text")
                .put("text", text));
        return block;
    }

    public String contentJson() {
        try { return new ObjectMapper().writeValueAsString(proseMirrorJson); }
        catch (Exception exception) { throw new IllegalArgumentException("content cannot be serialized", exception); }
    }
    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
}
