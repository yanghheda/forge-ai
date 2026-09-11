import { ProtectedApp } from "@/features/auth";
import { AgentCommandScreen } from "@/features/console";

export default function AgentCommandPage() { return <ProtectedApp><AgentCommandScreen /></ProtectedApp>; }
