import { z } from "zod";

export const projectRouteSchema = z.object({
  workspace: z.string().trim().min(1).max(80),
  project: z.string().trim().min(1).max(80),
});

export type ProjectRoute = z.infer<typeof projectRouteSchema>;
