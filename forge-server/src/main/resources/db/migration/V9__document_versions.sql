CREATE TABLE documents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文档元数据稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区；每次读取和写入必须显式限制',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '所属项目；用于项目级授权与范围隔离',
    work_item_id BIGINT UNSIGNED NULL COMMENT '可选关联工作项；本会话允许为空',
    type VARCHAR(40) NOT NULL COMMENT '文档业务类型；本轮支持 PRD',
    title VARCHAR(255) NOT NULL COMMENT '文档显示标题；不包含版本号',
    status VARCHAR(24) NOT NULL COMMENT '文档生命周期状态：DRAFT、PUBLISHED 或 ARCHIVED',
    visibility VARCHAR(24) NOT NULL COMMENT '文档可见性；本轮固定为项目范围 PROJECT',
    current_version_id BIGINT UNSIGNED NULL COMMENT '当前读取和发布引用的不可变版本；草稿可为空',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建文档元数据的用户标识',
    created_at DATETIME(6) NOT NULL COMMENT '文档元数据创建 UTC 时间',
    updated_at DATETIME(6) NOT NULL COMMENT '文档元数据或当前版本最近更新 UTC 时间',
    deleted_at DATETIME(6) NULL COMMENT '逻辑删除 UTC 时间；非空文档对普通查询不可见',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '文档元数据和保存操作使用的乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_documents_project_type_status (project_id, type, status),
    CONSTRAINT fk_documents_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_documents_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_documents_work_item FOREIGN KEY (work_item_id) REFERENCES work_items (id),
    CONSTRAINT fk_documents_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文档元数据与当前版本指针；正文仅存于不可变版本表';

CREATE TABLE document_versions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '不可变文档版本稳定标识',
    workspace_id BIGINT UNSIGNED NOT NULL COMMENT '所属工作区；与文档元数据必须一致',
    document_id BIGINT UNSIGNED NOT NULL COMMENT '所属文档元数据标识',
    version_no BIGINT UNSIGNED NOT NULL COMMENT '文档内单调递增版本号；从 1 开始',
    content_format VARCHAR(32) NOT NULL COMMENT '正文格式；本轮固定为 PROSEMIRROR_JSON',
    content JSON NOT NULL COMMENT 'Tiptap ProseMirror JSON 正文；版本创建后禁止修改',
    content_hash CHAR(64) NOT NULL COMMENT '规范化纯文本的 SHA-256，用于内容标识与检索去重',
    plain_text LONGTEXT NOT NULL COMMENT '由服务端规范化生成的搜索与摘要文本，不信任客户端提供',
    summary TEXT NULL COMMENT '可选摘要；本轮创建时为空',
    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建本版本的用户标识',
    created_at DATETIME(6) NOT NULL COMMENT '版本创建 UTC 时间；版本创建后不可更改',
    PRIMARY KEY (id),
    UNIQUE KEY uq_document_versions_number (document_id, version_no),
    KEY idx_document_versions_history (workspace_id, document_id, version_no DESC),
    CONSTRAINT fk_document_versions_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_document_versions_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_document_versions_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文档正文的追加写不可变版本；历史记录不允许普通更新或删除';

ALTER TABLE documents ADD CONSTRAINT fk_documents_current_version FOREIGN KEY (current_version_id) REFERENCES document_versions (id);

CREATE TABLE outbox_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Outbox 追加事件稳定标识',
    aggregate_type VARCHAR(64) NOT NULL COMMENT '产生事件的聚合类型；本轮为 DOCUMENT',
    aggregate_id BIGINT UNSIGNED NOT NULL COMMENT '产生事件的文档聚合标识',
    event_type VARCHAR(64) NOT NULL COMMENT '稳定事件类型；本轮为 DOCUMENT_VERSION_PUBLISHED',
    payload JSON NOT NULL COMMENT '下游索引所需的冻结版本引用与范围数据',
    created_at DATETIME(6) NOT NULL COMMENT '业务事务内写入的 UTC 时间',
    processed_at DATETIME(6) NULL COMMENT '异步消费者完成处理的 UTC 时间；本轮不消费',
    PRIMARY KEY (id),
    KEY idx_outbox_events_pending (processed_at, id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '业务事务与异步索引之间的可靠事件交接；本轮只生产不消费';

INSERT INTO permissions (code, resource, action, description) VALUES
    ('document.read', 'document', 'read', '读取项目范围内的文档与历史版本'),
    ('document.create', 'document', 'create', '在项目范围内创建文档'),
    ('document.edit', 'document', 'edit', '保存新的不可变文档版本'),
    ('document.publish', 'document', 'publish', '发布指定文档版本')
ON DUPLICATE KEY UPDATE resource = VALUES(resource), action = VALUES(action), description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code LIKE 'document.%' WHERE r.code IN ('OWNER', 'ADMIN', 'PRODUCT');
