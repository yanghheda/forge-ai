import { Alert, Space } from "@arco-design/web-react";

import { uxChecklistItems, uxChecklistLabel, type UxChecklist } from "../utils/ux-review";

export function UxReviewChecklist({ checklist, needsPublishedUxSpec, onChange }: { checklist: UxChecklist; needsPublishedUxSpec: boolean; onChange: (item: keyof UxChecklist, checked: boolean) => void }) {
  return (
    <>
      {needsPublishedUxSpec && <Alert type="warning" content="请先在 UX Spec 页签创建文档、保存版本并发布，再确认以下交付检查项。" />}
      <Space direction="vertical">
        {uxChecklistItems.map((item) => (
          <label key={item}>
            <input type="checkbox" checked={checklist[item]} onChange={(event) => onChange(item, event.target.checked)} /> {uxChecklistLabel(item)}
          </label>
        ))}
      </Space>
    </>
  );
}
