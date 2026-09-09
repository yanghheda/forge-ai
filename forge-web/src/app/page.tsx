"use client";

import { IconArrowRight, IconBranch, IconCheckCircle, IconRobot } from "@arco-design/web-react/icon";
import Link from "next/link";
import styles from "./page.module.css";

const capabilities = [
  { icon: <IconRobot />, title: "AI 协作，而非黑盒自动化", text: "从上下文、执行计划到审批记录，每一步都清晰可追溯。" },
  { icon: <IconBranch />, title: "以需求贯穿交付链路", text: "每条 Requirement 直接连接产品、UX、开发、QA 与 Release。" },
  { icon: <IconCheckCircle />, title: "策略内建，交付可信", text: "权限、Guard 与高风险审批随流程运行，而不是事后补救。" },
];

export default function HomePage() {
  return <div className={styles.page}>
    <header className={styles.header}><Link href="/" className={styles.logo}><span>F</span> ForgeAI</Link><Link href="/login" className={styles.login}>进入工作台 <IconArrowRight /></Link></header>
    <main>
      <section className={styles.hero}>
        <div className={styles.glow} />
        <div className={styles.eyebrow}><i /> AI 原生软件交付</div>
        <h1>把复杂的软件交付，<br /><em>变成清晰的协作流程。</em></h1>
        <p>ForgeAI 是可部署在公司内网的开源智能交付平台，以需求连接产品、设计、研发、测试与发布。</p>
        <div className={styles.actions}><Link href="/login" className={styles.primary}>开始使用 <IconArrowRight /></Link><a href="#capabilities" className={styles.secondary}>了解能力</a></div>
        <div className={styles.preview} aria-label="ForgeAI 产品预览">
          <div className={styles.previewBar}><span /><span /><span /><b>FORGE / 当前需求概览</b></div>
          <div className={styles.previewBody}><aside><strong>F</strong><i /><i /><i /></aside><div className={styles.previewContent}>
            <small>需求概览</small><h2>早上好，继续推进交付</h2><div className={styles.metrics}><span><b>18</b>全部需求</span><span><b>12</b>进行中需求</span><span><b>6</b>已完成</span></div>
            <div className={styles.flow}><span>产品 <b>8</b></span><i /><span>UX <b>4</b></span><i /><span>开发 <b>6</b></span><i /><span>QA <b>3</b></span></div>
          </div></div>
        </div>
      </section>
      <section id="capabilities" className={styles.capabilities}>{capabilities.map((item) => <article key={item.title}><div>{item.icon}</div><h2>{item.title}</h2><p>{item.text}</p></article>)}</section>
    </main>
    <footer className={styles.footer}>ForgeAI <span>·</span> 以智能锻造软件交付。</footer>
  </div>;
}
