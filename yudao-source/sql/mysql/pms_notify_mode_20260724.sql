CREATE TABLE IF NOT EXISTS `pms_notify_mode` (
  `mode_id` bigint NOT NULL COMMENT '通知模式ID',
  `mode_name` varchar(100) NOT NULL COMMENT '模式名称',
  `description` varchar(500) DEFAULT NULL COMMENT '模式说明',
  `status` varchar(16) NOT NULL DEFAULT 'enabled' COMMENT '状态',
  `default_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`mode_id`), KEY `idx_notify_mode_tenant_status` (`tenant_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PMS通知模式';

ALTER TABLE `pms_notify_rule`
  ADD COLUMN `mode_id` bigint NULL COMMENT '通知模式ID' AFTER `rule_id`,
  ADD COLUMN `source_mode_id` bigint NULL COMMENT '项目快照来源模式ID' AFTER `mode_id`;
CREATE INDEX `idx_pms_notify_rule_mode` ON `pms_notify_rule` (`tenant_id`,`mode_id`,`status`);

INSERT INTO `pms_notify_mode` (`mode_id`,`mode_name`,`description`,`status`,`default_flag`,`creator`,`updater`,`tenant_id`)
VALUES (1,'标准项目通知','派发通知负责人，延期通知项目经理，变更和完成审核通知相关人员','enabled',b'1','system','system',1);
INSERT INTO `pms_notify_rule` (`rule_id`,`mode_id`,`scope_type`,`rule_name`,`trigger_event`,`trigger_condition`,`notify_target`,`notify_channel`,`time_rule`,`template_title`,`template_content`,`status`,`do_not_disturb`,`escalation_flag`,`creator`,`updater`,`tenant_id`)
VALUES
(10001,1,'mode','任务派发通知负责人','task_dispatched','{}','main_owner','dingtalk','immediate','任务派发通知','您有新的项目任务：{task_name}，请及时接收并处理。','enabled',b'0',b'0','system','system',1),
(10002,1,'mode','任务延期通知项目经理','task_overdue','{}','pm','dingtalk','daily','任务延期提醒','任务 {task_name} 已延期 {delay_days} 天，请及时处理。','enabled',b'0',b'0','system','system',1),
(10003,1,'mode','任务变更待审核','change_submitted','{}','pm','dingtalk','immediate','任务变更待审核','任务 {task_name} 提交变更，请审核。','enabled',b'0',b'0','system','system',1),
(10004,1,'mode','任务完成待审核','completion_submitted','{}','pm','dingtalk','immediate','任务完成待审核','任务 {task_name} 提交完成，请审核。','enabled',b'0',b'0','system','system',1);
