"use client";

import { IconBranch, IconBug, IconEdit, IconFile, IconLaunch } from "@arco-design/web-react/icon";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";

import { getOrganizationRequirement } from "@/features/work-item";
import styles from "./console.module.css";

export function RequirementContext({ active, requirementId }: { active: string; requirementId: string }) {
  const numericRequirementId = Number(requirementId);
  const requirement = useQuery({
    queryKey: ["requirements", numericRequirementId],
    queryFn: () => getOrganizationRequirement(numericRequirementId),
    enabled: Number.isSafeInteger(numericRequirementId) && numericRequirementId > 0,
  });
  const base = `/requirements/${requirementId}`;
  const items = [
    ["detail", "详情", base, <IconFile key="detail" />], ["document", "PRD 文档", `${base}/documents`, <IconEdit key="document" />],
    ["ux", "UX 设计", `${base}/ux`, <IconBug key="ux" />], ["development", "开发", `${base}/dev`, <IconBranch key="development" />],
    ["qa", "QA 测试", `${base}/qa`, <IconBug key="qa" />], ["release", "发布", `${base}/release`, <IconLaunch key="release" />],
  ] as const;
  return <div className={styles.reqBar}><div><code>{requirement.data?.itemKey ?? `REQ-${requirementId}`}</code><span className={styles.blueTag}>● {requirement.data?.status ?? "加载中"}</span></div><nav aria-label="需求交付阶段">{items.map(([key,label,href,icon]) => <Link className={key === active ? styles.activeTab : ""} key={key} href={href}>{icon}{label}</Link>)}</nav></div>;
}
