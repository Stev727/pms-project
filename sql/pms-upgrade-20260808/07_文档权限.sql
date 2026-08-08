-- ============================================================
-- #7 文档权限分级 — 文档表加权限字段
-- ============================================================
-- 新增字段：
--   visibility        可见范围（public/role/private）
--   allowed_role_ids  允许查看的角色ID列表（JSON数组字符串）
--   allow_download    是否允许下载（0/1）
-- 存量数据回填：visibility='public', allow_download=1
-- 幂等 DDL：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema + PREPARE

-- ---------- 1. pms_document 加字段 ----------
DROP PROCEDURE IF EXISTS pms_add_document_perm_columns;
DELIMITER $$
CREATE PROCEDURE pms_add_document_perm_columns()
BEGIN
    -- visibility
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'pms_document' AND column_name = 'visibility') THEN
        ALTER TABLE `pms_document` ADD COLUMN `visibility` VARCHAR(20) DEFAULT 'public' COMMENT '可见范围：public 项目全员 / role 指定角色 / private 仅上传人+项目经理' AFTER `permission_flag`;
    END IF;

    -- allowed_role_ids
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'pms_document' AND column_name = 'allowed_role_ids') THEN
        ALTER TABLE `pms_document` ADD COLUMN `allowed_role_ids` TEXT COMMENT '允许查看的角色ID列表（JSON数组，如[101,102]，visibility=role时生效）' AFTER `visibility`;
    END IF;

    -- allow_download
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'pms_document' AND column_name = 'allow_download') THEN
        ALTER TABLE `pms_document` ADD COLUMN `allow_download` BIT(1) DEFAULT b'1' COMMENT '是否允许下载（0=禁止，1=允许）' AFTER `allowed_role_ids`;
    END IF;
END$$
DELIMITER ;
CALL pms_add_document_perm_columns();
DROP PROCEDURE IF EXISTS pms_add_document_perm_columns;

-- ---------- 2. 存量数据回填 ----------
-- visibility 为空时回填为 public
UPDATE `pms_document` SET `visibility` = 'public' WHERE `visibility` IS NULL OR `visibility` = '';
-- allow_download 为空时回填为 1（允许下载）
UPDATE `pms_document` SET `allow_download` = b'1' WHERE `allow_download` IS NULL;

-- ---------- 3. 索引 ----------
-- 按项目 + 可见范围查询的辅助索引
DROP PROCEDURE IF EXISTS pms_add_document_perm_index;
DELIMITER $$
CREATE PROCEDURE pms_add_document_perm_index()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'pms_document' AND index_name = 'idx_project_visibility') THEN
        ALTER TABLE `pms_document` ADD INDEX `idx_project_visibility` (`project_id`, `visibility`);
    END IF;
END$$
DELIMITER ;
CALL pms_add_document_perm_index();
DROP PROCEDURE IF EXISTS pms_add_document_perm_index;

