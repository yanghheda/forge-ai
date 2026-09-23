import type { AgentContext } from "@/stores/use-agent-panel-store";

/* 需求详情路由形如 /requirements/123，可带交付阶段子路径，如 /requirements/123/qa */
const REQUIREMENT_PATH = /^\/requirements\/(\d+)(?:\/|$)/;
/* 缺陷详情路由形如 /defects/123 */
const DEFECT_PATH = /^\/defects\/(\d+)(?:\/|$)/;

/* 把路由中的数字片段转成合法工作项标识；非法或非正数返回 undefined */
function toId(segment: string): number | undefined {
  const id = Number(segment);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

/* 从路由路径解析当前工作项上下文；任务无详情路由，只能由页面显式设置 */
export function parseAgentContext(pathname: string): AgentContext | undefined {
  const requirement = REQUIREMENT_PATH.exec(pathname);
  if (requirement) {
    const id = toId(requirement[1]);
    if (id !== undefined) return { kind: "requirement", id };
  }
  const defect = DEFECT_PATH.exec(pathname);
  if (defect) {
    const id = toId(defect[1]);
    if (id !== undefined) return { kind: "defect", id };
  }
  return undefined;
}
