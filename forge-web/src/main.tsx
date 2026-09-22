/*
 * 应用引导入口：在浏览器端创建 React 根并挂载路由应用。
 * 样式引入顺序固定：先加载 Arco Design 基础样式，再加载项目样式（base.css）以便后者覆盖主题变量。
 */
import "@arco-design/web-react/dist/css/arco.css";
import "@/styles/base.css";

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";

import { AppProviders } from "@/bootstrap/app-providers";
import { appRouter } from "@/routing";

const container = document.getElementById("root");

if (!container) {
  throw new Error("未找到根节点 #root，无法挂载应用。");
}

createRoot(container).render(
  <StrictMode>
    <AppProviders>
      <RouterProvider router={appRouter} />
    </AppProviders>
  </StrictMode>,
);
