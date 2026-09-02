import { spawnSync } from "node:child_process";

const result = spawnSync(
  process.execPath,
  ["./node_modules/eslint/bin/eslint.js", "--no-ignore", "tests/module-boundaries/fixtures/invalid-deep-import.ts"],
  { encoding: "utf8" },
);

if (result.status === 0) {
  console.error("模块边界测试失败：跨 Feature 深层导入未被 ESLint 阻断。");
  process.exit(1);
}

if (!result.stdout.includes("跨 Feature 只能通过其公开 index.ts 导入")) {
  process.stderr.write(result.stderr);
  process.stdout.write(result.stdout);
  console.error("模块边界测试失败：ESLint 失败原因不是预期的边界规则。");
  process.exit(1);
}

console.log("模块边界测试通过，已验证跨 Feature 深层导入会失败");
