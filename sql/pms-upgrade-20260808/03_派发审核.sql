-- ============================================================================
-- PMS 需求 #3：任务派发审核 —— 数据库变更脚本
-- ----------------------------------------------------------------------------
-- 目标库：ruoyi-vue-pro（MySQL 8.0.42）
-- 执行顺序：01_子任务层级.sql  →  02_权限分级.sql  →  【03_派发审核.sql】
--          本脚本依赖 01 已建好的 reviewer_id 字段（回填审核人时使用）。
--
-- 【重要】MySQL 8.0 不支持 `ADD COLUMN IF NOT EXISTS`，
--        统一用 information_schema 判断 + PREPARE 动态 SQL 实现幂等，可重复执行。
--
-- 变更内容：
--   1. pms_task    新增 assigner_id    派发人
--   2. pms_task    新增 review_status  审核状态 none/submitted/completed/rejected
--   3. pms_task    新增 review_comment 审核意见（驳回时必填）
--   4. pms_task    新增 review_policy  任务级审核策略覆盖（NULL=跟随项目）
--   5. pms_project 新增 review_policy  项目级默认审核策略
--   6. 索引 idx_task_review(project_id, review_status)
--   7. 存量数据回填
--
-- 【设计说明 —— 双状态并存】
--   源码已有 complete_status 状态机（not_started / pending_accept / in_progress /
--   completion_pending_review / completed / rejected / delayed / paused），
--   且已有 /submit-completion、/review-completion 两个端点在线上使用。
--   本需求新增的 review_status 是「审核维度」的独立状态，与 complete_status 并存：
--     review_status: none --提交--> submitted --通过--> completed
--                                            --驳回--> rejected
--   后端 TaskServiceImpl 在流转 review_status 的同时会同步 complete_status
--   （submitted→completion_pending_review、completed→completed、rejected→in_progress），
--   保证既有列表/看板的状态标签不会错乱。详见 README_任务模块.md「偏差记录」。
-- ============================================================================

SET @db := DATABASE();

-- ----------------------------------------------------------------------------
-- 1. pms_task.assigner_id —— 派发人
-- ----------------------------------------------------------------------------
SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'pms_task' AND COLUMN_NAME = 'assigner_id') = 0,
    'ALTER TABLE pms_task ADD COLUMN assigner_id bigint NULL COMMENT ''派发人ID：执行派发操作的用户'' AFTER dispatch_time',
    'SELECT ''[skip] pms_task.assigner_id 已存在'' AS msg'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. pms_task.review_status —— 审核状态
-- ----------------------------------------------------------------------------
SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'pms_task' AND COLUMN_NAME = 'review_status') = 0,
    'ALTER TABLE pms_task ADD COLUMN review_status varchar(20) NOT NULL DEFAULT ''none'' COMMENT ''审核状态：none未提交/submitted待审核/completed已通过/rejected已驳回'' AFTER review_opinion',
    'SELECT ''[skip] pms_task.review_status 已存在'' AS msg'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 3. pms_task.review_comment —— 审核意见 / 驳回原因
-- ----------------------------------------------------------------------------
SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'pms_task' AND COLUMN_NAME = 'review_comment') = 0,
    'ALTER TABLE pms_task ADD COLUMN review_comment varchar(500) NULL COMMENT ''审核意见/驳回原因，驳回时必填'' AFTER review_status',
    'SELECT ''[skip] pms_task.review_comment 已存在'' AS msg'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 4. pms_task.review_policy —— 任务级审核策略覆盖（NULL = 跟随项目）
-- ----------------------------------------------------------------------------
SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'pms_task' AND COLUMN_NAME = 'review_policy') = 0,
    'ALTER TABLE pms_task ADD COLUMN review_policy varchar(20) NULL COMMENT ''任务级审核策略覆盖：need_review/self_review/skip，为空则跟随项目'' AFTER review_comment',
    'SELECT ''[skip] pms_task.review_policy 已存在'' AS msg'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 5. pms_project.review_policy —— 项目级默认审核策略
-- ----------------------------------------------------------------------------
SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'pms_project' AND COLUMN_NAME = 'review_policy') = 0,
    'ALTER TABLE pms_project ADD COLUMN review_policy varchar(20) NOT NULL DEFAULT ''need_review'' COMMENT ''项目默认审核策略：need_review需审核/self_review自审通过/skip跳过审核''',
    'SELECT ''[skip] pms_project.review_policy 已存在'' AS msg'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 6. 索引：审核中心按项目 + 审核状态查询
-- ----------------------------------------------------------------------------
SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'pms_task' AND INDEX_NAME = 'idx_task_review') = 0,
    'ALTER TABLE pms_task ADD INDEX idx_task_review (project_id, review_status)',
    'SELECT ''[skip] idx_task_review 已存在'' AS msg'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 7. 存量数据回填
-- ----------------------------------------------------------------------------

-- 7.1 项目审核策略统一为「需要审核」（与线上既有行为一致，不改变现状）
UPDATE pms_project
   SET review_policy = 'need_review'
 WHERE deleted = 0
   AND (review_policy IS NULL OR review_policy = '');

-- 7.2 已完成任务 → review_status = completed
UPDATE pms_task
   SET review_status = 'completed'
 WHERE deleted = 0
   AND complete_status = 'completed'
   AND review_status = 'none';

-- 7.3 待审核任务（含历史两种写法）→ review_status = submitted
UPDATE pms_task
   SET review_status = 'submitted'
 WHERE deleted = 0
   AND complete_status IN ('completion_pending_review', 'pending_review')
   AND review_status = 'none';

-- 7.4 已退回任务 → review_status = rejected
UPDATE pms_task
   SET review_status = 'rejected'
 WHERE deleted = 0
   AND complete_status = 'rejected'
   AND review_status = 'none';

-- 7.5 历史 review_opinion 迁移到 review_comment（不覆盖已有值）
UPDATE pms_task
   SET review_comment = LEFT(review_opinion, 500)
 WHERE deleted = 0
   AND review_comment IS NULL
   AND review_opinion IS NOT NULL
   AND review_opinion <> '';

-- 7.6 已派发任务的派发人回填为项目经理（历史未记录真实派发人，取最接近的责任主体）
UPDATE pms_task t
  JOIN pms_project p ON p.project_id = t.project_id AND p.deleted = 0
   SET t.assigner_id = p.project_manager_id
 WHERE t.deleted = 0
   AND t.assigner_id IS NULL
   AND t.is_dispatched = 1
   AND p.project_manager_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 8. 校验（执行后人工确认）
-- ----------------------------------------------------------------------------
SELECT '03_派发审核.sql 执行完成' AS msg;

SELECT review_status AS 审核状态, COUNT(*) AS 任务数
  FROM pms_task WHERE deleted = 0 GROUP BY review_status ORDER BY 任务数 DESC;

SELECT complete_status AS 完成状态, review_status AS 审核状态, COUNT(*) AS 任务数
  FROM pms_task WHERE deleted = 0 GROUP BY complete_status, review_status ORDER BY 任务数 DESC;

SELECT review_policy AS 项目审核策略, COUNT(*) AS 项目数
  FROM pms_project WHERE deleted = 0 GROUP BY review_policy;

