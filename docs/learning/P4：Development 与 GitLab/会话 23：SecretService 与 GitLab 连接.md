# 会话 23：SecretService 与 GitLab 连接

## 我完成了什么

- 新增 `secrets`、`gitlab_connections`、`git_repositories` 三张带完整中文元数据注释的事实表，并新增 `integration.manage`、`repo.read` 权限。
- 实现 AES-256-GCM `SecretService`：每次使用随机 IV，以 Workspace、Secret 类型和密钥版本作为 AAD，并用 HMAC 短指纹帮助管理员辨识轮换结果。
- 实现 GitLab Base URL SSRF 策略：默认只允许 HTTPS，拒绝回环、私网、link-local、云元数据和带凭据 URL；私有 GitLab 只能通过显式 CIDR 白名单放行。
- 实现禁止自动重定向、带连接/请求超时与响应体上限的 GitLab 只读 Adapter，支持连接身份测试和仓库读取，不包含 Branch/MR。
- 实现连接保存、列表、Token 乐观锁轮换、连接测试和单仓库绑定 API，以及不回显 Token 的 Workspace 设置 UI。

## 我理解的核心设计

- 密码只需验证“是否相同”，所以使用不可逆 Hash；GitLab Token 必须被服务端取回后调用远端，因此必须可解密，并用有认证能力的 AES-GCM 检测篡改。
- 数据库只有密文、IV、密钥版本和不可逆指纹。主密钥只由部署环境注入；数据库泄露本身不足以恢复 Token。
- GCM AAD 把密文绑定到 Workspace、用途和密钥版本，攻击者把某工作区密文替换到另一工作区时会认证失败。
- SSRF 不只是校验 URL 字符串，还必须解析 DNS 并检查每个结果地址。`169.254.169.254` 等云元数据地址能泄露实例身份凭据，因此即使配置私网白名单也永久拒绝 link-local 元数据范围。
- GitLab 是远端仓库事实来源；ForgeAI 只保存连接和标准化仓库快照。应用服务依赖只读 SPI，不让 GitLab 特有字段进入核心工作流。

## 调用链

保存：浏览器设置表单 → Session/CSRF → `GitLabConnectionController` → `integration.manage` → URL 策略 → `SecretService.encrypt` → MyBatis 短事务写 Secret 与 Connection → 安全 DTO。

测试：Controller → 权限 → 按 Workspace 读取 Connection/Secret → 临时解密 → URL 再校验 → GitLab `/api/v4/user` → 独立短事务记录 ACTIVE/ERROR → 标准身份 DTO。

绑定：Controller → `project.manage` 与 `repo.read` → 同 Workspace Connection/Secret → 无事务读取 GitLab Repository → 短事务保存标准化 Repository 快照。

## 数据与事务边界

- MySQL 是连接配置、密文、连接状态和仓库绑定的事实来源；前端缓存不是授权或配置事实。
- 创建连接时 Secret 与 Connection 同一事务提交；轮换以 Connection `version` 乐观锁原子替换密文并回到 `UNVERIFIED`。
- DNS、GitLab HTTP 和 JSON 解析均不位于数据库事务或行锁中；远端完成后才开启短事务更新测试状态或仓库快照。
- Token 明文只存在于请求解析、SecretService 解密结果和 GitLab 请求头的短生命周期内，不进入 DTO、异常、日志、Agent 或前端状态回读。

## 失败路径

- 主密钥缺失、非 Base64 或不是 256 位：应用启动 fail fast，避免写入未来无法读取的密文。
- 密文、AAD 或认证标签被篡改：GCM 解密失败，不返回部分明文。
- GitLab 401/403/404/409/429、timeout、5xx 和非法响应映射为稳定错误码，远端响应正文不进入公开异常。
- 302 等重定向不跟随，避免安全主机把请求转发到云元数据或另一个 Host。
- 跨 Workspace 或缺少项目范围：在读取 Secret 前统一失败；Mapper 查询仍显式携带 `workspace_id`。

## 测试证据

- `SecretServiceTest`：随机 IV、往返、篡改、跨 Workspace AAD、主密钥 fail fast 通过。
- `GitLabUrlPolicyTest`：公网 HTTPS、私网/回环/元数据拒绝、显式 CIDR、开发 localhost HTTP 通过。
- `GitLabHttpClientTest`：身份/仓库读取、Token 请求头、401、403、timeout、redirect 通过。
- `GitLabConnectionServiceTest`：Token 轮换、后续调用使用新 Token、DTO 不泄露、越权先于 Store 读取通过。
- forge-web GitLab API 测试、TypeScript、ESLint 与模块边界通过。

## 风险与遗留

- 私有 GitLab 自签名证书的自定义 TrustStore 尚未提供管理界面；应通过部署层 JVM TrustStore 配置解决，不能关闭 TLS 校验。
- 当前密钥版本配置只读取一个活动主密钥；真正跨版本主密钥轮换需增加旧版本 keyring 与后台重加密流程，不能只修改版本号。
- DNS 校验紧邻请求执行，但 JDK 客户端仍会自行解析；高安全部署可进一步使用出口代理/网络策略形成第二道防线，抵御极端 DNS rebinding 竞态。
- Branch/MR、Pipeline、Webhook 均属于后续会话，本轮未实现。

## 3 个复盘问题

1. 为什么用户密码适合 Hash，而 GitLab Token 必须使用带认证的可逆加密？
2. SSRF 如何借由 DNS、重定向或云元数据地址窃取部署身份，当前实现分别在哪一层阻断？
3. 为什么数据库备份无法替代主密钥备份，主密钥遗失后哪些数据仍可恢复、哪些永远无法恢复？
