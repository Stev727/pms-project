package cn.iocoder.yudao.module.pms.service.performancemonthly;

import cn.iocoder.yudao.module.pms.dal.dataobject.performancemonthly.PmsPerformanceMonthlyDO;
import java.util.List;

/**
 * 月度绩效 Service 接口
 */
public interface PerformanceMonthlyService {

    Long createPerformanceMonthly(PmsPerformanceMonthlyDO entity);

    void updatePerformanceMonthly(PmsPerformanceMonthlyDO entity);

    void deletePerformanceMonthly(Long id);

    PmsPerformanceMonthlyDO getPerformanceMonthly(Long id);

    List<PmsPerformanceMonthlyDO> getPerformanceMonthlyList();

}