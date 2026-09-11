"use client";

import { Badge, Button, Drawer, Empty, Input, Modal, Spin } from "@arco-design/web-react";
import { IconApps, IconBranch, IconCommand, IconDashboard, IconHistory, IconMenuFold, IconMenuUnfold, IconNotification, IconRobot, IconSearch, IconSettings, IconUser } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { type ReactNode, useEffect, useState } from "react";

import { getUnreadNotificationCount, listNotifications, markNotificationRead, searchConsole } from "@/features/console";
import { useShellStore } from "@/stores/use-shell-store";
import styles from "./app-shell.module.css";

export interface AppShellProps { children: ReactNode; }
interface NavItem { label: string; href: string; icon: ReactNode; active: boolean; }

export function AppShell({ children }: AppShellProps) {
  const pathname = usePathname() ?? "/";
  const publicPage = ["/", "/login", "/register", "/init"].includes(pathname);
  const navigationCollapsed = useShellStore((state) => state.navigationCollapsed);
  const toggleNavigation = useShellStore((state) => state.toggleNavigation);
  const queryClient = useQueryClient();
  const [searchOpen, setSearchOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [query, setQuery] = useState("");
  const search = useQuery({ queryKey: ["global-search", query], queryFn: () => searchConsole(query), enabled: searchOpen && query.trim().length >= 2 });
  const unread = useQuery({ queryKey: ["notifications", "unread"], queryFn: () => getUnreadNotificationCount(), enabled: !publicPage, refetchInterval: 30_000 });
  const notifications = useQuery({ queryKey: ["notifications", "list"], queryFn: () => listNotifications(), enabled: notificationsOpen });
  const read = useMutation({ mutationFn: (id: number) => markNotificationRead(id), onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["notifications"] }); } });
  useEffect(() => { const onKey = (event: KeyboardEvent) => { if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") { event.preventDefault(); setSearchOpen(true); } }; window.addEventListener("keydown", onKey); return () => window.removeEventListener("keydown", onKey); }, []);
  if (publicPage) return <main className={styles.publicPage}>{children}</main>;

  const items: NavItem[] = [
    { label: "需求概览", href: "/overview", icon: <IconDashboard />, active: pathname === "/overview" },
    { label: "我的需求", href: "/my-requirements", icon: <IconUser />, active: pathname === "/my-requirements" },
    { label: "任务看板", href: "/task-board", icon: <IconBranch />, active: pathname === "/task-board" },
  ];

  return <div className={`${styles.shell} ${navigationCollapsed ? styles.collapsed : ""}`}>
    <aside className={styles.sidebar}>
      <Link className={styles.brand} href="/overview" aria-label="ForgeAI 首页">
        <span className={styles.brandMark}><Image src="/api/v1/branding/company-logo" alt="" width={36} height={36} unoptimized onError={(event) => { event.currentTarget.hidden = true; event.currentTarget.parentElement?.classList.add(styles.brandFallback); }} /><b>F</b></span>
        {!navigationCollapsed && <span><strong>ForgeAI</strong><small>交付操作系统</small></span>}
      </Link>
      <nav className={styles.nav} aria-label="主导航">
        <p className={styles.navLabel}>{navigationCollapsed ? "" : "工作台"}</p>
        {items.map((item) => <Link key={item.label} href={item.href} className={`${styles.navItem} ${item.active ? styles.active : ""}`} title={navigationCollapsed ? item.label : undefined}>
          <span className={styles.navIcon}>{item.icon}</span>{!navigationCollapsed && item.label}
        </Link>)}
        <p className={styles.navLabel}>{navigationCollapsed ? "" : "Agent"}</p>
        <Link href="/agent/command" className={`${styles.navItem} ${pathname === "/agent/command" ? styles.active : ""}`}><span className={styles.navIcon}><IconCommand /></span>{!navigationCollapsed && "Agent 指令"}</Link>
        <Link href="/agent/trace/demo-run" className={`${styles.navItem} ${pathname.startsWith("/agent/trace") ? styles.active : ""}`}><span className={styles.navIcon}><IconHistory /></span>{!navigationCollapsed && "执行轨迹"}</Link>
        <p className={styles.navLabel}>{navigationCollapsed ? "" : "系统设置"}</p>
        <Link href="/settings/members" className={`${styles.navItem} ${pathname === "/settings/members" ? styles.active : ""}`}><span className={styles.navIcon}><IconSettings /></span>{!navigationCollapsed && "成员管理"}</Link>
        <Link href="/settings/integrations" className={`${styles.navItem} ${pathname === "/settings/integrations" ? styles.active : ""}`}><span className={styles.navIcon}><IconApps /></span>{!navigationCollapsed && "集成设置"}</Link>
      </nav>
      <button className={styles.collapseButton} onClick={toggleNavigation} aria-label={navigationCollapsed ? "展开导航" : "收起导航"}>
        {navigationCollapsed ? <IconMenuUnfold /> : <IconMenuFold />}{!navigationCollapsed && <span>收起导航</span>}
      </button>
    </aside>
    <div className={styles.organization}>
      <header className={styles.topbar}>
        <button className={styles.mobileMenu} onClick={toggleNavigation} aria-label={navigationCollapsed ? "展开导航" : "收起导航"}>{navigationCollapsed ? <IconMenuUnfold /> : <IconMenuFold />}</button>
        <div className={styles.breadcrumb}><span>公司工作台</span><b>/</b><strong>{pageName(pathname)}</strong></div>
        <div className={styles.topActions}>
          <button className={styles.search} onClick={() => setSearchOpen(true)}><IconSearch /><span>搜索</span><kbd>⌘ K</kbd></button>
          <Button className={styles.agentButton} href="/agent/command"><IconRobot />Agent 指令</Button>
          <Badge count={unread.data?.count ?? 0} dot><button className={styles.iconButton} aria-label="通知" onClick={() => setNotificationsOpen(true)}><IconNotification /></button></Badge>
          <span className={styles.avatar}>张</span>
        </div>
      </header>
      <main className={styles.content}>{children}</main>
      <Modal visible={searchOpen} title="全局搜索" footer={null} autoFocus={false} onCancel={() => { setSearchOpen(false); setQuery(""); }}>
        <Input size="large" autoFocus prefix={<IconSearch />} value={query} onChange={setQuery} placeholder="搜索需求、文档或公司成员" />
        <div className={styles.searchResults}>{search.isFetching && <Spin />}{query.trim().length < 2 && <Empty description="输入至少两个字符" />}{search.data?.map((item) => <Link key={`${item.type}-${item.id}`} href={item.type === "REQUIREMENT" ? `/requirements/${item.id}` : "#"} onClick={() => setSearchOpen(false)}><b>{item.title}</b><span>{item.type} · {item.subtitle}</span></Link>)}</div>
      </Modal>
      <Drawer width={420} visible={notificationsOpen} title="通知" footer={null} onCancel={() => setNotificationsOpen(false)}>
        <div className={styles.notifications}>{notifications.isFetching && <Spin />}{notifications.data?.length === 0 && <Empty description="暂无通知" />}{notifications.data?.map((item) => <button key={item.id} className={item.readAt ? styles.readNotification : ""} onClick={() => { if (!item.readAt) read.mutate(item.id); }}><b>{item.title}</b><span>{item.body}</span><time>{new Date(item.createdAt).toLocaleString("zh-CN")}</time></button>)}</div>
      </Drawer>
    </div>
  </div>;
}

function pageName(pathname: string) {
  if (pathname === "/overview") return "需求概览";
  if (pathname === "/my-requirements") return "我的需求";
  if (pathname === "/task-board") return "任务看板";
  if (pathname.startsWith("/agent/command")) return "Agent 指令中心";
  if (pathname.startsWith("/agent/trace")) return "执行轨迹";
  if (pathname.startsWith("/settings/members")) return "成员管理";
  if (pathname.startsWith("/settings/integrations")) return "集成配置";
  if (pathname.startsWith("/requirements/")) return "需求详情";
  return "ForgeAI";
}
