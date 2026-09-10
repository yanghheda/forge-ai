"use client";

import { Alert, Button, Card, Form, Input, InputNumber, List, Message, Spin, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { getCurrentUser } from "@/features/auth";
import ui from "@/components/workbench/workbench.module.css";
import { formatRequestError } from "@/lib/api";
import { bindRepository, createConnection, listConnections, rotateToken, testConnection } from "../api/gitlab-api";

export function GitLabSettings({ workspaceSlug }: { workspaceSlug: string }) {
  const queryClient = useQueryClient();
  const currentUser = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const workspace = currentUser.data?.workspaces.find((item) => item.slug === workspaceSlug);
  const connections = useQuery({
    queryKey: ["gitlab-connections", workspace?.id],
    queryFn: () => listConnections(workspace!.id),
    enabled: workspace !== undefined,
  });
  const [connectionForm, setConnectionForm] = useState({ name: "", baseUrl: "https://gitlab.com", token: "" });
  const [rotationToken, setRotationToken] = useState("");
  const [binding, setBinding] = useState({ projectId: 0, connectionId: 0, remoteProjectId: "" });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["gitlab-connections", workspace?.id] });
  const create = useMutation({
    mutationFn: () => createConnection({ workspaceId: workspace!.id, ...connectionForm }),
    onSuccess: () => {
      Message.success("GitLab 连接已保存。");
      setConnectionForm((value) => ({ ...value, token: "" }));
      invalidate();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const rotate = useMutation({
    mutationFn: (connection: { id: number; version: number }) =>
      rotateToken(workspace!.id, connection.id, connection.version, rotationToken),
    onSuccess: () => {
      Message.success("访问令牌已轮换。");
      setRotationToken("");
      invalidate();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const test = useMutation({
    mutationFn: (connectionId: number) => testConnection(workspace!.id, connectionId),
    onSuccess: () => { Message.success("GitLab 连接测试完成。"); return invalidate(); },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const bind = useMutation({
    mutationFn: () => bindRepository({ workspaceId: workspace!.id, ...binding }),
    onSuccess: () => Message.success("项目仓库已绑定。"),
    onError: (error) => Message.error(formatRequestError(error)),
  });

  if (currentUser.isPending || connections.isPending) {
    return <Spin tip="正在加载 GitLab 设置…" />;
  }
  if (!workspace) {
    return <Alert type="error" content="当前账户无权访问此工作空间。" />;
  }

  return (
    <section className={ui.page}>
      <header className={ui.pageHeader}>
        <div><span className={ui.eyebrow}>集成</span><h1>GitLab 集成</h1><p>管理连接凭据、健康状态与项目仓库绑定。</p></div>
      </header>
      <div className={ui.twoColumns}>
      <Card title="新增 GitLab 连接">
        <Form layout="vertical" onSubmit={() => create.mutate()}>
          <Form.Item label="连接名称" required>
            <Input value={connectionForm.name} onChange={(name) => setConnectionForm({ ...connectionForm, name })} />
          </Form.Item>
          <Form.Item label="服务地址（Base URL）" required>
            <Input value={connectionForm.baseUrl} onChange={(baseUrl) => setConnectionForm({ ...connectionForm, baseUrl })} />
          </Form.Item>
          <Form.Item label="访问令牌（Access Token）" required extra="保存后不会再次回显。">
            <Input.Password value={connectionForm.token} onChange={(token) => setConnectionForm({ ...connectionForm, token })} />
          </Form.Item>
          <Button htmlType="submit" type="primary" loading={create.isPending}>保存连接</Button>
        </Form>
      </Card>

      <Card title="已有连接">
        <Input.Password
          aria-label="轮换后的新 Token"
          value={rotationToken}
          placeholder="输入新 Token 后选择连接轮换"
          onChange={setRotationToken}
        />
        <List
          dataSource={connections.data ?? []}
          render={(connection) => (
            <List.Item
              actions={[
                <Button key="test" onClick={() => test.mutate(connection.id)}>测试</Button>,
                <Button
                  key="rotate"
                  disabled={!rotationToken}
                  onClick={() => rotate.mutate({ id: connection.id, version: connection.version })}
                >
                  轮换 Token
                </Button>,
              ]}
            >
              <Typography.Text>
                {connection.name} · {connection.baseUrl} · {connection.status} · 指纹 {connection.tokenFingerprint}
              </Typography.Text>
            </List.Item>
          )}
        />
      </Card>
      </div>

      <Card title="绑定项目仓库">
        <Form layout="vertical" onSubmit={() => bind.mutate()}>
          <Form.Item label="项目 ID" required>
            <InputNumber min={1} value={binding.projectId} onChange={(projectId) => setBinding({ ...binding, projectId: projectId ?? 0 })} />
          </Form.Item>
          <Form.Item label="连接 ID" required>
            <InputNumber min={1} value={binding.connectionId} onChange={(connectionId) => setBinding({ ...binding, connectionId: connectionId ?? 0 })} />
          </Form.Item>
          <Form.Item label="GitLab Project ID 或完整路径" required>
            <Input value={binding.remoteProjectId} onChange={(remoteProjectId) => setBinding({ ...binding, remoteProjectId })} />
          </Form.Item>
          <Button htmlType="submit" loading={bind.isPending}>读取并绑定仓库</Button>
        </Form>
      </Card>
    </section>
  );
}
