package ai.forge.server.platform.observability;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReliabilityMetricsMapper {

    @Select("SELECT COUNT(*) FROM outbox_events WHERE processed_at IS NULL")
    long countPendingOutbox();

    @Select("SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), UTC_TIMESTAMP(6)), 0) "
            + "FROM outbox_events WHERE processed_at IS NULL")
    long oldestPendingOutboxAgeSeconds();

    @Select("SELECT COUNT(*) FROM document_index_jobs WHERE status = 'DEAD'")
    long countDeadDocumentIndexJobs();

    @Select("SELECT COUNT(*) FROM webhook_deliveries WHERE status = 'DEAD'")
    long countDeadWebhookDeliveries();
}
