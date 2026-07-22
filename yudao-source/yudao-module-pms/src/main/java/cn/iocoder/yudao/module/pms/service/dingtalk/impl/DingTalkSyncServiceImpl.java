package cn.iocoder.yudao.module.pms.service.dingtalk.impl;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkConfigDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkDeptDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkUserDO;
import cn.iocoder.yudao.module.pms.dal.mysql.dingtalk.PmsDingTalkDeptMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.dingtalk.PmsDingTalkUserMapper;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkApiService;
import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkSyncService;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/**
 * 钉钉组织架构同步服务实现
 * 将钉钉的部门/用户数据同步到 Yudao 的 system_dept / system_users 表
 * 并维护 pms_dingtalk_dept / pms_dingtalk_user 映射关系
 */
@Service
@Slf4j
public class DingTalkSyncServiceImpl implements DingTalkSyncService {

    @Resource
    private DingTalkApiService dingTalkApiService;

    @Resource
    private PmsDingTalkDeptMapper dingTalkDeptMapper;

    @Resource
    private PmsDingTalkUserMapper dingTalkUserMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> syncAll() {
        log.info("[DingTalkSync] 开始全量同步组织架构...");

        PmsDingTalkConfigDO config = dingTalkApiService.getConfig();
        if (config.getSyncEnabled() == null || !config.getSyncEnabled()) {
            log.info("[DingTalkSync] 组织架构同步未启用");
            Map<String, Object> result = new HashMap<>();
            result.put("status", "skipped");
            result.put("message", "组织架构同步未启用");
            return result;
        }

        // 1. 测试连接
        String testResult = dingTalkApiService.testConnection();
        if (!testResult.startsWith("OK")) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "failed");
            result.put("message", "钉钉连接失败: " + testResult);
            return result;
        }

        // 2. 同步部门（必须先于用户，因为用户需要关联部门ID）
        Map<String, Object> deptResult = syncDepartments();

        // 3. 同步用户
        Map<String, Object> userResult = syncUsers();

        // 4. 汇总
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("deptSync", deptResult);
        result.put("userSync", userResult);
        result.put("syncTime", new Date());
        log.info("[DingTalkSync] 全量同步完成: {}", result);
        return result;
    }

    @Override
    public Map<String, Object> syncDepartments() {
        log.info("[DingTalkSync] 开始同步部门...");

        int newCount = 0;
        int updateCount = 0;
        int repairCount = 0;
        int totalDepts = 0;

        // 从根部门(1)开始递归获取所有部门
        List<Map<String, Object>> allDepts = new ArrayList<>();
        collectDepartments(1L, allDepts);

        for (Map<String, Object> dingDept : allDepts) {
            Long dingDeptId = (Long) dingDept.get("dept_id");
            String deptName = (String) dingDept.get("name");
            Long parentId = (Long) dingDept.get("parent_id");
            totalDepts++;

            // 查找现有映射
            PmsDingTalkDeptDO existing = dingTalkDeptMapper.selectByDingTalkDeptId(dingDeptId);

            // 判断映射是否有效（dept_id > 0 表示已成功创建到 system_dept）
            boolean hasValidDeptId = existing != null
                    && existing.getDeptId() != null
                    && existing.getDeptId() > 0;

            if (hasValidDeptId) {
                // 映射有效，更新部门名称
                existing.setDeptName(deptName);
                existing.setDingtalkParentId(parentId);
                existing.setLastSyncTime(new Date());
                dingTalkDeptMapper.updateById(existing);

                // 更新 system_dept
                try {
                    jdbcTemplate.update(
                            "UPDATE system_dept SET name = ? WHERE id = ?",
                            deptName, existing.getDeptId());
                } catch (Exception e) {
                    log.warn("[DingTalkSync] 更新部门名称失败: deptId={}, name={}, error={}",
                            existing.getDeptId(), deptName, e.getMessage());
                }
                updateCount++;
            } else {
                // 无映射或映射的 dept_id=0（之前创建失败），需要创建到 system_dept
                Long parentDeptId = 0L;
                if (parentId != null && parentId != 1) {
                    PmsDingTalkDeptDO parentMapping = dingTalkDeptMapper.selectByDingTalkDeptId(parentId);
                    if (parentMapping != null
                            && parentMapping.getDeptId() != null
                            && parentMapping.getDeptId() > 0) {
                        parentDeptId = parentMapping.getDeptId();
                    }
                }

                // 插入 system_dept（使用 KeyHolder 确保同连接获取自增ID）
                Long newDeptId = null;
                final Long finalParentDeptId = parentDeptId;
                try {
                    KeyHolder keyHolder = new GeneratedKeyHolder();
                    jdbcTemplate.update(connection -> {
                        PreparedStatement ps = connection.prepareStatement(
                                "INSERT INTO system_dept (name, parent_id, sort, status, " +
                                        "creator, create_time, updater, update_time, deleted, tenant_id) " +
                                        "VALUES (?, ?, 0, 0, 'dingtalk_sync', NOW(), 'dingtalk_sync', NOW(), 0, 1)",
                                Statement.RETURN_GENERATED_KEYS);
                        ps.setString(1, deptName);
                        ps.setLong(2, finalParentDeptId);
                        return ps;
                    }, keyHolder);

                    Number key = keyHolder.getKey();
                    if (key != null) {
                        newDeptId = key.longValue();
                    }
                } catch (Exception e) {
                    log.error("[DingTalkSync] 创建部门失败: name={}, parentId={}, error={}",
                            deptName, parentDeptId, e.getMessage(), e);
                }

                if (newDeptId != null && newDeptId > 0) {
                    if (existing != null) {
                        // 修复已有映射记录（之前 dept_id=0 的情况）
                        existing.setDeptId(newDeptId);
                        existing.setDeptName(deptName);
                        existing.setDingtalkParentId(parentId);
                        existing.setLastSyncTime(new Date());
                        dingTalkDeptMapper.updateById(existing);
                        repairCount++;
                        log.info("[DingTalkSync] 修复部门映射: dingDeptId={}, newDeptId={}", dingDeptId, newDeptId);
                    } else {
                        // 创建新映射
                        PmsDingTalkDeptDO mapping = new PmsDingTalkDeptDO();
                        mapping.setDeptId(newDeptId);
                        mapping.setDingtalkDeptId(dingDeptId);
                        mapping.setDingtalkParentId(parentId);
                        mapping.setDeptName(deptName);
                        mapping.setLastSyncTime(new Date());
                        dingTalkDeptMapper.insert(mapping);
                        newCount++;
                        log.info("[DingTalkSync] 新建部门: dingDeptId={}, deptId={}, name={}",
                                dingDeptId, newDeptId, deptName);
                    }
                } else {
                    log.error("[DingTalkSync] 部门创建失败，未获取到自增ID: name={}", deptName);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", totalDepts);
        result.put("new", newCount);
        result.put("updated", updateCount);
        result.put("repaired", repairCount);
        log.info("[DingTalkSync] 部门同步完成: {}", result);
        return result;
    }

    /**
     * 递归收集所有部门
     */
    private void collectDepartments(Long parentDeptId, List<Map<String, Object>> allDepts) {
        List<Map<String, Object>> subDepts = dingTalkApiService.getDepartmentList(parentDeptId);
        for (Map<String, Object> dept : subDepts) {
            allDepts.add(dept);
            Long deptId = (Long) dept.get("dept_id");
            // 递归获取子部门
            collectDepartments(deptId, allDepts);
        }
    }

    @Override
    public Map<String, Object> syncUsers() {
        log.info("[DingTalkSync] 开始同步用户...");

        int newCount = 0;
        int updateCount = 0;
        int totalUsers = 0;
        int bindCount = 0;
        int deptLinkedCount = 0;

        // 获取所有已同步的部门（dept_id > 0 的才有效）
        List<PmsDingTalkDeptDO> deptMappings = dingTalkDeptMapper.selectList(null);
        Set<String> processedDingUserIds = new HashSet<>();

        for (PmsDingTalkDeptDO deptMapping : deptMappings) {
            // 跳过无效的部门映射（dept_id=0 表示部门未成功创建）
            if (deptMapping.getDeptId() == null || deptMapping.getDeptId() <= 0) {
                continue;
            }

            Long systemDeptId = deptMapping.getDeptId();
            List<Map<String, Object>> users = dingTalkApiService.getUserListByDept(deptMapping.getDingtalkDeptId());

            for (Map<String, Object> dingUser : users) {
                String dingUserId = (String) dingUser.get("userid");
                if (StrUtil.isBlank(dingUserId) || processedDingUserIds.contains(dingUserId)) {
                    continue;
                }
                processedDingUserIds.add(dingUserId);
                totalUsers++;

                String name = (String) dingUser.get("name");
                String mobile = (String) dingUser.get("mobile");
                String email = (String) dingUser.get("email");
                String unionId = (String) dingUser.get("unionid");
                String avatar = (String) dingUser.get("avatar");
                String jobNumber = (String) dingUser.get("job_number");
                String title = (String) dingUser.get("title");
                String workPlace = (String) dingUser.get("work_place");

                // 查找现有映射
                PmsDingTalkUserDO existing = dingTalkUserMapper.selectByDingTalkUserId(dingUserId);

                if (existing != null) {
                    // 更新映射信息
                    existing.setName(name);
                    existing.setAvatar(avatar);
                    existing.setMobile(mobile);
                    existing.setEmail(email);
                    existing.setJobNumber(jobNumber);
                    existing.setTitle(title);
                    existing.setWorkPlace(workPlace);
                    existing.setDingtalkUnionId(unionId);
                    existing.setLastSyncTime(new Date());
                    dingTalkUserMapper.updateById(existing);

                    // 更新 system_users 基本信息 + 部门关联
                    if (existing.getUserId() != null && existing.getUserId() > 0) {
                        try {
                            jdbcTemplate.update(
                                    "UPDATE system_users SET nickname = ?, email = ?, mobile = ?, avatar = ?, dept_id = ? WHERE id = ?",
                                    name, email, mobile, avatar, systemDeptId, existing.getUserId());
                            deptLinkedCount++;
                        } catch (Exception e) {
                            log.warn("[DingTalkSync] 更新用户信息失败: userId={}, error={}",
                                    existing.getUserId(), e.getMessage());
                        }
                    }
                    updateCount++;
                } else {
                    // 尝试通过手机号匹配已有用户
                    Long systemUserId = null;
                    if (StrUtil.isNotBlank(mobile)) {
                        try {
                            systemUserId = jdbcTemplate.queryForObject(
                                    "SELECT id FROM system_users WHERE mobile = ? AND deleted = 0 LIMIT 1",
                                    Long.class, mobile);
                        } catch (Exception e) {
                            // 未找到匹配用户
                        }
                    }

                    // 如果没匹配到，创建新用户
                    if (systemUserId == null) {
                        String username = StrUtil.isNotBlank(mobile) ? mobile : "ding_" + dingUserId;
                        try {
                            KeyHolder keyHolder = new GeneratedKeyHolder();
                            jdbcTemplate.update(connection -> {
                                PreparedStatement ps = connection.prepareStatement(
                                        "INSERT INTO system_users (username, nickname, email, mobile, avatar, dept_id, status, " +
                                                "creator, create_time, updater, update_time, deleted, tenant_id) " +
                                                "VALUES (?, ?, ?, ?, ?, ?, 0, 'dingtalk_sync', NOW(), 'dingtalk_sync', NOW(), 0, 1)",
                                        Statement.RETURN_GENERATED_KEYS);
                                ps.setString(1, username);
                                ps.setString(2, name);
                                ps.setString(3, email);
                                ps.setString(4, mobile);
                                ps.setString(5, avatar);
                                ps.setLong(6, systemDeptId);
                                return ps;
                            }, keyHolder);

                            Number key = keyHolder.getKey();
                            if (key != null) {
                                systemUserId = key.longValue();
                            }
                            bindCount++;
                            deptLinkedCount++;
                        } catch (Exception e) {
                            log.error("[DingTalkSync] 创建用户失败: name={}, mobile={}, error={}",
                                    name, mobile, e.getMessage(), e);
                            continue;
                        }
                    } else {
                        // 已有用户，更新部门关联
                        try {
                            jdbcTemplate.update(
                                    "UPDATE system_users SET dept_id = ? WHERE id = ?",
                                    systemDeptId, systemUserId);
                        } catch (Exception e) {
                            log.warn("[DingTalkSync] 更新用户部门失败: userId={}", systemUserId);
                        }
                    }

                    // 创建映射记录
                    PmsDingTalkUserDO mapping = new PmsDingTalkUserDO();
                    mapping.setUserId(systemUserId);
                    mapping.setDingtalkUserId(dingUserId);
                    mapping.setDingtalkUnionId(unionId);
                    mapping.setName(name);
                    mapping.setAvatar(avatar);
                    mapping.setMobile(mobile);
                    mapping.setEmail(email);
                    mapping.setJobNumber(jobNumber);
                    mapping.setTitle(title);
                    mapping.setWorkPlace(workPlace);
                    mapping.setLastSyncTime(new Date());
                    dingTalkUserMapper.insert(mapping);
                    newCount++;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", totalUsers);
        result.put("new", newCount);
        result.put("updated", updateCount);
        result.put("autoBind", bindCount);
        result.put("deptLinked", deptLinkedCount);
        result.put("mobileCount", countUsersWithMobile());
        log.info("[DingTalkSync] 用户同步完成: {}", result);
        return result;
    }

    /**
     * 统计有手机号的用户数
     */
    private int countUsersWithMobile() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pms_dingtalk_user WHERE mobile IS NOT NULL AND mobile != '' AND deleted = 0",
                    Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public Map<String, Object> getSyncStatus() {
        Map<String, Object> status = new HashMap<>();

        // 统计映射数据
        Long totalDepts = dingTalkDeptMapper.selectCount(null);
        Long totalUsers = dingTalkUserMapper.selectCount(null);

        // 统计有效部门映射（dept_id > 0）
        try {
            Integer validDepts = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pms_dingtalk_dept WHERE dept_id > 0 AND deleted = 0", Integer.class);
            status.put("validSyncedDepts", validDepts);
        } catch (Exception e) {
            status.put("validSyncedDepts", 0);
        }

        // 获取最后同步时间
        try {
            String lastDeptSync = jdbcTemplate.queryForObject(
                    "SELECT MAX(last_sync_time) FROM pms_dingtalk_dept WHERE deleted = 0", String.class);
            String lastUserSync = jdbcTemplate.queryForObject(
                    "SELECT MAX(last_sync_time) FROM pms_dingtalk_user WHERE deleted = 0", String.class);

            status.put("totalSyncedDepts", totalDepts);
            status.put("totalSyncedUsers", totalUsers);
            status.put("lastDeptSync", lastDeptSync);
            status.put("lastUserSync", lastUserSync);
        } catch (Exception e) {
            status.put("error", e.getMessage());
        }

        PmsDingTalkConfigDO config = dingTalkApiService.getConfig();
        status.put("syncEnabled", config.getSyncEnabled());
        status.put("configStatus", config.getStatus());

        return status;
    }
}
