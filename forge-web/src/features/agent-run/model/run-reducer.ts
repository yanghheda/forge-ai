export interface AgentEventEnvelope {
  runId: string;
  sequence: number;
  type: string;
  timestamp: string;
  requestId: string;
  payload: Record<string, unknown>;
}

export interface TimelineStep {
  stepNo: number;
  name: string;
  status: string;
  summary?: string;
}

export interface RunTimelineState {
  lastSequence: number;
  connectionState: "connecting" | "open" | "disconnected" | "gap" | "closed";
  steps: Record<number, TimelineStep>;
  toolCalls: Record<string, Record<string, unknown>>;
  approvals: Record<string, Record<string, unknown>>;
  plan: Record<string, unknown>[];
  final?: { status: string; summary?: string; errorCode?: string };
}

export function initialRunTimelineState(lastSequence: number): RunTimelineState {
  return {
    lastSequence,
    connectionState: "connecting",
    steps: {},
    toolCalls: {},
    approvals: {},
    plan: [],
  };
}

function text(payload: Record<string, unknown>, key: string): string | undefined {
  return typeof payload[key] === "string" ? payload[key] : undefined;
}

function number(payload: Record<string, unknown>, key: string): number | undefined {
  return typeof payload[key] === "number" ? payload[key] : undefined;
}

export function reduceRunEvent(
  state: RunTimelineState,
  event: AgentEventEnvelope,
): RunTimelineState {
  if (event.type === "heartbeat" || event.sequence <= state.lastSequence) {
    return state;
  }
  if (event.sequence !== state.lastSequence + 1) {
    return { ...state, connectionState: "gap" };
  }

  const next: RunTimelineState = {
    ...state,
    lastSequence: event.sequence,
    connectionState: "open",
  };
  if (event.type === "plan.created" && Array.isArray(event.payload.steps)) {
    return { ...next, plan: event.payload.steps as Record<string, unknown>[] };
  }
  if (event.type.startsWith("step.")) {
    const stepNo = number(event.payload, "stepNo");
    if (stepNo !== undefined) {
      return {
        ...next,
        steps: {
          ...state.steps,
          [stepNo]: {
            stepNo,
            name: text(event.payload, "name") ?? state.steps[stepNo]?.name ?? `Step ${stepNo}`,
            status: text(event.payload, "status") ?? state.steps[stepNo]?.status ?? "UNKNOWN",
            summary: text(event.payload, "summary") ?? state.steps[stepNo]?.summary,
          },
        },
      };
    }
  }
  if (event.type.startsWith("tool.")) {
    const callId = text(event.payload, "callId");
    if (callId) {
      return { ...next, toolCalls: { ...state.toolCalls, [callId]: event.payload } };
    }
  }
  if (event.type.startsWith("approval.")) {
    const approvalId = text(event.payload, "approvalId");
    if (approvalId) {
      return { ...next, approvals: { ...state.approvals, [approvalId]: event.payload } };
    }
  }
  if (["agent.completed", "agent.failed", "agent.cancelled"].includes(event.type)) {
    return {
      ...next,
      connectionState: "closed",
      final: {
        status: text(event.payload, "status") ?? "UNKNOWN",
        summary: text(event.payload, "summary"),
        errorCode: text(event.payload, "errorCode"),
      },
    };
  }
  return next;
}
