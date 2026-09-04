"use client";

import {
  Alert,
  Button,
  Card,
  Input,
  Select,
  Space,
  Typography,
} from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
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
      setTitle("");
      void queryClient.invalidateQueries({
        queryKey: ["work-items", projectId],
      });
    },
  });
  return (
    <Card title="Product Requirements" size="small">
      <Space direction="vertical" style={{ width: "100%" }}>
        <Space>
          <Input
            aria-label="Requirement 标题"
            value={title}
            onChange={setTitle}
            placeholder="新建 Requirement"
          />
          <Select
            aria-label="优先级"
            value={priority}
            onChange={setPriority}
            options={["LOW", "MEDIUM", "HIGH", "URGENT"].map((value) => ({
              label: value,
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
        </Space>
        {create.isError && (
          <Alert type="error" content={create.error.message} />
        )}
        {requirements.isError && (
          <Alert type="error" content={requirements.error.message} />
        )}
        {requirements.data?.items.length === 0 && (
          <Typography.Text>
            暂无 Requirement，可从上方开始人工交付闭环。
          </Typography.Text>
        )}
        {requirements.data?.items.map((item) => (
          <Link
            key={item.id}
            href={`/w/${workspaceSlug}/p/${projectKey}/requirements/${item.id}`}
          >
            {item.itemKey} · {item.title} · {item.status}
          </Link>
        ))}
      </Space>
    </Card>
  );
}
