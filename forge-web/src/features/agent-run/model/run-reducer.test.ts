import { describe, expect, it } from "vitest";
import {
  initialRunTimelineState,
  reduceRunEvent,
  type AgentEventEnvelope,
} from "./run-reducer";

const event = (
  sequence: number,
  type: string,
  payload: Record<string, unknown> = {},
): AgentEventEnvelope => ({
  runId: "01KTEST0000000000000000000",
  sequence,
  type,
  timestamp: "2026-09-05T00:00:00Z",
  requestId: "req_test",
  payload,
});

describe("reduceRunEvent", () => {
  it("ignores duplicate delivery by sequence", () => {
    const once = reduceRunEvent(
      initialRunTimelineState(0),
      event(1, "agent.started", { status: "RUNNING" }),
    );

    expect(reduceRunEvent(once, event(1, "agent.started"))).toBe(once);
    expect(once.lastSequence).toBe(1);
  });

  it("detects a gap without advancing the contiguous cursor", () => {
    const state = reduceRunEvent(initialRunTimelineState(3), event(5, "step.completed"));

    expect(state.connectionState).toBe("gap");
    expect(state.lastSequence).toBe(3);
  });

  it("projects step updates and closes on a terminal event", () => {
    const started = reduceRunEvent(
      initialRunTimelineState(0),
      event(1, "step.started", { stepNo: 1, name: "Prepare", status: "RUNNING" }),
    );
    const completed = reduceRunEvent(
      started,
      event(2, "step.completed", {
        stepNo: 1,
        name: "Prepare",
        status: "SUCCEEDED",
        summary: "done",
      }),
    );
    const terminal = reduceRunEvent(
      completed,
      event(3, "agent.completed", { status: "SUCCEEDED", summary: "finished" }),
    );

    expect(terminal.steps[1]).toMatchObject({ status: "SUCCEEDED", summary: "done" });
    expect(terminal.final).toEqual({ status: "SUCCEEDED", summary: "finished" });
    expect(terminal.connectionState).toBe("closed");
  });
});
