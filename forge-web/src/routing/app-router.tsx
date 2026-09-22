/*
 * 前端路由表：以 RootLayout 作为根路由 element，子路由 path 与 @/views/* 视图文件及命名导出严格一一对应。
 * 说明：/healthz 由生产环境的 Node 服务（server/web-server.mjs）直接处理，不属于前端路由，因此不在此注册。
 */
import { createBrowserRouter } from "react-router";

import { RootLayout } from "@/bootstrap/root-layout";
import { AgentCommandView } from "@/views/agent-command-view";
import { AgentTraceView } from "@/views/agent-trace-view";
import { HomeView } from "@/views/home-view";
import { InitView } from "@/views/init-view";
import { IntegrationSettingsView } from "@/views/integration-settings-view";
import { LoginView } from "@/views/login-view";
import { MembersSettingsView } from "@/views/members-settings-view";
import { MyRequirementsView } from "@/views/my-requirements-view";
import { NotFoundView } from "@/views/not-found-view";
import { OverviewView } from "@/views/overview-view";
import { RegisterView } from "@/views/register-view";
import { RequirementDetailView } from "@/views/requirement-detail-view";
import { RequirementDevelopmentView } from "@/views/requirement-development-view";
import { RequirementDevView } from "@/views/requirement-dev-view";
import { RequirementDocumentsView } from "@/views/requirement-documents-view";
import { RequirementListView } from "@/views/requirement-list-view";
import { RequirementQaView } from "@/views/requirement-qa-view";
import { RequirementReleaseView } from "@/views/requirement-release-view";
import { RequirementUxView } from "@/views/requirement-ux-view";
import { TaskBoardView } from "@/views/task-board-view";

export const appRouter = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      { path: "/", element: <HomeView /> },
      { path: "/login", element: <LoginView /> },
      { path: "/register", element: <RegisterView /> },
      { path: "/init", element: <InitView /> },
      { path: "/overview", element: <OverviewView /> },
      { path: "/requirements", element: <RequirementListView /> },
      { path: "/requirements/:id", element: <RequirementDetailView /> },
      { path: "/requirements/:id/documents", element: <RequirementDocumentsView /> },
      { path: "/requirements/:id/ux", element: <RequirementUxView /> },
      { path: "/requirements/:id/dev", element: <RequirementDevView /> },
      { path: "/requirements/:id/development", element: <RequirementDevelopmentView /> },
      { path: "/requirements/:id/qa", element: <RequirementQaView /> },
      { path: "/requirements/:id/release", element: <RequirementReleaseView /> },
      { path: "/my-requirements", element: <MyRequirementsView /> },
      { path: "/task-board", element: <TaskBoardView /> },
      { path: "/agent/command", element: <AgentCommandView /> },
      { path: "/agent/trace/:id", element: <AgentTraceView /> },
      { path: "/settings/members", element: <MembersSettingsView /> },
      { path: "/settings/integrations", element: <IntegrationSettingsView /> },
      { path: "*", element: <NotFoundView /> },
    ],
  },
]);
