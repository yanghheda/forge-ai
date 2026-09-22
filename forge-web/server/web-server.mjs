// forge-web 生产运行时：零依赖 Node 静态文件服务 + 同源 API 反向代理。
// 仅使用 Node 内置模块，替代 Next.js standalone server，保持 compose 部署契约。
import { createServer, request as httpRequest } from "node:http";
import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

/* 静态资源扩展名到响应 Content-Type 的映射，未命中时回退为二进制流。 */
const CONTENT_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".map": "application/json; charset=utf-8",
  ".txt": "text/plain; charset=utf-8",
};

/* 代理时不回传的逐跳响应头，避免与本地连接管理冲突。 */
const HOP_BY_HOP_HEADERS = new Set(["connection", "keep-alive", "transfer-encoding", "upgrade"]);

/* 存活探针响应体，被 tests/three-app-smoke.sh 的 grep 断言直接匹配。 */
const HEALTH_BODY = JSON.stringify({ application: "forge-web", status: "UP" });

/* 取出去掉 query 的原始路径，保留 `%2e` 等未解码形式，供目录穿越校验使用。 */
function rawPathname(requestUrl) {
  const raw = requestUrl || "/";
  const queryIndex = raw.indexOf("?");
  return queryIndex === -1 ? raw : raw.slice(0, queryIndex);
}

/* 把解码后的路径安全解析到 staticRoot 内；越界（目录穿越）时返回 null。 */
function resolveStaticPath(root, decodedPathname) {
  const candidate = path.join(root, decodedPathname);
  if (candidate !== root && !candidate.startsWith(root + path.sep)) {
    return null;
  }
  return candidate;
}

/* 判断文件是否存在且为普通文件。 */
async function isFile(candidate) {
  try {
    return (await stat(candidate)).isFile();
  } catch {
    return false;
  }
}

/* 命中文件时返回文件路径；目录则尝试其 index.html，否则返回 null。 */
async function resolveExistingFile(candidate) {
  try {
    if ((await stat(candidate)).isDirectory()) {
      const indexCandidate = path.join(candidate, "index.html");
      return (await isFile(indexCandidate)) ? indexCandidate : null;
    }
    return candidate;
  } catch {
    return null;
  }
}

/* 请求是否声明接受 HTML：仅此类请求允许 SPA 回退。 */
function acceptsHtml(req) {
  return (req.headers.accept || "").includes("text/html");
}

/* 输出纯文本响应；HEAD 请求不写响应体。 */
function sendText(res, status, body, method = "GET") {
  res.writeHead(status, { "content-type": "text/plain; charset=utf-8" });
  res.end(method === "HEAD" ? undefined : body);
}

/* 输出存活探针；content-length 固定，HEAD 不带响应体。 */
function sendHealth(res, method) {
  res.writeHead(200, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(HEALTH_BODY),
  });
  res.end(method === "HEAD" ? undefined : HEALTH_BODY);
}

/* 流式输出静态文件，按扩展名设置 Content-Type。 */
function sendFile(res, filePath, method) {
  const type = CONTENT_TYPES[path.extname(filePath).toLowerCase()] || "application/octet-stream";
  res.writeHead(200, { "content-type": type });
  if (method === "HEAD") {
    res.end();
    return;
  }
  const stream = createReadStream(filePath);
  stream.on("error", () => res.destroy());
  stream.pipe(res);
}

/* 把静态请求解析到 staticRoot；全部未命中且接受 HTML 时回退 index.html。 */
async function serveStatic(req, res, root, pathname, method) {
  let decodedPathname;
  try {
    decodedPathname = decodeURIComponent(pathname);
  } catch {
    sendText(res, 404, "Not Found", method);
    return;
  }

  const resolved = resolveStaticPath(root, decodedPathname);
  if (resolved) {
    const filePath = await resolveExistingFile(resolved);
    if (filePath) {
      sendFile(res, filePath, method);
      return;
    }
  }

  // SPA 回退：只有声明接受 HTML 的请求才返回首页，图片等资源缺失一律 404。
  if (acceptsHtml(req)) {
    const indexPath = path.join(root, "index.html");
    if (await isFile(indexPath)) {
      sendFile(res, indexPath, method);
      return;
    }
  }

  sendText(res, 404, "Not Found", method);
}

/* 保留 method、路径、query、请求体与请求头（去掉 host），流式转发并回传响应。 */
function proxyToUpstream(req, res, upstream) {
  const headers = { ...req.headers };
  delete headers.host;

  const proxyReq = httpRequest(
    {
      protocol: upstream.protocol,
      hostname: upstream.hostname,
      port: upstream.port || undefined,
      method: req.method,
      path: req.url,
      headers,
    },
    (proxyRes) => {
      res.statusCode = proxyRes.statusCode || 502;
      for (const [key, value] of Object.entries(proxyRes.headers)) {
        if (value === undefined || HOP_BY_HOP_HEADERS.has(key.toLowerCase())) {
          continue;
        }
        // set-cookie 可能有多条，setHeader 保持数组形式以输出多行 Set-Cookie。
        res.setHeader(key, value);
      }
      proxyRes.pipe(res);
    },
  );

  proxyReq.on("error", () => {
    if (res.headersSent) {
      res.destroy();
      return;
    }
    sendText(res, 502, "Bad Gateway");
  });

  req.pipe(proxyReq);
}

/**
 * 创建一个未 listen 的 HTTP server，便于测试用随机端口。
 * @param {{ staticRoot: string, serverUrl: string, apiPrefix?: string }} options 静态根目录、上游地址与 API 前缀
 * @returns {import("node:http").Server} 未启动的 server 实例
 */
export function createWebServer({ staticRoot, serverUrl, apiPrefix = "/api" }) {
  const root = path.resolve(staticRoot);
  const upstream = new URL(serverUrl);
  const isApiRequest = (pathname) => pathname === apiPrefix || pathname.startsWith(`${apiPrefix}/`);

  return createServer((req, res) => {
    const pathname = rawPathname(req.url);
    const method = req.method || "GET";

    if ((method === "GET" || method === "HEAD") && pathname === "/healthz") {
      sendHealth(res, method);
      return;
    }

    if (isApiRequest(pathname)) {
      proxyToUpstream(req, res, upstream);
      return;
    }

    if (method !== "GET" && method !== "HEAD") {
      sendText(res, 405, "Method Not Allowed", method);
      return;
    }

    void serveStatic(req, res, root, pathname, method);
  });
}

/**
 * 从环境变量读取配置并启动生产 server。
 * @returns {import("node:http").Server} 已开始 listen 的 server 实例
 */
export function startWebServer() {
  const staticRoot = path.resolve(import.meta.dirname, "..", "dist");
  const serverUrl = process.env.FORGE_SERVER_INTERNAL_URL || "http://forge-server:8080";
  const port = Number(process.env.PORT || 3000);
  const host = process.env.HOSTNAME || "0.0.0.0";

  const server = createWebServer({ staticRoot, serverUrl });
  server.listen(port, host, () => {
    console.log(`forge-web 静态服务已启动：http://${host}:${port} -> ${serverUrl}`);
  });
  return server;
}

// 仅在直接执行 `node server/web-server.mjs` 时自动启动；被 import 时不启动。
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  startWebServer();
}
