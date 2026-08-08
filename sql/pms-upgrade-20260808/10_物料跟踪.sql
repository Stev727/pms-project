-- ============================================================
-- #10 物料跟踪嵌入项目详情 — DDL（幂等）
-- 目标表：pms_material_track
--
-- 说明：DO 已有 project_id 字段，无需 ALTER TABLE 新增字段。
--      本脚本仅追加 project_id 索引，加速项目详情页物料 Tab
--      按 projectId 过滤的列表查询。
--
-- 兼容性：MySQL 8.0 幂等可重复执行
-- ============================================================

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pms_material_track'
      AND INDEX_NAME = 'idx_project_id_create_time'
);
SET @sql := IF(@index_exists = 0,
    'CREATE INDEX idx_project_id_create_time ON pms_material_track (project_id, create_time)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证索引已添加
-- SHOW INDEX FROM pms_material_track;

