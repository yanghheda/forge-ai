"use client";

import { Alert, Button, Card, Checkbox, Input, Message, Space } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { formatRequestError } from "@/lib/api";
import { guardHintLabel, workflowActionLabel } from "@/lib/labels";
import { getRequirementWorkflow, transitionRequirementWorkflow, type RequirementWorkflowAction } from "../api/work-item-api";

const checklistKeys = ["userFlow", "pageList", "keyInteraction", "exceptionState"];
export function RequirementWorkflowControls({ requirementId }: { requirementId: number }) {
  const client = useQueryClient();
  const [reason, setReason] = useState("");
  const [checklist, setChecklist] = useState<string[]>([]);
  const workflow = useQuery({ queryKey: ["requirements", requirementId, "workflow"], queryFn: () => getRequirementWorkflow(requirementId) });
  const transition = useMutation({
    mutationFn: (action: RequirementWorkflowAction) => transitionRequirementWorkflow(requirementId, action, workflow.data!.version, { reason, checklist }),
    onSuccess: async () => {
      setReason("");
      await client.invalidateQueries();
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });
  if (workflow.isError) return <Alert type="error" content={formatRequestError(workflow.error)} />;
  if (!workflow.data?.availableActions.length) return null;
  return (
    <Card title="需求阶段操作">
      {workflow.data.availableActions.some((action) => action.startsWith("REJECT")) && <Input.TextArea aria-label="退回原因" value={reason} onChange={setReason} placeholder="退回时请填写修改意见" />}
      {workflow.data.availableActions.includes("SUBMIT_UX_REVIEW") && <Checkbox.Group value={checklist} onChange={(values) => setChecklist(values.map(String))} options={checklistKeys.map((value) => ({ value, label: guardHintLabel(value) }))} />}
      <Space wrap style={{ marginTop: 12 }}>
        {workflow.data.availableActions.map((action) => {
          const hints = workflow.data!.guardHints[action] ?? [];
          const missing = [...hints, ...(action.startsWith("REJECT") && !reason.trim() ? ["reason"] : [])].filter((hint) => (hint === "reason" ? !reason.trim() : checklistKeys.includes(hint) ? !checklist.includes(hint) : true));
          return (
            <div key={action}>
              <Button type={action.startsWith("REJECT") ? "secondary" : "primary"} disabled={missing.length > 0} loading={transition.isPending} onClick={() => transition.mutate(action)}>
                {workflowActionLabel(action)}
              </Button>
              {missing.length > 0 && <p>{missing.map(guardHintLabel).join("、")}</p>}
            </div>
          );
        })}
      </Space>
    </Card>
  );
}
