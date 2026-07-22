package cn.iocoder.yudao.module.pms.service.materialtrack;

import cn.iocoder.yudao.module.pms.dal.dataobject.materialtrack.PmsMaterialTrackDO;
import java.util.List;

/**
 * 物料跟踪 Service 接口
 */
public interface MaterialTrackService {

    Long createMaterialTrack(PmsMaterialTrackDO entity);

    void updateMaterialTrack(PmsMaterialTrackDO entity);

    void deleteMaterialTrack(Long id);

    PmsMaterialTrackDO getMaterialTrack(Long id);

    List<PmsMaterialTrackDO> getMaterialTrackList();

}