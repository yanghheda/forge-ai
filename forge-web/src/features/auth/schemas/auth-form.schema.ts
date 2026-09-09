import { z } from "zod";

const slug = z.string().regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/, "仅支持小写字母、数字和短横线").max(80);

export const loginSchema = z.object({
  email: z.email("请输入有效邮箱").max(320),
  password: z.string().min(1, "请输入密码"),
});

export const initializeSchema = z.object({
  adminEmail: z.email("请输入有效邮箱").max(320),
  adminDisplayName: z.string().trim().min(1, "请输入管理员名称").max(120),
  password: z.string().min(12, "密码至少 12 个字符").refine((value) => /[A-Za-z]/.test(value) && /\d/.test(value), "密码必须包含字母和数字"),
  organizationName: z.string().trim().min(1, "请输入组织名称").max(120),
  organizationSlug: slug,
  workspaceName: z.string().trim().min(1, "请输入工作空间名称").max(120),
  workspaceSlug: slug,
});
