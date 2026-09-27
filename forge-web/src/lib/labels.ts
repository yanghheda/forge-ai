/* 工作项流转动作的中文按钮文案；未命中时回退英文原值。 */
const workflowActionLabels: Record<string, string> = {
  SUBMIT_PRODUCT_REVIEW: "提交需求审核",
  APPROVE_REQUIREMENT: "需求审核通过",
  REJECT_REQUIREMENT: "驳回到草稿",
  START_PRODUCT_REVIEW: "开始四方评审",
  START_UX: "开始 UX 设计",
  START_DEVELOPMENT: "开始开发阶段",
  ACCEPT_REQUIREMENT: "验收通过",
  QA_FAIL: "进入验证修复",
  BLOCK: "标记阻塞",
  UNBLOCK: "解除阻塞",
  APPROVE_PRODUCT_REVIEW: "确认评审通过",
  REJECT_PRODUCT_REVIEW: "退回产品评审",
  SUBMIT_UX_REVIEW: "提交 UX 审核",
  APPROVE_UX_REVIEW: "UX 审核通过",
  REJECT_UX_REVIEW: "驳回 UX 设计",
  SKIP_UX: "跳过 UX",
  SUBMIT_FOR_QA: "提交测试",
  START_QA: "开始测试",
  QA_PASS: "测试通过",
  START: "开始处理",
  SUBMIT_REVIEW: "提交验证",
  APPROVE: "验证通过",
  REJECT: "退回修改",
};

/* Bug 流转动作的中文按钮文案。 */
const bugActionLabels: Record<string, string> = {
  BLOCK: "标记阻塞",
  UNBLOCK: "解除阻塞",
  START_FIX: "开始修复",
  RESOLVE: "提交修复",
  VERIFY: "验证",
  CLOSE: "关闭",
  REOPEN: "重新打开",
};

/* 发布流程写入需求事件流的动作代码文案。 */
const releaseActionLabels: Record<string, string> = {
  MARK_RELEASED: "标记已发布",
  CLOSE_REQUIREMENT: "关闭需求",
};

/* 流程 Guard 缺失项的中文提示文案。 */
const guardHintLabels: Record<string, string> = {
  allTestCasesExecuted: "全部测试用例执行完毕并完成当前测试轮次",
  unfinishedBugs: "全部缺陷修复并验证通过",
  unfinishedUxTasks: "全部设计任务验证通过",
  unfinishedQaTasks: "全部测试任务验证通过",
  activeTaskStage: "需求进入对应执行阶段",
  "task:UX_TASK": "至少一个设计任务",
  "task:DEV_TASK": "至少一个开发任务",
  "task:QA_TASK": "至少一个测试任务",
  description: "需求描述",
  goal: "业务目标",
  inScope: "纳入范围",
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
  IN_PROGRESS: "执行中",
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

/* Organization 成员角色的中文文案。 */
const roleLabels: Record<string, string> = {
  OWNER: "所有者",
  ADMIN: "全局管理员",
  PRODUCT: "产品",
  UX: "设计",
  DEVELOPER: "研发",
  QA: "测试",
  PRODUCT_ADMIN: "产品管理员",
  UX_ADMIN: "UX 管理员",
  DEVELOPER_ADMIN: "开发管理员",
  QA_ADMIN: "测试管理员",
};

export function workflowActionLabel(value: string): string {
  return workflowActionLabels[value] ?? value;
}

export function bugActionLabel(value: string): string {
  return bugActionLabels[value] ?? value;
}

/* 统一 Activity 的动作文案，按需求、Bug、发布三类来源解析后回退原始代码。 */
export function activityActionLabel(value: string): string {
  return workflowActionLabels[value] ?? bugActionLabels[value] ?? releaseActionLabels[value] ?? value;
}

export function guardHintLabel(value: string): string {
  if (value.startsWith("leader:")) return `${roleLabel(value.slice(7))}负责人`;
  if (value.startsWith("executor:")) return `${roleLabel(value.slice(9))}执行人`;
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
