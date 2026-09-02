"use client";

import { Button, Layout, Typography } from "@arco-design/web-react";
import type { ReactNode } from "react";

import { useShellStore } from "@/stores/use-shell-store";

import styles from "./app-shell.module.css";

const { Header, Sider, Content } = Layout;

export interface AppShellProps {
  children: ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  const navigationCollapsed = useShellStore((state) => state.navigationCollapsed);
  const toggleNavigation = useShellStore((state) => state.toggleNavigation);

  return (
    <Layout className={styles.shell}>
      <Header className={styles.header}>
        <Typography.Title heading={5} className={styles.brand}>
          ForgeAI
        </Typography.Title>
        <Typography.Text className={styles.subtitle}>
          AI-Native Software Delivery Workbench
        </Typography.Text>
      </Header>
      <Layout>
        <Sider collapsed={navigationCollapsed} collapsible trigger={null} className={styles.sider}>
          <Button type="text" onClick={toggleNavigation} aria-label={navigationCollapsed ? "展开导航" : "收起导航"}>
            {navigationCollapsed ? "展开" : "收起导航"}
          </Button>
          {!navigationCollapsed && <Typography.Text>项目工作台</Typography.Text>}
        </Sider>
        <Content className={styles.content}>{children}</Content>
      </Layout>
    </Layout>
  );
}
