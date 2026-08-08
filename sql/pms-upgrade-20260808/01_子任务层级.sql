-- ============================================================================
-- PMS 需求 #1：子任务层级结构 —— 数据库变更脚本
-- ----------------------------------------------------------------------------
-- 目标库：ruoyi-vue-pro（MySQL 8.0.42）
-- 执行顺序：01_子任务层级.sql  →  02_权限分级.sql  →  03_派发审核.sql
--          （01 与 03 都改 pms_task，必须按序执行；02 与本脚本无依赖，先后皆可）
--
-- 【重要】MySQL 8.0 不支持 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`
--        （那是 MariaDB 语法），因此本脚本统一用
--        information_schema 判断 + PREPARE 动态 SQL 实现幂等，可重复执行。
--
-- 变更内容：
--   1. pms_task 新增 level        任务层级（1=顶层，最多 3 级）
--   2. pms_task 新增 reviewer_id  审核人（子任务默认=父任务主责任人，顶层=项目经理）
--   3. pms_task 新增复合索引 idx_task_parent_level
--   4. 存量数据回填 level / reviewer_id
--
-- 实测前置事实（执行前已核对）：
--   - pms_task 共 3424 行，parent_task_id 全部为 NULL，即子任务功能为全新特性
--   - pms_task 已存在索引 idx_task_parent(parent_task_id)，本脚本另建复合索引不冲突
-- ============================================================================

SET @db := DATABASE();

-- ----------------------------------------------------------------------------
-- 1. pms_task.level —— 任务层级
-- ----------------------------------------------------------------------------
SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'pms_task' AND COLUMN_NAME = 'level') = 0,
    'ALTER TABLE pms_task ADD COLUMN `level` int NOT NULL DEFAULT 1 COMMENT ''任务层级：1=顶层任务，2/3=子任务，最大3级'' AFTER parent_task_id',
    'SELECT ''[skip] pms_task.level 已存在'' AS msg'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. pms_task.reviewer_id —— 审核人
-- ----------------------------------------------------------------------------
SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'pms_task' AND COLUMN_NAME = 'reviewer_id') = 0,
    'ALTER TABLE pms_task ADD COLUMN reviewer_id bigint NULL COMMENT ''审核人ID：子任务默认=父任务主责任人，顶层任务默认=项目经理'' AFTER main_owner_id',
    'SELECT ''[skip] pms_task.reviewer_id 已存在'' AS msg'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 3. 索引：按父任务 + 层级查询子任务树
-- ----------------------------------------------------------------------------
SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'pms_task' AND INDEX_NAME = 'idx_task_parent_level') = 0,
    'ALTER TABLE pms_task ADD INDEX idx_task_parent_level (parent_task_id, `level`)',
    'SELECT ''[skip] idx_task_parent_level 已存在'' AS msg'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'pms_task' AND INDEX_NAME = 'idx_task_reviewer') = 0,
    'ALTER TABLE pms_task ADD INDEX idx_task_reviewer (reviewer_id)',
    'SELECT ''[skip] idx_task_reviewer 已存在'' AS msg'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 4. 存量数据回填
-- ----------------------------------------------------------------------------

-- 4.1 顶层任务 level = 1（含历史脏数据 level=0 的兜底）
UPDATE pms_task
   SET `level` = 1
 WHERE deleted = 0
   AND (`level` IS NULL OR `level` < 1);

-- 4.2 子任务 level = 父任务 level + 1。
--     当前存量数据 parent_task_id 全为 NULL，以下三条为「脚本可重复执行 / 未来补跑」而保留，
--     逐层推进最多支持 3 级（第 3 条兜底第 4 级异常数据不会被继续放大）。
UPDATE pms_task t
  JOIN pms_task p ON p.task_id = t.parent_task_id AND p.deleted = 0
   SET t.`level` = 2
 WHERE t.deleted = 0 AND t.parent_task_id IS NOT NULL AND p.parent_task_id IS NULL;

UPDATE pms_task t
  JOIN pms_task p ON p.task_id = t.parent_task_id AND p.deleted = 0
   SET t.`level` = 3
 WHERE t.deleted = 0 AND t.parent_task_id IS NOT NULL AND p.`level` = 2;

-- 4.3 顶层任务审核人默认 = 项目经理
UPDATE pms_task t
  JOIN pms_project p ON p.project_id = t.project_id AND p.deleted = 0
   SET t.reviewer_id = p.project_manager_id
 WHERE t.deleted = 0
   AND t.reviewer_id IS NULL
   AND t.parent_task_id IS NULL
   AND p.project_manager_id IS NOT NULL;

-- 4.4 子任务审核人默认 = 父任务主责任人
UPDATE pms_task t
  JOIN pms_task p ON p.task_id = t.parent_task_id AND p.deleted = 0
   SET t.reviewer_id = p.main_owner_id
 WHERE t.deleted = 0
   AND t.reviewer_id IS NULL
   AND t.parent_task_id IS NOT NULL
   AND p.main_owner_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 5. 校验（执行后人工确认）
-- ----------------------------------------------------------------------------
SELECT '01_子任务层级.sql 执行完成' AS msg;

SELECT `level` AS 层级, COUNT(*) AS 任务数
  FROM pms_task WHERE deleted = 0 GROUP BY `level` ORDER BY `level`;

SELECT COUNT(*) AS 无审核人任务数
  FROM pms_task WHERE deleted = 0 AND reviewer_id IS NULL;

