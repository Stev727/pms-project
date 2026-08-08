package cn.iocoder.yudao.module.pms.service.materialtrack;

import cn.iocoder.yudao.module.pms.dal.dataobject.materialtrack.PmsMaterialTrackDO;
import java.util.List;

/**
 * 物料跟踪 Service 接口
 *
 * 改造说明（#10 物料跟踪嵌入项目详情）：
 *  - {@link #getMaterialTrackList()} 改为 {@link #getMaterialTrackList(Long projectId)}，
 *    projectId 为 null 时返回全量（PMO 全局菜单走此路径）
 */
public interface MaterialTrackService {

    Long createMaterialTrack(PmsMaterialTrackDO entity);

    void updateMaterialTrack(PmsMaterialTrackDO entity);

    void deleteMaterialTrack(Long id);

    PmsMaterialTrackDO getMaterialTrack(Long id);

    /**
     * 按项目ID查询物料跟踪列表（#10）
     *  - projectId 非空：只返回该项目的物料（项目详情页物料 Tab 走此路径）
     *  - projectId 为空：返回全量（PMO 全局菜单 /pms/material 走此路径）
     */
    List<PmsMaterialTrackDO> getMaterialTrackList(Long projectId);

}

