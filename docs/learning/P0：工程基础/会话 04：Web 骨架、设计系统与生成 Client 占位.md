# 会话 04：Web 骨架、设计系统与生成 Client 占位

## 我完成了什么

- 用户可见结果：`forge-web` 可以启动，提供登录占位页、项目概览路由、工作台外壳，以及 loading、error、empty 等基础状态。
- 建立 Arco Provider、TanStack Query、Zustand 空 UI store、统一 API transport、错误映射和生成 Client 的稳定接入点。
- 调用链：访问 `/w/{workspace}/p/{project}/overview` → `ProjectOverviewPage` 用 Zod 校验参数 → `ProjectOverview` → `useProjectOverview` → `loadProjectOverview` → `GeneratedApiClient.request()` → `ApiTransport.request()` → Next rewrite → `forge-server` → 页面渲染成功、空或错误状态。

## 我理解的核心设计

- 关键不变量：`page.tsx` 只负责路由参数和页面组合，不承担远程请求细节或复杂业务渲染。
- 关键不变量：跨 Feature 只能从目标 Feature 的公开 `index.ts` 导入，不能依赖内部 components、hooks 或 api 文件。
- 关键不变量：服务端资源属于 TanStack Query 管理的 server state；Zustand 只保存客户端 UI 状态，不能复制项目等服务端事实。
- `ApiTransport` 统一 base URL、`credentials: include`、JSON 解析和错误映射，使 Feature 不重复处理 HTTP 细节。
- Next rewrite 让浏览器只访问同源 `/api`，再由 Web 容器通过内部网络请求 Server；浏览器不需要知道 Server 容器地址。
- 当前 `GeneratedApiClient` 只是 transport 注入边界，并未真正从 OpenAPI 生成 DTO 和端点；`ProjectOverviewSummary` 仍是手写类型，所以类型防漂移尚未完全实现。
- `Providers` 应稳定持有 QueryClient，避免每次 render 创建新实例并清空缓存。
- 事务/一致性边界：Web 不拥有业务事务，也不能把本地 UI 状态当成提交成功的业务事实；最终状态以 Server 响应为准。
- 权限与安全边界：请求默认携带同源 cookie；Web 不直接连接 Agent、MySQL、Redis 或 Qdrant；路由参数先经 Zod 验证。
- 为什么不立即建设复杂代码生成平台：P0 只要求占位，当前 API 很少，身份和业务 DTO 还会变化；应在首个真实业务切片出现时补生成命令和 drift check。

## 失败路径

- 触发方式：路由参数不合法、Server 返回非 2xx 或非 JSON、错误信封字段缺失，或者 Feature 使用深层导入。
- 系统如何失败：非法路由进入 `notFound()`；HTTP 错误转换为带 status、code、requestId 的 `ApiError`；无法构成概览数据时返回 empty 状态；深层导入在 ESLint 门禁失败。
- 数据是否保持正确：这些失败不会让 Web 成为事实来源；错误结果不会写入 Zustand 冒充 Server 数据。
- 如何定位与恢复：用页面错误中的 requestId 对照 Server 日志；导入违规则按 ESLint 提示改为从 Feature 的 `index.ts` 导入。

## 测试证据

- `transport.test.ts`：验证 base URL、cookie credentials、成功 JSON 和错误信封映射。
- `project-overview-api.test.ts`：验证响应到概览摘要的转换，以及非法或缺失字段得到空结果。
- `use-project-overview.test.tsx`：验证 Hook 的加载、成功和错误状态。
- `project-overview-view.test.tsx`：验证展示组件的正常、空和错误呈现。
- `app-shell.test.tsx`、`providers.test.tsx`：验证工作台外壳和 Provider 基础行为。
- `tests/module-boundaries/check-boundaries.mjs` 使用故意深层导入的 fixture，证明 ESLint 边界规则确实会失败。
- 它不能证明什么：单元测试不能证明真实浏览器、Next rewrite、Server 和 cookie 的完整闭环；手写 DTO 也不能证明 OpenAPI 与 Web 类型不会漂移。

## 仍不清楚的问题

- 首个真实业务 API 出现时，应选择哪种 OpenAPI 生成器，并如何在 CI 中检测生成文件未更新？
- 哪些页面适合 Server Component 预取，哪些交互必须留在 Client Component 和 Query Hook 中？

