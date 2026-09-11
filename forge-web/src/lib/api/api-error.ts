export interface ApiErrorOptions {
  code: string;
  message: string;
  status: number;
  requestId?: string;
  details?: Readonly<Record<string, unknown>>;
}

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly requestId?: string;
  readonly details: Readonly<Record<string, unknown>>;

  constructor({ code, message, status, requestId, details = {} }: ApiErrorOptions) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.requestId = requestId;
    this.details = details;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

const ERROR_MESSAGES: Readonly<Record<string, string>> = {
  RESOURCE_NOT_FOUND: "请求的资源不存在或当前账户无权访问。",
  MEMBER_EMAIL_CONFLICT: "该邮箱已关联现有账号。",
  VERSION_CONFLICT: "数据已被其他操作更新，请刷新后重试。",
  INVALID_TRANSITION: "当前状态不允许执行此操作。",
  WORKFLOW_ACTION_NOT_ALLOWED: "当前状态不允许执行此流程操作。",
  WORKFLOW_GUARD_FAILED: "当前流程条件尚未满足，请检查缺失项。",
  IDEMPOTENCY_CONFLICT: "此操作标识已用于其他请求，请刷新后重试。",
  IDEMPOTENCY_IN_PROGRESS: "相同操作正在处理中，请稍后查看结果。",
  IDEMPOTENCY_KEY_REUSED: "此操作标识已用于其他内容，请重新发起。",
  RELATION_CONFLICT: "该工作项关系已存在。",
  AGENT_RUN_IDEMPOTENCY_CONFLICT: "Agent 请求标识已用于不同内容，请重新发起。",
  VALIDATION_FAILED: "提交内容不符合要求，请检查后重试。",
  INVALID_ARGUMENT: "提交参数不符合要求，请检查后重试。",
  SCHEMA_INVALID: "提交内容格式不正确，请检查后重试。",
  INSTANCE_ALREADY_INITIALIZED: "当前实例已经完成初始化。",
  UNAUTHENTICATED: "登录状态已失效，请重新登录。",
  FORBIDDEN: "当前账户没有执行此操作的权限。",
  PERMISSION_DENIED: "当前账户没有执行此操作的权限。",
  TENANT_SCOPE_MISMATCH: "请求的资源不属于当前工作空间。",
  LOGIN_RATE_LIMITED: "登录尝试次数过多，请稍后再试。",
  RATE_LIMITED: "请求过于频繁，请稍后再试。",
  CSRF_REJECTED: "请求安全校验失败，请刷新页面后重试。",
  ORIGIN_REJECTED: "当前页面来源未通过安全校验，请从正确地址重新打开。",
  CSRF_TOKEN_UNAVAILABLE: "暂时无法建立安全请求，请刷新页面后重试。",
  RAG_UNAVAILABLE: "文档检索服务暂时不可用，请稍后重试。",
  GITLAB_UNAUTHORIZED: "GitLab 凭据无效，请检查连接配置。",
  GITLAB_FORBIDDEN: "GitLab 凭据缺少所需权限。",
  GITLAB_NOT_FOUND: "GitLab 中未找到请求的资源。",
  GITLAB_RATE_LIMITED: "GitLab 请求过于频繁，请稍后重试。",
  GITLAB_CONFLICT: "GitLab 资源状态冲突，请刷新后重试。",
  GITLAB_TIMEOUT: "GitLab 请求超时，请稍后重试。",
  GITLAB_UNAVAILABLE: "GitLab 服务暂时不可用，请稍后重试。",
  GITLAB_INVALID_RESPONSE: "GitLab 返回了无法处理的响应，请检查连接配置。",
  REMOTE_RESOURCE_CONFLICT: "远端分支已存在且基准版本不同，请人工确认。",
  LOCAL_STATE_CHANGED: "本地开发状态已经变化，请刷新后重试。",
  DEVELOPMENT_STATE_CONFLICT: "当前状态不允许启动或完成开发。",
  QA_STATE_CONFLICT: "当前测试状态不允许执行此操作。",
  WEBHOOK_REJECTED: "Webhook 校验失败或请求内容超过限制。",
  APPROVAL_REQUIRED: "此操作需要审批后才能继续。",
  APPROVAL_EXPIRED: "审批已过期，请重新发起。",
  APPROVAL_NOT_ACTIVE: "审批已失效，无法继续操作。",
  APPROVAL_INPUT_CHANGED: "审批内容已经变化，请重新发起审批。",
  APPROVAL_REJECTED: "审批未通过，操作已停止。",
  APPROVAL_CANCELLED: "审批已取消。",
  APPROVER_UNAVAILABLE: "当前没有可用的审批人，请联系管理员。",
  SELF_APPROVAL_FORBIDDEN: "不能审批自己发起的操作。",
  APPROVAL_CANCEL_FORBIDDEN: "只有申请人可以取消待处理审批。",
  INVALID_TTL: "审批有效期设置不正确，请检查后重试。",
  RESOURCE_VERSION_CHANGED: "关键资源版本已经变化，请刷新后重试。",
  PRECHECK_NOT_CURRENT_PASS: "当前发布预检未通过或已经失效，请重新运行预检。",
  TOOL_NOT_FOUND: "未找到可执行的 Agent 工具。",
  TOOL_NOT_ALLOWED_FOR_SKILL: "当前 Agent 技能不允许使用此工具。",
  UNSUPPORTED_CONFIRMATION: "当前确认策略不受支持，请联系管理员。",
  RUN_NOT_FOUND: "Agent Run 不存在或当前账户无权访问。",
  RUN_NOT_ACTIVE: "Agent Run 当前不处于可操作状态。",
  MEDIUM_DENIED: "操作未获得用户确认，已停止执行。",
  AGENT_GATEWAY_FAILED: "Agent 服务暂时不可用，请稍后重试。",
  QDRANT_UNAVAILABLE: "文档索引服务暂时不可用，请稍后重试。",
  INDEX_FAILED: "文档索引处理失败，请稍后重试。",
  EXTERNAL_SERVICE_ERROR: "外部服务调用失败，请稍后重试。",
  INTERNAL_ERROR: "服务器处理请求时发生错误，请稍后重试。",
};

const STATUS_MESSAGES: Readonly<Record<number, string>> = {
  400: "请求内容不正确，请检查后重试。",
  401: "登录状态已失效，请重新登录。",
  403: "当前账户没有执行此操作的权限。",
  404: "请求的资源不存在或当前账户无权访问。",
  408: "请求超时，请稍后重试。",
  409: "数据状态发生冲突，请刷新后重试。",
  413: "提交的内容过大，请调整后重试。",
  422: "当前操作条件尚未满足，请检查后重试。",
  429: "请求过于频繁，请稍后再试。",
  500: "服务器处理请求时发生错误，请稍后重试。",
  502: "上游服务响应异常，请稍后重试。",
  503: "服务暂时不可用，请稍后重试。",
  504: "上游服务响应超时，请稍后重试。",
};

export function getUserErrorMessage(error: unknown): string {
  if (!isApiError(error)) {
    return error instanceof TypeError ? "网络连接异常，请检查网络后重试。" : "请求失败，请稍后重试。";
  }
  const messageCode = ERROR_MESSAGES[error.message] ? error.message : error.code;
  return ERROR_MESSAGES[messageCode]
    ?? STATUS_MESSAGES[error.status]
    ?? "请求失败，请稍后重试。";
}

export function formatRequestError(error: unknown): string {
  return getUserErrorMessage(error);
}
