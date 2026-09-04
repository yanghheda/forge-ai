package ai.forge.server.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
    public String contentJson() {
        try { return new ObjectMapper().writeValueAsString(proseMirrorJson); }
        catch (Exception exception) { throw new IllegalArgumentException("content cannot be serialized", exception); }
    }
    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
}
