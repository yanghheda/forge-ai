import { cleanup, render, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { EChart } from "./echart";

/* jsdom 没有 canvas，这里用替身模块验证 React 封装对 echarts 生命周期的调用契约。 */
const echarts = vi.hoisted(() => {
  const chart = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() };
  return { chart, init: vi.fn(() => chart), use: vi.fn() };
});

vi.mock("echarts/core", () => ({ init: echarts.init, use: echarts.use }));
vi.mock("echarts/charts", () => ({ BarChart: {}, LineChart: {}, PieChart: {} }));
vi.mock("echarts/components", () => ({ GridComponent: {}, TooltipComponent: {}, LegendComponent: {} }));
vi.mock("echarts/renderers", () => ({ CanvasRenderer: {} }));

/* 记录组件创建的 ResizeObserver，便于在测试中手动触发尺寸回调。 */
const observers: { callback: ResizeObserverCallback; observe: ReturnType<typeof vi.fn>; disconnect: ReturnType<typeof vi.fn> }[] = [];

class ResizeObserverStub {
  callback: ResizeObserverCallback;
  observe = vi.fn();
  disconnect = vi.fn();

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
    observers.push(this);
  }
}

const option = { series: [{ type: "bar" as const, data: [1, 2] }] };

describe("EChart", () => {
  afterEach(cleanup);

  beforeEach(() => {
    observers.length = 0;
    echarts.chart.setOption.mockClear();
    echarts.chart.resize.mockClear();
    echarts.chart.dispose.mockClear();
    echarts.init.mockClear();
    vi.stubGlobal("ResizeObserver", ResizeObserverStub);
  });

  it("动态加载 echarts 后初始化实例并写入初始 option", async () => {
    const { container } = render(<EChart option={option} ariaLabel="需求状态分布" />);

    await waitFor(() => expect(echarts.init).toHaveBeenCalledWith(container.firstElementChild));
    expect(echarts.chart.setOption).toHaveBeenCalledWith(option);
    expect(echarts.use).toHaveBeenCalledTimes(1);
    expect(echarts.use.mock.calls[0][0]).toHaveLength(7);
  });

  it("option 变化时以 notMerge 重绘且不重复初始化", async () => {
    const next = { series: [{ type: "line" as const, data: [3] }] };
    const { rerender } = render(<EChart option={option} ariaLabel="新增 vs 完成" />);

    await waitFor(() => expect(echarts.init).toHaveBeenCalledTimes(1));
    rerender(<EChart option={next} ariaLabel="新增 vs 完成" />);

    expect(echarts.chart.setOption).toHaveBeenLastCalledWith(next, { notMerge: true });
    expect(echarts.init).toHaveBeenCalledTimes(1);
  });

  it("容器尺寸变化触发重绘", async () => {
    render(<EChart option={option} ariaLabel="存量燃尽" />);

    await waitFor(() => expect(observers).toHaveLength(1));
    observers[0].observe(observers[0] as unknown as Element);
    observers[0].callback([], observers[0] as unknown as ResizeObserver);

    expect(echarts.chart.resize).toHaveBeenCalledTimes(1);
  });

  it("卸载时断开尺寸监听并销毁实例", async () => {
    const { unmount } = render(<EChart option={option} ariaLabel="QA 通过率" />);

    await waitFor(() => expect(observers).toHaveLength(1));
    unmount();

    expect(observers[0].disconnect).toHaveBeenCalledTimes(1);
    expect(echarts.chart.dispose).toHaveBeenCalledTimes(1);
  });

  it("以图片语义与指定高度暴露容器", () => {
    render(<EChart option={option} height={320} ariaLabel="缺陷分布" />);

    const container = document.querySelector('[role="img"]');
    expect(container).toHaveAttribute("aria-label", "缺陷分布");
    expect(container).toHaveStyle({ height: "320px" });
  });
});
