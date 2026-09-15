import { IconRobot } from "@arco-design/web-react/icon";
import type { ReactNode } from "react";

import styles from "./console.module.css";

export function AgentMessage({ title, children }: { title: string; children: ReactNode }) {
  return (
    <article className={styles.agentMsg}>
      <header className={styles.agentIdentity}>
        <span>
          <IconRobot />
        </span>
        <b>{title}</b>
      </header>
      {children}
    </article>
  );
}

export function ReasoningDetails({ reasoning, completed }: { reasoning: string[]; completed: boolean }) {
  return (
    <details className={styles.reasoning} open={!completed}>
      <summary>{completed ? "已完成思考" : "正在思考…"}</summary>
      {reasoning.length > 0 ? (
        <ol aria-live="polite">
          {reasoning.map((item, index) => (
            <li key={`${index}-${item}`}>{item}</li>
          ))}
        </ol>
      ) : (
        <p>正在分析上下文并形成执行计划…</p>
      )}
    </details>
  );
}
