"use client";

import { Alert, Button, Card, Input, Select, Spin, Typography } from "@arco-design/web-react";
import { IconCheckCircle, IconLock, IconRobot } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { getSetupStatus, initializeInstance, login, register, type InitializeInput, type RegisterInput } from "../api/auth-api";
import { initializeSchema, loginSchema, registerSchema } from "../schemas/auth-form.schema";
import { AuthErrorAlert } from "./auth-error-alert";
import styles from "./auth-forms.module.css";

const emptyInitialization: InitializeInput = {
  adminEmail: "", adminDisplayName: "", password: "", organizationName: "", organizationSlug: "", logo: null,
};

export function AuthEntry({ initialMode = "login" }: { initialMode?: "login" | "register" | "init" }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const status = useQuery({ queryKey: ["setup-status"], queryFn: () => getSetupStatus() });
  const [initializedLocally, setInitializedLocally] = useState(false);
  const [mode, setMode] = useState<"login" | "register">(initialMode === "register" ? "register" : "login");
  const [loginValues, setLoginValues] = useState({ email: "", password: "" });
  const [initializeValues, setInitializeValues] = useState(emptyInitialization);
  const [registerValues, setRegisterValues] = useState<RegisterInput>({ email: "", displayName: "", password: "", role: "PRODUCT" });
  const [registered, setRegistered] = useState(false);
  const [validationError, setValidationError] = useState<string>();
  const initializeMutation = useMutation({
    mutationFn: (input: InitializeInput) => initializeInstance(input),
    onSuccess: () => {
      if (initialMode === "init") {
        router.replace("/login");
        return;
      }
      setInitializedLocally(true);
      setValidationError(undefined);
      void queryClient.invalidateQueries({ queryKey: ["setup-status"] });
    },
  });
  const loginMutation = useMutation({
    mutationFn: (input: { email: string; password: string }) => login(input),
    onSuccess: () => router.replace("/overview"),
  });
  const registerMutation = useMutation({
    mutationFn: (input: RegisterInput) => register(input),
    onSuccess: () => { setRegistered(true); setMode("login"); setValidationError(undefined); },
  });

  if (status.isPending) return <div className={styles.loading}><Spin tip="正在检查实例状态…" /></div>;
  if (status.isError) return <div className={styles.loading}><Card title="无法连接 ForgeAI"><AuthErrorAlert error={status.error} /></Card></div>;
  const initialized = status.data.initialized || initializedLocally;

  function submitLogin(event: FormEvent) {
    event.preventDefault();
    const parsed = loginSchema.safeParse(loginValues);
    if (!parsed.success) return setValidationError(parsed.error.issues[0]?.message ?? "请检查输入内容。");
    setValidationError(undefined);
    loginMutation.mutate(parsed.data);
  }

  async function submitInitialization(event: FormEvent) {
    event.preventDefault();
    const parsed = initializeSchema.safeParse(initializeValues);
    if (!parsed.success) return setValidationError(parsed.error.issues[0]?.message ?? "请检查输入内容。");
    if (!initializeValues.logo) return setValidationError("请选择 WebP 格式的公司 Logo。");
    if (initializeValues.logo.type !== "image/webp" || !initializeValues.logo.name.toLowerCase().endsWith(".webp")) return setValidationError("公司 Logo 仅支持 WebP 格式。");
    if (initializeValues.logo.size > 2 * 1024 * 1024) return setValidationError("公司 Logo 不能超过 2 MiB。");
    if (!await hasRequiredLogoDimensions(initializeValues.logo)) return setValidationError("公司 Logo 尺寸必须严格为 28 × 28 像素。");
    setValidationError(undefined);
    initializeMutation.mutate({ ...parsed.data, logo: initializeValues.logo });
  }

  function submitRegistration(event: FormEvent) {
    event.preventDefault();
    const parsed = registerSchema.safeParse(registerValues);
    if (!parsed.success) return setValidationError(parsed.error.issues[0]?.message ?? "请检查输入内容。");
    setValidationError(undefined);
    registerMutation.mutate(parsed.data);
  }

  if (initialMode === "init" || !initialized) {
    const fields: Array<[Exclude<keyof InitializeInput, "logo">, string, string]> = [
      ["adminEmail", "管理员邮箱", "name@example.com"], ["adminDisplayName", "管理员名称", "Forge 所有者"],
      ["password", "密码", "至少 12 个字符，包含字母和数字"], ["organizationName", "公司名称", "Forge"],
      ["organizationSlug", "公司短名", "forge"],
    ];
    return <AuthLayout title="让需求到发布\n由 AI 智能体协同完成" description="ForgeAI 以 PRD 为事实来源，编排产品、UX、开发、测试智能体在受控工具边界内协同交付。"><Card className={styles.card}>
      <div className={styles.formHeading}><span>01</span><div><Typography.Title heading={4}>初始化 ForgeAI</Typography.Title><Typography.Paragraph>一个实例服务一家公司</Typography.Paragraph></div></div>
      <form onSubmit={submitInitialization} className={styles.form}>
        {fields.map(([name, label, placeholder]) => <label key={name}>{label}{name === "password" ? <Input.Password
          value={initializeValues[name]} placeholder={placeholder} autoComplete="new-password"
          onChange={(value) => setInitializeValues((current) => ({ ...current, [name]: value }))} /> : <Input
          value={initializeValues[name]} placeholder={placeholder}
          onChange={(value) => setInitializeValues((current) => ({ ...current, [name]: value }))} />}</label>)}
        <label>公司 Logo（必传，仅 WebP，28 × 28 像素）<input aria-label="公司 Logo" type="file" accept="image/webp,.webp" required
          onChange={(event) => setInitializeValues((current) => ({ ...current, logo: event.target.files?.[0] ?? null }))} /></label>
        {validationError && <Alert type="warning" content={validationError} />}
        <AuthErrorAlert error={initializeMutation.error} />
        <Button htmlType="submit" type="primary" long size="large" loading={initializeMutation.isPending}>完成初始化</Button>
      </form>
    </Card></AuthLayout>;
  }

  if (mode === "register") return <AuthLayout title="加入交付团队" description="与 AI 智能体一起，让每个需求可追溯地走向发布。"><Card className={styles.card}>
    <div className={styles.formHeading}><span>02</span><div><Typography.Title heading={4}>注册团队账号</Typography.Title><Typography.Paragraph>选择你的主要岗位角色</Typography.Paragraph></div></div>
    <form onSubmit={submitRegistration} className={styles.form}>
      <label>姓名<Input value={registerValues.displayName} onChange={(displayName) => setRegisterValues((value) => ({ ...value, displayName }))} /></label>
      <label>邮箱<Input value={registerValues.email} autoComplete="email" onChange={(email) => setRegisterValues((value) => ({ ...value, email }))} /></label>
      <label>密码<Input.Password value={registerValues.password} autoComplete="new-password" onChange={(password) => setRegisterValues((value) => ({ ...value, password }))} /></label>
      <label>岗位角色<Select aria-label="岗位角色" value={registerValues.role} onChange={(role) => setRegisterValues((value) => ({ ...value, role }))} options={[{ label: "产品", value: "PRODUCT" }, { label: "UX", value: "UX" }, { label: "开发", value: "DEVELOPER" }, { label: "测试", value: "QA" }]} /></label>
      {validationError && <Alert type="warning" content={validationError} />}
      <AuthErrorAlert error={registerMutation.error} />
      <Button htmlType="submit" type="primary" long size="large" loading={registerMutation.isPending}>完成注册</Button>
      <Button type="text" long onClick={() => setMode("login")}>已有账号，返回登录</Button>
    </form>
  </Card></AuthLayout>;

  return <AuthLayout title="让需求到发布\n由 AI 智能体协同完成" description="ForgeAI 以 PRD 为事实来源，编排产品、UX、开发、测试智能体在受控工具边界内协同交付。"><Card className={styles.card}>
    <div className={styles.formHeading}><span><IconLock /></span><div><Typography.Title heading={4}>登录 ForgeAI</Typography.Title><Typography.Paragraph>使用你的工作账户进入</Typography.Paragraph></div></div>
    {initializedLocally && <Alert type="success" content="初始化完成，请使用所有者账户登录。" />}
    {registered && <Alert type="success" content="注册申请已提交，请等待公司管理员审核后登录。" />}
    <form onSubmit={submitLogin} className={styles.form}>
      <label>邮箱<Input value={loginValues.email} autoComplete="email" onChange={(email) => setLoginValues((value) => ({ ...value, email }))} /></label>
      <label>密码<Input.Password value={loginValues.password} autoComplete="current-password" onChange={(password) => setLoginValues((value) => ({ ...value, password }))} /></label>
      {validationError && <Alert type="warning" content={validationError} />}
      <AuthErrorAlert error={loginMutation.error} login />
      <Button htmlType="submit" type="primary" long size="large" loading={loginMutation.isPending}>登录</Button>
      <Button type="text" long onClick={() => { setMode("register"); setValidationError(undefined); }}>注册账号</Button>
    </form>
  </Card></AuthLayout>;
}

