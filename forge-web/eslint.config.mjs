import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import tseslint from "typescript-eslint";
import { defineConfig, globalIgnores } from "eslint/config";

/**
 * Feature 边界规则：跨 Feature 只能通过其公开 index.ts 导入，
 * 禁止深层路径直连，避免模块之间形成隐式耦合。
 */
const featureBoundaryRules = {
  "no-restricted-imports": [
    "error",
    {
      patterns: [
        {
          group: ["@/features/*/*", "@/features/*/**"],
          message: "跨 Feature 只能通过其公开 index.ts 导入。",
        },
      ],
    },
  ],
};

export default defineConfig([
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    // SPA 客户端源码使用浏览器全局变量，并启用 react-hooks 的 flat 推荐规则。
    files: ["src/**/*.{ts,tsx}"],
    extends: [reactHooks.configs.flat.recommended],
    languageOptions: {
      globals: {
        ...globals.browser,
      },
    },
  },
  {
    // 顶层页面装配、路由与共享模块不得越过 Feature 的公开边界。
    files: ["src/views/**/*.{ts,tsx}", "src/bootstrap/**/*.{ts,tsx}", "src/routing/**/*.{ts,tsx}", "src/components/**/*.{ts,tsx}", "src/lib/**/*.{ts,tsx}", "src/stores/**/*.{ts,tsx}"],
    rules: featureBoundaryRules,
  },
  {
    files: ["src/features/**/*.{ts,tsx}"],
    rules: featureBoundaryRules,
  },
  {
    files: ["tests/module-boundaries/fixtures/**/*.{ts,tsx}"],
    rules: featureBoundaryRules,
  },
  {
    // 构建配置与 Node 侧服务脚本运行在 Node 环境，需显式声明 Node 全局变量。
    files: ["server/**/*.mjs", "**/*.config.{ts,mjs,js}"],
    languageOptions: {
      globals: {
        ...globals.node,
      },
    },
  },
  globalIgnores(["dist/**", "coverage/**", "node_modules/**"]),
]);
