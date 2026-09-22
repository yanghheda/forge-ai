/*
 * 根布局：SPA 中的 <html>/<body> 与 metadata 已由 index.html 承担，因此此处只负责套用应用外壳并通过 <Outlet /> 渲染匹配的子路由。
 * AppShell 内部使用 useLocation，必须位于路由上下文内，这正是把 RootLayout 作为根路由 element 的原因。
 */
import { Outlet } from "react-router";

import { AppShell } from "@/components/app-shell/app-shell";

export function RootLayout() {
  return (
    <AppShell>
      <Outlet />
    </AppShell>
  );
}
