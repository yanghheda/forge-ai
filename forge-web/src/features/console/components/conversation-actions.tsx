import { Button, Dropdown, Input, Menu, Modal } from "@arco-design/web-react";
import { IconDelete, IconEdit, IconMore } from "@arco-design/web-react/icon";
import { useState } from "react";
import styles from "./console.module.css";

export function ConversationActions({ title, onRename, onDelete }: { title: string; onRename: (title: string) => Promise<unknown>; onDelete: () => Promise<unknown> }) {
  const [action, setAction] = useState<"rename" | "delete">();
  const [draft, setDraft] = useState(title);
  const [pending, setPending] = useState(false);
  const [menuVisible, setMenuVisible] = useState(false);
  async function submit() {
    setPending(true);
    try {
      if (action === "rename") await onRename(draft.trim());
      else await onDelete();
      setAction(undefined);
    } catch {
      // Mutation 统一展示服务端错误，保留弹窗以便重试。
    } finally {
      setPending(false);
    }
  }
  return (
    <>
      <Dropdown
        trigger="click"
        popupVisible={menuVisible}
        onVisibleChange={setMenuVisible}
        droplist={
          <Menu
            onClickMenuItem={(key) => {
              setMenuVisible(false);
              setDraft(title);
              setAction(key as "rename" | "delete");
            }}
          >
            <Menu.Item key="rename">
              <IconEdit /> 重命名
            </Menu.Item>
            <Menu.Item key="delete">
              <IconDelete /> 删除
            </Menu.Item>
          </Menu>
        }
      >
        <Button aria-label={`更多操作：${title}`} aria-haspopup="menu" aria-expanded={menuVisible} className={styles.moreSession} type="text" shape="circle" icon={<IconMore />} />
      </Dropdown>
      <Modal
        title={action === "rename" ? "重命名会话" : `确定删除“${title}”吗？`}
        visible={action !== undefined}
        okText={action === "rename" ? "保存" : "删除"}
        cancelText="取消"
        confirmLoading={pending}
        okButtonProps={{ disabled: action === "rename" && !draft.trim(), status: action === "delete" ? "danger" : undefined }}
        cancelButtonProps={{ disabled: pending }}
        closable={!pending}
        maskClosable={!pending}
        escToExit={!pending}
        onCancel={() => setAction(undefined)}
        onOk={submit}
      >
        {action === "rename" ? <Input aria-label="会话标题" value={draft} maxLength={255} onChange={setDraft} disabled={pending} /> : "会话消息将被删除，此操作无法撤销。"}
      </Modal>
    </>
  );
}
