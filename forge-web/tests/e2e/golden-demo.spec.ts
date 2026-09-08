import { expect, test } from "@playwright/test";

const workspace = "demo";
const project = "DEMO";
const requirementId = 32011;
const runId = "DEMA0000000000000000000001";

test("黄金 Demo 最终 Delivery Graph 与 Agent Trace 可从浏览器回溯", async ({ page }) => {
  await test.step("登录确定性 Demo 账户", async () => {
    await page.goto("/login");
    await page.getByLabel("邮箱").fill("owner@demo.forgeai.local");
    await page.getByLabel("密码").fill("ForgeAI-Demo-2026!");
    await page.getByRole("button", { name: "登录" }).click();
    await expect(page).toHaveURL(new RegExp(`/w/${workspace}$`));
  });

  await test.step("Delivery Graph 展示跨角色交付节点", async () => {
    await page.goto(`/w/${workspace}/p/${project}/requirements/${requirementId}`);
    await page.getByRole("tab", { name: "Delivery Graph" }).click();
    const nodes = page.getByRole("list", { name: "交付节点列表" });
    await expect(nodes).toContainText("增加手机号验证码登录");
    await expect(nodes).toContainText("UX_TASK");
    await expect(nodes).toContainText("DEV_TASK");
    await expect(nodes).toContainText("BUG");
    await expect(nodes).toContainText("PRD");
    await expect(nodes).toContainText("UX_SPEC");
    await expect(nodes).toContainText("TECH_DESIGN");
    await expect(nodes).toContainText("MERGE_REQUEST");
    await expect(nodes).toContainText("PIPELINE");
    await expect(nodes).toContainText("TEST_CASE");
    await expect(nodes).toContainText("TEST_RUN");
    await expect(nodes).toContainText("RELEASE");
    await expect(nodes).toContainText("DEPLOYMENT");
  });

  await test.step("Agent Trace 展示固定 Run 与连续步骤", async () => {
    await page.goto(`/w/${workspace}/p/${project}/agent-runs/${runId}`);
    await expect(page.getByRole("heading", { name: "Agent Run" })).toBeVisible();
    await expect(page.getByText(runId)).toBeVisible();
    await expect(page.getByText("读取交付事实", { exact: false })).toBeVisible();
    await expect(page.getByText("生成 Release Note", { exact: false })).toBeVisible();
    await expect(page.getByText("状态：SUCCEEDED")).toBeVisible();
  });
});
