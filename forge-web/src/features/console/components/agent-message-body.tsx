import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";

import styles from "./console.module.css";

export function AgentMessageBody({ content, streaming = false }: { content: string; streaming?: boolean }) {
  return (
    <div className={`${styles.messageBody} ${streaming ? styles.streamingAnswer : ""}`} aria-live={streaming ? "polite" : undefined}>
      <Markdown remarkPlugins={[remarkGfm]}>{content}</Markdown>
    </div>
  );
}
