"use client";

import { Alert, Button, Card, Form, Input, Select, Spin, Typography } from "@arco-design/web-react";
import { IconLock } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { getSetupStatus, initializeInstance, login, register, type InitializeInput, type RegisterInput } from "../api/auth-api";
import { initializeSchema, loginSchema, registerSchema } from "../schemas/auth-form.schema";
import { AuthErrorAlert } from "./auth-error-alert";
import { AuthLayout } from "./auth-layout";
import { LogoUpload } from "./logo-upload";
import { issuesToFieldErrors, schemaRule, type FieldErrors } from "../utils/auth-validation";
import styles from "./auth-forms.module.css";

const emptyInitialization: InitializeInput = {
  adminEmail: "",
  adminDisplayName: "",
  password: "",
  organizationName: "",
  organizationSlug: "",
  logo: null,
};

export function AuthEntry({ initialMode = "login" }: { initialMode?: "login" | "register" | "init" }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const status = useQuery({
    queryKey: ["setup-status"],
    queryFn: () => getSetupStatus(),
  });
  const [initializedLocally, setInitializedLocally] = useState(false);
  const [mode, setMode] = useState<"login" | "register">(initialMode === "register" ? "register" : "login");
  const [loginValues, setLoginValues] = useState({ email: "", password: "" });
  const [initializeValues, setInitializeValues] = useState(emptyInitialization);
  const [registerValues, setRegisterValues] = useState<RegisterInput>({
    email: "",
    displayName: "",
    password: "",
    role: "PRODUCT",
  });
  const [registered, setRegistered] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [initializeForm] = Form.useForm<InitializeInput>();
  const initializeMutation = useMutation({
    mutationFn: (input: InitializeInput) => initializeInstance(input),
    onSuccess: () => {
      if (initialMode === "init") {
        router.replace("/login");
        return;
      }
      setInitializedLocally(true);
      setFieldErrors({});
      void queryClient.invalidateQueries({ queryKey: ["setup-status"] });
    },
  });
  const loginMutation = useMutation({
    mutationFn: (input: { email: string; password: string }) => login(input),
    onSuccess: () => router.replace("/overview"),
  });
  const registerMutation = useMutation({
    mutationFn: (input: RegisterInput) => register(input),
    onSuccess: () => {
      setRegistered(true);
      setMode("login");
      setFieldErrors({});
    },
  });

  if (status.isPending)
    return (
      <div className={styles.loading}>
        <Spin tip="正在检查实例状态…" />
      </div>
    );
  if (status.isError)
    return (
      <div className={styles.loading}>
        <Card title="无法连接 ForgeAI">
          <AuthErrorAlert error={status.error} />
        </Card>
      </div>
    );
  const initialized = status.data.initialized || initializedLocally;

  function showSchemaErrors(issues: Array<{ path: PropertyKey[]; message: string }>) {
    setFieldErrors(issuesToFieldErrors(issues));
  }

  function clearFieldError(name: string) {
    setFieldErrors((current) => ({ ...current, [name]: undefined }));
  }

  function submitLogin() {
    const parsed = loginSchema.safeParse(loginValues);
    if (!parsed.success) return showSchemaErrors(parsed.error.issues);
    setFieldErrors({});
    loginMutation.mutate(parsed.data);
  }

  async function submitInitialization() {
    const parsed = initializeSchema.safeParse(initializeValues);
    if (!parsed.success) {
      showSchemaErrors([...parsed.error.issues, ...(!initializeValues.logo ? [{ path: ["logo"], message: "请选择公司 Logo" }] : [])]);
      return;
    }
    if (!initializeValues.logo) return setFieldErrors({ logo: "请选择 WebP 格式的公司 Logo。" });
    if (initializeValues.logo.type !== "image/webp" || !initializeValues.logo.name.toLowerCase().endsWith(".webp")) return setFieldErrors({ logo: "公司 Logo 仅支持 WebP 格式。" });
    if (initializeValues.logo.size > 2 * 1024 * 1024) return setFieldErrors({ logo: "公司 Logo 不能超过 2 MiB。" });
    if (!(await hasSquareLogoDimensions(initializeValues.logo)))
      return setFieldErrors({
        logo: "公司 Logo 的宽高比例必须为 1:1。",
      });
    setFieldErrors({});
    initializeMutation.mutate({ ...parsed.data, logo: initializeValues.logo });
  }

  function submitRegistration() {
    const parsed = registerSchema.safeParse(registerValues);
    if (!parsed.success) return showSchemaErrors(parsed.error.issues);
    setFieldErrors({});
    registerMutation.mutate(parsed.data);
  }

  if (initialMode === "init" || !initialized) {
    const fields: Array<[Exclude<keyof InitializeInput, "logo">, string, string, string]> = [
      ["adminEmail", "管理员邮箱", "name@example.com", "请输入有效邮箱"],
      ["adminDisplayName", "管理员名称", "Forge 所有者", "请输入管理员名称"],
      ["password", "密码", "至少 12 个字符，包含字母和数字", "请输入密码"],
      ["organizationName", "公司名称", "Forge", "请输入组织名称"],
      ["organizationSlug", "公司短名", "forge", "请输入公司短名"],
    ];
    return (
      <AuthLayout title="让需求到发布\n由 AI 智能体协同完成" description="ForgeAI 以 PRD 为事实来源，编排产品、UX、开发、测试智能体在受控工具边界内协同交付。">
        <Card className={styles.card}>
          <div className={styles.formHeading}>
            <span>01</span>
            <div>
              <Typography.Title heading={4}>初始化 ForgeAI</Typography.Title>
              <Typography.Paragraph>一个实例服务一家公司</Typography.Paragraph>
            </div>
          </div>
          <Form form={initializeForm} layout="vertical" requiredSymbol={{ position: "start" }} wrapperProps={{ noValidate: true }} initialValues={emptyInitialization} onSubmit={submitInitialization} className={styles.form}>
            {fields.map(([name, label, placeholder, requiredMessage]) => (
              <Form.Item key={name} field={name} label={label} required rules={[schemaRule(initializeSchema.shape[name], requiredMessage)]} validateStatus={fieldErrors[name] ? "error" : undefined} help={fieldErrors[name]}>
                {name === "password" ? (
                  <Input.Password
                    aria-label={label}
                    error={Boolean(fieldErrors[name])}
                    value={initializeValues[name]}
                    placeholder={placeholder}
                    autoComplete="new-password"
                    onChange={(value) => {
                      clearFieldError(name);
                      setInitializeValues((current) => ({
                        ...current,
                        [name]: value,
                      }));
                    }}
                  />
                ) : (
                  <Input
                    aria-label={label}
                    error={Boolean(fieldErrors[name])}
                    value={initializeValues[name]}
                    placeholder={placeholder}
                    onChange={(value) => {
                      clearFieldError(name);
                      setInitializeValues((current) => ({
                        ...current,
                        [name]: value,
                      }));
                    }}
                  />
                )}
              </Form.Item>
            ))}
            <Form.Item field="logo" label="公司 Logo" required rules={[{ required: true, message: "请选择公司 Logo" }]} validateStatus={fieldErrors.logo ? "error" : undefined} help={fieldErrors.logo}>
              <div>
                <LogoUpload
                  value={initializeValues.logo}
                  error={Boolean(fieldErrors.logo)}
                  onFileChange={(logo) => {
                    initializeForm.setFieldValue("logo", logo);
                    clearFieldError("logo");
                    setInitializeValues((current) => ({ ...current, logo }));
                  }}
                />
              </div>
            </Form.Item>
            <AuthErrorAlert error={initializeMutation.error} />
            <Button htmlType="submit" type="primary" long size="large" loading={initializeMutation.isPending}>
              完成初始化
            </Button>
          </Form>
        </Card>
      </AuthLayout>
    );
  }

  if (mode === "register")
    return (
      <AuthLayout title="加入交付团队" description="与 AI 智能体一起，让每个需求可追溯地走向发布。">
        <Card className={styles.card}>
          <div className={styles.formHeading}>
            <span>02</span>
            <div>
              <Typography.Title heading={4}>注册团队账号</Typography.Title>
              <Typography.Paragraph>选择你的主要岗位角色</Typography.Paragraph>
            </div>
          </div>
          <Form layout="vertical" wrapperProps={{ noValidate: true }} initialValues={registerValues} onSubmit={submitRegistration} className={styles.form}>
            <Form.Item field="displayName" label="姓名" required rules={[schemaRule(registerSchema.shape.displayName, "请输入姓名")]} validateStatus={fieldErrors.displayName ? "error" : undefined} help={fieldErrors.displayName}>
              <Input
                aria-label="姓名"
                error={Boolean(fieldErrors.displayName)}
                value={registerValues.displayName}
                onChange={(displayName) => {
                  clearFieldError("displayName");
                  setRegisterValues((value) => ({ ...value, displayName }));
                }}
              />
            </Form.Item>
            <Form.Item field="email" label="邮箱" required rules={[schemaRule(registerSchema.shape.email, "请输入有效邮箱")]} validateStatus={fieldErrors.email ? "error" : undefined} help={fieldErrors.email}>
              <Input
                aria-label="邮箱"
                error={Boolean(fieldErrors.email)}
                value={registerValues.email}
                autoComplete="email"
                onChange={(email) => {
                  clearFieldError("email");
                  setRegisterValues((value) => ({ ...value, email }));
                }}
              />
            </Form.Item>
            <Form.Item field="password" label="密码" required rules={[schemaRule(registerSchema.shape.password, "请输入密码")]} validateStatus={fieldErrors.password ? "error" : undefined} help={fieldErrors.password}>
              <Input.Password
                aria-label="密码"
                error={Boolean(fieldErrors.password)}
                value={registerValues.password}
                autoComplete="new-password"
                onChange={(password) => {
                  clearFieldError("password");
                  setRegisterValues((value) => ({ ...value, password }));
                }}
              />
            </Form.Item>
            <Form.Item field="role" label="岗位角色" required rules={[{ required: true, message: "请选择岗位角色" }]}>
              <Select
                aria-label="岗位角色"
                value={registerValues.role}
                onChange={(role) => setRegisterValues((value) => ({ ...value, role }))}
                options={[
                  { label: "产品", value: "PRODUCT" },
                  { label: "UX", value: "UX" },
                  { label: "开发", value: "DEVELOPER" },
                  { label: "测试", value: "QA" },
                ]}
              />
            </Form.Item>
            <AuthErrorAlert error={registerMutation.error} />
            <Button htmlType="submit" type="primary" long size="large" loading={registerMutation.isPending}>
              完成注册
            </Button>
            <Button type="text" className={styles.authSwitchButton} onClick={() => setMode("login")}>
              已有账号，返回登录
            </Button>
          </Form>
        </Card>
      </AuthLayout>
    );

  return (
    <AuthLayout title="让需求到发布\n由 AI 智能体协同完成" description="ForgeAI 以 PRD 为事实来源，编排产品、UX、开发、测试智能体在受控工具边界内协同交付。">
      <Card className={styles.card}>
        <div className={styles.formHeading}>
          <span>
            <IconLock />
          </span>
          <div>
            <Typography.Title heading={4}>登录 ForgeAI</Typography.Title>
            <Typography.Paragraph>使用你的工作账户进入</Typography.Paragraph>
          </div>
        </div>
        {initializedLocally && <Alert type="success" content="初始化完成，请使用所有者账户登录。" />}
        {registered && <Alert type="success" content="注册申请已提交，请等待公司管理员审核后登录。" />}
        <Form layout="vertical" wrapperProps={{ noValidate: true }} initialValues={loginValues} onSubmit={submitLogin} className={styles.form}>
          <Form.Item field="email" label="邮箱" required rules={[schemaRule(loginSchema.shape.email, "请输入有效邮箱")]} validateStatus={fieldErrors.email ? "error" : undefined} help={fieldErrors.email}>
            <Input
              aria-label="邮箱"
              error={Boolean(fieldErrors.email)}
              value={loginValues.email}
              autoComplete="email"
              onChange={(email) => {
                clearFieldError("email");
                setLoginValues((value) => ({ ...value, email }));
              }}
            />
          </Form.Item>
          <Form.Item field="password" label="密码" required rules={[schemaRule(loginSchema.shape.password, "请输入密码")]} validateStatus={fieldErrors.password ? "error" : undefined} help={fieldErrors.password}>
            <Input.Password
              aria-label="密码"
              error={Boolean(fieldErrors.password)}
              value={loginValues.password}
              autoComplete="current-password"
              onChange={(password) => {
                clearFieldError("password");
                setLoginValues((value) => ({ ...value, password }));
              }}
            />
          </Form.Item>
          <AuthErrorAlert error={loginMutation.error} login />
          <Button htmlType="submit" type="primary" long size="large" loading={loginMutation.isPending}>
            登录
          </Button>
          <Button
            type="text"
            className={styles.authSwitchButton}
            onClick={() => {
              setMode("register");
              setFieldErrors({});
            }}
          >
            注册账号
          </Button>
        </Form>
      </Card>
    </AuthLayout>
  );
}

function hasSquareLogoDimensions(file: File): Promise<boolean> {
  return new Promise((resolve) => {
    const image = new Image();
    const source = URL.createObjectURL(file);
    image.onload = () => {
      URL.revokeObjectURL(source);
      resolve(image.naturalWidth === image.naturalHeight);
    };
    image.onerror = () => {
      URL.revokeObjectURL(source);
      resolve(false);
    };
    image.src = source;
  });
}
