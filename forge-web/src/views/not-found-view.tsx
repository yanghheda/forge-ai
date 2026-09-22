import { Result } from "@arco-design/web-react";
import { Link } from "react-router";

export function NotFoundView() {
  return <Result status="404" title="页面不存在" subTitle="你访问的页面已移除，或地址输入有误。" extra={<Link to="/overview">返回工作台</Link>} />;
}
