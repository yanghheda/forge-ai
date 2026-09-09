/* 工作项流转动作的中文按钮文案；未命中时回退英文原值。 */
const workflowActionLabels: Record<string, string> = {
  SUBMIT_PRODUCT_REVIEW: "提交产品评审",
  APPROVE_PRODUCT_REVIEW: "通过产品评审",
  REJECT_PRODUCT_REVIEW: "退回产品评审",
  SUBMIT_UX_REVIEW: "提交 UX 评审",
  APPROVE_UX_REVIEW: "通过 UX 评审",
  REJECT_UX_REVIEW: "退回 UX 评审",
  SKIP_UX: "跳过 UX",
  SUBMIT_FOR_QA: "提交测试",
  START_QA: "开始测试",
  QA_PASS: "测试通过",
  START: "开始处理",
  SUBMIT_REVIEW: "提交评审",
  APPROVE: "批准",
  REJECT: "退回修改",
};

/* Bug 流转动作的中文按钮文案。 */
const bugActionLabels: Record<string, string> = {
  START_FIX: "开始修复",
  RESOLVE: "提交修复",
  VERIFY: "验证",
  CLOSE: "关闭",
  REOPEN: "重新打开",
};

/* 流程 Guard 缺失项的中文提示文案。 */
const guardHintLabels: Record<string, string> = {
  goal: "业务目标",
  inScope: "范围",
  acceptanceCriteria: "验收标准",
  publishedPrd: "已发布 PRD",
  publishedUxSpec: "已发布 UX Spec",
  userFlow: "用户流",
  pageList: "页面清单",
  keyInteraction: "关键交互",
  exceptionState: "异常状态",
  reason: "退回原因",
  devTask: "研发任务",
  devTaskIncomplete: "未完成的研发任务",
  repository: "可用 GitLab 仓库",
  mergeRequest: "研发任务关联 MR",
  pipeline: "MR Pipeline",
  pipelineRunning: "运行完成的 Pipeline",
  pipelineFailed: "成功的 Pipeline",
  pipelineHeadMismatch: "MR 当前 head 的成功 Pipeline",
  completedTestRun: "已完成测试执行",
  testNotRun: "全部用例执行完成",
  testFailed: "无失败用例",
  testBlocked: "无阻塞用例",
  mandatoryTestSkipped: "P0/P1 用例全部通过",
};

/* 测试结果状态的中文文案。 */
const testResultLabels: Record<string, string> = {
  NOT_RUN: "未执行",
  PASS: "通过",
  FAIL: "失败",
  BLOCKED: "阻塞",
  SKIPPED: "跳过",
};

/* 工作项优先级的中文文案。 */
const priorityLabels: Record<string, string> = {
  LOW: "低",
  MEDIUM: "中",
  HIGH: "高",
  URGENT: "紧急",
};

/* Workspace 成员角色的中文文案。 */
const roleLabels: Record<string, string> = {
  OWNER: "所有者",
  ADMIN: "管理员",
  PRODUCT: "产品",
  UX: "设计",
  DEVELOPER: "研发",
  QA: "测试",
  RELEASE_APPROVER: "发布审批",
};

export function workflowActionLabel(value: string): string {
  return workflowActionLabels[value] ?? value;
}

export function bugActionLabel(value: string): string {
  return bugActionLabels[value] ?? value;
}

export function guardHintLabel(value: string): string {
  return guardHintLabels[value] ?? value;
}

export function testResultLabel(value: string): string {
  return testResultLabels[value] ?? value;
}

export function priorityLabel(value: string): string {
  return priorityLabels[value] ?? value;
}

export function roleLabel(value: string): string {
  return roleLabels[value] ?? value;
}
