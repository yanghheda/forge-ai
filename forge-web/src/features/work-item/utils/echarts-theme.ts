/* 图表配色统一从 globals.css 的 --forge-* token 读取，避免在 TS 里维护第二套硬编码色值。 */

export interface ChartPalette {
  /* 并列来源的固定序列色，顺序稳定以便同一指标在不同图表里保持同色。 */
  series: string[];
  /* 语义色，仅在数据本身表达状态或风险时使用。 */
  success: string;
  warning: string;
  error: string;
  /* 坐标轴主线与文字颜色。 */
  axis: string;
  /* 网格分隔线颜色。 */
  split: string;
  /* 标签与提示框的中性色。 */
  label: string;
  tooltipBackground: string;
  tooltipText: string;
}

/* 服务端渲染与 token 缺失时的兜底值，取自 globals.css 的浅色主题。 */
const fallback: ChartPalette = {
  series: ["#165dff", "#0fc6c2", "#722ed1", "#ff7d00", "#00b42a", "#f7ba1e"],
  success: "#00b42a",
  warning: "#ff7d00",
  error: "#f53f3f",
  axis: "#e5e6eb",
  split: "#f2f3f5",
  label: "#86909c",
  tooltipBackground: "#ffffff",
  tooltipText: "#1d2129",
};

function token(style: CSSStyleDeclaration | null, name: string, value: string) {
  return style?.getPropertyValue(name).trim() || value;
}

export function readChartPalette(): ChartPalette {
  const style = typeof window === "undefined" ? null : getComputedStyle(document.documentElement);
  return {
    series: [
      token(style, "--forge-primary-6", fallback.series[0]),
      token(style, "--forge-cyan", fallback.series[1]),
      token(style, "--forge-purple", fallback.series[2]),
      token(style, "--forge-warning", fallback.series[3]),
      token(style, "--forge-success", fallback.series[4]),
      token(style, "--forge-gold", fallback.series[5]),
    ],
    success: token(style, "--forge-success", fallback.success),
    warning: token(style, "--forge-warning", fallback.warning),
    error: token(style, "--forge-error", fallback.error),
    axis: token(style, "--forge-border-2", fallback.axis),
    split: token(style, "--forge-border-1", fallback.split),
    label: token(style, "--forge-text-3", fallback.label),
    tooltipBackground: token(style, "--forge-card", fallback.tooltipBackground),
    tooltipText: token(style, "--forge-text-1", fallback.tooltipText),
  };
}
