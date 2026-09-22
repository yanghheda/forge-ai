import { Alert, Message } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { formatRequestError } from "@/lib/api";
import { listBugs, transitionBug, type BugAction, type BugView } from "../api/work-item-api";
import { BugList } from "./qa-panel";

export function DevelopmentBugs({ requirementId }: { requirementId: number }) {
  const client = useQueryClient();
  const bugs = useQuery({ queryKey: ["qa", 0, requirementId, "bugs"], queryFn: () => listBugs(0, requirementId) });
  const move = useMutation({
    mutationFn: ({ bug, action, reason, evidence }: { bug: BugView; action: BugAction; reason?: string; evidence?: string }) =>
      transitionBug({ organizationId: 0, bugId: bug.id, action, expectedVersion: bug.version, reason, fixEvidence: evidence ? [evidence] : [], idempotencyKey: crypto.randomUUID() }),
    onSuccess: () => client.invalidateQueries(),
    onError: (e) => Message.error(formatRequestError(e)),
  });
  if (bugs.isError) return <Alert type="error" content={formatRequestError(bugs.error)} />;
  return <BugList bugs={bugs.data ?? []} onAction={(bug, action, reason, evidence) => move.mutate({ bug, action, reason, evidence })} />;
}
