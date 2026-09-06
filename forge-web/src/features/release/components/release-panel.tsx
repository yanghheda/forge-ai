"use client";

import { Alert, Button, Card, Checkbox, Input, Space, Tag, Typography } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { listRequirements } from "@/features/work-item";

import { createRelease, listReleases, runPrecheck, updateReleaseNote, type ReleaseView } from "../api/release-api";

export function ReleasePanel({ workspaceId, projectId }: { workspaceId: number; projectId: number }) {
  const queryClient = useQueryClient();
  const key = ["releases", workspaceId, projectId];
  const releases = useQuery({ queryKey: key, queryFn: () => listReleases(workspaceId, projectId) });
  const requirements = useQuery({
    queryKey: ["requirements", workspaceId, projectId],
    queryFn: () => listRequirements(workspaceId, projectId),
  });
  const [versionName, setVersionName] = useState("");
  const [selected, setSelected] = useState<number[]>([]);
  const refresh = () => queryClient.invalidateQueries({ queryKey: key });
  const create = useMutation({
    mutationFn: () => createRelease({
      workspaceId,
      projectId,
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
      workspaceId,
      projectId,
      releaseId: release.id,
      note: value,
      expectedVersion: release.version,
    }),
    onSuccess: refresh,
  });
  const precheck = useMutation({
    mutationFn: (releaseId: number) => runPrecheck({ workspaceId, projectId, releaseId }),
    onSuccess: refresh,
  });

  return (
    <Card title="Release candidates">
      <Space direction="vertical" style={{ width: "100%" }}>
        <Input aria-label="Release version" value={versionName} onChange={setVersionName} placeholder="v1.0.0" />
        <Checkbox.Group value={selected} onChange={(values) => setSelected(values.map(Number))}>
          {(requirements.data?.items ?? []).map((item) => (
            <Checkbox key={item.id} value={item.id}>
              {item.itemKey} · {item.status}
            </Checkbox>
          ))}
        </Checkbox.Group>
        <Button disabled={!versionName.trim() || selected.length === 0} onClick={() => create.mutate()}>
          创建 Release Candidate
        </Button>
        {(create.error || note.error || precheck.error) && (
          <Alert type="error" content={(create.error ?? note.error ?? precheck.error)?.message} />
        )}
        {(releases.data ?? []).map((release) => (
          <ReleaseCard
            key={release.id}
            release={release}
            onSaveNote={(value) => note.mutate({ release, value })}
            onPrecheck={() => precheck.mutate(release.id)}
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
}) {
  const [draft, setDraft] = useState(release.releaseNote);
  return (
    <Card size="small" title={`${release.versionName} · ${release.environment}`}>
      <Space direction="vertical" style={{ width: "100%" }}>
        <Input.TextArea aria-label={`${release.versionName} Release Note`} value={draft} onChange={setDraft} />
        <Space>
          <Button disabled={!draft.trim()} onClick={() => onSaveNote(draft)}>
            保存 Release Note
          </Button>
          <Button type="primary" onClick={onPrecheck}>
            运行 Precheck
          </Button>
        </Space>
        {release.latestPrecheck && (
          <>
            {!release.latestPrecheck.current && (
              <Alert type="warning" content="关键资源版本已变化，请重新运行 Precheck。" />
            )}
            <Typography.Text>Precheck #{release.latestPrecheck.id}</Typography.Text>
            {release.latestPrecheck.checks.map((check) => (
              <Space key={check.rule}>
                <Tag color={check.passed ? "green" : "red"}>{check.passed ? "PASS" : "FAIL"}</Tag>
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
