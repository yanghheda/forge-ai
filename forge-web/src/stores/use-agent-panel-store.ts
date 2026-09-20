import { create } from "zustand";

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
  setOpen: (open: boolean) => void;
  setActiveConversation: (id?: number) => void;
  setRunState: (state: { running: boolean; awaitingApproval: boolean }) => void;
  dismissContext: () => void;
  resetContext: () => void;
}

export const useAgentPanelStore = create<AgentPanelState>((set) => ({
  open: false,
  activeConversationId: undefined,
  running: false,
  awaitingApproval: false,
  contextDismissed: false,
  setOpen: (open) => set({ open }),
  setActiveConversation: (activeConversationId) => set({ activeConversationId }),
  setRunState: ({ running, awaitingApproval }) => set({ running, awaitingApproval }),
  dismissContext: () => set({ contextDismissed: true }),
  resetContext: () => set({ contextDismissed: false }),
}));
