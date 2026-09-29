import { Alert, Button, Empty, Message, Modal, Spin, Tag } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { getCurrentUser } from "@/features/auth";
import { formatRequestError } from "@/lib/api";
import { decideApproval, listPendingApprovals, type PendingApproval } from "../api/agent-run-api";
import styles from "./pending-approvals.module.css";

/* 判断审批是否已过期：有效期无法解析时按未过期处理，避免误禁操作。 */
function isExpired(expiresAt: string): boolean {
  const deadline = Date.parse(expiresAt);
  return !Number.isNaN(deadline) && deadline < Date.now();
}

/* 将有效期 ISO 字符串本地化展示；无法解析时原样返回，便于排查。 */
function formatExpiry(expiresAt: string): string {
  const deadline = new Date(expiresAt);
  return Number.isNaN(deadline.getTime()) ? expiresAt : deadline.toLocaleString("zh-CN");
}

/* 公司级待审批列表：展示待处理审批并支持批准 / 驳回，30 秒轮询保证审批 TTL 内的新鲜度。 */
export function PendingApprovals() {
  const queryClient = useQueryClient();
  const user = useQuery({ queryKey: ["current-user"], queryFn: () => getCurrentUser() });
  const organizationId = user.data?.organization.id;
  const approvals = useQuery({
    queryKey: ["pending-approvals"],
    queryFn: () => listPendingApprovals(),
    refetchInterval: 30_000,
  });
  const decide = useMutation({
    mutationFn: ({ approval, decision }: { approval: { id: string; version: number }; decision: "APPROVE" | "REJECT" }) => decideApproval(approval, organizationId!, decision),
    onSuccess: (_, variables) => {
      Message.success(variables.decision === "APPROVE" ? "已批准该审批请求。" : "已驳回该审批请求。");
      void queryClient.invalidateQueries({ queryKey: ["pending-approvals"] });
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
    onError: (error) => {
      Message.error(formatRequestError(error));
      // 版本冲突或审批已过期时列表可能已变化，刷新以便展示最新状态。
      void queryClient.invalidateQueries({ queryKey: ["pending-approvals"] });
    },
  });

  /* 先二次确认再提交，避免误操作不可逆的审批决策。 */
  const confirmDecision = (approval: PendingApproval, decision: "APPROVE" | "REJECT") => {
    Modal.confirm({
      title: decision === "APPROVE" ? "确认批准" : "确认驳回",
      content: `确认${decision === "APPROVE" ? "批准" : "驳回"}工具「${approval.toolName}」的审批请求？`,
      okButtonProps: decision === "REJECT" ? { status: "danger" } : undefined,
      onOk: () => decide.mutate({ approval: { id: approval.id, version: approval.version }, decision }),
    });
  };

  const items = approvals.data ?? [];

  return (
    <section className={styles.page}>
      <header className={styles.pageHead}>
        <div>
          <h1>待审批</h1>
          <p>处理 Agent 高风险 Tool 的待办审批，审批到期后自动失效。</p>
        </div>
      </header>
      {approvals.isPending && <Spin />}
      {approvals.isError && <Alert type="error" content={formatRequestError(approvals.error)} />}
      {!approvals.isPending && !approvals.isError && items.length === 0 && <Empty description="暂无待审批" />}
      <div className={styles.list}>
        {items.map((approval) => {
          const expired = isExpired(approval.expiresAt);
          return (
            <article key={approval.id} className={styles.item}>
              <div className={styles.itemHead}>
                <b className={styles.tool}>{approval.toolName}</b>
                <Tag color={approval.riskLevel === "HIGH" ? "red" : "blue"}>{approval.riskLevel}</Tag>
                {expired && <Tag color="gray">已过期</Tag>}
              </div>
              <dl className={styles.meta}>
                <dt>发起人</dt>
                <dd>{approval.requestedByName}</dd>
                <dt>原因</dt>
                <dd>{approval.reason}</dd>
                <dt>有效期</dt>
                <dd>
                  {formatExpiry(approval.expiresAt)}
                  {expired ? "（已过期）" : ""}
                </dd>
              </dl>
              <div className={styles.actions}>
                <Button type="primary" disabled={expired || decide.isPending} onClick={() => confirmDecision(approval, "APPROVE")}>
                  批准
                </Button>
                <Button status="danger" disabled={expired || decide.isPending} onClick={() => confirmDecision(approval, "REJECT")}>
                  驳回
                </Button>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}
