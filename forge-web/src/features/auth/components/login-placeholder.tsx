"use client";

import { Alert, Button, Card, Form, Input, Typography } from "@arco-design/web-react";

export function LoginPlaceholder() {
  return (
    <Card title="登录" style={{ maxWidth: 480 }}>
      <Alert type="info" content="登录仅为界面占位；真实 Session 与 CSRF 将在身份会话实现。" />
      <Typography.Paragraph>当前表单不会提交凭据。</Typography.Paragraph>
      <Form layout="vertical" disabled>
        <Form.Item label="邮箱">
          <Input placeholder="name@example.com" />
        </Form.Item>
        <Form.Item label="密码">
          <Input.Password placeholder="••••••••" />
        </Form.Item>
        <Button type="primary" disabled>
          登录（占位）
        </Button>
      </Form>
    </Card>
  );
}
