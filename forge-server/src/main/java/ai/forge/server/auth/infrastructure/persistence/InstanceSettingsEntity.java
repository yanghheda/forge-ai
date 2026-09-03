package ai.forge.server.auth.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("instance_settings")
public class InstanceSettingsEntity {

    /* 固定为 1 的实例单例主键，是并发初始化的数据库锁目标。 */
    @TableId
    private Integer id;

    /* 首次初始化提交的 UTC 时间；非空后公开初始化永久关闭。 */
    private LocalDateTime initializedAt;

    /* 初始化创建的默认组织标识；初始化前为空。 */
    private Long defaultOrganizationId;

    /* 实例设置并发更新版本，本轮初始化成功时递增。 */
    private Long version;

    public Integer getId() {
        return id;
    }

    public LocalDateTime getInitializedAt() {
        return initializedAt;
    }

    public Long getDefaultOrganizationId() {
        return defaultOrganizationId;
    }

    public Long getVersion() {
        return version;
    }
}
