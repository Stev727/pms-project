-- 存量孤儿数据清理（一次性）
-- 背景：deleteProject 历史只删项目行，未级联子表，导致 157 个已删项目残留 2460 孤儿任务/433 阶段/175 成员等
-- 本脚本把所有"项目已软删但子表未删"的数据软删（deleted=1）
-- 已于 2026-08-18 执行：tasks 2460 / stages 433 / members 175 / docs 35 / changes 46 / quality 33 / notify_rule 6
-- 代码侧 deleteProject 已改为级联软删（ProjectServiceImpl.deleteProject），后续删除项目无需再手动跑此脚本

UPDATE pms_task t LEFT JOIN pms_project p ON p.project_id=t.project_id AND p.deleted=0 SET t.deleted=1, t.update_time=NOW() WHERE t.deleted=0 AND p.project_id IS NULL;
UPDATE pms_project_stage t LEFT JOIN pms_project p ON p.project_id=t.project_id AND p.deleted=0 SET t.deleted=1, t.update_time=NOW() WHERE t.deleted=0 AND p.project_id IS NULL;
UPDATE pms_project_member t LEFT JOIN pms_project p ON p.project_id=t.project_id AND p.deleted=0 SET t.deleted=1, t.update_time=NOW() WHERE t.deleted=0 AND p.project_id IS NULL;
UPDATE pms_document t LEFT JOIN pms_project p ON p.project_id=t.project_id AND p.deleted=0 SET t.deleted=1, t.update_time=NOW() WHERE t.deleted=0 AND p.project_id IS NULL;
UPDATE pms_change_record t LEFT JOIN pms_project p ON p.project_id=t.project_id AND p.deleted=0 SET t.deleted=1, t.update_time=NOW() WHERE t.deleted=0 AND p.project_id IS NULL;
UPDATE pms_quality_issue t LEFT JOIN pms_project p ON p.project_id=t.project_id AND p.deleted=0 SET t.deleted=1, t.update_time=NOW() WHERE t.deleted=0 AND p.project_id IS NULL;
UPDATE pms_notify_rule t LEFT JOIN pms_project p ON p.project_id=t.project_id AND p.deleted=0 SET t.deleted=1, t.update_time=NOW() WHERE t.deleted=0 AND p.project_id IS NULL;
UPDATE pms_project_permission t LEFT JOIN pms_project p ON p.project_id=t.project_id AND p.deleted=0 SET t.deleted=1, t.update_time=NOW() WHERE t.deleted=0 AND p.project_id IS NULL;
UPDATE pms_project_role t LEFT JOIN pms_project p ON p.project_id=t.project_id AND p.deleted=0 SET t.deleted=1, t.update_time=NOW() WHERE t.deleted=0 AND p.project_id IS NULL;
