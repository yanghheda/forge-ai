import "@arco-design/web-react/dist/css/arco.css";
import "./globals.css";

import type { Metadata } from "next";
import type { ReactNode } from "react";

import { AppShell } from "@/components/app-shell/app-shell";

import { AppProviders } from "./providers";

export const metadata: Metadata = {
  title: "ForgeAI",
  description: "AI-Native Software Delivery Workbench",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>
        <AppProviders>
          <AppShell>{children}</AppShell>
        </AppProviders>
      </body>
    </html>
  );
}
