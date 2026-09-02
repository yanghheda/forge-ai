import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ApiError } from "@/lib/api";

import { ProjectOverviewView } from "./project-overview-view";

const routeProps = { workspace: "forge", project: "demo" };

describe("ProjectOverviewView", () => {
  it("显示加载态", () => {
    render(<ProjectOverviewView {...routeProps} loading />);
    expect(screen.getByText("正在连接 ForgeAI 服务…")).toBeInTheDocument();
  });

  it("显示空态", () => {
    render(<ProjectOverviewView {...routeProps} loading={false} data={null} />);
    expect(screen.getByText("当前还没有可展示的项目数据")).toBeInTheDocument();
  });

  it("错误态显示后端 requestId", () => {
    const error = new ApiError({
      code: "RESOURCE_NOT_FOUND",
      message: "missing",
      requestId: "req_01VIEW",
      status: 404,
    });
    render(<ProjectOverviewView {...routeProps} loading={false} error={error} />);
    expect(screen.getByText(/req_01VIEW/)).toBeInTheDocument();
  });

  it("成功态显示连接信息", () => {
    render(
      <ProjectOverviewView
        {...routeProps}
        loading={false}
        data={{ serviceName: "forge-server", serviceStatus: "UP" }}
      />,
    );
    expect(screen.getByText("forge-server：UP")).toBeInTheDocument();
  });
});
