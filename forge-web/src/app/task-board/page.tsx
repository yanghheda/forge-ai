import { ProtectedApp } from "@/features/auth";
import { DeliveryScreen } from "@/features/console";

export default function TaskBoardPage() { return <ProtectedApp><DeliveryScreen kind="tasks" /></ProtectedApp>; }
