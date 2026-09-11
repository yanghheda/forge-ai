import { ProtectedApp } from "@/features/auth";
import { DeliveryScreen } from "@/features/console";

export default async function UxPage({params}:{params:Promise<{id:string}>}) { return <ProtectedApp><DeliveryScreen kind="ux" requirementId={(await params).id} /></ProtectedApp>; }
