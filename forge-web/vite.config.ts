import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig, loadEnv } from "vite";

/**
 * Vite 构建配置：`vite dev` 在 3000 端口提供前端资源，并把同源 `/api` 请求
 * 反向代理到 forge-server 内部地址，避免浏览器跨域并保持会话 cookie 透传。
 */
export default defineConfig(({ mode }) => {
  // 第三个参数传空前缀，以便读取不带 VITE_ 前缀的 FORGE_SERVER_INTERNAL_URL。
  // loadEnv 会依次合并 .env、.env.local、.env.[mode]、.env.[mode].local。
  const env = loadEnv(mode, process.cwd(), "");
  const serverInternalUrl = env.FORGE_SERVER_INTERNAL_URL || "http://127.0.0.1:8080";

  return {
    plugins: [react()],
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
    server: {
      port: 3000,
      strictPort: false,
      proxy: {
        // 默认即原样透传 Cookie 请求头与 Set-Cookie 响应头，无需额外改写选项。
        "/api": {
          target: serverInternalUrl,
          changeOrigin: true,
        },
      },
    },
    preview: {
      port: 3000,
    },
  };
});
