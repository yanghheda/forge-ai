import type { ReactNode } from "react";

export type FieldErrors = Partial<Record<string, string>>;

export function schemaRule(
  schema: {
    safeParse: (value: unknown) => {
      success: boolean;
      error?: { issues: Array<{ message: string }> };
    };
  },
  requiredMessage: string,
) {
  return {
    validator: (value: unknown, callback: (error?: ReactNode) => void) => {
      if (typeof value !== "string" || !value.trim()) {
        callback(requiredMessage);
        return;
      }
      const result = schema.safeParse(value);
      callback(result.success ? undefined : (result.error?.issues[0]?.message ?? "请检查输入内容。"));
    },
  };
}

export function issuesToFieldErrors(issues: Array<{ path: PropertyKey[]; message: string }>): FieldErrors {
  return Object.fromEntries(issues.map((issue) => [String(issue.path[0] ?? "form"), issue.message]));
}
