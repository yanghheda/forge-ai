"use client";

import { Alert, Button, Drawer, Form, Input, Spin, Tag } from "@arco-design/web-react";
import { IconApps, IconPlus, IconSafe } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ProtectedApp } from "@/features/auth";
import { createConnection, listConnections } from "@/features/gitlab";
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
  const [form] = Form.useForm<{ name: string; baseUrl: string; token: string }>();
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
    onSuccess: async () => {
      close();
      await client.invalidateQueries({ queryKey: ["gitlab-connections"] });
    },
  });
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
            <Button>配置</Button>
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
    </section>
  );
}
