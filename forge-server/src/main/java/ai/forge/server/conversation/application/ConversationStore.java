package ai.forge.server.conversation.application;

import java.util.List;
import java.util.Map;

public interface ConversationStore {
    int insertConversation(long organizationId, long userId, String title);
    long lastInsertId();
    List<Map<String, Object>> conversations(long organizationId, long userId);
    int owns(long organizationId, long userId, long id);
    Long requirementId(long organizationId, long userId, long id);
    int bindRequirement(long organizationId, long userId, long id, long requirementId);
    int initializeTitle(long organizationId, long userId, long id, String title);
    int insertUserMessage(long conversationId, String body, String runId);
    int touch(long id);
    int delete(long organizationId, long userId, long id);
    List<Map<String, Object>> messages(long id);
}
