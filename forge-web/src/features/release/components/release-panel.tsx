"use client";

import { Alert, Button, Card, Checkbox, Input, Space, Tag, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { listRequirements } from "@/features/work-item";
import { formatRequestError } from "@/lib/api";

import {
  createRelease,
  decideDeployment,
  listDeployments,
  listReleases,
  requestDeployment,
  runPrecheck,
  updateReleaseNote,
  type DeploymentView,
  type ReleaseView,
} from "../api/release-api";

export function ReleasePanel({ organizationId }: { organizationId: number; organizationId: number }) {
  const queryClient = useQueryClient();
  const key = ["releases", organizationId];
  const releases = useQuery({ queryKey: key, queryFn: () => listReleases(organizationId) });
  const requirements = useQuery({
    queryKey: ["requirements", organizationId],
    queryFn: () => listRequirements(organizationId),
  });
  const [versionName, setVersionName] = useState("");
  const [selected, setSelected] = useState<number[]>([]);
  const refresh = () => queryClient.invalidateQueries({ queryKey: key });
  const create = useMutation({
    mutationFn: () => createRelease({
      organizationId,
      versionName,
      environment: "production",
      itemIds: selected,
      approvalTtlMinutes: 60,
    }),
    onSuccess: () => {
      setVersionName("");
      setSelected([]);
      void refresh();
    },
  });
  const note = useMutation({
    mutationFn: ({ release, value }: { release: ReleaseView; value: string }) => updateReleaseNote({
      organizationId,
      releaseId: release.id,
      note: value,
      expectedVersion: release.version,
    }),
    onSuccess: refresh,
  });
  const precheck = useMutation({
    mutationFn: (releaseId: number) => runPrecheck({ organizationId, releaseId }),
    onSuccess: refresh,
  });

  return (
    <Card title="发布候选版本">
      <Space direction="vertical" style={{ width: "100%" }}>
        <Input aria-label="发布版本号" value={versionName} onChange={setVersionName} placeholder="v1.0.0" />
        <Checkbox.Group value={selected} onChange={(values) => setSelected(values.map(Number))}>
          {(requirements.data?.items ?? []).map((item) => (
            <Checkbox key={item.id} value={item.id}>
              {item.itemKey} · {item.status}
            </Checkbox>
          ))}
        </Checkbox.Group>
        <Button disabled={!versionName.trim() || selected.length === 0} onClick={() => create.mutate()}>
          创建发布候选版本
        </Button>
        {(create.error || note.error || precheck.error) && (
          <Alert type="error" content={formatRequestError(create.error ?? note.error ?? precheck.error)} />
        )}
        {(releases.data ?? []).map((release) => (
          <ReleaseCard
            key={release.id}
            release={release}
            onSaveNote={(value) => note.mutate({ release, value })}
            onPrecheck={() => precheck.mutate(release.id)}
            organizationId={organizationId}
          />
        ))}
      </Space>
    </Card>
  );
}

export function ReleaseCard({ release, onSaveNote, onPrecheck }: {
  release: ReleaseView;
  onSaveNote: (value: string) => void;
  onPrecheck: () => void;
  organizationId?: number;
  organizationId?: number;
}) {
  const [draft, setDraft] = useState(release.releaseNote);
  const organizationId = release.organizationId;
  const queryClient = useQueryClient();
  const deploymentKey = ["deployments", organizationId, release.id];
  const deploymentQuery = useQuery({
    queryKey: deploymentKey,
    queryFn: () => listDeployments(organizationId, release.id),
  });
  const refreshDeployments = () => queryClient.invalidateQueries({ queryKey: deploymentKey });
  const deploy = useMutation({
    mutationFn: () => requestDeployment({
      organizationId,
      releaseId: release.id,
      simulateFailure: false,
      idempotencyKey: crypto.randomUUID(),
    }),
    onSuccess: refreshDeployments,
  });
  const decide = useMutation({
    mutationFn: ({ deployment, decision }: {
      deployment: DeploymentView;
      decision: "APPROVE" | "REJECT";
    }) => decideDeployment({
      organizationId,
      deploymentId: deployment.id,
      decision,
      expectedVersion: deployment.version,
    }),
    onSuccess: refreshDeployments,
  });
  return (
    <Card size="small" title={`${release.versionName} · ${release.environment}`}>
      <Space direction="vertical" style={{ width: "100%" }}>
        <Input.TextArea aria-label={`${release.versionName} 发布说明`} value={draft} onChange={setDraft} />
        <Space>
          <Button disabled={!draft.trim()} onClick={() => onSaveNote(draft)}>
            保存发布说明
          </Button>
          <Button type="primary" onClick={onPrecheck}>
            运行预检
          </Button>
        </Space>
        <Alert
          type="warning"
          content="仅模拟执行：不会连接或改变生产环境。"
        />
        <Button
          status="warning"
          disabled={release.latestPrecheck?.status !== "PASS" || !release.latestPrecheck.current}
          onClick={() => deploy.mutate()}
        >
          申请 HIGH 审批并模拟部署
        </Button>
        {(deploy.error || decide.error) && (
          <Alert type="error" content={formatRequestError(deploy.error ?? decide.error)} />
        )}
        {(deploymentQuery.data ?? []).map((deployment) => (
          <DeploymentCard
            key={deployment.id}
            deployment={deployment}
            onDecide={(decision) => decide.mutate({ deployment, decision })}
          />
        ))}
        {release.latestPrecheck && (
          <>
            {!release.latestPrecheck.current && (
              <Alert type="warning" content="关键资源版本已变化，请重新运行预检。" />
            )}
            <Typography.Text>预检 #{release.latestPrecheck.id}</Typography.Text>
            {release.latestPrecheck.checks.map((check) => (
              <Space key={check.rule}>
                <Tag color={check.passed ? "green" : "red"}>{check.passed ? "通过" : "未通过"}</Tag>
                <Typography.Text>{check.rule}</Typography.Text>
                {!check.passed && <Typography.Text>{check.details.join("、")}</Typography.Text>}
              </Space>
            ))}
          </>
        )}
      </Space>
    </Card>
  );
}

export function DeploymentCard({ deployment, onDecide }: {
  deployment: DeploymentView;
  onDecide: (decision: "APPROVE" | "REJECT") => void;
}) {
  return (
    <Card size="small" title={`部署 #${deployment.id}`}>
      <Space direction="vertical">
        <Tag color="orange">模拟部署 · 非生产环境</Tag>
        <Typography.Text>{deployment.status}</Typography.Text>
        {deployment.status === "PENDING_APPROVAL" && (
          <Space>
            <Button type="primary" onClick={() => onDecide("APPROVE")}>批准</Button>
            <Button status="danger" onClick={() => onDecide("REJECT")}>拒绝</Button>
          </Space>
        )}
        {deployment.resultSummary && <Typography.Text>{deployment.resultSummary}</Typography.Text>}
      </Space>
    </Card>
  );
}
