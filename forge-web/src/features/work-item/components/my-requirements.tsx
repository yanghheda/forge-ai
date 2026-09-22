import { Button, Typography } from "@arco-design/web-react";
import { IconPlus } from "@arco-design/web-react/icon";
import { useState } from "react";

import { RequirementCreateDrawer } from "./requirement-create-drawer";
import { RequirementList } from "./requirement-list";
import styles from "./my-requirements.module.css";

/* 「我的需求」保留列表与创建入口；全局视图改由全局仪表盘承担。 */
export function MyRequirements() {
  const [creating, setCreating] = useState(false);

  return (
    <section className={styles.page} aria-labelledby="requirement-page-title">
      <header className={styles.header}>
        <div>
          <Typography.Title id="requirement-page-title" heading={2}>
            我的需求
          </Typography.Title>
          <Typography.Paragraph>我创建、我负责或等待我评审的需求</Typography.Paragraph>
        </div>
        <Button type="primary" icon={<IconPlus />} onClick={() => setCreating(true)}>
          新建需求
        </Button>
      </header>
      <RequirementCreateDrawer visible={creating} onClose={() => setCreating(false)} />
      <RequirementList mine />
    </section>
  );
}
