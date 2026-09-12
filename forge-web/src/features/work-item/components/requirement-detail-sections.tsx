import { Alert, Avatar, Card, Progress, Tag } from "@arco-design/web-react";
import { IconCheck, IconRobot, IconUserGroup } from "@arco-design/web-react/icon";

import type { OrganizationRequirement, RequirementActivity, RequirementParticipant, RequirementWorkflow } from "../api/work-item-api";
import { formatDate, roleMeta, stageIndex, stages, statusLabel } from "../utils/requirement-detail-display";
import styles from "./organization-requirement-detail.module.css";

export function RequirementSteps({ item }: { item: OrganizationRequirement }) {
  const current = stageIndex(item.status);
  return (
    <Card className={styles.stepsCard} bordered>
      <ol className={styles.steps} aria-label="需求交付阶段">
        {stages.map((stage, index) => (
          <li key={stage.title} className={index < current ? styles.finished : index === current ? styles.current : ""}>
            <span className={styles.stepDot}>{index < current ? <IconCheck /> : index + 1}</span>
            <strong>{stage.title}</strong>
            <small>{index < current ? "已完成" : index === current ? `${statusLabel[item.status] ?? item.status} · 进行中` : "待开始"}</small>
          </li>
        ))}
      </ol>
    </Card>
  );
}

export function BasicInfo({ item, participants }: { item: OrganizationRequirement; participants: RequirementParticipant[] }) {
  const activeRole = (["PRODUCT", "UX", "DEVELOPER", "QA"] as const)[Math.min(stageIndex(item.status), 3)];
  return (
    <Card title="基本信息">
      <dl className={styles.descriptions}>
        <div>
          <dt>编号</dt>
          <dd className={styles.mono}>{item.itemKey}</dd>
        </div>
        <div>
          <dt>所属公司</dt>
          <dd>{item.organizationName}</dd>
        </div>
        <div>
          <dt>类型</dt>
          <dd>产品需求</dd>
        </div>
        <div>
          <dt>来源</dt>
          <dd>人工创建</dd>
        </div>
        <div>
          <dt>当前 Agent</dt>
          <dd>
            <Tag color="purple">
              <IconRobot /> {roleMeta[activeRole].agent}
            </Tag>
          </dd>
        </div>
        <div>
          <dt>负责人</dt>
          <dd>{participants.find((member) => member.role === activeRole)?.displayName ?? "待分配"}</dd>
        </div>
        <div>
          <dt>期望发布</dt>
          <dd>{formatDate(item.dueAt)}</dd>
        </div>
        <div>
          <dt>最近更新</dt>
          <dd>{formatDate(item.updatedAt, true)}</dd>
        </div>
      </dl>
    </Card>
  );
}

export function DescriptionCard({ item }: { item: OrganizationRequirement }) {
  return (
    <Card title="需求描述">
      <div className={styles.prose}>
        <p>{item.description || "尚未补充需求描述。"}</p>
        {item.goal && (
          <>
            <h3>目标</h3>
            <p>{item.goal}</p>
          </>
        )}
        {item.businessValue && (
          <>
            <h3>业务价值</h3>
            <p>{item.businessValue}</p>
          </>
        )}
        {item.inScope && (
          <>
            <h3>纳入范围</h3>
            <p>{item.inScope}</p>
          </>
        )}
        {item.outOfScope && (
          <>
            <h3>排除范围</h3>
            <p>{item.outOfScope}</p>
          </>
        )}
        {item.acceptanceCriteria?.length > 0 && (
          <>
            <h3>验收标准</h3>
            <ul>
              {item.acceptanceCriteria.map((criterion) => (
                <li key={criterion}>{criterion}</li>
              ))}
            </ul>
          </>
        )}
      </div>
    </Card>
  );
}

export function StageCard({ item, workflow }: { item: OrganizationRequirement; workflow?: RequirementWorkflow }) {
  const current = stageIndex(item.status);
  const progress = Math.round(((current + (item.status.startsWith("READY_FOR_") ? 0.2 : 0.62)) / stages.length) * 100);
  const missing = workflow?.availableActions.flatMap((action) => workflow.guardHints[action] ?? []) ?? [];
  return (
    <Card title={`阶段 · ${statusLabel[item.status] ?? item.status}`} extra={<Tag color="arcoblue">{item.status}</Tag>}>
      <div className={styles.progress}>
        <Progress percent={progress} showText={false} />
        <b>{progress}%</b>
      </div>
      <dl className={styles.stageFacts}>
        <dt>进入时间</dt>
        <dd>{formatDate(item.updatedAt, true)}</dd>
        <dt>当前状态</dt>
        <dd>{statusLabel[item.status] ?? item.status}</dd>
        <dt>执行 Agent</dt>
        <dd>
          <Tag color="purple">
            <IconRobot /> {roleMeta[(["PRODUCT", "UX", "DEVELOPER", "QA"] as const)[Math.min(current, 3)]].agent}
          </Tag>
        </dd>
        <dt>下一步</dt>
        <dd>{workflow?.availableActions.length ? "完成当前阶段准入条件并推进流程" : "当前暂无可执行动作"}</dd>
      </dl>
      {missing.length > 0 && <Alert type="warning" title="当前阶段仍有未满足条件" content={missing.join("、")} />}
    </Card>
  );
}

export function MembersCard({ item, participants, onManage }: { item: OrganizationRequirement; participants: RequirementParticipant[]; onManage: () => void }) {
  return (
    <Card
      title={
        <>
          <IconUserGroup /> 协作成员
        </>
      }
    >
      <div className={styles.members}>
        <div className={styles.member}>
          <span>创建人</span>
          <Avatar size={30}>{Array.from(item.reporterName)[0]}</Avatar>
          <b>{item.reporterName}</b>
          <small>需求创建 · 流程发起</small>
        </div>
        {participants.map((member) => (
          <div className={styles.member} key={member.role}>
            <span>{roleMeta[member.role].label}</span>
            <Avatar size={30}>{Array.from(member.displayName)[0]}</Avatar>
            <b>{member.displayName}</b>
            <small>{roleMeta[member.role].duty}</small>
          </div>
        ))}
      </div>
      <button type="button" className={styles.manageButton} onClick={onManage}>
        ＋ 管理成员
      </button>
    </Card>
  );
}

export function ActivityCard({ activity }: { activity: RequirementActivity[] }) {
  return (
    <Card title="动态时间线">
      <div className={styles.timeline}>
        {activity.length ? (
          activity.map((event) => (
            <div key={`${event.kind}-${event.id}`}>
              <i />
              <div>
                <b>{event.kind === "COMMENT" ? "新增评论" : event.action}</b>
                <time>{formatDate(event.createdAt, true)}</time>
                <p>{event.body || event.reason || "状态已由服务端记录。"}</p>
              </div>
            </div>
          ))
        ) : (
          <p className={styles.empty}>暂无动态记录</p>
        )}
      </div>
    </Card>
  );
}
