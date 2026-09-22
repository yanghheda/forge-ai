/*
 * src/routing 是前端路由的唯一公开入口：仓库约定跨目录只能通过公开入口导入，
 * 因此外部（如 src/main.tsx）一律从 "@/routing" 取用路由实例，而不直接引用内部文件。
 */
export { appRouter } from "./app-router";
