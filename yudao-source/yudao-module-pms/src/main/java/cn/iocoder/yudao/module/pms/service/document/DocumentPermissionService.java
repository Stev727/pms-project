package cn.iocoder.yudao.module.pms.service.document;

import cn.iocoder.yudao.module.pms.dal.dataobject.document.PmsDocumentDO;

/**
 * 文档权限 Service 接口（#7 文档权限分级）
 *
 * 判定逻辑：
 * - public  → 项目成员即可看
 * - role    → 用户在该项目的 roleId 与 allowedRoleIds 求交集，非空即可看
 * - private → 仅 creator 本人 + 项目经理
 *
 * 下载额外校验 allowDownload 字段。
 *
 * 与 #2 权限分级的关系：
 * - 菜单级 checkPermi('pms:document:query') 决定能否进文档功能
 * - 项目级 ProjectPermissionService.can(userId, projectId, 'document_view') 决定在该项目能否看文档列表
 * - 文档级本 Service 决定能否看/下载某个具体文档（基于 visibility 配置）
 */
public interface DocumentPermissionService {

    /**
     * 判定某用户能否查看某文档
     *
     * @param doc    文档 DO（需含 projectId/visibility/allowedRoleIds/uploadBy）
     * @param userId 用户ID
     * @return true 允许
     */
    boolean canView(PmsDocumentDO doc, Long userId);

    /**
     * 判定某用户能否查看某文档（按 docId 查）
     */
    boolean canView(Long docId, Long userId);

    /**
     * 校验查看权限，无权限抛 DOCUMENT_PREVIEW_DENIED
     */
    void checkView(Long docId, Long userId);

    /**
     * 判定某用户能否下载某文档。
     * 下载需先通过查看权限，再校验 allowDownload。
     *
     * @param doc    文档 DO
     * @param userId 用户ID
     * @return true 允许
     */
    boolean canDownload(PmsDocumentDO doc, Long userId);

    /**
     * 判定某用户能否下载某文档（按 docId 查）
     */
    boolean canDownload(Long docId, Long userId);

    /**
     * 校验下载权限，无权限抛 DOCUMENT_DOWNLOAD_DENIED
     */
    void checkDownload(Long docId, Long userId);

}

