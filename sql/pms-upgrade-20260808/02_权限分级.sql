-- =====================================================================
-- #2 权限分级 —— 建表 + 默认权限模板 + 存量数据初始化
-- 目标库：ruoyi-vue-pro
-- 幂等：可重复执行
-- 依赖：无（这是所有其它需求的地基，必须最先执行）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 项目角色表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pms_project_role` (
  `role_id`     BIGINT       NOT NULL                COMMENT '角色ID(雪花)',
  `project_id`  BIGINT       NOT NULL                COMMENT '项目ID',
  `role_name`   VARCHAR(64)  NOT NULL                COMMENT '角色显示名(如 质量负责人)',
  `role_code`   VARCHAR(64)  NOT NULL                COMMENT '角色编码(项目内唯一, 如 qa_lead)',
  `is_system`   BIT(1)       NOT NULL DEFAULT b'0'   COMMENT '是否系统内置(内置不可删)',
  `sort_order`  INT          NOT NULL DEFAULT 0      COMMENT '排序号',
  `remark`      VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  `creator`     VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_proj_code` (`project_id`, `role_code`, `deleted`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PMS 项目角色';

-- ---------------------------------------------------------------------
-- 2. 项目权限矩阵表（无记录 = 不允许）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pms_project_permission` (
  `perm_id`     BIGINT       NOT NULL                COMMENT '主键ID(雪花)',
  `project_id`  BIGINT       NOT NULL                COMMENT '项目ID',
  `role_id`     BIGINT       NOT NULL                COMMENT '项目角色ID',
  `perm_key`    VARCHAR(64)  NOT NULL                COMMENT '权限点编码, 见 PmsPermKeyEnum',
  `allowed`     BIT(1)       NOT NULL DEFAULT b'1'   COMMENT '是否允许',
  `creator`     VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`perm_id`),
  UNIQUE KEY `uk_proj_role_key` (`project_id`, `role_id`, `perm_key`, `deleted`),
  KEY `idx_proj_role` (`project_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PMS 项目权限矩阵';

-- ---------------------------------------------------------------------
-- 3. 默认权限模板表（新项目按此初始化）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pms_project_permission_template` (
  `template_id` BIGINT       NOT NULL                COMMENT '主键ID',
  `role_code`   VARCHAR(64)  NOT NULL                COMMENT '角色编码',
  `role_name`   VARCHAR(64)  NOT NULL                COMMENT '角色显示名',
  `perm_key`    VARCHAR(64)  NOT NULL                COMMENT '权限点编码',
  `allowed`     BIT(1)       NOT NULL DEFAULT b'1'   COMMENT '是否允许',
  `sort_order`  INT          NOT NULL DEFAULT 0      COMMENT '角色排序号',
  `creator`     VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_code_key` (`role_code`, `perm_key`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PMS 项目权限默认模板';

-- =====================================================================
-- 4. 默认权限模板数据
--
-- 【重要】这 11 个 role_code 与 MembersTab.vue 现有硬编码下拉项完全一致，
--         必须全部覆盖，否则存量成员的 role_code 匹配不到角色，权限会全空。
--
--   pm                  项目经理      —— 全部权限（且代码层无条件放行）
--   admin               系统管理员    —— 全部权限
--   dept_head           部门负责人    —— 除权限/成员管理外全部
--   management          管理层        —— 只读
--   main_owner          主责任人      —— 任务执行 + 内容录入
--   helper              协助人        —— 任务编辑 + 查看
--   developer           开发工程师    —— 同工程师组
--   hw_engineer         硬件工程师    —— 同工程师组
--   sw_engineer         软件工程师    —— 同工程师组
--   mechanical_engineer 结构工程师    —— 同工程师组
--   external            外部成员      —— 最小权限（仅看文档，不可下载）
-- =====================================================================

DELETE FROM `pms_project_permission_template`;
SET @tid = 9040000000000000;

-- ---------- pm 项目经理：全部 23 个权限点 ----------
INSERT INTO `pms_project_permission_template` (template_id, role_code, role_name, perm_key, allowed, sort_order) VALUES
(@tid := @tid + 1, 'pm', '项目经理', 'task_create', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'task_edit', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'task_delete', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'task_assign', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'task_review', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'document_view', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'document_preview', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'document_download', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'document_upload', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'document_delete', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'document_manage_perm', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'material_view', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'material_add', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'material_edit', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'material_delete', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'quality_view', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'quality_add', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'quality_edit', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'quality_delete', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'quality_import', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'member_manage', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'project_edit', b'1', 1),
(@tid := @tid + 1, 'pm', '项目经理', 'permission_manage', b'1', 1);

-- ---------- admin 系统管理员：全部 ----------
INSERT INTO `pms_project_permission_template` (template_id, role_code, role_name, perm_key, allowed, sort_order)
SELECT @tid := @tid + 1, 'admin', '系统管理员', perm_key, b'1', 2
FROM `pms_project_permission_template` WHERE role_code = 'pm';

-- ---------- dept_head 部门负责人：除 member_manage / permission_manage / document_manage_perm ----------
INSERT INTO `pms_project_permission_template` (template_id, role_code, role_name, perm_key, allowed, sort_order)
SELECT @tid := @tid + 1, 'dept_head', '部门负责人', perm_key, b'1', 3
FROM `pms_project_permission_template`
WHERE role_code = 'pm'
  AND perm_key NOT IN ('member_manage', 'permission_manage', 'document_manage_perm');

-- ---------- management 管理层：只读 ----------
INSERT INTO `pms_project_permission_template` (template_id, role_code, role_name, perm_key, allowed, sort_order) VALUES
(@tid := @tid + 1, 'management', '管理层', 'document_view', b'1', 4),
(@tid := @tid + 1, 'management', '管理层', 'document_preview', b'1', 4),
(@tid := @tid + 1, 'management', '管理层', 'document_download', b'1', 4),
(@tid := @tid + 1, 'management', '管理层', 'material_view', b'1', 4),
(@tid := @tid + 1, 'management', '管理层', 'quality_view', b'1', 4);

-- ---------- main_owner 主责任人 ----------
INSERT INTO `pms_project_permission_template` (template_id, role_code, role_name, perm_key, allowed, sort_order) VALUES
(@tid := @tid + 1, 'main_owner', '主责任人', 'task_create', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'task_edit', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'task_assign', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'task_review', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'document_view', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'document_preview', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'document_download', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'document_upload', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'material_view', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'material_add', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'material_edit', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'quality_view', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'quality_add', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'quality_edit', b'1', 5),
(@tid := @tid + 1, 'main_owner', '主责任人', 'quality_import', b'1', 5);

-- ---------- helper 协助人 ----------
INSERT INTO `pms_project_permission_template` (template_id, role_code, role_name, perm_key, allowed, sort_order) VALUES
(@tid := @tid + 1, 'helper', '协助人', 'task_edit', b'1', 6),
(@tid := @tid + 1, 'helper', '协助人', 'document_view', b'1', 6),
(@tid := @tid + 1, 'helper', '协助人', 'document_preview', b'1', 6),
(@tid := @tid + 1, 'helper', '协助人', 'document_download', b'1', 6),
(@tid := @tid + 1, 'helper', '协助人', 'document_upload', b'1', 6),
(@tid := @tid + 1, 'helper', '协助人', 'material_view', b'1', 6),
(@tid := @tid + 1, 'helper', '协助人', 'quality_view', b'1', 6),
(@tid := @tid + 1, 'helper', '协助人', 'quality_add', b'1', 6);

-- ---------- 工程师组：developer / hw_engineer / sw_engineer / mechanical_engineer ----------
-- 先建 developer 基线
INSERT INTO `pms_project_permission_template` (template_id, role_code, role_name, perm_key, allowed, sort_order) VALUES
(@tid := @tid + 1, 'developer', '开发工程师', 'task_edit', b'1', 7),
(@tid := @tid + 1, 'developer', '开发工程师', 'document_view', b'1', 7),
(@tid := @tid + 1, 'developer', '开发工程师', 'document_preview', b'1', 7),
(@tid := @tid + 1, 'developer', '开发工程师', 'document_download', b'1', 7),
(@tid := @tid + 1, 'developer', '开发工程师', 'document_upload', b'1', 7),
(@tid := @tid + 1, 'developer', '开发工程师', 'material_view', b'1', 7),
(@tid := @tid + 1, 'developer', '开发工程师', 'material_add', b'1', 7),
(@tid := @tid + 1, 'developer', '开发工程师', 'material_edit', b'1', 7),
(@tid := @tid + 1, 'developer', '开发工程师', 'quality_view', b'1', 7),
(@tid := @tid + 1, 'developer', '开发工程师', 'quality_add', b'1', 7),
(@tid := @tid + 1, 'developer', '开发工程师', 'quality_edit', b'1', 7);

-- 其余 3 个工程师角色复制 developer 权限集
INSERT INTO `pms_project_permission_template` (template_id, role_code, role_name, perm_key, allowed, sort_order)
SELECT @tid := @tid + 1, 'hw_engineer', '硬件工程师', perm_key, b'1', 8
FROM `pms_project_permission_template` WHERE role_code = 'developer';

INSERT INTO `pms_project_permission_template` (template_id, role_code, role_name, perm_key, allowed, sort_order)
SELECT @tid := @tid + 1, 'sw_engineer', '软件工程师', perm_key, b'1', 9
FROM `pms_project_permission_template` WHERE role_code = 'developer';

INSERT INTO `pms_project_permission_template` (template_id, role_code, role_name, perm_key, allowed, sort_order)
SELECT @tid := @tid + 1, 'mechanical_engineer', '结构工程师', perm_key, b'1', 10
FROM `pms_project_permission_template` WHERE role_code = 'developer';

-- ---------- external 外部成员：仅查看+预览，不可下载 ----------
INSERT INTO `pms_project_permission_template` (template_id, role_code, role_name, perm_key, allowed, sort_order) VALUES
(@tid := @tid + 1, 'external', '外部成员', 'document_view', b'1', 11),
(@tid := @tid + 1, 'external', '外部成员', 'document_preview', b'1', 11);

-- 校验模板行数（预期 156 行左右，随上面调整而变）
SELECT role_code, role_name, COUNT(*) AS perm_count
FROM `pms_project_permission_template`
GROUP BY role_code, role_name
ORDER BY MIN(sort_order);

-- =====================================================================
-- 5. 存量项目初始化
--
-- 说明：SQL 无法生成雪花ID，存量项目建议走接口初始化（幂等）：
--   POST /admin-api/pms/project-permission/init?projectId=xxx
--
-- 若要用纯 SQL 批量回填，用下面这段（role_id 用 项目ID+序号 拼一个不冲突的值）。
-- ⚠️ 执行前务必备份，且只在存量项目未初始化时执行一次。
-- =====================================================================

-- 5.1 为所有未初始化的项目建角色
-- INSERT INTO `pms_project_role` (role_id, project_id, role_name, role_code, is_system, sort_order, tenant_id)
-- SELECT
--   p.project_id * 100 + t.rn,
--   p.project_id,
--   t.role_name,
--   t.role_code,
--   b'1',
--   t.sort_order,
--   p.tenant_id
-- FROM `pms_project` p
-- JOIN (
--   SELECT role_code, MIN(role_name) AS role_name, MIN(sort_order) AS sort_order,
--          ROW_NUMBER() OVER (ORDER BY MIN(sort_order)) AS rn
--   FROM `pms_project_permission_template`
--   GROUP BY role_code
-- ) t ON 1 = 1
-- WHERE p.deleted = b'0'
--   AND NOT EXISTS (SELECT 1 FROM `pms_project_role` r WHERE r.project_id = p.project_id AND r.deleted = b'0');

-- 5.2 按模板回填权限矩阵
-- INSERT INTO `pms_project_permission` (perm_id, project_id, role_id, perm_key, allowed, tenant_id)
-- SELECT
--   r.role_id * 1000 + (@rownum := @rownum + 1) % 1000,
--   r.project_id,
--   r.role_id,
--   t.perm_key,
--   b'1',
--   r.tenant_id
-- FROM `pms_project_role` r
-- JOIN `pms_project_permission_template` t ON t.role_code = r.role_code AND t.allowed = b'1'
-- CROSS JOIN (SELECT @rownum := 0) init
-- WHERE r.deleted = b'0'
--   AND NOT EXISTS (
--     SELECT 1 FROM `pms_project_permission` pp
--     WHERE pp.project_id = r.project_id AND pp.role_id = r.role_id AND pp.perm_key = t.perm_key AND pp.deleted = b'0'
--   );

-- =====================================================================
-- 6. 验证
-- =====================================================================
-- SELECT COUNT(*) AS role_cnt FROM pms_project_role WHERE deleted = b'0';
-- SELECT COUNT(*) AS perm_cnt FROM pms_project_permission WHERE deleted = b'0';
-- -- 查看某项目的矩阵
-- SELECT r.role_name, p.perm_key
-- FROM pms_project_role r
-- LEFT JOIN pms_project_permission p ON p.role_id = r.role_id AND p.deleted = b'0'
-- WHERE r.project_id = <你的项目ID> AND r.deleted = b'0'
-- ORDER BY r.sort_order, p.perm_key;

