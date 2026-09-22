import { Alert, Button, Message, Spin } from "@arco-design/web-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { formatRequestError } from "@/lib/api";
import { QaPanel, StageTasks, RequirementWorkflowControls, DevelopmentBugs } from "@/features/work-item";
import { listBoardItems, moveBoardItem } from "../api/console-api";
import { RequirementContext } from "./requirement-context";
import styles from "./console.module.css";
import { UxDelivery } from "./ux-delivery";
import { DevelopmentWorkspace } from "./development-workspace";
import { ReleaseWorkspace } from "./release-workspace";

type DeliveryKind = "document" | "ux" | "tasks" | "development" | "qa" | "release";

export function DeliveryScreen({ kind, requirementId }: { kind: DeliveryKind; requirementId?: string }) {
  return (
    <section className={styles.page} data-page={kind}>
      {requirementId && <RequirementContext active={kind === "tasks" ? "development" : kind} requirementId={requirementId} />}
      {kind === "document" && <Document />}
      {kind === "ux" && requirementId && (
        <>
          <StageTasks requirementId={Number(requirementId)} type="UX_TASK" />
          <RequirementWorkflowControls requirementId={Number(requirementId)} />
          <UxDelivery requirementId={Number(requirementId)} />
        </>
      )}
      {kind === "tasks" && <Tasks />}
      {kind === "development" && requirementId && (
        <>
          <StageTasks requirementId={Number(requirementId)} type="DEV_TASK" />
          <RequirementWorkflowControls requirementId={Number(requirementId)} />
          <DevelopmentWorkspace requirementId={Number(requirementId)} />
          <DevelopmentBugs requirementId={Number(requirementId)} />
        </>
      )}
      {kind === "qa" && requirementId && (
        <>
          <StageTasks requirementId={Number(requirementId)} type="QA_TASK" />
          <RequirementWorkflowControls requirementId={Number(requirementId)} />
          <Qa requirementId={Number(requirementId)} />
        </>
      )}
      {kind === "release" && requirementId && <ReleaseWorkspace requirementId={Number(requirementId)} />}
    </section>
  );
}

function Head({ title, description, action }: { title: string; description: string; action?: React.ReactNode }) {
  return (
    <header className={styles.pageHead}>
      <div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </header>
  );
}
function Card({ title, children, extra }: { title: string; children: React.ReactNode; extra?: React.ReactNode }) {
  return (
    <article className={styles.card}>
      <header>
        <strong>{title}</strong>
        {extra}
      </header>
      <div className={styles.cardBody}>{children}</div>
    </article>
  );
}

function Document() {
  return (
    <>
      <Head
        title="PRD 文档"
        description="REQ-1 · 产品需求文档 v3"
        action={
          <>
            <Button>版本历史</Button>
            <Button type="primary">保存新版本</Button>
          </>
        }
      />
      <div className={styles.editorGrid}>
        <Card title="文档大纲">
          <ol className={styles.outline}>
            <li>1 背景与目标</li>
            <li>2 用户故事</li>
            <li>3 功能需求</li>
            <li>4 非功能需求</li>
            <li>5 验收标准</li>
          </ol>
        </Card>
        <Card title="手机验证码登录">
          <div className={styles.prose}>
            <h2>1 背景与目标</h2>
            <p>降低登录摩擦，同时保持账号与会话安全。</p>
            <h2>2 用户故事</h2>
            <p>作为公司成员，我希望使用手机验证码登录，以便在忘记密码时继续访问工作台。</p>
            <h2>3 功能需求</h2>
            <h3>3.1 验证码校验</h3>
            <p>验证码必须具备有效期、重试次数和单次使用约束。</p>
            <h3>3.2 人工审批边界</h3>
            <p>高风险工具调用必须暂停并等待审批，超时后转为待恢复。</p>
          </div>
        </Card>
        <Card title="Agent 协作">
          <div className={styles.chat}>
            <span>ProductAgent</span>
            <p>已根据评审意见补充异常流程与验收标准。</p>
            <span>引用 PRD v3 · 12:04</span>
          </div>
        </Card>
      </div>
    </>
  );
}
function Tasks() {
  const queryClient = useQueryClient();
  const board = useQuery({ queryKey: ["task-board"], queryFn: () => listBoardItems() });
  const move = useMutation({
    mutationFn: ({ id, lane, position }: { id: number; lane: string; position: number }) => moveBoardItem(id, lane, position),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["task-board"] }),
    onError: (error) => Message.error(formatRequestError(error)),
  });
  const lanes = [
    ["TODO", "待处理"],
    ["IN_PROGRESS", "进行中"],
    ["BLOCKED", "阻塞中"],
    ["WAITING_VERIFICATION", "待验证"],
    ["DONE", "已完成"],
  ] as const;
  return (
    <>
      <Head title="任务看板" description="公司全部需求的交付任务与阻塞状态" action={<a href="/my-requirements">进入需求创建任务</a>} />
      {board.isPending && <Spin tip="正在加载看板…" />}
      {board.isError && <Alert type="error" content={formatRequestError(board.error)} />}
      <div className={styles.board}>
        {lanes.map(([lane, title]) => {
          const items = board.data?.filter((item) => item.lane === lane) ?? [];
          return (
            <div
              className={styles.column}
              key={lane}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => {
                const id = Number(event.dataTransfer.getData("text/work-item-id"));
                if (id && board.data?.find((item) => item.id === id)?.lane === lane) move.mutate({ id, lane, position: items.length * 1000 + 1000 });
                else Message.info("请进入任务工作区执行状态操作。");
              }}
            >
              <header>
                <strong>{title}</strong>
                <span>{items.length}</span>
              </header>
              {items.map((item) => (
                <article draggable key={item.id} onDragStart={(event) => event.dataTransfer.setData("text/work-item-id", String(item.id))}>
                  <code>{item.itemKey}</code>
                  <h3>{item.requirementId ? <a href={`/requirements/${item.requirementId}/${item.type === "UX_TASK" ? "ux" : item.type === "QA_TASK" ? "qa" : "development"}`}>{item.title}</a> : item.title}</h3>
                  <p>{{ UX_TASK: "设计任务", DEV_TASK: "开发任务", QA_TASK: "测试任务", BUG: "缺陷任务" }[item.type] ?? item.type} · 同列拖拽排序</p>
                  <footer>
                    <span className={styles.avatar}>{item.assigneeUserId ?? "—"}</span>
                    <time>#{item.position}</time>
                  </footer>
                </article>
              ))}
            </div>
          );
        })}
      </div>
    </>
  );
}
function Qa({ requirementId }: { requirementId: number }) {
  return (
    <>
      <Head title="QA 测试" description="测试用例、执行结果与质量门禁" />
      <QaPanel organizationId={0} requirementId={requirementId} onChanged={async () => undefined} />
    </>
  );
}
