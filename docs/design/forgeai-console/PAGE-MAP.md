# ForgeAI Console 设计稿 → 前端还原清单

> 设计稿根目录：`docs/design/forgeai-console/`
> 技术栈：Next.js + TypeScript + Arco Design
> 先读 `colors_and_type.css` 建立全局主题变量映射，再按分组逐页还原。

---

## 全局基础（第 0 步）

| 文件 | 用途 | 说明 |
|------|------|------|
| `colors_and_type.css` | 设计令牌 | 颜色、字号、圆角、阴影、间距、侧边栏深色面，直接映射 Arco 主题变量与 CSS 变量 |
| `partials/project-shell.html` | 应用外壳 | 深色侧边栏 + 顶栏布局，所有业务页面共用，做成 AppLayout 组件 |
| `partials/auth-shell.html` | 认证外壳 | 左品牌右表单的登录/注册/初始化布局，做成 AuthLayout 组件 |

---

## 一、认证流程（无需侧边栏）

| 页面 | 文件 | 建议路由 | 类型 | 说明 |
|------|------|----------|------|------|
| 登录 | `login.html` | `/login` | 页面 | 邮箱 + 密码登录，底部"去注册"链接 |
| 注册 | `register.html` | `/register` | 页面 | 姓名 + 邮箱 + 密码 + 申请角色，注册后需管理员审核 |
| 实例初始化 | `init-wizard.html` | `/init` | 页面 | 仅首次使用，创建公司 + 超级管理员 + 上传 Logo |

---

## 二、需求协作

| 页面 | 文件 | 建议路由 | 类型 | 说明 |
|------|------|----------|------|------|
| 需求概览 | `overview.html` | `/requirements` | 页面 | 统计卡片 + 需求列表 + 新建需求按钮 |
| 新建需求（概览页抽屉） | `overview-create-requirement.html` | — | Drawer 组件 | 概览页"新建需求"按钮触发 |
| 我的需求 | `my-requirements.html` | `/my-requirements` | 页面 | 我负责的 + 我创建的 + 我参与的，列表可点击跳转详情 |
| 新建需求（我的需求抽屉） | `my-requirements-create.html` | — | Drawer 组件 | 我的需求页"新建需求"按钮触发 |
| 需求详情 | `requirement-detail.html` | `/requirements/[id]` | 页面 | 顶部 Tab 切换：概览/PRD/UX/开发/QA/发布，侧边栏详情 |
| PRD 文档 | `document-editor.html` | — | Tab 内组件 | 需求详情的 PRD 标签页内容 |
| UX 工作区 | `ux-workspace.html` | — | Tab 内组件 | 需求详情的 UX 标签页内容 |
| 任务看板 | `task-board.html` | — | Tab 内组件 | 需求详情的任务标签页内容，也可作为独立页 |
| 新建任务（抽屉） | `task-board-create.html` | — | Drawer 组件 | 任务看板"新建任务"按钮触发 |

---

## 三、交付执行

> 以下页面均为需求详情内的 Tab 页，也支持作为独立页面访问。

| 页面 | 文件 | 建议路由 | 类型 | 说明 |
|------|------|----------|------|------|
| 开发 | `development.html` | `/requirements/[id]/dev` | Tab/独立页 | 分支/MR/CI 状态 |
| QA 测试 | `qa.html` | `/requirements/[id]/qa` | Tab/独立页 | 测试用例列表 + 执行结果 |
| 新建测试用例（抽屉） | `qa-create-testcase.html` | — | Drawer 组件 | QA 页"新建测试用例"按钮触发 |
| 发布管理 | `release.html` | `/requirements/[id]/release` | Tab/独立页 | 发布计划 + 版本记录 |

---

## 四、Agent 观测

| 页面 | 文件 | 建议路由 | 类型 | 说明 |
|------|------|----------|------|------|
| Agent 指令中心 | `agent-command.html` | `/agent/command` | 页面 | 三栏：会话列表 + 对话区 + Run 详情/工具列表 |
| Agent 执行轨迹 | `agent-trace.html` | `/agent/trace/[id]` | 页面 | Trace 时间线 + 调用详情 |

> 全局 Agent 指令悬浮对话框（点击顶栏"Agent 指令"按钮唤起）可参考 `agent-command.html` 中栏对话区样式，做成全局浮层组件。

---

## 五、系统设置

| 页面 | 文件 | 建议路由 | 类型 | 说明 |
|------|------|----------|------|------|
| 成员管理 | `settings-members.html` | `/settings/members` | 页面 | 公司成员列表 + 角色分配 |
| 编辑成员（抽屉） | `settings-members-edit.html` | — | Drawer 组件 | 列表"编辑"按钮触发 |
| 集成配置 | `settings-integrations.html` | `/settings/integrations` | 页面 | GitLab/飞书/Webhook 集成列表 |
| 添加集成（抽屉） | `settings-integrations-add.html` | — | Drawer 组件 | "添加集成"按钮触发 |

---

## 六、原型导航（开发时可忽略）

| 页面 | 文件 | 说明 |
|------|------|------|
| 原型首页 | `index.html` | 所有页面入口导航，仅用于设计走查，正式项目不需要 |

---

## 还原顺序建议

1. 全局主题 + AppLayout / AuthLayout 外壳
2. 认证流程（登录 → 注册 → 初始化）
3. 需求协作（概览 → 我的需求 → 详情 → 子 Tab）
4. 交付执行（开发 → QA → 发布）
5. Agent 观测（指令中心 → 轨迹）
6. 系统设置（成员管理 → 集成配置）
7. 抽屉组件按页面逐个补齐
