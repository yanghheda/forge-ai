"use client";

import { Alert, Button, Drawer, Form, Input, Select, Spin, Table, Tag } from "@arco-design/web-react";
import { IconEdit, IconSearch } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { ProtectedApp } from "@/features/auth";
import { listCompanyMembers, updateCompanyMember, type CompanyMember } from "@/features/console";
import { formatRequestError } from "@/lib/api";
import { roleLabel } from "@/lib/labels";
import styles from "../settings.module.css";

const roles = ["PRODUCT", "UX", "DEVELOPER", "QA", "RELEASE_APPROVER"];

export default function MembersPage() {
  return (
    <ProtectedApp>
      <Members />
    </ProtectedApp>
  );
}

function Members() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<CompanyMember | null>(null);
  const [form] = Form.useForm<{ role: string; status: "ACTIVE" | "DISABLED" }>();
  const members = useQuery({ queryKey: ["company-members"], queryFn: () => listCompanyMembers() });
  const save = useMutation({
    mutationFn: (values: { role: string; status: "ACTIVE" | "DISABLED" }) => updateCompanyMember(editing!.userId, { ...values, expectedVersion: editing!.version }),
    onSuccess: async () => {
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ["company-members"] });
    },
  });
  const edit = (member: CompanyMember) => {
    setEditing(member);
    form.setFieldsValue({
      role: member.roles[0] ?? "DEVELOPER",
      status: member.status === "DISABLED" ? "DISABLED" : "ACTIVE",
    });
  };
  const count = (value: CompanyMember["status"]) => members.data?.filter((member) => member.status === value).length ?? "—";

  return (
    <section className={styles.page}>
      <header>
        <div>
          <h1>成员管理</h1>
          <p>审核自主注册账号，管理岗位角色与启停状态</p>
        </div>
      </header>
      <div className={styles.stats}>
        <div>
          <span>全部成员</span>
          <strong>{members.data?.length ?? "—"}</strong>
        </div>
        <div>
          <span>已启用</span>
          <strong>{count("ACTIVE")}</strong>
        </div>
        <div>
          <span>待审核</span>
          <strong>{count("PENDING")}</strong>
        </div>
        <div>
          <span>已停用</span>
          <strong>{count("DISABLED")}</strong>
        </div>
      </div>
      <article className={styles.card}>
        <div className={styles.filters}>
          <Input prefix={<IconSearch />} placeholder="搜索姓名或邮箱" />
          <Select
            defaultValue="全部角色"
            options={["全部角色", "产品", "UX", "开发", "测试"].map((value) => ({
              label: value,
              value,
            }))}
          />
        </div>
        {members.isPending && <Spin tip="正在加载成员…" />}
        {members.isError && <Alert type="error" content={formatRequestError(members.error)} />}
        {members.data && (
          <Table
            pagination={{ pageSize: 8 }}
            rowKey="userId"
            data={members.data}
            columns={[
              {
                title: "成员",
                render: (_, row) => (
                  <div className={styles.member}>
                    <span>{row.displayName.slice(0, 1)}</span>
                    <div>
                      <b>{row.displayName}</b>
                      <small>{row.email}</small>
                    </div>
                  </div>
                ),
              },
              {
                title: "角色",
                render: (_, row) =>
                  row.roles.map((item) => (
                    <Tag key={item} color="arcoblue">
                      {roleLabel(item)}
                    </Tag>
                  )),
              },
              {
                title: "账号状态",
                render: (_, row) => <Tag color={row.status === "ACTIVE" ? "green" : row.status === "PENDING" ? "orange" : "gray"}>{row.status === "ACTIVE" ? "● 已启用" : row.status === "PENDING" ? "待审核" : "已停用"}</Tag>,
              },
              {
                title: "最近登录",
                render: (_, row) => (row.lastLoginAt ? new Date(row.lastLoginAt).toLocaleString("zh-CN") : "尚未登录"),
              },
              {
                title: "操作",
                render: (_, row) => (
                  <Button type="text" icon={<IconEdit />} onClick={() => edit(row)}>
                    {row.status === "PENDING" ? "审核" : "编辑"}
                  </Button>
                ),
              },
            ]}
          />
        )}
      </article>
      <Drawer
        visible={!!editing}
        width={480}
        title={editing?.status === "PENDING" ? "审核成员" : "编辑成员"}
        onCancel={() => setEditing(null)}
        footer={
          <>
            <Button onClick={() => setEditing(null)}>取消</Button>
            <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
              {editing?.status === "PENDING" ? "审核并启用" : "保存修改"}
            </Button>
          </>
        }
      >
        <Form form={form} className={styles.form} layout="vertical" aria-label="编辑成员" onSubmit={(values) => save.mutate(values)}>
          <Form.Item label="姓名">
            <Input value={editing?.displayName} readOnly />
          </Form.Item>
          <Form.Item label="邮箱">
            <Input value={editing?.email} disabled />
          </Form.Item>
          <Form.Item field="role" label="岗位角色" required rules={[{ required: true, message: "请选择岗位角色" }]}>
            <Select options={roles.map((value) => ({ label: roleLabel(value), value }))} />
          </Form.Item>
          <Form.Item field="status" label="账号状态" required rules={[{ required: true, message: "请选择账号状态" }]}>
            <Select
              options={[
                { label: "已启用", value: "ACTIVE" },
                { label: "已停用", value: "DISABLED" },
              ]}
            />
          </Form.Item>
          {save.isError && <Alert type="error" content={formatRequestError(save.error)} />}
        </Form>
      </Drawer>
    </section>
  );
}
