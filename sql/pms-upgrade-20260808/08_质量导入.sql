-- ============================================================
-- #8 质量问题 Excel 批量导入 — DDL（幂等）
-- 目标表：pms_quality_issue
-- 新增 5 个字段：
--   issue_title        VARCHAR(200)  问题标题
--   issue_type         VARCHAR(64)   问题类型
--   discovered_date    DATE          发现日期
--   discoverer_id      BIGINT        发现人（系统用户ID）
--   due_date           DATE          期望完成日期
--
-- 兼容性：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，
--        用 information_schema + PREPARE 动态 SQL 实现幂等可重复执行。
-- ============================================================

-- 1. issue_title
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pms_quality_issue'
      AND COLUMN_NAME = 'issue_title'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE pms_quality_issue ADD COLUMN issue_title VARCHAR(200) NULL COMMENT ''问题标题''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. issue_type
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pms_quality_issue'
      AND COLUMN_NAME = 'issue_type'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE pms_quality_issue ADD COLUMN issue_type VARCHAR(64) NULL COMMENT ''问题类型''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. discovered_date
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pms_quality_issue'
      AND COLUMN_NAME = 'discovered_date'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE pms_quality_issue ADD COLUMN discovered_date DATE NULL COMMENT ''发现日期''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. discoverer_id
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pms_quality_issue'
      AND COLUMN_NAME = 'discoverer_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE pms_quality_issue ADD COLUMN discoverer_id BIGINT NULL COMMENT ''发现人（系统用户ID）''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. due_date
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pms_quality_issue'
      AND COLUMN_NAME = 'due_date'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE pms_quality_issue ADD COLUMN due_date DATE NULL COMMENT ''期望完成日期''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 索引（用于项目详情页质量 Tab 按项目+创建时间倒序拉取）
-- 幂等：先查 INDEXES 视图再决定是否创建
-- ============================================================
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pms_quality_issue'
      AND INDEX_NAME = 'idx_project_id_create_time'
);
SET @sql := IF(@index_exists = 0,
    'CREATE INDEX idx_project_id_create_time ON pms_quality_issue (project_id, create_time)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证字段已添加（执行后可注释）
-- DESC pms_quality_issue;

