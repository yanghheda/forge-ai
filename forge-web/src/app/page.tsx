import Link from "next/link";

import styles from "./page.module.css";

export default function HomePage() {
  return (
    <section className={styles.card}>
      <h1>ForgeAI 工作台骨架</h1>
      <p>本轮提供设计系统、状态边界和 API transport；业务数据将在后续会话接入。</p>
      <nav className={styles.actions} aria-label="占位页面入口">
        <Link className={styles.primaryLink} href="/login">
          登录占位页
        </Link>
        <Link className={styles.secondaryLink} href="/w/forge/p/demo/overview">
          项目概览占位页
        </Link>
      </nav>
    </section>
  );
}
