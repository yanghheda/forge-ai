"use client";

import { Card, Typography } from "@arco-design/web-react";

import { ProtectedApp } from "@/features/auth";

export default function IntegrationsPage() {
  return <ProtectedApp><section><Typography.Title heading={2}>集成设置</Typography.Title><Card><Typography.Paragraph>GitLab、模型与部署集成统一作用于当前公司。</Typography.Paragraph></Card></section></ProtectedApp>;
}
