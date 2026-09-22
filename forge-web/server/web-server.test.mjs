// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createServer, request as httpRequest } from "node:http";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";

import { createWebServer } from "./web-server.mjs";

/* 每个用例独立搭建的静态根目录；用例结束后连同临时 server 一并清理。 */
let staticRoot;
/* 当前用例创建的所有 server（被测服务与 stub 上游），afterEach 统一关闭。 */
let openServers;

const INDEX_HTML = '<!doctype html><html><body><div id="root">SPA</div></body></html>';
const APP_JS = 'console.log("app");';

/* 在随机端口 listen，返回实际端口号以避免端口冲突。 */
function listen(server) {
  return new Promise((resolve) => {
    server.listen(0, "127.0.0.1", () => {
      resolve(server.address().port);
    });
  });
}

/* 启动被测静态服务，返回其根地址。 */
async function startWeb(upstreamUrl) {
  const server = createWebServer({ staticRoot, serverUrl: upstreamUrl });
  openServers.push(server);
  const port = await listen(server);
  return `http://127.0.0.1:${port}`;
}

/* 启动 stub 上游，模拟 forge-server：仅 /api/v1/ping 返回状态码、body 与 Set-Cookie。 */
async function startUpstream() {
  const server = createServer((req, res) => {
    if (req.url.startsWith("/api/v1/ping")) {
      res.statusCode = 201;
      res.setHeader("content-type", "application/json; charset=utf-8");
      res.setHeader("set-cookie", ["session=abc; Path=/", "csrf=xyz; Path=/"]);
      res.end(JSON.stringify({ ok: true, url: req.url }));
      return;
    }
    res.statusCode = 404;
    res.end("upstream-miss");
  });
  openServers.push(server);
  const port = await listen(server);
  return `http://127.0.0.1:${port}`;
}

/* 用原始 http 请求发送未被 URL 归一化的路径（用于目录穿越用例）。 */
function rawRequest(base, requestPath) {
  const { hostname, port } = new URL(base);
  return new Promise((resolve, reject) => {
    const req = httpRequest({ hostname, port, path: requestPath, method: "GET" }, (res) => {
      let body = "";
      res.setEncoding("utf8");
      res.on("data", (chunk) => {
        body += chunk;
      });
      res.on("end", () => resolve({ status: res.statusCode, body }));
    });
    req.on("error", reject);
    req.end();
  });
}

beforeEach(() => {
  staticRoot = mkdtempSync(path.join(os.tmpdir(), "forge-web-server-"));
  mkdirSync(path.join(staticRoot, "assets"));
  writeFileSync(path.join(staticRoot, "index.html"), INDEX_HTML);
  writeFileSync(path.join(staticRoot, "assets", "app.js"), APP_JS);
  openServers = [];
});

afterEach(async () => {
  await Promise.all(
    openServers.map(
      (server) =>
        new Promise((resolve) => {
          server.closeAllConnections?.();
          server.close(() => resolve());
        }),
    ),
  );
  rmSync(staticRoot, { recursive: true, force: true });
});

describe("forge-web 静态服务", () => {
  it("healthz 返回固定 JSON 存活探针", async () => {
    const base = await startWeb(await startUpstream());

    const response = await fetch(`${base}/healthz`);

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("application/json");
    await expect(response.json()).resolves.toEqual({ application: "forge-web", status: "UP" });
  });

  it("命中静态资源时按扩展名返回 Content-Type", async () => {
    const base = await startWeb(await startUpstream());

    const response = await fetch(`${base}/assets/app.js`);

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("text/javascript");
    await expect(response.text()).resolves.toBe(APP_JS);
  });

  it("未命中的前端路由在 Accept: text/html 时回退 index.html", async () => {
    const base = await startWeb(await startUpstream());

    const response = await fetch(`${base}/requirements/12`, { headers: { Accept: "text/html" } });

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("text/html");
    await expect(response.text()).resolves.toBe(INDEX_HTML);
  });

  it("缺失的静态资源返回 404 而不是 HTML 回退", async () => {
    const base = await startWeb(await startUpstream());

    const response = await fetch(`${base}/assets/missing.png`);

    expect(response.status).toBe(404);
    expect(response.headers.get("content-type")).toContain("text/plain");
    await expect(response.text()).resolves.not.toContain("<html");
  });

  it("代理 /api 请求并透传状态码、body 与多条 Set-Cookie", async () => {
    const base = await startWeb(await startUpstream());

    const response = await fetch(`${base}/api/v1/ping?x=1`);

    expect(response.status).toBe(201);
    await expect(response.json()).resolves.toEqual({ ok: true, url: "/api/v1/ping?x=1" });
    const cookies = response.headers.getSetCookie();
    expect(cookies).toHaveLength(2);
    expect(cookies[0]).toContain("session=abc");
    expect(cookies[1]).toContain("csrf=xyz");
  });

  it("目录穿越路径不会读取 staticRoot 之外的文件", async () => {
    const base = await startWeb(await startUpstream());

    // 编码形式绕过 URL 归一化，直达服务端的解码与越界校验。
    const encoded = await fetch(`${base}/%2e%2e/%2e%2e/etc/passwd`);
    expect(encoded.status).toBe(404);
    await expect(encoded.text()).resolves.not.toContain("root:");

    // 原始形式直接发送未归一化路径。
    const plain = await rawRequest(base, "/../../etc/passwd");
    expect(plain.status).toBe(404);
    expect(plain.body).not.toContain("root:");
  });

  it("非 GET/HEAD 且非 /api 的请求返回 405", async () => {
    const base = await startWeb(await startUpstream());

    const response = await fetch(`${base}/anything`, { method: "POST" });

    expect(response.status).toBe(405);
  });

  it("上游不可达时返回 502", async () => {
    const base = await startWeb("http://127.0.0.1:1");

    const response = await fetch(`${base}/api/v1/ping`);

    expect(response.status).toBe(502);
  });
});
