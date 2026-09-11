package ai.forge.server.conversation.application;

import java.util.List;
import java.util.Map;

public interface ConversationStore {
    int insertConversation(long organizationId, long userId, String title);
    long lastInsertId();
    List<Map<String, Object>> conversations(long organizationId, long userId);
    int owns(long organizationId, long userId, long id);
    int insertUserMessage(long conversationId, String body, String runId);
    int touch(long id);
    List<Map<String, Object>> messages(long id);
}
