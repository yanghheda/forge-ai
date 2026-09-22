package ai.forge.server.console.application;

import java.util.List;
import java.util.Map;

public interface ConsoleStore {
    List<Map<String, Object>> search(long organizationId, String query);
    List<Map<String, Object>> notifications(long organizationId, long userId, int limit);
    long unread(long organizationId, long userId);
    int markRead(long organizationId, long userId, long id);
    List<Map<String, Object>> board(long organizationId);
    List<Map<String, Object>> requirementsByParticipant(long organizationId, Long userId);
    int workItemExists(long organizationId, long workItemId);
    int savePosition(long organizationId, long userId, long workItemId, String lane, long position);
}
