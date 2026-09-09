"use client";

import { IconApps, IconDashboard, IconMenuFold, IconMenuUnfold, IconSettings, IconUser } from "@arco-design/web-react/icon";
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
  if (pathname === "/" || pathname === "/login") return <main className={styles.publicPage}>{children}</main>;

  const items: NavItem[] = [
    { label: "当前需求概览", href: "/overview", icon: <IconDashboard />, active: pathname === "/overview" },
    { label: "我的需求", href: "/my-requirements", icon: <IconUser />, active: pathname === "/my-requirements" },
  ];

  return <div className={`${styles.shell} ${navigationCollapsed ? styles.collapsed : ""}`}>
    <aside className={styles.sidebar}>
      <Link className={styles.brand} href="/overview" aria-label="ForgeAI 首页">
        <span className={styles.brandMark}>F</span>
        {!navigationCollapsed && <span><strong>ForgeAI</strong><small>交付操作系统</small></span>}
      </Link>
      <nav className={styles.nav} aria-label="主导航">
        <p className={styles.navLabel}>{navigationCollapsed ? "" : "需求中心"}</p>
        {items.map((item) => <Link key={item.label} href={item.href} className={`${styles.navItem} ${item.active ? styles.active : ""}`} title={navigationCollapsed ? item.label : undefined}>
          <span className={styles.navIcon}>{item.icon}</span>{!navigationCollapsed && item.label}
        </Link>)}
        <p className={styles.navLabel}>{navigationCollapsed ? "" : "公司设置"}</p>
        <Link href="/settings/members" className={`${styles.navItem} ${pathname === "/settings/members" ? styles.active : ""}`}><span className={styles.navIcon}><IconSettings /></span>{!navigationCollapsed && "成员与角色"}</Link>
        <Link href="/settings/integrations" className={`${styles.navItem} ${pathname === "/settings/integrations" ? styles.active : ""}`}><span className={styles.navIcon}><IconApps /></span>{!navigationCollapsed && "集成设置"}</Link>
      </nav>
      <button className={styles.collapseButton} onClick={toggleNavigation} aria-label={navigationCollapsed ? "展开导航" : "收起导航"}>
        {navigationCollapsed ? <IconMenuUnfold /> : <IconMenuFold />}{!navigationCollapsed && <span>收起导航</span>}
      </button>
    </aside>
    <div className={styles.workspace}>
      <header className={styles.topbar}>
        <button className={styles.mobileMenu} onClick={toggleNavigation} aria-label={navigationCollapsed ? "展开导航" : "收起导航"}>{navigationCollapsed ? <IconMenuUnfold /> : <IconMenuFold />}</button>
        <div className={styles.breadcrumb}><span>公司交付平台</span><b>/</b><strong>需求中心</strong></div>
        <div className={styles.systemState}><i /> 系统运行正常</div>
      </header>
      <main className={styles.content}>{children}</main>
    </div>
  </div>;
}
