package cn.iocoder.yudao.module.pms.service.externalworklog.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.externalworklog.PmsExternalWorklogDO;
import cn.iocoder.yudao.module.pms.dal.mysql.externalworklog.ExternalWorklogMapper;
import cn.iocoder.yudao.module.pms.service.externalworklog.ExternalWorklogService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class ExternalWorklogServiceImpl implements ExternalWorklogService {

    @Resource
    private ExternalWorklogMapper externalWorklogMapper;

    @Override
    public Long createExternalWorklog(PmsExternalWorklogDO entity) {
        externalWorklogMapper.insert(entity);
        return entity.getWorklogId();
    }

    @Override
    public void updateExternalWorklog(PmsExternalWorklogDO entity) {
        externalWorklogMapper.updateById(entity);
    }

    @Override
    public void deleteExternalWorklog(Long id) {
        externalWorklogMapper.deleteById(id);
    }

    @Override
    public PmsExternalWorklogDO getExternalWorklog(Long id) {
        return externalWorklogMapper.selectById(id);
    }

    @Override
    public List<PmsExternalWorklogDO> getExternalWorklogList() {
        return externalWorklogMapper.selectList(null);
    }

}