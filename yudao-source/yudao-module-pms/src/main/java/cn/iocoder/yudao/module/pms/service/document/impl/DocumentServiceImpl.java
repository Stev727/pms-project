package cn.iocoder.yudao.module.pms.service.document.impl;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.dal.dataobject.document.PmsDocumentDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.mysql.document.DocumentMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.service.document.DocumentPermissionService;
import cn.iocoder.yudao.module.pms.service.document.DocumentService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class DocumentServiceImpl implements DocumentService {

    @Resource
    private DocumentMapper documentMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private DocumentPermissionService documentPermissionService;

    @Override
    public Long createDocument(PmsDocumentDO entity) {
        if (entity.getUploadTime() == null) {
            entity.setUploadTime(LocalDateTime.now());
        }
        if (entity.getDownloadCount() == null) {
            entity.setDownloadCount(0);
        }
        // #7：默认可见范围 public、允许下载
        if (entity.getVisibility() == null || entity.getVisibility().isEmpty()) {
            entity.setVisibility("public");
        }
        if (entity.getAllowDownload() == null) {
            entity.setAllowDownload(true);
        }
        if (entity.getUploadBy() == null) {
            entity.setUploadBy(SecurityFrameworkUtils.getLoginUserId());
        }
        documentMapper.insert(entity);
        return entity.getDocumentId();
    }

    @Override
    public void updateDocument(PmsDocumentDO entity) {
        documentMapper.updateById(entity);
    }

    @Override
    public void deleteDocument(Long id) {
        documentMapper.deleteById(id);
    }

    @Override
    public PmsDocumentDO getDocument(Long id) {
        return documentMapper.selectById(id);
    }

    @Override
    public List<PmsDocumentDO> getDocumentList() {
        return documentMapper.selectList(null);
    }

    // ========== #7 新增 ==========

    @Override
    public List<PmsDocumentDO> getDocumentListByProject(Long projectId) {
        return documentMapper.selectListByProjectId(projectId);
    }

    @Override
    public List<PmsDocumentDO> getDocumentListForUser(Long projectId, Long userId) {
        List<PmsDocumentDO> allDocs = documentMapper.selectListByProjectId(projectId);
        if (allDocs.isEmpty()) {
            return allDocs;
        }
        // 超管/PMO/项目经理 看全部
        if (isManagerLevel(userId, projectId)) {
            return allDocs;
        }
        // 普通用户按文档级权限过滤
        return allDocs.stream()
                .filter(doc -> documentPermissionService.canView(doc, userId))
                .collect(Collectors.toList());
    }

    @Override
    public void incrementDownloadCount(Long docId) {
        PmsDocumentDO doc = documentMapper.selectById(docId);
        if (doc == null) {
            return;
        }
        PmsDocumentDO update = new PmsDocumentDO();
        update.setDocumentId(docId);
        update.setDownloadCount((doc.getDownloadCount() == null ? 0 : doc.getDownloadCount()) + 1);
        documentMapper.updateById(update);
    }

    // ==================== 内部方法 ====================

    /**
     * 是否为管理员级别（超管 / PMO / 项目经理），可看全部文档
     */
    private boolean isManagerLevel(Long userId, Long projectId) {
        if (userId == null) {
            return false;
        }
        try {
            if (securityFrameworkService.hasAnyRoles("super_admin", "pmo")) {
                return true;
            }
        } catch (Exception e) {
            // 无登录上下文
        }
        PmsProjectDO project = projectMapper.selectById(projectId);
        return project != null && Objects.equals(project.getProjectManagerId(), userId);
    }

}

