package ai.forge.server.workitem.infrastructure.persistence;

import ai.forge.server.workitem.application.RequirementAnalyticsStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RequirementAnalyticsMapper extends RequirementAnalyticsStore {

    @Select("SELECT status,COUNT(*) total FROM work_items WHERE organization_id=#{organizationId} "
            + "AND type='REQUIREMENT' AND deleted_at IS NULL GROUP BY status")
    List<Map<String, Object>> statusDistribution(@Param("organizationId") long organizationId);

    @Select("SELECT priority,COUNT(*) total FROM work_items WHERE organization_id=#{organizationId} "
            + "AND type='REQUIREMENT' AND deleted_at IS NULL GROUP BY priority")
    List<Map<String, Object>> priorityDistribution(@Param("organizationId") long organizationId);

    @Select("SELECT DATE(created_at) day,COUNT(*) total FROM work_items WHERE organization_id=#{organizationId} "
            + "AND type='REQUIREMENT' AND deleted_at IS NULL AND created_at>=UTC_DATE()-INTERVAL #{daysBack} DAY "
            + "GROUP BY DATE(created_at) ORDER BY day")
    List<Map<String, Object>> dailyCreated(
            @Param("organizationId") long organizationId, @Param("daysBack") int daysBack);

    @Select("SELECT DATE(first_done) day,COUNT(*) total FROM ("
            + "SELECT e.work_item_id,MIN(e.created_at) first_done FROM work_item_events e "
            + "JOIN work_items wi ON wi.id=e.work_item_id "
            + "WHERE e.organization_id=#{organizationId} AND wi.type='REQUIREMENT' AND wi.deleted_at IS NULL "
            + "AND e.to_status IN ('DONE','RELEASED') GROUP BY e.work_item_id) t "
            + "WHERE first_done>=UTC_DATE()-INTERVAL #{daysBack} DAY GROUP BY DATE(first_done) ORDER BY day")
    List<Map<String, Object>> dailyCompleted(
            @Param("organizationId") long organizationId, @Param("daysBack") int daysBack);

    @Select("SELECT SUM(CASE WHEN wi.due_at IS NOT NULL THEN 1 ELSE 0 END) due_total,"
            + "SUM(CASE WHEN wi.due_at IS NOT NULL AND c.first_done<=wi.due_at THEN 1 ELSE 0 END) due_on_time,"
            + "AVG(TIMESTAMPDIFF(HOUR,wi.created_at,c.first_done))/24 avg_cycle_days "
            + "FROM work_items wi JOIN ("
            + "SELECT e.work_item_id,MIN(e.created_at) first_done FROM work_item_events e "
            + "WHERE e.organization_id=#{organizationId} AND e.to_status IN ('DONE','RELEASED') "
            + "GROUP BY e.work_item_id) c ON c.work_item_id=wi.id "
            + "WHERE wi.organization_id=#{organizationId} AND wi.type='REQUIREMENT' AND wi.deleted_at IS NULL "
            + "AND c.first_done>=#{windowStart}")
    Map<String, Object> completionQuality(
            @Param("organizationId") long organizationId, @Param("windowStart") LocalDateTime windowStart);

    @Select("SELECT COUNT(*) FROM work_items WHERE organization_id=#{organizationId} AND type='REQUIREMENT' "
            + "AND deleted_at IS NULL AND due_at IS NOT NULL AND due_at<UTC_TIMESTAMP(6) "
            + "AND status NOT IN ('DONE','RELEASED','CANCELLED','REJECTED')")
    long overdue(@Param("organizationId") long organizationId);

    @Select("SELECT COUNT(*) FROM work_items wi WHERE wi.organization_id=#{organizationId} "
            + "AND wi.type='REQUIREMENT' AND wi.deleted_at IS NULL AND wi.created_at<#{windowStart} "
            + "AND NOT EXISTS (SELECT 1 FROM work_item_events e WHERE e.work_item_id=wi.id "
            + "AND e.to_status IN ('DONE','RELEASED') AND e.created_at<#{windowStart})")
    long backlogBaseline(
            @Param("organizationId") long organizationId, @Param("windowStart") LocalDateTime windowStart);

    @Select("SELECT COALESCE(severity,'') severity,status,COUNT(*) total FROM work_items "
            + "WHERE organization_id=#{organizationId} AND type='BUG' AND deleted_at IS NULL "
            + "GROUP BY COALESCE(severity,''),status")
    List<Map<String, Object>> bugDistribution(@Param("organizationId") long organizationId);

    @Select("SELECT SUM(CASE WHEN r.status='PASS' THEN 1 ELSE 0 END) passed,"
            + "SUM(CASE WHEN r.status='FAIL' THEN 1 ELSE 0 END) failed,"
            + "SUM(CASE WHEN r.status='BLOCKED' THEN 1 ELSE 0 END) blocked,"
            + "SUM(CASE WHEN r.status='SKIPPED' THEN 1 ELSE 0 END) skipped,"
            + "SUM(CASE WHEN r.status='NOT_RUN' THEN 1 ELSE 0 END) not_run "
            + "FROM test_results r JOIN test_runs tr ON tr.id=r.test_run_id "
            + "WHERE r.organization_id=#{organizationId} AND tr.started_at>=#{windowStart}")
    Map<String, Object> qaQuality(
            @Param("organizationId") long organizationId, @Param("windowStart") LocalDateTime windowStart);

    @Select("SELECT wi.id,wi.item_key,wi.title,wi.status,wi.priority,wi.due_at,wi.blocked_at "
            + "FROM work_items wi WHERE wi.organization_id=#{organizationId} AND wi.type='REQUIREMENT' "
            + "AND wi.deleted_at IS NULL AND wi.status NOT IN ('DONE','RELEASED','CANCELLED','REJECTED') "
            + "AND (wi.due_at<UTC_TIMESTAMP(6) OR wi.blocked_at IS NOT NULL) "
            + "ORDER BY (wi.blocked_at IS NOT NULL) DESC,wi.due_at ASC LIMIT #{limit}")
    List<Map<String, Object>> risks(
            @Param("organizationId") long organizationId, @Param("limit") int limit);
}
