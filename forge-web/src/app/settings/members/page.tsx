"use client";

import { Alert, Card, Spin, Table, Tag, Typography } from "@arco-design/web-react";
import { useQuery } from "@tanstack/react-query";

import { ProtectedApp } from "@/features/auth";
import { listRequirementMembers } from "@/features/work-item";
import { formatRequestError } from "@/lib/api";
import { roleLabel } from "@/lib/labels";

export default function MembersPage() {
  return <ProtectedApp><Members /></ProtectedApp>;
}

function Members() {
  const members = useQuery({ queryKey: ["requirements", "members"], queryFn: () => listRequirementMembers() });
  return <section><Typography.Title heading={2}>成员与角色</Typography.Title><Typography.Paragraph>除首个 Owner 外，团队成员从登录页自行注册产品、UX、开发或测试账号。</Typography.Paragraph>{members.isPending && <Spin tip="正在加载成员…" />}{members.isError && <Alert type="error" content={formatRequestError(members.error)} />}{members.data && <Card><Table pagination={false} rowKey="userId" data={members.data} columns={[{ title: "成员", dataIndex: "displayName" }, { title: "邮箱", dataIndex: "email" }, { title: "角色", render: (_, record) => record.roles.map((role) => <Tag key={role}>{roleLabel(role)}</Tag>) }]} /></Card>}</section>;
}
