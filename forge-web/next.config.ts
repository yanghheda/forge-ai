import type { NextConfig } from "next";

/**
 * 生产构建使用 webpack（见 package.json 的 `next build --webpack`）。
 * Turbopack 打包 @arco-design/web-react 时会把其循环依赖的 ESM 模块合并进同一个
 * SSR 模块组，并在模块初始化前读取默认导出绑定，导致静态预渲染抛出
 * `ReferenceError: Cannot access ... before initialization`。
 * 已确认 optimizePackageImports、transpilePackages、serverExternalPackages
 * 均无法规避；待 Turbopack 修复后再切回默认打包器。
 */
const nextConfig: NextConfig = {
  reactStrictMode: true,
  allowedDevOrigins: ["127.0.0.1"],
  output: "standalone",
  async rewrites() {
    const serverUrl = process.env.FORGE_SERVER_INTERNAL_URL ?? "http://forge-server:8080";
    return [
      {
        source: "/api/:path*",
        destination: `${serverUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
