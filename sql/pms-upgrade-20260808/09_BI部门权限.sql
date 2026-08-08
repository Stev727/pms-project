-- ============================================================
-- #9 BI 看板按部门数据权限 — DDL（幂等）
--
-- 说明：
--   pms_project.dept_id 字段已在原表中存在（PmsProjectDO.java 第 88 行左右），
--   无需 ALTER TABLE 新增字段。
--
--   本脚本仅追加以下内容：
--     1. pms_project.dept_id 字段索引（加速按部门过滤的项目查询）
--     2. pms_task.dept_id 字段索引（加速按部门过滤的任务查询）
--     3. 菜单 SQL：BI 看板菜单（如已存在则跳过）
--     4. 权限点：pms:dashboard:query / pms:dashboard:all
--
-- 兼容性：MySQL 8.0 幂等可重复执行（不支持 ADD COLUMN IF NOT EXISTS，
--        已用 information_schema + PREPARE 动态 SQL 替代）
-- ============================================================

-- 1. pms_project.dept_id 索引（BI 看板按部门过滤项目）
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pms_project'
      AND INDEX_NAME = 'idx_dept_id'
);
SET @sql := IF(@index_exists = 0,
    'CREATE INDEX idx_dept_id ON pms_project (dept_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. pms_task.dept_id 索引（BI 看板按部门过滤任务）
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pms_task'
      AND INDEX_NAME = 'idx_dept_id'
);
SET @sql := IF(@index_exists = 0,
    'CREATE INDEX idx_dept_id ON pms_task (dept_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 菜单 SQL：BI 看板菜单（如已存在则跳过）
-- 注意：BI 看板菜单大概率在原系统已存在（路径 /pms/dashboard），这里只兜底补权限点。
-- 这里假设菜单 ID 段预留 2090 用于 PMS 仪表盘权限点；如已存在自动跳过。

-- 3.1 查询「BI 看板查询」权限点（pms:dashboard:query）是否已存在
SET @perm_count := (
    SELECT COUNT(*) FROM system_menu
    WHERE permission = 'pms:dashboard:query' AND deleted = 0
);
SET @sql := IF(@perm_count = 0,
    'INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, creator, create_time, updater, update_time, deleted) VALUES (''PMS BI 看板查询'', ''pms:dashboard:query'', 3, 10, 0, ''/pms/dashboard'', '''', '''', 0, ''1'', NOW(), ''1'', NOW(), 0)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.2 查询「全局数据权限」权限点（pms:dashboard:all）是否已存在
SET @perm_count := (
    SELECT COUNT(*) FROM system_menu
    WHERE permission = 'pms:dashboard:all' AND deleted = 0
);
SET @sql := IF(@perm_count = 0,
    'INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, creator, create_time, updater, update_time, deleted) VALUES (''PMS BI 全局数据权限'', ''pms:dashboard:all'', 3, 11, 0, '''', '''', '''', 0, ''1'', NOW(), ''1'', NOW(), 0)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 菜单 SQL：消息中心页菜单（「PMS 站内消息中心」路由 /pms/message）
SET @menu_count := (
    SELECT COUNT(*) FROM system_menu
    WHERE permission = 'pms:message:list' AND deleted = 0
);
SET @sql := IF(@menu_count = 0,
    'INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, creator, create_time, updater, update_time, deleted) VALUES (''PMS 消息中心'', ''pms:message:list'', 2, 12, 0, ''/pms/message'', ''ep:bell'', ''views/pms/message/index'', 0, ''1'', NOW(), ''1'', NOW(), 0)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证
-- SHOW INDEX FROM pms_project WHERE Key_name = 'idx_dept_id';
-- SHOW INDEX FROM pms_task WHERE Key_name = 'idx_dept_id';
-- SELECT * FROM system_menu WHERE permission LIKE 'pms:dashboard:%' OR permission = 'pms:message:list';
