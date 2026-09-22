import { ProtectedApp } from "@/features/auth";
import { TaskBoard } from "@/features/console";

export function TaskBoardView() {
  return (
    <ProtectedApp>
      <TaskBoard />
    </ProtectedApp>
  );
}