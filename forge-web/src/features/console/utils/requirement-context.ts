/* 需求详情路由形如 /requirements/123，可带交付阶段子路径，如 /requirements/123/qa */
const REQUIREMENT_PATH = /^\/requirements\/(\d+)(?:\/|$)/;

/* 从路由路径解析当前需求标识；非需求详情页或标识非法时返回 undefined */
export function parseRequirementId(pathname: string): number | undefined {
  const matched = REQUIREMENT_PATH.exec(pathname);
  if (!matched) return undefined;
  const id = Number(matched[1]);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}
