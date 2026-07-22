package cn.iocoder.yudao.module.pms.service.materialtrack.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.materialtrack.PmsMaterialTrackDO;
import cn.iocoder.yudao.module.pms.dal.mysql.materialtrack.MaterialTrackMapper;
import cn.iocoder.yudao.module.pms.service.materialtrack.MaterialTrackService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

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
        materialTrackMapper.updateById(entity);
    }

    @Override
    public void deleteMaterialTrack(Long id) {
        materialTrackMapper.deleteById(id);
    }

    @Override
    public PmsMaterialTrackDO getMaterialTrack(Long id) {
        return materialTrackMapper.selectById(id);
    }

    @Override
    public List<PmsMaterialTrackDO> getMaterialTrackList() {
        return materialTrackMapper.selectList(null);
    }

}