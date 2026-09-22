import { ProtectedApp } from "@/features/auth";
import { DeliveryScreen } from "@/features/console";

export function TaskBoardView() {
  return (
    <ProtectedApp>
      <DeliveryScreen kind="tasks" />
    </ProtectedApp>
  );
}
