"use client";

import { Alert, Button, Card, Input, Spin, Typography } from "@arco-design/web-react";
import { IconCheckCircle, IconLock, IconRobot } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
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

  if (status.isPending) return <div className={styles.loading}><Spin tip="正在检查实例状态…" /></div>;
  if (status.isError) return <div className={styles.loading}><Card title="无法连接 ForgeAI"><AuthErrorAlert error={status.error} /></Card></div>;
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
      ["adminEmail", "管理员邮箱", "name@example.com"], ["adminDisplayName", "管理员名称", "Forge 所有者"],
      ["password", "密码", "至少 12 个字符，包含字母和数字"], ["organizationName", "组织名称", "Forge"],
      ["organizationSlug", "组织短名", "forge"], ["workspaceName", "工作空间名称", "工程团队"],
      ["workspaceSlug", "工作空间短名", "engineering"],
    ];
    return <AuthLayout title="初始化智能交付空间" description="从组织、工作空间到第一个所有者，一次完成可信交付环境的建立。"><Card className={styles.card}>
      <div className={styles.formHeading}><span>01</span><div><Typography.Title heading={4}>初始化 ForgeAI</Typography.Title><Typography.Paragraph>创建组织与首个工作空间</Typography.Paragraph></div></div>
      <form onSubmit={submitInitialization} className={styles.form}>
        {fields.map(([name, label, placeholder]) => <label key={name}>{label}{name === "password" ? <Input.Password
          value={initializeValues[name]} placeholder={placeholder} autoComplete="new-password"
          onChange={(value) => setInitializeValues((current) => ({ ...current, [name]: value }))} /> : <Input
          value={initializeValues[name]} placeholder={placeholder}
          onChange={(value) => setInitializeValues((current) => ({ ...current, [name]: value }))} />}</label>)}
        {validationError && <Alert type="warning" content={validationError} />}
        <AuthErrorAlert error={initializeMutation.error} />
        <Button htmlType="submit" type="primary" long size="large" loading={initializeMutation.isPending}>完成初始化</Button>
      </form>
    </Card></AuthLayout>;
  }

  return <AuthLayout title="欢迎回来" description="回到清晰、连续、可追溯的软件交付流程。"><Card className={styles.card}>
    <div className={styles.formHeading}><span><IconLock /></span><div><Typography.Title heading={4}>登录 ForgeAI</Typography.Title><Typography.Paragraph>使用你的工作账户进入</Typography.Paragraph></div></div>
    {initializedLocally && <Alert type="success" content="初始化完成，请使用所有者账户登录。" />}
    <form onSubmit={submitLogin} className={styles.form}>
      <label>邮箱<Input value={loginValues.email} autoComplete="email" onChange={(email) => setLoginValues((value) => ({ ...value, email }))} /></label>
      <label>密码<Input.Password value={loginValues.password} autoComplete="current-password" onChange={(password) => setLoginValues((value) => ({ ...value, password }))} /></label>
      {validationError && <Alert type="warning" content={validationError} />}
      <AuthErrorAlert error={loginMutation.error} login />
      <Button htmlType="submit" type="primary" long size="large" loading={loginMutation.isPending}>登录</Button>
    </form>
  </Card></AuthLayout>;
}

function AuthLayout({ children, title, description }: { children: React.ReactNode; title: string; description: string }) {
  return <div className={styles.page}>
    <aside className={styles.story}>
      <Link href="/" className={styles.logo}><span>F</span> ForgeAI</Link>
      <div className={styles.storyContent}><div className={styles.kicker}><IconRobot /> 智能交付</div><h1>{title}</h1><p>{description}</p><ul><li><IconCheckCircle /> 从需求到发布的统一上下文</li><li><IconCheckCircle /> 可审计的人机协作过程</li><li><IconCheckCircle /> 内建权限、策略与审批</li></ul></div>
      <small>AI 原生软件交付工作台</small>
    </aside>
    <main className={styles.formSide}>{children}<p className={styles.security}><IconLock /> 会话安全保护 · 所有操作可追踪</p></main>
  </div>;
}
