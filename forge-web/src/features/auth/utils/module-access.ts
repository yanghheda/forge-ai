/* 需求交付工作区中的可编辑模块；每个模块只允许对应角色写入，其他角色一律只读。 */
export type EditableModule = "prd" | "ux" | "development" | "qa" | "release";

/* 模块与可编辑公司角色的对应关系；产品文档只归产品，UX 文档只归设计，研发与发布归研发，测试归测试。
   领域管理员作为对应阶段的负责人，同样可编辑本模块。 */
const moduleRoles: Record<EditableModule, readonly string[]> = {
  prd: ["PRODUCT", "PRODUCT_ADMIN"],
  ux: ["UX", "UX_ADMIN"],
  development: ["DEVELOPER", "DEVELOPER_ADMIN"],
  qa: ["QA", "QA_ADMIN"],
  release: ["DEVELOPER", "DEVELOPER_ADMIN"],
};

/* 判断当前成员角色是否命中模块的任一可编辑角色；全局管理员与所有者不绕过该限制。 */
export function canEditModule(roles: readonly string[] | undefined, module: EditableModule): boolean {
  return moduleRoles[module].some((role) => roles?.includes(role) ?? false);
}
