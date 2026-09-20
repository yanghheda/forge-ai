"use client";

import { useEffect, useRef } from "react";

import type { BarSeriesOption, LineSeriesOption, PieSeriesOption } from "echarts/charts";
import type { GridComponentOption, LegendComponentOption, TooltipComponentOption } from "echarts/components";
import type { ComposeOption, ECharts } from "echarts/core";

/* 仪表盘只使用条形、折线与环形三类图表，类型按需组合以免打包整个 echarts。 */
export type ChartOption = ComposeOption<BarSeriesOption | LineSeriesOption | PieSeriesOption | GridComponentOption | LegendComponentOption | TooltipComponentOption>;

/* 图表依赖按需注册只执行一次，避免多个图表实例重复 use() 触发重复注册告警。 */
let loader: Promise<typeof import("echarts/core")> | null = null;

function loadEcharts() {
  loader ??= (async () => {
    const [core, charts, components, renderers] = await Promise.all([import("echarts/core"), import("echarts/charts"), import("echarts/components"), import("echarts/renderers")]);
    core.use([charts.BarChart, charts.LineChart, charts.PieChart, components.GridComponent, components.TooltipComponent, components.LegendComponent, renderers.CanvasRenderer]);
    return core;
  })();
  return loader;
}

/* ECharts 的 React 薄封装：动态加载保持首屏与 SSR 安全，容器尺寸变化由 ResizeObserver 驱动重绘。 */
export function EChart({ option, height = 240, ariaLabel }: { option: ChartOption; height?: number; ariaLabel: string }) {
  const container = useRef<HTMLDivElement>(null);
  const instance = useRef<ECharts | null>(null);
  const latestOption = useRef(option);

  useEffect(() => {
    const element = container.current;
    if (!element) return;
    let disposed = false;
    let observer: ResizeObserver | null = null;
    void loadEcharts().then((core) => {
      if (disposed) return;
      const chart = core.init(element);
      instance.current = chart;
      chart.setOption(latestOption.current);
      observer = new ResizeObserver(() => chart.resize());
      observer.observe(element);
    });
    return () => {
      disposed = true;
      observer?.disconnect();
      instance.current?.dispose();
      instance.current = null;
    };
  }, []);

  useEffect(() => {
    latestOption.current = option;
    instance.current?.setOption(option, { notMerge: true });
  }, [option]);

  return <div ref={container} role="img" aria-label={ariaLabel} style={{ width: "100%", height }} />;
}
