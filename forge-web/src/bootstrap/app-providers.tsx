/*
 * 应用级 Provider 组合：集中挂载 Arco ConfigProvider 与 React Query QueryClientProvider。
 * QueryClient 使用惰性初始化以保证在应用生命周期内只创建一次；默认关闭重试并设置 30 秒新鲜时间。
 */
import "@arco-design/web-react/es/_util/react-19-adapter";
import { ConfigProvider } from "@arco-design/web-react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

export interface AppProvidersProps {
  children: ReactNode;
}

export function AppProviders({ children }: AppProvidersProps) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: false,
            staleTime: 30_000,
          },
        },
      }),
  );

  return (
    <ConfigProvider>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </ConfigProvider>
  );
}
