import { apiClient } from "@/lib/api";
import type { ProseMirrorDocument } from "../editor/local-draft";
interface RequestClient {
  request<T>(path: string, init?: RequestInit): Promise<T>;
}
export interface Document {
  id: number;
  organizationId: number;
  workItemId: number;
  type: "PRD" | "UX_SPEC" | "PROTOTYPE_SPEC" | "DESIGN_GUIDE";
  title: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  currentVersionId: number | null;
  version: number;
}
export interface DocumentVersion {
  id: number;
  documentId: number;
  versionNo: number;
  content: ProseMirrorDocument;
  plainText: string;
  contentHash: string;
  createdAt: string;
}
const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});
export const createDocument = (
  input: {
    organizationId: number;
    workItemId: number;
    type: Document["type"];
    title: string;
  },
  client: RequestClient = apiClient,
) => client.request<Document>("/v1/documents", json("POST", input));
export const listWorkItemDocuments = (
  organizationId: number,
  workItemId: number,
  client: RequestClient = apiClient,
) =>
  client.request<Document[]>(
    `/v1/documents?organizationId=${organizationId}&workItemId=${workItemId}`,
  );
export const getDocument = (
  organizationId: number,
  id: number,
  client: RequestClient = apiClient,
) =>
  client.request<Document>(
    `/v1/documents/${id}?organizationId=${organizationId}`,
  );
export const saveDocumentVersion = (
  organizationId: number,
  id: number,
  expectedVersion: number,
  content: ProseMirrorDocument,
  client: RequestClient = apiClient,
) =>
  client.request<Document>(
    `/v1/documents/${id}/versions?organizationId=${organizationId}`,
    json("POST", { expectedVersion, content }),
  );
export const publishDocumentVersion = (
  organizationId: number,
  id: number,
  versionId: number,
  expectedVersion: number,
  client: RequestClient = apiClient,
) =>
  client.request<Document>(
    `/v1/documents/${id}/publish?organizationId=${organizationId}`,
    json("POST", { versionId, expectedVersion }),
  );
export const listDocumentVersions = (
  organizationId: number,
  id: number,
  client: RequestClient = apiClient,
) =>
  client.request<DocumentVersion[]>(
    `/v1/documents/${id}/versions?organizationId=${organizationId}`,
  );
