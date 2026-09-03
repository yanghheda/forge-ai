import { apiClient } from "@/lib/api";
import type { ProseMirrorDocument } from "../editor/local-draft";
interface RequestClient { request<T>(path: string, init?: RequestInit): Promise<T>; }
export interface Document { id:number; workspaceId:number; projectId:number; type:"PRD"; title:string; status:"DRAFT"|"PUBLISHED"|"ARCHIVED"; currentVersionId:number|null; version:number; }
export interface DocumentVersion { id:number; documentId:number; versionNo:number; content:ProseMirrorDocument; plainText:string; contentHash:string; createdAt:string; }
const json = (method:string, body:unknown):RequestInit => ({ method, headers:{"Content-Type":"application/json"}, body:JSON.stringify(body) });
export const createDocument=(input:{workspaceId:number;projectId:number;type:"PRD";title:string},client:RequestClient=apiClient)=>client.request<Document>("/v1/documents",json("POST",input));
export const getDocument=(workspaceId:number,projectId:number,id:number,client:RequestClient=apiClient)=>client.request<Document>(`/v1/documents/${id}?workspaceId=${workspaceId}&projectId=${projectId}`);
export const saveDocumentVersion=(workspaceId:number,projectId:number,id:number,expectedVersion:number,content:ProseMirrorDocument,client:RequestClient=apiClient)=>client.request<Document>(`/v1/documents/${id}/versions?workspaceId=${workspaceId}&projectId=${projectId}`,json("POST",{expectedVersion,content}));
export const publishDocumentVersion=(workspaceId:number,projectId:number,id:number,versionId:number,expectedVersion:number,client:RequestClient=apiClient)=>client.request<Document>(`/v1/documents/${id}/publish?workspaceId=${workspaceId}&projectId=${projectId}`,json("POST",{versionId,expectedVersion}));
export const listDocumentVersions=(workspaceId:number,projectId:number,id:number,client:RequestClient=apiClient)=>client.request<DocumentVersion[]>(`/v1/documents/${id}/versions?workspaceId=${workspaceId}&projectId=${projectId}`);
