interface StreamPlaybackState {
  hasAnswer: boolean;
  isTyping: boolean;
}

interface LiveRunState {
  activeConversationId: number | undefined;
  effectiveRunId: string | undefined;
  isRunActive: boolean;
  latestRunConversationId: number | undefined;
  latestRunId: string | undefined;
}

export function isLiveRunPlaybackEnabled(state: LiveRunState) {
  const isLocallyStartedRun = state.latestRunConversationId === state.activeConversationId && state.latestRunId === state.effectiveRunId;
  return Boolean(state.effectiveRunId && (state.isRunActive || isLocallyStartedRun));
}

export function isStreamPlaybackVisible(stream: StreamPlaybackState, hasPersistedAgentAnswer: boolean) {
  return stream.hasAnswer && (stream.isTyping || !hasPersistedAgentAnswer);
}