function hasRequiredLogoDimensions(file: File): Promise<boolean> {
  return new Promise((resolve) => {
    const image = new Image();
    const source = URL.createObjectURL(file);
    image.onload = () => { URL.revokeObjectURL(source); resolve(image.naturalWidth === 28 && image.naturalHeight === 28); };
    image.onerror = () => { URL.revokeObjectURL(source); resolve(false); };
    image.src = source;
  });
}

function AuthLayout({ children, title, description }: { children: React.ReactNode; title: string; description: string }) {
  return <div className={styles.page}>
    <aside className={styles.story}>
      <Link href="/" className={styles.logo}><span>F</span> ForgeAI</Link>
      <div className={styles.storyContent}><h1>{title.split("\n").map((line) => <span key={line}>{line}</span>)}</h1><p>{description}</p><ul><li><IconRobot /> <span><b>多智能体交付流水线</b><small>PRD 生成、UX 设计、代码实现、QA 回归按状态机自动推进</small></span></li><li><IconLock /> <span><b>受控工具与权限边界</b><small>Agent 不持有部署凭据，所有写操作经 Server Tool API 鉴权</small></span></li><li><IconCheckCircle /> <span><b>执行轨迹全程可追溯</b><small>每次工具调用、人工确认与状态流转均有 Trace 记录</small></span></li></ul></div>
      <small>私有化部署 · 数据不出企业内网</small>
    </aside>
    <main className={styles.formSide}>{children}<p className={styles.security}><IconLock /> 会话安全保护 · 所有操作可追踪</p></main>
  </div>;
}
