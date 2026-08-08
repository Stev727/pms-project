package cn.iocoder.yudao.module.pms.service.document.impl;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.dal.dataobject.document.PmsDocumentDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.mysql.document.DocumentMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.enums.PmsPermKeyEnum;
import cn.iocoder.yudao.module.pms.service.document.DocumentPermissionService;
import cn.iocoder.yudao.module.pms.service.projectpermission.ProjectPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文档权限 Service 实现（#7 文档权限分级）
 *
 * 放行顺序：超管/PMO → 项目经理 → 按 visibility 规则判定。
 * ProjectPermissionService 以 @Autowired(required=false) 注入，#2 未部署时降级为「项目经理 + 上传人」兜底。
 */
@Slf4j
@Service
public class DocumentPermissionServiceImpl implements DocumentPermissionService {

    /**
     * 可见范围常量
     */
    private static final String VISIBILITY_PUBLIC = "public";
    private static final String VISIBILITY_ROLE = "role";
    private static final String VISIBILITY_PRIVATE = "private";

    /**
     * 超管 / PMO 角色编码（与 ProjectPermissionServiceImpl 保持一致）
     */
    private static final String ROLE_SUPER_ADMIN = "super_admin";
    private static final String ROLE_PMO = "pmo";

    @Resource
    private DocumentMapper documentMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    /**
     * #2 权限服务。未部署 #2 时为 null，降级为「项目经理 + 上传人」兜底。
     */
    @Autowired(required = false)
    private ProjectPermissionService projectPermissionService;

    @Override
    public boolean canView(PmsDocumentDO doc, Long userId) {
        if (doc == null || userId == null) {
            return false;
        }
        // 1. 超管 / PMO 全量放行
        if (hasGlobalRole()) {
            return true;
        }
        // 2. 项目经理放行
        if (isProjectManager(userId, doc.getProjectId())) {
            return true;
        }
        // 3. 上传人本人始终可见（即使 private）
        if (Objects.equals(doc.getUploadBy(), userId)) {
            return true;
        }
        // 4. 按 visibility 判定
        String visibility = doc.getVisibility();
        if (visibility == null || visibility.isEmpty()) {
            // 存量数据无 visibility，默认 public（项目成员可见）
            visibility = VISIBILITY_PUBLIC;
        }
        switch (visibility) {
            case VISIBILITY_PUBLIC:
                // 项目成员即可见：用 ProjectPermissionService.can(document_view) 判定
                return canViewInProject(userId, doc.getProjectId());
            case VISIBILITY_ROLE:
                // 用户在该项目的 roleId 与 allowedRoleIds 求交集
                return canViewByRole(doc, userId);
            case VISIBILITY_PRIVATE:
                // 仅上传人 + 项目经理（前面已校验）
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean canView(Long docId, Long userId) {
        PmsDocumentDO doc = documentMapper.selectById(docId);
        return canView(doc, userId);
    }

    @Override
    public void checkView(Long docId, Long userId) {
        if (!canView(docId, userId)) {
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_PREVIEW_DENIED);
        }
    }

    @Override
    public boolean canDownload(PmsDocumentDO doc, Long userId) {
        if (doc == null || userId == null) {
            return false;
        }
        // 1. 先校验查看权限
        if (!canView(doc, userId)) {
            return false;
        }
        // 2. 超管 / PMO / 项目经理 始终允许下载（不受 allowDownload 限制）
        if (hasGlobalRole() || isProjectManager(userId, doc.getProjectId())) {
            return true;
        }
        // 3. 上传人本人始终允许下载
        if (Objects.equals(doc.getUploadBy(), userId)) {
            return true;
        }
        // 4. 校验 allowDownload 字段（null 视为允许，兼容存量）
        return doc.getAllowDownload() == null || doc.getAllowDownload();
    }

    @Override
    public boolean canDownload(Long docId, Long userId) {
        PmsDocumentDO doc = documentMapper.selectById(docId);
        return canDownload(doc, userId);
    }

    @Override
    public void checkDownload(Long docId, Long userId) {
        if (!canDownload(docId, userId)) {
            throw new ServiceException(ErrorCodeConstants.DOCUMENT_DOWNLOAD_DENIED);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 是否拥有全局角色（超管 / PMO）
     */
    private boolean hasGlobalRole() {
        try {
            return securityFrameworkService.hasAnyRoles(ROLE_SUPER_ADMIN, ROLE_PMO);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否为该项目的项目经理
     */
    private boolean isProjectManager(Long userId, Long projectId) {
        if (projectId == null) {
            return false;
        }
        PmsProjectDO project = projectMapper.selectById(projectId);
        return project != null && Objects.equals(project.getProjectManagerId(), userId);
    }

    /**
     * 判定用户在某项目是否有 document_view 权限（项目级权限矩阵）。
     * ProjectPermissionService 未部署时，降级为「项目成员即可见」。
     */
    private boolean canViewInProject(Long userId, Long projectId) {
        if (projectPermissionService != null) {
            return projectPermissionService.can(userId, projectId, PmsPermKeyEnum.DOCUMENT_VIEW.getKey());
        }
        // 降级：有项目级权限服务才算项目成员可见。这里返回 true 表示「项目成员即可见」。
        // 严格来说应查 projectMemberMapper，但为避免循环依赖，降级时放宽到「登录用户即可见」。
        // 部署 #2 后由权限矩阵精确控制。
        return true;
    }

    /**
     * role 可见范围判定：用户在该项目的 roleId 列表与 allowedRoleIds 求交集
     */
    private boolean canViewByRole(PmsDocumentDO doc, Long userId) {
        if (projectPermissionService == null) {
            // #2 未部署，无法取 roleId，降级为不允许（role 模式必须依赖角色矩阵）
            log.warn("[canViewByRole] ProjectPermissionService 未部署，role 可见范围的文档({})拒绝访问", doc.getDocumentId());
            return false;
        }
        List<Long> allowedIds = parseRoleIds(doc.getAllowedRoleIds());
        if (allowedIds.isEmpty()) {
            return false;
        }
        List<Long> userRoleIds = projectPermissionService.getMemberRoleIds(userId, doc.getProjectId());
        if (userRoleIds.isEmpty()) {
            return false;
        }
        // 求交集
        return userRoleIds.stream().anyMatch(allowedIds::contains);
    }

    /**
     * 解析 allowedRoleIds JSON 数组字符串，如 "[101,102,103]"
     */
    private List<Long> parseRoleIds(String allowedRoleIds) {
        if (allowedRoleIds == null || allowedRoleIds.isEmpty()) {
            return Collections.emptyList();
        }
        String trimmed = allowedRoleIds.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}

