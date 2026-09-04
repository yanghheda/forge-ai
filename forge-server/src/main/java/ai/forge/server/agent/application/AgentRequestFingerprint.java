package ai.forge.server.agent.application;

import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.agent.domain.MediumToolConfirmation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class AgentRequestFingerprint {

    private AgentRequestFingerprint() {
    }

    static String create(
            AgentSkill skill, Long workItemId, String message, MediumToolConfirmation mediumToolConfirmation) {
        String canonical = skill.name() + "\n" + (workItemId == null ? "" : workItemId) + "\n" + message.trim()
                + "\n" + mediumToolConfirmation.name();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
