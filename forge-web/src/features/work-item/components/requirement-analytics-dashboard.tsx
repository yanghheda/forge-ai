"use client";

import { Alert, Button, Card, Radio, Spin, Tabs, Tag } from "@arco-design/web-react";
import { IconPlus } from "@arco-design/web-react/icon";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useMemo, useState } from "react";

import { formatRequestError } from "@/lib/api";
import { priorityLabel } from "@/lib/labels";
import type { RequirementAnalytics } from "../api/work-item-api";
import { getRequirementAnalytics } from "../api/work-item-api";
import type { ChartPalette } from "../utils/echarts-theme";
import { readChartPalette } from "../utils/echarts-theme";
import { bugRows, bugStatusLabel, bugStatusOrder, formatCycle, formatDay, formatDelta, formatPercent, funnelRows, priorityRows, severityLabel } from "../utils/requirement-analytics";
import { formatDate, statusLabel } from "../utils/requirement-detail-display";
import type { ChartOption } from "./echart";
import { EChart } from "./echart";
import { RequirementCreateDrawer } from "./requirement-create-drawer";
import styles from "./requirement-analytics-dashboard.module.css";

/* 仪表盘统计窗口选项；与后端 MAX_RANGE_DAYS 保持在同一量级。 */
const ranges = [7, 30, 90];

function tooltipStyle(palette: ChartPalette) {
  return { backgroundColor: palette.tooltipBackground, borderColor: palette.axis, textStyle: { color: palette.tooltipText, fontSize: 12 } };
}

function statusOption(data: RequirementAnalytics, palette: ChartPalette): ChartOption {
  /* 倒序后 ECharts 的类目轴自下而上渲染，草稿位于顶部。 */
  const rows = funnelRows(data.statusFunnel).reverse();
  return {
    grid: { left: 4, right: 28, top: 4, bottom: 4, outerBoundsMode: "same", outerBoundsContain: "axisLabel" },
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, ...tooltipStyle(palette) },
    xAxis: {
      type: "value",
      minInterval: 1,
      axisLabel: { color: palette.label, fontSize: 11 },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: palette.split } },
    },
    yAxis: {
      type: "category",
      data: rows.map((row) => row.label),
      axisLabel: { color: palette.label, fontSize: 11 },
      axisTick: { show: false },
      axisLine: { lineStyle: { color: palette.axis } },
    },
    series: [{ type: "bar", data: rows.map((row) => row.total), barMaxWidth: 12, itemStyle: { color: palette.series[0], borderRadius: [0, 2, 2, 0] } }],
  };
}

function priorityOption(data: RequirementAnalytics, palette: ChartPalette): ChartOption {
  return {
    color: palette.series,
    tooltip: { trigger: "item", ...tooltipStyle(palette) },
    legend: { bottom: 0, icon: "circle", itemWidth: 8, itemHeight: 8, textStyle: { color: palette.label, fontSize: 12 } },
    series: [
      {
        type: "pie",
        radius: ["50%", "72%"],
        center: ["50%", "44%"],
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderColor: palette.tooltipBackground, borderWidth: 2 },
        data: priorityRows(data.priorityDistribution).map((row) => ({ name: row.label, value: row.total })),
      },
    ],
  };
}

function flowOption(data: RequirementAnalytics, palette: ChartPalette): ChartOption {
  return {
    color: [palette.series[0], palette.series[4]],
    tooltip: { trigger: "axis", ...tooltipStyle(palette) },
    legend: { top: 0, right: 0, icon: "roundRect", itemWidth: 8, itemHeight: 8, textStyle: { color: palette.label, fontSize: 12 } },
    grid: { left: 4, right: 8, top: 32, bottom: 4, outerBoundsMode: "same", outerBoundsContain: "axisLabel" },
    xAxis: {
      type: "category",
      boundaryGap: false,
      data: data.flowTrend.map((point) => formatDay(point.date)),
      axisLabel: { color: palette.label, fontSize: 11 },
      axisTick: { show: false },
      axisLine: { lineStyle: { color: palette.axis } },
    },
    yAxis: { type: "value", minInterval: 1, axisLabel: { color: palette.label, fontSize: 11 }, splitLine: { lineStyle: { color: palette.split } } },
    series: [
      { name: "新增", type: "line", smooth: true, showSymbol: false, data: data.flowTrend.map((point) => point.created) },
      { name: "完成", type: "line", smooth: true, showSymbol: false, data: data.flowTrend.map((point) => point.completed) },
    ],
  };
}

