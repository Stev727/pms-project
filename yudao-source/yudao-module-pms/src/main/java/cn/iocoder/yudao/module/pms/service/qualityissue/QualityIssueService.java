package cn.iocoder.yudao.module.pms.service.qualityissue;

import cn.iocoder.yudao.module.pms.dal.dataobject.qualityissue.PmsQualityIssueDO;
import java.util.List;

/**
 * 质量问题 Service 接口
 */
public interface QualityIssueService {

    Long createQualityIssue(PmsQualityIssueDO entity);

    void updateQualityIssue(PmsQualityIssueDO entity);

    void deleteQualityIssue(Long id);

    PmsQualityIssueDO getQualityIssue(Long id);

    List<PmsQualityIssueDO> getQualityIssueList();

}