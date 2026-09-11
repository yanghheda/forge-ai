import { NextResponse } from "next/server";

export function GET() {
  return NextResponse.json({ application: "forge-web", status: "UP" });
}
