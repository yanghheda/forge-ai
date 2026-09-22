import { Card } from "@arco-design/web-react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, type ReactNode } from "react";
import { useNavigate } from "react-router";

import { isApiError } from "@/lib/api";
import { getCurrentUser } from "../api/auth-api";
import { AuthErrorAlert } from "./auth-error-alert";
import styles from "./protected-app.module.css";

export function ProtectedApp({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const currentUser = useQuery({
    queryKey: ["current-user"],
    queryFn: () => getCurrentUser(),
    retry: false,
  });

  useEffect(() => {
    if (currentUser.isError && isApiError(currentUser.error) && currentUser.error.status === 401) navigate("/login", { replace: true });
  }, [currentUser.error, currentUser.isError, navigate]);

  if (currentUser.isPending)
    return (
      <div className={styles.overlay} role="status" aria-live="polite">
        <div className={styles.card}>
          <span className={styles.mark}>
            <span className={styles.markGlyph}>F</span>
            <span className={styles.ring} aria-hidden="true" />
          </span>
          <p className={styles.label}>
            正在验证登录状态
            <span className={styles.dots} aria-hidden="true">
              <i />
              <i />
              <i />
            </span>
          </p>
          <span className={styles.progress} aria-hidden="true">
            <i />
          </span>
        </div>
      </div>
    );
  if (currentUser.isError)
    return (
      <Card title="无法进入工作台">
        <AuthErrorAlert error={currentUser.error} />
      </Card>
    );

  return children;
}
