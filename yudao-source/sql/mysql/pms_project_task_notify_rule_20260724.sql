-- 项目默认规则与任务覆盖规则范围字段
ALTER TABLE `pms_notify_rule`
    ADD COLUMN `scope_type` varchar(16) NOT NULL DEFAULT project COMMENT 规则范围 project/task AFTER `rule_id`,
    ADD COLUMN `project_id` bigint NULL COMMENT 所属项目 AFTER `scope_type`,
    ADD COLUMN `task_id` bigint NULL COMMENT 所属任务，项目规则为空 AFTER `project_id`;

CREATE INDEX `idx_pms_notify_rule_scope_event`
    ON `pms_notify_rule` (`tenant_id`, `project_id`, `task_id`, `trigger_event`, `status`);
