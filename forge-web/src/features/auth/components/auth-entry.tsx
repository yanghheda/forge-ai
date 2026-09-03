"use client";

import { Alert, Button, Card, Input, Spin, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { getCurrentUser, getSetupStatus, initializeInstance, login, type InitializeInput } from "../api/auth-api";
import { initializeSchema, loginSchema } from "../schemas/auth-form.schema";
import { AuthErrorAlert } from "./auth-error-alert";
import styles from "./auth-forms.module.css";

const emptyInitialization: InitializeInput = {
  adminEmail: "", adminDisplayName: "", password: "", organizationName: "", organizationSlug: "",
  workspaceName: "", workspaceSlug: "",
};

export function AuthEntry() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const status = useQuery({ queryKey: ["setup-status"], queryFn: () => getSetupStatus() });
  const [initializedLocally, setInitializedLocally] = useState(false);
  const [loginValues, setLoginValues] = useState({ email: "", password: "" });
  const [initializeValues, setInitializeValues] = useState(emptyInitialization);
  const [validationError, setValidationError] = useState<string>();

  const initializeMutation = useMutation({
    mutationFn: (input: InitializeInput) => initializeInstance(input),
    onSuccess: () => {
      setInitializedLocally(true);
      setValidationError(undefined);
      void queryClient.invalidateQueries({ queryKey: ["setup-status"] });
    },
  });
  const loginMutation = useMutation({
    mutationFn: (input: { email: string; password: string }) => login(input),
    onSuccess: async () => {
      const user = await queryClient.fetchQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
      router.replace(user.workspaces[0] ? `/w/${user.workspaces[0].slug}` : "/");
    },
  });

  if (status.isPending) return <Spin tip="正在检查实例状态…" />;
  if (status.isError) return <Card title="无法连接 ForgeAI"><AuthErrorAlert error={status.error} /></Card>;
  const initialized = status.data.initialized || initializedLocally;

  function submitLogin(event: FormEvent) {
    event.preventDefault();
    const parsed = loginSchema.safeParse(loginValues);
    if (!parsed.success) return setValidationError(parsed.error.issues[0]?.message);
    setValidationError(undefined);
    loginMutation.mutate(parsed.data);
  }

  function submitInitialization(event: FormEvent) {
    event.preventDefault();
    const parsed = initializeSchema.safeParse(initializeValues);
    if (!parsed.success) return setValidationError(parsed.error.issues[0]?.message);
    setValidationError(undefined);
    initializeMutation.mutate(parsed.data);
  }

  if (!initialized) {
    const fields: Array<[keyof InitializeInput, string, string]> = [
      ["adminEmail", "管理员邮箱", "name@example.com"], ["adminDisplayName", "管理员名称", "Forge Owner"],
      ["password", "密码", "至少 12 个字符，包含字母和数字"], ["organizationName", "组织名称", "Forge"],
      ["organizationSlug", "组织短名", "forge"], ["workspaceName", "Workspace 名称", "Engineering"],
      ["workspaceSlug", "Workspace 短名", "engineering"],
    ];
    return <Card title="初始化 ForgeAI" className={styles.card}>
      <Typography.Paragraph>创建首个 Owner、Organization 与 Workspace。此操作只能成功一次。</Typography.Paragraph>
      <form onSubmit={submitInitialization} className={styles.form}>
        {fields.map(([name, label, placeholder]) => <label key={name}>{label}{name === "password" ? <Input.Password
          value={initializeValues[name]} placeholder={placeholder} autoComplete="new-password"
          onChange={(value) => setInitializeValues((current) => ({ ...current, [name]: value }))} /> : <Input
          value={initializeValues[name]} placeholder={placeholder}
          onChange={(value) => setInitializeValues((current) => ({ ...current, [name]: value }))} />}</label>)}
        {validationError && <Alert type="warning" content={validationError} />}
        <AuthErrorAlert error={initializeMutation.error} />
        <Button htmlType="submit" type="primary" loading={initializeMutation.isPending}>完成初始化</Button>
      </form>
    </Card>;
  }

  return <Card title="登录 ForgeAI" className={styles.card}>
    {initializedLocally && <Alert type="success" content="初始化完成，请使用 Owner 账户登录。" />}
    <form onSubmit={submitLogin} className={styles.form}>
      <label>邮箱<Input value={loginValues.email} autoComplete="email" onChange={(email) => setLoginValues((value) => ({ ...value, email }))} /></label>
      <label>密码<Input.Password value={loginValues.password} autoComplete="current-password" onChange={(password) => setLoginValues((value) => ({ ...value, password }))} /></label>
      {validationError && <Alert type="warning" content={validationError} />}
      <AuthErrorAlert error={loginMutation.error} />
      <Button htmlType="submit" type="primary" loading={loginMutation.isPending}>登录</Button>
    </form>
  </Card>;
}
