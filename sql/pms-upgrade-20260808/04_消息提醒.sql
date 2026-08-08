-- ============================================================
-- #4 消息提醒增强（钉钉待办 + 站内消息中心）— DDL（幂等）
--
-- 内容：
--   1. 新建 pms_dingtalk_todo 表（PMS 任务 ↔ 钉钉待办映射）
--   2. 新建 pms_message 表（站内消息中心）
--   3. 菜单 SQL：消息铃铛权限点 pms:message:list / pms:message:mark-read
--
-- 兼容性：MySQL 8.0 幂等可重复执行
-- ============================================================

-- ============================================================
-- 1. 钉钉待办映射表 pms_dingtalk_todo
-- ============================================================
CREATE TABLE IF NOT EXISTS `pms_dingtalk_todo` (
    `id`                 bigint(20)    NOT NULL COMMENT '主键ID（雪花）',
    `biz_task_id`        bigint(20)    DEFAULT NULL COMMENT '关联业务任务ID（pms_task.task_id）',
    `user_id`            bigint(20)    DEFAULT NULL COMMENT '接收人系统用户ID',
    `dingtalk_union_id`  varchar(64)   DEFAULT NULL COMMENT '接收人钉钉 unionId',
    `ding_todo_id`       varchar(64)   DEFAULT NULL COMMENT '钉钉待办ID（创建成功后返回）',
    `status`             varchar(16)   DEFAULT NULL COMMENT '待办状态：pending/completed/failed',
    `title`              varchar(255)  DEFAULT NULL COMMENT '待办标题',
    `content`            text          DEFAULT NULL COMMENT '待办内容',
    `todo_create_time`   datetime      DEFAULT NULL COMMENT '待办创建时间',
    `todo_complete_time` datetime      DEFAULT NULL COMMENT '待办完成时间',
    `fail_reason`        varchar(512)  DEFAULT NULL COMMENT '失败原因',
    -- TenantBaseDO 标准字段
    `creator`            varchar(64)   DEFAULT '' COMMENT '创建者',
    `create_time`        datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`            varchar(64)   DEFAULT '' COMMENT '更新者',
    `update_time`        datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            bit(1)        DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`          bigint(20)    DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    KEY `idx_biz_task_id` (`biz_task_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_biz_task_status` (`biz_task_id`, `status`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COMMENT = 'PMS 钉钉待办映射';

-- ============================================================
-- 2. 站内消息表 pms_message
-- ============================================================
CREATE TABLE IF NOT EXISTS `pms_message` (
    `message_id`      bigint(20)    NOT NULL COMMENT '消息主键ID（雪花）',
    `receiver_id`     bigint(20)    NOT NULL COMMENT '接收人系统用户ID',
    `title`           varchar(255)  DEFAULT NULL COMMENT '消息标题',
    `content`         text          DEFAULT NULL COMMENT '消息内容',
    `biz_type`        varchar(32)   DEFAULT NULL COMMENT '业务类型：task/project/change/quality',
    `biz_id`          bigint(20)    DEFAULT NULL COMMENT '业务ID（如 task_id），用于前端跳转',
    `trigger_event`  varchar(64)   DEFAULT NULL COMMENT '触发事件（与 pms_notify_rule.trigger_event 对齐）',
    `read_status`    tinyint(1)    DEFAULT 0 COMMENT '阅读状态：0 未读 / 1 已读',
    `read_time`      datetime      DEFAULT NULL COMMENT '阅读时间',
    -- TenantBaseDO 标准字段
    `creator`         varchar(64)   DEFAULT '' COMMENT '创建者',
    `create_time`     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         varchar(64)   DEFAULT '' COMMENT '更新者',
    `update_time`     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         bit(1)        DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`       bigint(20)    DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`message_id`),
    KEY `idx_receiver_read` (`receiver_id`, `read_status`),
    KEY `idx_receiver_create` (`receiver_id`, `create_time`),
    KEY `idx_biz` (`biz_type`, `biz_id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COMMENT = 'PMS 站内消息';

-- ============================================================
-- 3. 菜单/权限点：消息铃铛 + 消息中心
-- ============================================================

-- 3.1 「PMS 消息铃铛查询」按钮权限点（用于 ToolHeader 显示铃铛）
SET @perm_count := (
    SELECT COUNT(*) FROM system_menu
    WHERE permission = 'pms:message:list' AND deleted = 0
);
SET @sql := IF(@perm_count = 0,
    'INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, creator, create_time, updater, update_time, deleted) VALUES (''PMS 消息铃铛查询'', ''pms:message:list'', 3, 13, 0, ''/pms/message'', ''ep:bell'', ''views/pms/message/index'', 0, ''1'', NOW(), ''1'', NOW(), 0)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.2 「PMS 消息标记已读」权限点
SET @perm_count := (
    SELECT COUNT(*) FROM system_menu
    WHERE permission = 'pms:message:mark-read' AND deleted = 0
);
SET @sql := IF(@perm_count = 0,
    'INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, creator, create_time, updater, update_time, deleted) VALUES (''PMS 消息标记已读'', ''pms:message:mark-read'', 3, 14, 0, '''', '''', '''', 0, ''1'', NOW(), ''1'', NOW(), 0)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 4. 钉钉待办 API 所需权限点说明（README 同步说明，不在 SQL 中开通）
-- ============================================================
-- 钉钉待办 API（新版 /v1.0/todo/users/{unionId}/tasks）需要应用具备以下权限点：
--   1. 个人待办读写权限（"待办事项"应用授权）
--      - 调用 POST /v1.0/todo/users/{unionId}/tasks   创建待办
--      - 调用 PUT  /v1.0/todo/users/{unionId}/tasks/{taskId} 完成待办
--
-- 开通方式（管理员在钉钉开放平台操作）：
--   1. 进入 https://open.dingtalk.com → 应用开发 → 企业内部应用
--   2. 选中 PMS 集成所用的应用（appKey 在 pms_dingtalk_config 表）
--   3. 「权限管理」→ 申请「待办事项」相关权限点（个人待办读写）
--   4. 等 1-2 小时权限同步后生效
--
-- 配置项：
--   pms_dingtalk_config.app_key       — 应用 AppKey（已存在）
--   pms_dingtalk_config.app_secret    — 应用 AppSecret（已存在）
--   pms.dingtalk.new-api-base-url     — 默认 https://api.dingtalk.com
--                                        （PmsDingTalkProperties.newApiBaseUrl，已存在）
--
-- 验证方式：调用 POST https://api.dingtalk.com/v1.0/todo/users/{unionId}/tasks
--          header: x-acs-dingtalk-access-token: <access_token>
--          成功响应含 taskId 字段。未实际联调，需要真机验证。

-- 验证表创建成功
-- SHOW CREATE TABLE pms_dingtalk_todo;
-- SHOW CREATE TABLE pms_message;
-- SELECT * FROM system_menu WHERE permission LIKE 'pms:message:%';
