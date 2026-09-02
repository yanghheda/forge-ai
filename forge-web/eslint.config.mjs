import { defineConfig, globalIgnores } from "eslint/config";
import nextCoreWebVitals from "eslint-config-next/core-web-vitals";
import nextTypeScript from "eslint-config-next/typescript";

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
  ...nextCoreWebVitals,
  ...nextTypeScript,
  {
    files: ["src/app/**/*.{ts,tsx}", "src/components/**/*.{ts,tsx}", "src/lib/**/*.{ts,tsx}", "src/stores/**/*.{ts,tsx}"],
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
  globalIgnores([".next/**", "coverage/**", "node_modules/**"]),
]);
