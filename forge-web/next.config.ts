import type { NextConfig } from "next";

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
