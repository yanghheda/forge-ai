"use client";

import { Button, Result } from "@arco-design/web-react";

export default function ProjectOverviewError({ reset }: { error: Error; reset: () => void }) {
  return (
    <Result
      status="error"
      title="项目页面发生错误"
      subTitle="页面边界已阻止错误影响工作台其他区域。"
      extra={<Button onClick={reset}>重试</Button>}
    />
  );
}
