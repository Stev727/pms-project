package cn.iocoder.yudao.module.pms.service.performancemonthly.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.performancemonthly.PmsPerformanceMonthlyDO;
import cn.iocoder.yudao.module.pms.dal.mysql.performancemonthly.PerformanceMonthlyMapper;
import cn.iocoder.yudao.module.pms.service.performancemonthly.PerformanceMonthlyService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class PerformanceMonthlyServiceImpl implements PerformanceMonthlyService {

    @Resource
    private PerformanceMonthlyMapper performanceMonthlyMapper;

    @Override
    public Long createPerformanceMonthly(PmsPerformanceMonthlyDO entity) {
        performanceMonthlyMapper.insert(entity);
        return entity.getPerformanceId();
    }

    @Override
    public void updatePerformanceMonthly(PmsPerformanceMonthlyDO entity) {
        performanceMonthlyMapper.updateById(entity);
    }

    @Override
    public void deletePerformanceMonthly(Long id) {
        performanceMonthlyMapper.deleteById(id);
    }

    @Override
    public PmsPerformanceMonthlyDO getPerformanceMonthly(Long id) {
        return performanceMonthlyMapper.selectById(id);
    }

    @Override
    public List<PmsPerformanceMonthlyDO> getPerformanceMonthlyList() {
        return performanceMonthlyMapper.selectList(null);
    }

}