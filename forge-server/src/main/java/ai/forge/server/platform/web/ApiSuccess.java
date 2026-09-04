package ai.forge.server.platform.web;

public record ApiSuccess<T>(
        /* 成功响应使用固定数值零，供调用方识别统一信封。 */ int code,
        /* 成功响应使用稳定消息，不承载业务判断。 */ String message,
        /* 接口原始业务响应；列表、对象和标量均放在此字段。 */ T data) {

    public static <T> ApiSuccess<T> of(T data) {
        return new ApiSuccess<>(0, "success", data);
    }
}
