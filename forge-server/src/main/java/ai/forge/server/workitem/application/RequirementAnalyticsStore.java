package ai.forge.server.workitem.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface RequirementAnalyticsStore {

    List<Map<String, Object>> statusDistribution(long organizationId);

    List<Map<String, Object>> priorityDistribution(long organizationId);

    List<Map<String, Object>> dailyCreated(long organizationId, int daysBack);

    List<Map<String, Object>> dailyCompleted(long organizationId, int daysBack);

    Map<String, Object> completionQuality(long organizationId, LocalDateTime windowStart);

    long overdue(long organizationId);

    long backlogBaseline(long organizationId, LocalDateTime windowStart);

    List<Map<String, Object>> bugDistribution(long organizationId);

    Map<String, Object> qaQuality(long organizationId, LocalDateTime windowStart);

    List<Map<String, Object>> risks(long organizationId, int limit);
}
