import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";

import { type NotificationItem, markNotificationRead } from "../api/console-api";
import { notificationLink, notificationTypeLabel } from "../model/notification-link";
import styles from "./notification-list.module.css";

export interface NotificationListProps {
  /* 待展示的通知列表，通常来自通知查询的最新一页数据 */
  items: NotificationItem[];
  /* 跳转或标记后关闭抽屉的回调，由 AppShell 控制抽屉显隐 */
  onClose: () => void;
}

export function NotificationList({ items, onClose }: NotificationListProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const open = async (item: NotificationItem) => {
    if (!item.readAt) {
      // 标记失败不阻断跳转；未读数刷新依赖随后的查询失效。
      await markNotificationRead(item.id).catch(() => undefined);
      await queryClient.invalidateQueries({ queryKey: ["notifications"] });
    }
    const link = notificationLink(item);
    if (link) {
      navigate(link);
    }
    onClose();
  };

  return (
    <div className={styles.list}>
      {items.map((item) => (
        <button key={item.id} type="button" className={item.readAt ? `${styles.item} ${styles.read}` : styles.item} onClick={() => void open(item)}>
          <span className={styles.type}>{notificationTypeLabel(item.type)}</span>
          <b className={styles.title}>{item.title}</b>
          <span className={styles.body}>{item.body}</span>
          <time className={styles.time}>{new Date(item.createdAt).toLocaleString("zh-CN")}</time>
        </button>
      ))}
    </div>
  );
}
