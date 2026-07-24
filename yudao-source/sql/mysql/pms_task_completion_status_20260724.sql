-- 任务完成审核状态 completion_pending_review 长度为 25，需要扩展原 varchar(20)
ALTER TABLE `pms_task`
    MODIFY COLUMN `complete_status` varchar(32) NOT NULL DEFAULT 'not_started' COMMENT '完成状态';
