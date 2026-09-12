"use client";

import { Alert, Button, Card, Form, Input } from "@arco-design/web-react";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

import { formatRequestError } from "@/lib/api";

import { saveRequirementDetails, type RequirementDetails } from "../api/work-item-api";
import styles from "./requirement-materials.module.css";

function MaterialField({ label, hint, placeholder, minRows, maxRows, value, onChange }: { label: string; hint?: string; placeholder?: string; minRows: number; maxRows: number; value: string; onChange: (value: string) => void }) {
  return (
    <Form.Item
      className={styles.field}
      label={
        <span className={styles.fieldLabel}>
          {label}
          {hint && <span className={styles.fieldHint}>{hint}</span>}
        </span>
      }
    >
      <Input.TextArea aria-label={label} value={value} onChange={onChange} placeholder={placeholder} autoSize={{ minRows, maxRows }} />
    </Form.Item>
  );
}

export function RequirementMaterials({ workItemId, details, onChanged }: { workItemId: number; details: RequirementDetails; onChanged: () => Promise<unknown> }) {
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({
    goal: details.goal,
    inScope: details.inScope,
    outOfScope: details.outOfScope,
    acceptanceCriteria: details.acceptanceCriteria.join("\n"),
    businessValue: details.businessValue,
  });
  const save = useMutation({
    mutationFn: () =>
      saveRequirementDetails(workItemId, {
        ...form,
        acceptanceCriteria: form.acceptanceCriteria.split("\n").filter(Boolean),
        version: details.version,
      }),
    onSuccess: () => {
      setEditing(false);
      void onChanged();
    },
  });

  if (!editing) {
    return (
      <Card title="需求材料" extra={<Button onClick={() => setEditing(true)}>编辑需求</Button>}>
        <dl className={styles.materials}>
          <div>
            <dt>业务目标</dt>
            <dd>{details.goal || <span className={styles.empty}>未填写</span>}</dd>
          </div>
          <div>
            <dt>纳入范围</dt>
            <dd>{details.inScope || <span className={styles.empty}>未填写</span>}</dd>
          </div>
          <div>
            <dt>排除范围</dt>
            <dd>{details.outOfScope || <span className={styles.empty}>未填写</span>}</dd>
          </div>
          <div>
            <dt>验收标准</dt>
            <dd>{details.acceptanceCriteria.length > 0 ? details.acceptanceCriteria.map((item) => <div key={item}>• {item}</div>) : <span className={styles.empty}>未填写</span>}</dd>
          </div>
          <div>
            <dt>业务价值</dt>
            <dd>{details.businessValue || <span className={styles.empty}>未填写</span>}</dd>
          </div>
        </dl>
      </Card>
    );
  }

  return (
    <Card title="需求材料">
      <Form className={styles.form} layout="vertical" onSubmit={() => save.mutate()}>
        <MaterialField label="业务目标" hint="需求要达成的业务成果" placeholder="例：让新用户 3 分钟内完成首次需求提交" minRows={2} maxRows={4} value={form.goal} onChange={(value) => setForm({ ...form, goal: value })} />
        <div className={styles.fieldRow}>
          <MaterialField label="纳入范围" hint="本次交付包含的内容" placeholder="本次要交付的功能、页面与场景" minRows={3} maxRows={6} value={form.inScope} onChange={(value) => setForm({ ...form, inScope: value })} />
          <MaterialField label="排除范围" hint="明确不做的内容" placeholder="不做、留给后续迭代的部分" minRows={3} maxRows={6} value={form.outOfScope} onChange={(value) => setForm({ ...form, outOfScope: value })} />
        </div>
        <MaterialField label="验收标准" hint="每行一条" placeholder="例：需求材料保存后版本号自增" minRows={4} maxRows={8} value={form.acceptanceCriteria} onChange={(value) => setForm({ ...form, acceptanceCriteria: value })} />
        <MaterialField label="业务价值" hint="对业务的收益" placeholder="例：减少需求澄清往返，降低返工成本" minRows={2} maxRows={4} value={form.businessValue} onChange={(value) => setForm({ ...form, businessValue: value })} />
        <div className={styles.formFooter}>
          <Button disabled={save.isPending} onClick={() => setEditing(false)}>
            取消
          </Button>
          <Button htmlType="submit" type="primary" loading={save.isPending}>
            保存材料
          </Button>
        </div>
      </Form>
      {save.isError && <Alert className={styles.errorAlert} type="error" content={formatRequestError(save.error)} />}
    </Card>
  );
}
