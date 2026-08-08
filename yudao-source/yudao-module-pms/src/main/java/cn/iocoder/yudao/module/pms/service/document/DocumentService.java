package cn.iocoder.yudao.module.pms.service.document;

import cn.iocoder.yudao.module.pms.dal.dataobject.document.PmsDocumentDO;

import java.util.List;

/**
 * 文档 Service 接口
 *
 * 【#7 改造】新增：
 * - getDocumentListByProject：按项目查文档
 * - getDocumentListForUser：按用户权限过滤的文档列表（后端过滤，不只前端藏）
 * - incrementDownloadCount：下载计数 +1
 */
public interface DocumentService {

    Long createDocument(PmsDocumentDO entity);

    void updateDocument(PmsDocumentDO entity);

    void deleteDocument(Long id);

    PmsDocumentDO getDocument(Long id);

    List<PmsDocumentDO> getDocumentList();

    // ========== #7 新增 ==========

    /**
     * 按项目ID查询文档列表
     */
    List<PmsDocumentDO> getDocumentListByProject(Long projectId);

    /**
     * 按用户权限过滤的文档列表（后端过滤）。
     * 规则：
     * - 超管/PMO/项目经理：看全部
     * - 其他用户：过滤掉无权查看的文档（private 非本人/非PM、role 非授权角色）
     *
     * @param projectId 项目ID
     * @param userId    当前用户ID
     * @return 过滤后的文档列表
     */
    List<PmsDocumentDO> getDocumentListForUser(Long projectId, Long userId);

    /**
     * 下载计数 +1
     */
    void incrementDownloadCount(Long docId);

}

