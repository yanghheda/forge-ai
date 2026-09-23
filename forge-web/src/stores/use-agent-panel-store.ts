import { create } from "zustand";

/* Agent 上下文类型：需求、任务、缺陷对应后端三类工作项；任务没有详情路由，只能由页面显式设置。 */
export type AgentContextKind = "requirement" | "task" | "defect";

/* Agent 上下文：id 决定后端上溯到哪个根需求，label 仅用于界面展示。 */
export interface AgentContext {
  /* 工作项类型 */
  kind: AgentContextKind;
  /* 工作项标识，后端据此上溯根需求并决定角色 Skill */
  id: number;
  /* 界面展示用的可读名称，例如「REQ-31 找回密码」 */
  label?: string;
}

interface AgentPanelState {
  /* 全局 Agent 指令抽屉是否展开 */
  open: boolean;
  /* Agent 指令中心页面与全局抽屉共用的当前会话；为空表示尚未选中任何会话 */
  activeConversationId?: number;
  /* 当前会话是否存在未结束的 Run，用于顶栏按钮的运行指示 */
  running: boolean;
  /* 当前会话是否停留在待审批，用于顶栏按钮的警示提示 */
  awaitingApproval: boolean;
  /* 用户在当前页面手动解绑路由上下文后置为 true，避免再次自动绑定 */
  contextDismissed: boolean;
  /* 列表页显式交给 Agent 的工作项上下文；路由上下文由页面路径实时解析得出 */
  context?: AgentContext;
  setOpen: (open: boolean) => void;
  setActiveConversation: (id?: number) => void;
  setRunState: (state: { running: boolean; awaitingApproval: boolean }) => void;
  setContext: (context?: AgentContext) => void;
  dismissContext: () => void;
  resetContext: () => void;
}

export const useAgentPanelStore = create<AgentPanelState>((set) => ({
  open: false,
  activeConversationId: undefined,
  running: false,
  awaitingApproval: false,
  contextDismissed: false,
  context: undefined,
  setOpen: (open) => set({ open }),
  setActiveConversation: (activeConversationId) => set({ activeConversationId }),
  setRunState: ({ running, awaitingApproval }) => set({ running, awaitingApproval }),
  setContext: (context) => set({ context }),
  dismissContext: () => set({ contextDismissed: true }),
  /* 路由变化时同时清掉显式上下文，避免上一个页面的工作项残留到新页面 */
  resetContext: () => set({ contextDismissed: false, context: undefined }),
}));
