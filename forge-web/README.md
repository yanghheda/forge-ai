# forge-web

ForgeAI 的 Next.js 用户工作台。该应用负责展示、输入校验、服务端状态缓存与交互状态，不承担最终授权，也不得直连 Agent、数据库、向量库或 GitLab。

## 开发命令

```bash
npm ci
npm run dev
npm run lint
npm run typecheck
npm test
npm run build
```

浏览器请求默认使用同源 `/api` 前缀，再由 Next.js 转发给 Server。
本机开发时，在未提交的 `.env.development.local` 中设置
`FORGE_SERVER_INTERNAL_URL=http://127.0.0.1:8080`；`npm run dev` 会自动加载该文件。

`src/lib/api/generated` 是 OpenAPI 生成物的占位入口。契约事实来源始终是 `forge-server` 的 `/v3/api-docs`，不得手工修改生成文件来修复类型错误。
