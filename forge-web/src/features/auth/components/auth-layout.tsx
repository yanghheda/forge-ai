import { IconCheckCircle, IconLock, IconRobot } from "@arco-design/web-react/icon";
import Link from "next/link";
import type { ReactNode } from "react";

import styles from "./auth-forms.module.css";

export function AuthLayout({ children, title, description }: { children: ReactNode; title: string; description: string }) {
  return (
    <div className={styles.page}>
      <aside className={styles.story}>
        <Link href="/" className={styles.logo}>
          <span>F</span> ForgeAI
        </Link>
        <div className={styles.storyContent}>
          <h1>
            {title.split(/\\n|\n/).map((line) => (
              <span key={line}>{line}</span>
            ))}
          </h1>
          <p>{description}</p>
          <ul>
            <li>
              <IconRobot />
              <span>
                <b>多智能体交付流水线</b>
                <small>PRD 生成、UX 设计、代码实现、QA 回归按状态机自动推进</small>
              </span>
            </li>
            <li>
              <IconLock />
              <span>
                <b>受控工具与权限边界</b>
                <small>Agent 不持有部署凭据，所有写操作经 Server Tool API 鉴权</small>
              </span>
            </li>
            <li>
              <IconCheckCircle />
              <span>
                <b>执行轨迹全程可追溯</b>
                <small>每次工具调用、人工确认与状态流转均有 Trace 记录</small>
              </span>
            </li>
          </ul>
        </div>
        <small>私有化部署 · 数据不出企业内网</small>
      </aside>
      <main className={styles.formSide}>
        {children}
        <p className={styles.security}>
          <IconLock /> 会话安全保护 · 所有操作可追踪
        </p>
      </main>
    </div>
  );
}
