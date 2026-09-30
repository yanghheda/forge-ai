import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";

import { notificationEventUrl } from "../api/console-api";

/*
 * 订阅当前用户的站内通知 SSE 事件流：收到新通知后去抖失效 ["notifications"] 相关查询，
 * 让未读角标与（抽屉打开时的）通知列表无需手动刷新即可更新。
 * 环境不支持 EventSource（jsdom / SSR）或未启用时安全退出；出错时静默重连，
 * 重连携带最新游标以避免重复推送，页面仍由 30 秒轮询兜底，不向上抛出错误。
 */
export function useNotificationStream(enabled: boolean) {
  const queryClient = useQueryClient();
  /* 本地游标：记录已收到通知的最大 id，重连时据此续传，避免重复收到已处理事件。 */
  const cursor = useRef(0);
  /* 是否停止订阅：重连回调与卸载清理都读取该标记，避免卸载后继续建连。 */
  const stopped = useRef(false);

  useEffect(() => {
    stopped.current = false;
    /* 事件源缺失或未启用时不做任何订阅，仅保留轮询兜底。 */
    if (!enabled || typeof EventSource === "undefined") {
      return;
    }
    let source: EventSource | undefined;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let debounce: ReturnType<typeof setTimeout> | undefined;

    const scheduleInvalidate = () => {
      if (debounce) {
        return;
      }
      debounce = setTimeout(() => {
        debounce = undefined;
        void queryClient.invalidateQueries({ queryKey: ["notifications"] });
      }, 300);
    };

    const connect = () => {
      source = new EventSource(notificationEventUrl(cursor.current), { withCredentials: true });
      source.addEventListener("notification", (event) => {
        /* 只在事件 id 为数字且大于当前游标时前推，规避乱序或重复帧。 */
        const next = Number((event as MessageEvent<string>).lastEventId);
        if (Number.isFinite(next) && next > cursor.current) {
          cursor.current = next;
        }
        scheduleInvalidate();
      });
      source.onerror = () => {
        source?.close();
        if (!stopped.current) {
          retry = setTimeout(connect, 500);
        }
      };
    };

    connect();
    return () => {
      stopped.current = true;
      source?.close();
      if (retry) {
        clearTimeout(retry);
      }
      if (debounce) {
        clearTimeout(debounce);
      }
    };
  }, [enabled, queryClient]);
}
