package ai.forge.server.system.domain;

public record SystemStatus(
        /* 当前响应对应的服务名称，用于调用方识别健康信息来源。 */
        String application,
        /* 当前服务的基础运行状态；本轮仅在应用上下文可用时返回 UP。 */
        String status) {}
