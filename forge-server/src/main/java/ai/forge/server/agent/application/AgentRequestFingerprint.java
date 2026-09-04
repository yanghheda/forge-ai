package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentSkill;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class AgentRequestFingerprint {

    private AgentRequestFingerprint() {
    }

    static String create(AgentSkill skill, Long workItemId, String message) {
        String canonical = skill.name() + "\n" + (workItemId == null ? "" : workItemId) + "\n" + message.trim();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