function backlogOption(data: RequirementAnalytics, palette: ChartPalette): ChartOption {
  return {
    tooltip: { trigger: "axis", ...tooltipStyle(palette) },
    grid: { left: 4, right: 8, top: 16, bottom: 4, outerBoundsMode: "same", outerBoundsContain: "axisLabel" },
    xAxis: {
      type: "category",
      boundaryGap: false,
      data: data.backlogTrend.map((point) => formatDay(point.date)),
      axisLabel: { color: palette.label, fontSize: 11 },
      axisTick: { show: false },
      axisLine: { lineStyle: { color: palette.axis } },
    },
    yAxis: { type: "value", minInterval: 1, axisLabel: { color: palette.label, fontSize: 11 }, splitLine: { lineStyle: { color: palette.split } } },
    series: [
      {
        name: "未完成存量",
        type: "line",
        smooth: true,
        showSymbol: false,
        data: data.backlogTrend.map((point) => point.value),
        lineStyle: { color: palette.series[2] },
        itemStyle: { color: palette.series[2] },
        areaStyle: { color: palette.series[2], opacity: 0.12 },
      },
    ],
  };
}

function bugOption(data: RequirementAnalytics, palette: ChartPalette): ChartOption {
  const rows = bugRows(data.bugDistribution);
  const statuses = bugStatusOrder.filter((status) => rows.some((row) => row.status === status));
  const severities = [...new Set(rows.map((row) => row.severity))];
  return {
    color: palette.series,
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, ...tooltipStyle(palette) },
    legend: { top: 0, right: 0, icon: "roundRect", itemWidth: 8, itemHeight: 8, textStyle: { color: palette.label, fontSize: 12 } },
    grid: { left: 4, right: 8, top: 32, bottom: 4, outerBoundsMode: "same", outerBoundsContain: "axisLabel" },
    xAxis: {
      type: "category",
      data: statuses.map((status) => bugStatusLabel[status] ?? status),
      axisLabel: { color: palette.label, fontSize: 11 },
      axisTick: { show: false },
      axisLine: { lineStyle: { color: palette.axis } },
    },
    yAxis: { type: "value", minInterval: 1, axisLabel: { color: palette.label, fontSize: 11 }, splitLine: { lineStyle: { color: palette.split } } },
    series: severities.map((severity) => ({
      name: severityLabel[severity] ?? severity,
      type: "bar",
      stack: "bugs",
      barMaxWidth: 32,
      data: statuses.map((status) => rows.find((row) => row.status === status && row.severity === severity)?.total ?? 0),
    })),
  };
}

function qaOption(data: RequirementAnalytics, palette: ChartPalette): ChartOption {
  const quality = data.qaQuality;
  return {
    color: [palette.success, palette.error, palette.warning],
    tooltip: { trigger: "item", ...tooltipStyle(palette) },
    legend: { bottom: 0, icon: "circle", itemWidth: 8, itemHeight: 8, textStyle: { color: palette.label, fontSize: 12 } },
    series: [
      {
        type: "pie",
        radius: ["52%", "74%"],
        center: ["50%", "44%"],
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderColor: palette.tooltipBackground, borderWidth: 2 },
        data: [
          { name: "通过", value: quality.passed },
          { name: "失败", value: quality.failed },
          { name: "阻塞", value: quality.blocked },
        ],
      },
    ],
  };
}

