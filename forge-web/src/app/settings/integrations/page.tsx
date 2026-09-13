"use client";

import { Alert, Button, Descriptions, Drawer, Form, Input, Message, Spin, Tag } from "@arco-design/web-react";
import { IconApps, IconPlus, IconSafe } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ProtectedApp } from "@/features/auth";
import { bindRepository, configureWebhookSecret, createConnection, listConnections, testConnection, type GitLabConnection } from "@/features/gitlab";
import { formatRequestError } from "@/lib/api";
import styles from "../settings.module.css";

export default function IntegrationsPage() {
  return (
    <ProtectedApp>
      <Integrations />
    </ProtectedApp>
  );
}
function Integrations() {
  const [open, setOpen] = useState(false);
  const [configuredConnection, setConfiguredConnection] = useState<GitLabConnection>();
  const [form] = Form.useForm<{ name: string; baseUrl: string; token: string }>();
  const [repositoryForm] = Form.useForm<{ remoteProjectId: string }>();
  const [webhookForm] = Form.useForm<{ secret: string }>();
  const client = useQueryClient();
  const connections = useQuery({
    queryKey: ["gitlab-connections"],
    queryFn: () => listConnections(),
  });
  const close = () => {
    form.resetFields();
    setOpen(false);
  };
  const create = useMutation({
    mutationFn: (values: { name: string; baseUrl: string; token: string }) => createConnection(values),
    onSuccess: async (connection) => {
      close();
      setConfiguredConnection(connection);
      Message.success("GitLab 连接已保存，请继续测试连接并绑定仓库。");
      await client.invalidateQueries({ queryKey: ["gitlab-connections"] });
    },
  });
  const test = useMutation({
    mutationFn: (connectionId: number) => testConnection(connectionId),
    onSuccess: async (result) => {
      Message.success(`连接成功，GitLab 用户：${result.username}`);
      await client.invalidateQueries({ queryKey: ["gitlab-connections"] });
    },
  });
  const bind = useMutation({
    mutationFn: (values: { remoteProjectId: string }) => bindRepository({ connectionId: configuredConnection!.id, remoteProjectId: values.remoteProjectId }),
    onSuccess: () => {
      repositoryForm.resetFields();
      Message.success("GitLab 仓库已绑定。");
    },
  });
  const saveWebhook = useMutation({
    mutationFn: (values: { secret: string }) => configureWebhookSecret(configuredConnection!.id, values.secret),
    onSuccess: () => {
      webhookForm.resetFields();
      Message.success("Webhook Secret 已安全保存，请在 GitLab 中使用相同的 Secret Token。");
    },
  });
  const closeConfiguration = () => {
    repositoryForm.resetFields();
    webhookForm.resetFields();
    setConfiguredConnection(undefined);
  };
  return (
    <section className={styles.page}>
      <header>
        <div>
          <h1>集成配置</h1>
          <p>连接公司 GitLab 代码仓库与 CI Pipeline</p>
        </div>
        <Button type="primary" icon={<IconPlus />} onClick={() => setOpen(true)}>
          添加 GitLab
        </Button>
      </header>
      {connections.isPending && <Spin tip="正在加载 GitLab 连接…" />}
      {connections.isError && <Alert type="error" content={formatRequestError(connections.error)} />}
      <div className={styles.integrationGrid}>
        {connections.data?.map((item) => (
          <article className={styles.integration} key={item.id}>
            <div className={styles.integrationIcon}>
              <IconApps />
            </div>
            <div>
              <h2>{item.name}</h2>
              <p>{item.baseUrl}</p>
              <Tag color={item.status === "ACTIVE" ? "green" : "orange"}>{item.status === "ACTIVE" ? "● 已连接" : "待验证"}</Tag>
            </div>
            <Button onClick={() => setConfiguredConnection(item)}>配置</Button>
          </article>
        ))}
      </div>
      <article className={styles.notice}>
        <IconSafe />
        <div>
          <b>凭据由 forge-server 安全托管</b>
          <p>Agent 不持有 GitLab Token 或部署凭据，只能通过受控 Tool API 发起操作。</p>
        </div>
      </article>
      <Drawer
        visible={open}
        width={520}
        title="添加 GitLab"
        onCancel={close}
        footer={
          <>
            <Button onClick={close}>取消</Button>
            <Button type="primary" loading={create.isPending} onClick={() => form.submit()}>
              添加集成
            </Button>
          </>
        }
      >
        <Form form={form} className={styles.form} layout="vertical" aria-label="添加 GitLab" onSubmit={(values) => create.mutate(values)}>
          <Form.Item field="name" label="连接名称" required rules={[{ required: true, message: "请输入连接名称" }]}>
            <Input placeholder="公司 GitLab" />
          </Form.Item>
          <Form.Item
            field="baseUrl"
            label="服务地址"
            required
            rules={[
              { required: true, message: "请输入服务地址" },
              { type: "url", message: "请输入有效的服务地址" },
            ]}
          >
            <Input placeholder="https://gitlab.example.com" />
          </Form.Item>
          <Form.Item field="token" label="访问令牌" required rules={[{ required: true, message: "请输入访问令牌" }]}>
            <Input.Password placeholder="输入访问令牌" />
          </Form.Item>
          {create.isError && <Alert type="error" content={formatRequestError(create.error)} />}
        </Form>
      </Drawer>
      <Drawer visible={configuredConnection !== undefined} width={560} title={configuredConnection ? `配置 ${configuredConnection.name}` : "配置 GitLab"} onCancel={closeConfiguration} footer={<Button onClick={closeConfiguration}>完成</Button>}>
        {configuredConnection && (
          <div className={styles.connectionConfiguration}>
            <Descriptions
              column={1}
              data={[
                { label: "服务地址", value: configuredConnection.baseUrl },
                { label: "连接状态", value: configuredConnection.status === "ACTIVE" ? "已连接" : "待验证" },
                { label: "Token 指纹", value: configuredConnection.tokenFingerprint },
              ]}
            />
            <Button type="primary" loading={test.isPending} onClick={() => test.mutate(configuredConnection.id)}>
              测试连接
            </Button>
            {test.isError && <Alert type="error" content={formatRequestError(test.error)} />}
            <Form form={repositoryForm} className={styles.form} layout="vertical" aria-label="绑定 GitLab 仓库" onSubmit={(values) => bind.mutate(values)}>
              <Form.Item field="remoteProjectId" label="GitLab Project ID 或完整路径" required extra="例如：root/forge-ai-demo，也可以填写 GitLab 数字 Project ID。" rules={[{ required: true, message: "请输入 GitLab Project ID 或完整路径" }]}>
                <Input placeholder="root/forge-ai-demo" />
              </Form.Item>
              {bind.isError && <Alert type="error" content={formatRequestError(bind.error)} />}
              <Button htmlType="submit" loading={bind.isPending}>
                读取并绑定仓库
              </Button>
            </Form>
            <Form form={webhookForm} className={styles.form} layout="vertical" aria-label="配置 GitLab Webhook" onSubmit={(values) => saveWebhook.mutate(values)}>
              <Form.Item label="Webhook URL" extra="在 GitLab 中填写可从 GitLab 网络访问的 ForgeAI 公网地址，并拼接此路径。">
                <Input readOnly value={`/api/v1/gitlab/webhooks/${configuredConnection.id}`} />
              </Form.Item>
              <Form.Item
                field="secret"
                label="Webhook Secret"
                required
                extra="至少 16 个字符；保存后不再回显，请将同一个值填入 GitLab 的 Secret token。"
                rules={[
                  { required: true, message: "请输入 Webhook Secret" },
                  { minLength: 16, message: "Webhook Secret 至少需要 16 个字符" },
                ]}
              >
                <Input.Password placeholder="输入 Webhook Secret" maxLength={2000} />
              </Form.Item>
              {saveWebhook.isError && <Alert type="error" content={formatRequestError(saveWebhook.error)} />}
              <Button htmlType="submit" loading={saveWebhook.isPending}>
                保存 Webhook Secret
              </Button>
            </Form>
          </div>
        )}
      </Drawer>
    </section>
  );
}
