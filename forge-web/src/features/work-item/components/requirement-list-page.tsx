import { Button, Typography } from "@arco-design/web-react";
import { IconPlus } from "@arco-design/web-react/icon";
import { useState } from "react";

import { RequirementCreateDrawer } from "./requirement-create-drawer";
import { RequirementList } from "./requirement-list";
import styles from "./requirement-list-page.module.css";

/* 需求列表页是需求详情及其阶段页的一级入口，只列需求本体。 */
export function RequirementListPage() {
  const [creating, setCreating] = useState(false);

  return (
    <section className={styles.page} aria-labelledby="requirement-list-page-title">
      <header className={styles.header}>
        <div>
          <Typography.Title id="requirement-list-page-title" heading={2}>
            需求列表
          </Typography.Title>
          <Typography.Paragraph>全部需求；点击行进入详情，行内可调整优先级与期望发布时间</Typography.Paragraph>
        </div>
        <Button type="primary" icon={<IconPlus />} onClick={() => setCreating(true)}>
          新建需求
        </Button>
      </header>
      <RequirementCreateDrawer visible={creating} onClose={() => setCreating(false)} />
      <RequirementList />
    </section>
  );
}
