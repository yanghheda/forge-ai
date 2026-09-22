# forge-web

ForgeAI 的 Vite + React 单页用户工作台。该应用负责展示、输入校验、服务端状态缓存与交互状态，不承担最终授权，也不得直连 Agent、数据库、向量库或 GitLab。

## 目录结构

- `src/main.tsx`：SPA 入口，挂载 Arco 样式与全局设计变量后渲染 `AppProviders` + `RouterProvider`。
- `src/bootstrap/`：应用引导层（Provider 组合与根布局）。
- `src/routing/`：前端路由表，`/healthz` 不属于前端路由。
- `src/views/`：页面级视图，一行路由对应一个 `*-view.tsx`。
- `src/features/`、`src/components/`、`src/lib/`、`src/stores/`：功能切片、共享组件、API 访问与交互状态。
- `server/web-server.mjs`：生产运行时的静态资源服务，同源代理 `/api` 并提供 `/healthz`。

## 开发命令

```bash
npm ci
npm run dev
npm run lint
npm run typecheck
npm test
npm run build
```

浏览器请求默认使用同源 `/api` 前缀。开发时由 `vite.config.ts` 的 dev server 代理转发，
生产时由 `server/web-server.mjs` 转发给 Server。
本机开发时，在未提交的 `.env.development.local` 中设置
`FORGE_SERVER_INTERNAL_URL=http://127.0.0.1:8080`；`npm run dev` 会自动加载该文件。

`src/lib/api/generated` 是 OpenAPI 生成物的占位入口。契约事实来源始终是 `forge-server` 的 `/v3/api-docs`，不得手工修改生成文件来修复类型错误。
