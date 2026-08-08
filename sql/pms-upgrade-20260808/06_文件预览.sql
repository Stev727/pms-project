-- ============================================================
-- #6 文件预览 — 缓存元数据表（可选，用于运维查看和定期清理）
-- ============================================================
-- 说明：
-- 1. 缓存文件本身落盘在 pms.preview.cache-dir 配置目录下，文件名 {cacheKey}.pdf
-- 2. 本表记录缓存元数据，方便运维查看缓存状态、定期清理过期缓存
-- 3. 本表非必须（代码不依赖此表运行），但建议创建以便管理
-- 4. 幂等 DDL，MySQL 8.0 不支持 IF NOT EXISTS 建表用 information_schema 判断

-- ---------- 1. 缓存元数据表 ----------
DROP PROCEDURE IF EXISTS pms_create_preview_cache_table;
DELIMITER $$
CREATE PROCEDURE pms_create_preview_cache_table()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = DATABASE() AND table_name = 'pms_document_preview_cache') THEN
        CREATE TABLE `pms_document_preview_cache` (
            `id`             BIGINT       NOT NULL COMMENT '主键（雪花ID）',
            `doc_id`         BIGINT       NOT NULL COMMENT '文档ID',
            `cache_key`      VARCHAR(128) NOT NULL COMMENT '缓存key（docId_updateTime）',
            `cache_file_path` VARCHAR(512) NOT NULL COMMENT '缓存文件绝对路径',
            `file_size`      BIGINT       DEFAULT 0 COMMENT '缓存文件大小（字节）',
            `creator`        VARCHAR(64)  DEFAULT '' COMMENT '创建者',
            `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
            `updater`        VARCHAR(64)  DEFAULT '' COMMENT '更新者',
            `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
            `deleted`        BIT(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
            `tenant_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_cache_key` (`cache_key`),
            KEY `idx_doc_id` (`doc_id`),
            KEY `idx_create_time` (`create_time`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PMS 文档预览缓存元数据表（#6）';
    END IF;
END$$
DELIMITER ;
CALL pms_create_preview_cache_table();
DROP PROCEDURE IF EXISTS pms_create_preview_cache_table;

-- ---------- 2. 说明 ----------
-- 本 SQL 仅建元数据表，不需要在应用启动前执行（代码不依赖此表）。
-- 缓存目录由 application.yaml 的 pms.preview.cache-dir 配置，应用启动时自动创建。
-- 如需定期清理过期缓存，可按 create_time 清理：
--   DELETE FROM pms_document_preview_cache WHERE create_time < DATE_SUB(NOW(), INTERVAL 30 DAY);
--   并手动删除对应的缓存文件。

