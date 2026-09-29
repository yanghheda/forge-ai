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
