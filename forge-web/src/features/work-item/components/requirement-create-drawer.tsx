import { Button, Drawer, Form, Input, Message, Select } from "@arco-design/web-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";

import { formatRequestError } from "@/lib/api";
import { priorityLabel } from "@/lib/labels";
import { createOrganizationRequirement } from "../api/work-item-api";
import styles from "./requirement-create-drawer.module.css";

interface CreateRequirementValues {
  title: string;
  description: string;
  priority: string;
}

const emptyRequirement: CreateRequirementValues = {
  title: "",
  description: "",
  priority: "MEDIUM",
};

/* 新建需求抽屉同时服务于「我的需求」与全局仪表盘，避免两个入口维护两份表单。 */
export function RequirementCreateDrawer({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<CreateRequirementValues>();
  const title = Form.useWatch("title", form);

  function close() {
    form.resetFields();
    onClose();
  }

  const create = useMutation({
    mutationFn: (values: CreateRequirementValues) => createOrganizationRequirement(values),
    onSuccess: () => {
      Message.success("需求创建成功。");
      close();
      void queryClient.invalidateQueries({ queryKey: ["requirements"] });
    },
    onError: (error) => Message.error(formatRequestError(error)),
  });

  return (
    <Drawer
      className={styles.createDrawer}
      width={520}
      visible={visible}
      title="创建需求"
      footer={
        <div className={styles.drawerFooter}>
          <Button disabled={create.isPending} onClick={close}>
            取消
          </Button>
          <Button type="primary" disabled={!title?.trim()} loading={create.isPending} onClick={() => form.submit()}>
            创建需求
          </Button>
        </div>
      }
      maskClosable={!create.isPending}
      onCancel={close}
    >
      <p className={styles.drawerDescription}>记录问题背景与优先级，创建后可继续完善协作人员和交付材料。</p>
      <Form form={form} className={styles.drawerForm} layout="vertical" aria-label="新建需求表单" initialValues={emptyRequirement} requiredSymbol={{ position: "start" }} onSubmit={(values) => create.mutate(values)}>
        <Form.Item
          field="title"
          label="需求标题"
          required
          rules={[
            { required: true, message: "请输入需求标题" },
            { maxLength: 120, message: "需求标题不能超过 120 个字符" },
          ]}
          extra="建议使用清晰的目标或问题描述，便于团队快速识别。"
        >
          <Input aria-label="需求标题" size="large" placeholder="一句话说明要解决的问题" maxLength={120} showWordLimit />
        </Form.Item>
        <Form.Item field="description" label="需求描述" rules={[{ maxLength: 2000, message: "需求描述不能超过 2000 个字符" }]}>
          <Input.TextArea aria-label="需求描述" placeholder="补充业务背景、目标、用户价值或范围边界" maxLength={2000} showWordLimit rows={7} />
        </Form.Item>
        <Form.Item field="priority" label="优先级" required rules={[{ required: true, message: "请选择优先级" }]}>
          <Select
            aria-label="需求优先级"
            size="large"
            options={["LOW", "MEDIUM", "HIGH", "URGENT"].map((value) => ({
              value,
              label: `${priorityLabel(value)}优先级`,
            }))}
          />
        </Form.Item>
        <div className={styles.formHint}>
          <strong>创建后的初始状态</strong>
          <span>需求将以草稿状态保存，可在详情页继续补充材料并发起产品评审。</span>
        </div>
      </Form>
    </Drawer>
  );
}
