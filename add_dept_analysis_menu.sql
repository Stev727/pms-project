-- 部门协作分析 菜单（PMS 新增一级菜单）
-- 父菜单：7000 (PMS 根)
-- 权限：pms:dept-analysis:query
-- 组件：pms/dept-analysis/index  →  src/views/pms/dept-analysis/index.vue
-- 超管(super_admin) 默认拥有全部菜单，无需插 role_menu；其它角色需在 [角色管理] 勾选本菜单
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (7150, '部门协作分析', 'pms:dept-analysis:query', 2, 6, 7000, 'dept-analysis', 'ep:office-building', 'pms/dept-analysis/index', 'PmsDeptAnalysis', 0, 1, 1, 1, '1', NOW(), '1', NOW(), 0)
ON DUPLICATE KEY UPDATE name='部门协作分析', permission='pms:dept-analysis:query', component='pms/dept-analysis/index', component_name='PmsDeptAnalysis', update_time=NOW();

-- ⚠️ yudao 菜单有两级缓存(Caffeine L1 + Redis L2)：raw SQL 插入不触发 @CacheEvict
--    插入后必须：重启后端(清 L1) + FLUSHALL/删 menu 相关 Redis key(清 L2)，菜单才在 get-permission-info 下发
