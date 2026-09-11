"use client";

import { Alert, Button, Drawer, Input, Spin, Tag } from "@arco-design/web-react";
import { IconApps, IconPlus, IconSafe } from "@arco-design/web-react/icon";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ProtectedApp } from "@/features/auth";
import { createConnection, listConnections } from "@/features/gitlab";
import { formatRequestError } from "@/lib/api";
import styles from "../settings.module.css";

export default function IntegrationsPage() { return <ProtectedApp><Integrations /></ProtectedApp>; }
function Integrations(){
  const[open,setOpen]=useState(false);const[name,setName]=useState("");const[baseUrl,setBaseUrl]=useState("");const[token,setToken]=useState("");const client=useQueryClient();
  const connections=useQuery({queryKey:["gitlab-connections"],queryFn:()=>listConnections()});
  const create=useMutation({mutationFn:()=>createConnection({name,baseUrl,token}),onSuccess:async()=>{setOpen(false);setName("");setBaseUrl("");setToken("");await client.invalidateQueries({queryKey:["gitlab-connections"]});}});
  return <section className={styles.page}><header><div><h1>集成配置</h1><p>连接公司 GitLab 代码仓库与 CI Pipeline</p></div><Button type="primary" icon={<IconPlus/>} onClick={()=>setOpen(true)}>添加 GitLab</Button></header>{connections.isPending&&<Spin tip="正在加载 GitLab 连接…"/>}{connections.isError&&<Alert type="error" content={formatRequestError(connections.error)}/>}<div className={styles.integrationGrid}>{connections.data?.map(item=><article className={styles.integration} key={item.id}><div className={styles.integrationIcon}><IconApps/></div><div><h2>{item.name}</h2><p>{item.baseUrl}</p><Tag color={item.status==="ACTIVE"?"green":"orange"}>{item.status==="ACTIVE"?"● 已连接":"待验证"}</Tag></div><Button>配置</Button></article>)}</div><article className={styles.notice}><IconSafe/><div><b>凭据由 forge-server 安全托管</b><p>Agent 不持有 GitLab Token 或部署凭据，只能通过受控 Tool API 发起操作。</p></div></article><Drawer visible={open} width={520} title="添加 GitLab" onCancel={()=>setOpen(false)} footer={<><Button onClick={()=>setOpen(false)}>取消</Button><Button type="primary" loading={create.isPending} onClick={()=>create.mutate()}>添加集成</Button></>}><div className={styles.form} role="dialog" aria-label="添加 GitLab"><label>连接名称<Input value={name} onChange={setName} placeholder="公司 GitLab"/></label><label>服务地址<Input value={baseUrl} onChange={setBaseUrl} placeholder="https://gitlab.example.com"/></label><label>访问令牌<Input.Password value={token} onChange={setToken} placeholder="输入访问令牌"/></label>{create.isError&&<Alert type="error" content={formatRequestError(create.error)}/>}</div></Drawer></section>}
