"use client";

import {
  Alert,
  Button,
  Card,
  Input,
  Message,
  Select,
  Tag,
  Typography,
} from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import ui from "@/components/workbench/workbench.module.css";
import { formatRequestError } from "@/lib/api";
import { priorityLabel } from "@/lib/labels";

import { createRequirement, listRequirements } from "../api/work-item-api";

export function ProductSlice({
  workspaceId,
  projectId,
  workspaceSlug,
  projectKey,
}: {
  workspaceId: number;
  projectId: number;
  workspaceSlug: string;
  projectKey: string;
}) {
  const queryClient = useQueryClient();
  const [title, setTitle] = useState("");
  const [priority, setPriority] = useState("MEDIUM");
  const requirements = useQuery({
    queryKey: ["work-items", projectId, "REQUIREMENT"],
    queryFn: () => listRequirements(workspaceId, projectId),
  });
  const create = useMutation({
    mutationFn: () =>
      createRequirement({
        workspaceId,
        projectId,
        title,
        description: "",
        priority,
      }),
    onSuccess: () => {
      Message.success("需求创建成功。");
      setTitle("");
      void queryClient.invalidateQueries({
        queryKey: ["work-items", projectId],
      });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  return (
    <Card title="产品需求" size="small" className={ui.panel}>
      <div className={ui.panelIntro}>
        <div>
          <strong>需求队列</strong>
          <p>创建需求并推进 Product、UX、研发与 QA 交付流程。</p>
        </div>
        <div className={ui.inlineForm}>
          <Input
            aria-label="需求标题"
            value={title}
            onChange={setTitle}
            placeholder="新建需求"
          />
          <Select
            aria-label="优先级"
            value={priority}
            onChange={setPriority}
            options={["LOW", "MEDIUM", "HIGH", "URGENT"].map((value) => ({
              label: priorityLabel(value),
              value,
            }))}
          />
          <Button
            type="primary"
            disabled={!title.trim()}
            loading={create.isPending}
            onClick={() => create.mutate()}
          >
            创建
          </Button>
        </div>
      </div>
      <div className={ui.content}>
        {requirements.isError && (
          <Alert type="error" content={formatRequestError(requirements.error)} />
        )}
        {requirements.data?.items.length === 0 && (
          <div className={ui.empty}><Typography.Text type="secondary">
            暂无需求，可从上方开始人工交付闭环。
          </Typography.Text></div>
        )}
        <div className={ui.list}>
        {requirements.data?.items.map((item) => (
          <Link
            className={ui.listItem}
            key={item.id}
            href={`/w/${workspaceSlug}/p/${projectKey}/requirements/${item.id}`}
          >
            <span className={ui.itemMain}>
              <span className={ui.itemTitle}>{item.title}</span>
              <span className={ui.itemMeta}>{item.itemKey} · 优先级 {priorityLabel(item.priority)}</span>
            </span>
            <Tag color={item.status === "READY_FOR_DEV" ? "green" : "arcoblue"}>{item.status}</Tag>
          </Link>
        ))}
        </div>
      </div>
    </Card>
  );
}
