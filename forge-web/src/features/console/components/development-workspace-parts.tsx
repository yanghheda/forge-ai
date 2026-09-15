import { IconCheck } from "@arco-design/web-react/icon";
import type { ReactNode } from "react";

import styles from "./development-workspace.module.css";

export function Panel({ title, icon, extra, children }: { title: string; icon: ReactNode; extra?: ReactNode; children: ReactNode }) {
  return (
    <section className={styles.panel}>
      <header>
        <strong>
          {icon}
          {title}
        </strong>
        {extra}
      </header>
      <div className={styles.body}>{children}</div>
    </section>
  );
}
export function Stat({ icon, tone = "blue", label, value, foot }: { icon: ReactNode; tone?: "blue" | "orange" | "gray" | "red" | "green"; label: string; value: string; foot: string }) {
  return (
    <article className={styles.stat}>
      <div>
        <span className={`${styles.statIcon} ${styles[tone]}`}>{icon}</span>
        <span>{label}</span>
      </div>
      <strong>{value}</strong>
      <p>{foot}</p>
    </article>
  );
}
/* 交付链路中的单个节点，tone 表示该节点自身的状态而非整条链路。 */
export function ChainNode({ icon, title, caption, tone = "idle" }: { icon: ReactNode; title: string; caption: string; tone?: "ok" | "run" | "fail" | "idle" }) {
  return (
    <div className={`${styles.node} ${styles[tone]}`}>
      <span className={styles.nodeIcon}>{icon}</span>
      <div>
        <b>{title}</b>
        <small>{caption}</small>
      </div>
    </div>
  );
}
export function Guard({ ok, label, detail }: { ok: boolean; label: string; detail: string }) {
  return (
    <div className={styles.guardRow}>
      <span className={ok ? styles.guardOk : styles.guardFail}>{ok ? <IconCheck /> : "×"}</span>
      <strong>{label}</strong>
      <span>{detail}</span>
    </div>
  );
}
export function Empty({ text }: { text: string }) {
  return <p className={styles.empty}>{text}</p>;
}
export function shortSha(value: string | null | undefined) {
  return value ? value.slice(0, 8) : "—";
}
export function syncText(value: string | null | undefined) {
  return value ? `同步于 ${new Date(value).toLocaleString("zh-CN")}` : "暂无同步记录";
}
export function statusLabel(value: string) {
  return ({ TODO: "待处理", IN_PROGRESS: "进行中", DONE: "已完成", running: "运行中", success: "成功", failed: "失败", canceled: "已取消" } as Record<string, string>)[value] ?? value;
}
