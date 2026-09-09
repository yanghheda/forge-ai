"use client";

import { IconApps, IconDashboard, IconFile, IconMenuFold, IconMenuUnfold, IconRobot, IconSettings } from "@arco-design/web-react/icon";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

import { useShellStore } from "@/stores/use-shell-store";
import styles from "./app-shell.module.css";

export interface AppShellProps { children: ReactNode; }
interface NavItem { label: string; href: string; icon: ReactNode; active: boolean; }

export function AppShell({ children }: AppShellProps) {
  const pathname = usePathname() ?? "/";
  const navigationCollapsed = useShellStore((state) => state.navigationCollapsed);
  const toggleNavigation = useShellStore((state) => state.toggleNavigation);
  const route = pathname.match(/^\/w\/([^/]+)(?:\/p\/([^/]+))?/);
  const workspace = route?.[1];
  const project = route?.[2];

  if (pathname === "/" || pathname === "/login") return <main className={styles.publicPage}>{children}</main>;

  const workspaceBase = workspace ? `/w/${workspace}` : "/";
  const projectBase = project ? `${workspaceBase}/p/${project}` : undefined;
  const items: NavItem[] = projectBase ? [
    { label: "项目概览", href: `${projectBase}/overview`, icon: <IconDashboard />, active: pathname.includes("/overview") },
    { label: "需求与交付", href: `${projectBase}/overview#requirements`, icon: <IconFile />, active: pathname.includes("/requirements") },
    { label: "UX 工作台", href: `${projectBase}/ux`, icon: <IconApps />, active: pathname.includes("/ux") },
    { label: "Agent 运行", href: `${projectBase}/overview#agent-runs`, icon: <IconRobot />, active: pathname.includes("/agent-runs") },
  ] : [{ label: "全部项目", href: workspaceBase, icon: <IconDashboard />, active: pathname === workspaceBase }];

  return <div className={`${styles.shell} ${navigationCollapsed ? styles.collapsed : ""}`}>
    <aside className={styles.sidebar}>
      <Link className={styles.brand} href={workspaceBase} aria-label="ForgeAI 首页">
        <span className={styles.brandMark}>F</span>
        {!navigationCollapsed && <span><strong>ForgeAI</strong><small>交付操作系统</small></span>}
      </Link>
      <nav className={styles.nav} aria-label="主导航">
        <p className={styles.navLabel}>{navigationCollapsed ? "" : project ? "项目空间" : "工作空间"}</p>
        {items.map((item) => <Link key={item.label} href={item.href} className={`${styles.navItem} ${item.active ? styles.active : ""}`} title={navigationCollapsed ? item.label : undefined}>
          <span className={styles.navIcon}>{item.icon}</span>{!navigationCollapsed && item.label}
        </Link>)}
        {workspace && <>
          <p className={styles.navLabel}>{navigationCollapsed ? "" : "管理"}</p>
          <Link href={`${workspaceBase}/settings/members`} className={`${styles.navItem} ${pathname.includes("/settings/members") ? styles.active : ""}`}><span className={styles.navIcon}><IconSettings /></span>{!navigationCollapsed && "成员与权限"}</Link>
          <Link href={`${workspaceBase}/settings/gitlab`} className={`${styles.navItem} ${pathname.includes("/settings/gitlab") ? styles.active : ""}`}><span className={styles.navIcon}><IconApps /></span>{!navigationCollapsed && "GitLab 集成"}</Link>
        </>}
      </nav>
      <button className={styles.collapseButton} onClick={toggleNavigation} aria-label={navigationCollapsed ? "展开导航" : "收起导航"}>
        {navigationCollapsed ? <IconMenuUnfold /> : <IconMenuFold />}{!navigationCollapsed && <span>收起导航</span>}
      </button>
    </aside>
    <div className={styles.workspace}>
      <header className={styles.topbar}>
        <button className={styles.mobileMenu} onClick={toggleNavigation} aria-label={navigationCollapsed ? "展开导航" : "收起导航"}>{navigationCollapsed ? <IconMenuUnfold /> : <IconMenuFold />}</button>
        <div className={styles.breadcrumb}><span>{workspace ?? "工作空间"}</span>{project && <><b>/</b><strong>{project}</strong></>}</div>
        <div className={styles.systemState}><i /> 系统运行正常</div>
      </header>
      <main className={styles.content}>{children}</main>
    </div>
  </div>;
}
