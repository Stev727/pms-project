package cn.iocoder.yudao.module.pms.service.materialtrack.impl;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.dal.dataobject.materialtrack.PmsMaterialTrackDO;
import cn.iocoder.yudao.module.pms.dal.mysql.materialtrack.MaterialTrackMapper;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.service.materialtrack.MaterialTrackService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

/**
 * 物料跟踪 Service 实现
 *
 * 改造说明（#10 物料跟踪嵌入项目详情）：
 *  - getMaterialTrackList 增加 projectId 参数；非空走 selectListByProjectId，空走 selectList(null)
 *  - deleteMaterialTrack 增加存在性校验，缺失抛 MATERIAL_NOT_EXISTS
 */
@Service
public class MaterialTrackServiceImpl implements MaterialTrackService {

    @Resource
    private MaterialTrackMapper materialTrackMapper;

    @Override
    public Long createMaterialTrack(PmsMaterialTrackDO entity) {
        materialTrackMapper.insert(entity);
        return entity.getTrackId();
    }

    @Override
    public void updateMaterialTrack(PmsMaterialTrackDO entity) {
        validateMaterialTrackExists(entity.getTrackId());
        materialTrackMapper.updateById(entity);
    }

    @Override
    public void deleteMaterialTrack(Long id) {
        validateMaterialTrackExists(id);
        materialTrackMapper.deleteById(id);
    }

    @Override
    public PmsMaterialTrackDO getMaterialTrack(Long id) {
        return materialTrackMapper.selectById(id);
    }

    @Override
    public List<PmsMaterialTrackDO> getMaterialTrackList(Long projectId) {
        if (projectId == null) {
            return materialTrackMapper.selectList(null);
        }
        return materialTrackMapper.selectListByProjectId(projectId);
    }

    private void validateMaterialTrackExists(Long id) {
        if (id == null || materialTrackMapper.selectById(id) == null) {
            throw new ServiceException(ErrorCodeConstants.MATERIAL_NOT_EXISTS);
        }
    }

}