/* 全局仪表盘：只读聚合视图，回答需求分布、交付吞吐与质量风险三个问题。 */
export function RequirementAnalyticsDashboard() {
  const [range, setRange] = useState(30);
  const [creating, setCreating] = useState(false);
  const analytics = useQuery({
    queryKey: ["requirements", "analytics", range],
    queryFn: () => getRequirementAnalytics(range),
  });
  const palette = useMemo(() => readChartPalette(), []);
  const data = analytics.data;
  const options = useMemo(
    () =>
      data
        ? {
            status: statusOption(data, palette),
            priority: priorityOption(data, palette),
            flow: flowOption(data, palette),
            backlog: backlogOption(data, palette),
            bug: bugOption(data, palette),
            qa: qaOption(data, palette),
          }
        : null,
    [data, palette],
  );
  const metrics = data?.metrics;
  const quality = data?.qaQuality;
  const hasQaSample = quality ? quality.passed + quality.failed + quality.blocked > 0 : false;
  const risks = data?.risks ?? [];

  return (
    <section className={styles.page} aria-labelledby="requirement-page-title">
      <header className={styles.header}>
        <div>
          <h2 id="requirement-page-title">全局仪表盘</h2>
          <p>需求分布、交付吞吐与质量风险的全局只读视图</p>
        </div>
        <div className={styles.actions}>
          <Radio.Group type="button" value={range} onChange={(value) => setRange(Number(value))} options={ranges.map((value) => ({ label: `${value} 天`, value }))} />
          <Button type="primary" icon={<IconPlus />} onClick={() => setCreating(true)}>
            新建需求
          </Button>
        </div>
      </header>

      <RequirementCreateDrawer visible={creating} onClose={() => setCreating(false)} />

      {analytics.isPending && (
        <div className={styles.loading}>
          <Spin tip="正在加载仪表盘…" />
        </div>
      )}
      {analytics.isError && <Alert type="error" content={formatRequestError(analytics.error)} />}

      {/* 核心指标常驻在滑块上方，打开页面即可看到总量、进行中与超期，无需先切分区。 */}
      {data && (
        <div className={styles.metrics}>
          <Metric title="需求总量" value={metrics?.total} note={`窗口内新增 ${metrics?.createdInRange ?? 0} · ${formatDelta(metrics?.createdDeltaRate ?? null)}`} tone="blue" />
          <Metric title="进行中" value={metrics?.inProgress} note="未进入终态" tone="cyan" />
          <Metric title="已完成" value={metrics?.completed} note={`窗口内完成 ${metrics?.completedInRange ?? 0} · ${formatDelta(metrics?.completedDeltaRate ?? null)}`} tone="green" />
          <Metric title="超期未完成" value={metrics?.overdue} note="超过期望完成时间" tone={(metrics?.overdue ?? 0) > 0 ? "red" : "gray"} />
          <Metric title="按时交付率" value={formatPercent(metrics?.onTimeRate ?? null)} note="窗口内完成且设置期望时间" tone="purple" />
          <Metric title="平均交付周期" value={formatCycle(metrics?.avgCycleDays ?? null)} note="按状态流转估算" tone="orange" />
        </div>
      )}

      {data && options && (
        /* 三个分区按滑块切换，避免所有图表纵向平铺后需要长距离滚动。 */
        <Tabs className={styles.tabs} type="capsule" destroyOnHide defaultActiveTab="distribution">
          <Tabs.TabPane key="distribution" title="需求分布">
            <div className={styles.grid}>
              <ChartCard className={styles.wide} title="状态分布" note={`共 ${metrics?.total ?? 0} 条需求，按生命周期顺序统计当前存量`} option={options.status} ariaLabel="需求状态分布" height={300} />
              <ChartCard title="优先级分布" option={hasPriorityBuckets(data) ? options.priority : null} ariaLabel="需求优先级分布" height={300} empty="当前没有需求。" />
            </div>
          </Tabs.TabPane>

          <Tabs.TabPane key="trend" title="交付趋势">
            <div className={styles.grid}>
              <ChartCard className={styles.wide} title="新增 vs 完成" option={options.flow} ariaLabel="每日新增与完成趋势" height={300} />
              <ChartCard title="存量燃尽" note="窗口内每日日终未完成存量" option={options.backlog} ariaLabel="未完成存量趋势" height={300} />
            </div>
          </Tabs.TabPane>

          <Tabs.TabPane key="quality" title="质量与风险">
            <div className={styles.grid}>
              <ChartCard className={styles.wide} title="缺陷分布" note="当前未删除缺陷按严重级别与状态" option={data.bugDistribution.length > 0 ? options.bug : null} ariaLabel="缺陷严重级别与状态分布" height={280} empty="当前没有缺陷记录。" />

              <Card className={styles.chartCard} title="QA 通过率">
                <p className={styles.readout}>
                  窗口内通过率 <strong>{formatPercent(quality?.passRate ?? null)}</strong>
                  <span>
                    通过 {quality?.passed ?? 0} / 失败 {quality?.failed ?? 0} / 阻塞 {quality?.blocked ?? 0}
                  </span>
                </p>
                {hasQaSample ? <EChart option={options.qa} ariaLabel="测试结果通过率" height={220} /> : <div className={styles.chartEmpty}>窗口内没有测试执行记录。</div>}
              </Card>

              <Card className={`${styles.riskCard} ${styles.full}`} title="风险预警">
                <p className={styles.cardNote}>超过期望完成时间或处于阻塞状态的需求</p>
                {risks.length === 0 ? (
                  <div className={styles.chartEmpty}>当前没有需要关注的风险需求。</div>
                ) : (
                  <div className={styles.riskList}>
                    {risks.map((item) => (
                      <Link key={item.id} href={`/requirements/${item.id}`} className={styles.riskRow}>
                        <div>
                          <small>{item.itemKey}</small>
                          <strong>{item.title}</strong>
                        </div>
                        <Tag color={item.blocked ? "red" : "orange"}>{item.blocked ? "阻塞" : "超期"}</Tag>
                        <Tag color={item.priority === "HIGH" || item.priority === "URGENT" ? "red" : "orange"}>{priorityLabel(item.priority)}</Tag>
                        <Tag color="arcoblue">{statusLabel[item.status] ?? item.status}</Tag>
                        <time>{formatDate(item.dueAt)}</time>
                      </Link>
                    ))}
                  </div>
                )}
              </Card>
            </div>
          </Tabs.TabPane>
        </Tabs>
      )}
    </section>
  );
}

function hasPriorityBuckets(data: RequirementAnalytics) {
  return data.priorityDistribution.some((bucket) => bucket.total > 0);
}

/* 指标卡的强调色，与六项核心指标各自的语义对应。 */
type MetricTone = "blue" | "cyan" | "green" | "red" | "purple" | "orange" | "gray";

function Metric({ title, value, note, tone = "blue" }: { title: string; value?: number | string; note: string; tone?: MetricTone }) {
  return (
    <article className={`${styles.metric} ${styles[tone]}`}>
      <span>{title}</span>
      <strong>{value ?? "—"}</strong>
      <small>{note}</small>
    </article>
  );
}

function ChartCard({ title, note, option, ariaLabel, height, empty, className }: { title: string; note?: string; option: ChartOption | null; ariaLabel: string; height: number; empty?: string; className?: string }) {
  return (
    <Card className={`${styles.chartCard} ${className ?? ""}`} title={title}>
      {note && <p className={styles.cardNote}>{note}</p>}
      {option ? <EChart option={option} ariaLabel={ariaLabel} height={height} /> : <div className={styles.chartEmpty}>{empty ?? "暂无数据。"}</div>}
    </Card>
  );
}
