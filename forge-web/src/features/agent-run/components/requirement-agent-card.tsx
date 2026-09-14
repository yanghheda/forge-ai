"use client";

import { Alert, Button, Card, Input, Space, Typography } from "@arco-design/web-react";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { createRequirementConversation, sendRequirementAgentMessage } from "../api/agent-run-api";

export function RequirementAgentCard({ requirementId }: { requirementId: number }) {
  const [conversationId, setConversationId] = useState<number>();
  const [message, setMessage] = useState("");
  const run = useMutation({
    mutationFn: async () => {
      const id = conversationId ?? (await createRequirementConversation(`Requirement #${requirementId}`)).id;
      setConversationId(id);
      return sendRequirementAgentMessage(id, requirementId, message.trim());
    },
    onSuccess: () => setMessage(""),
  });

  return (
    <Card title="当前阶段 Agent">
      <Typography.Paragraph type="secondary">服务端根据 Requirement 当前状态自动选择 Product、UX、Developer、QA 或 Release Skill。</Typography.Paragraph>
      <Space direction="vertical" style={{ width: "100%" }}>
        <Input.TextArea value={message} onChange={setMessage} placeholder="例如：检查当前材料并提交到下一阶段" autoSize={{ minRows: 3, maxRows: 6 }} />
        <Button type="primary" disabled={!message.trim()} loading={run.isPending} onClick={() => run.mutate()}>
          发送给当前阶段 Agent
        </Button>
        {run.isError && <Alert type="error" content="Agent Run 创建失败，请检查权限、阶段与服务状态。" />}
        {run.data && (
          <Alert
            type="info"
            content={
              <span>
                已由 {run.data.skill} Agent 接收，状态 {run.data.status}。 <Link href={`/agent/trace/${run.data.id}`}>查看真实 Run 与审批</Link>
              </span>
            }
          />
        )}
      </Space>
    </Card>
  );
}
