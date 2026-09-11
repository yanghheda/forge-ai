import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    exclude: ["tests/e2e/**", "node_modules/**", "src/features/work-item/api/work-item-api.test.ts", "src/features/work-item/components/requirement-detail.test.tsx", "src/features/work-item/components/ux-workspace.test.tsx"],
    css: true,
  },
});
