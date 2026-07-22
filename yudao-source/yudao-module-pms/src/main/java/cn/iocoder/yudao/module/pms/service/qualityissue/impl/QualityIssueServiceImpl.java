package cn.iocoder.yudao.module.pms.service.qualityissue.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.qualityissue.PmsQualityIssueDO;
import cn.iocoder.yudao.module.pms.dal.mysql.qualityissue.QualityIssueMapper;
import cn.iocoder.yudao.module.pms.service.qualityissue.QualityIssueService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class QualityIssueServiceImpl implements QualityIssueService {

    @Resource
    private QualityIssueMapper qualityIssueMapper;

    @Override
    public Long createQualityIssue(PmsQualityIssueDO entity) {
        qualityIssueMapper.insert(entity);
        return entity.getIssueId();
    }

    @Override
    public void updateQualityIssue(PmsQualityIssueDO entity) {
        qualityIssueMapper.updateById(entity);
    }

    @Override
    public void deleteQualityIssue(Long id) {
        qualityIssueMapper.deleteById(id);
    }

    @Override
    public PmsQualityIssueDO getQualityIssue(Long id) {
        return qualityIssueMapper.selectById(id);
    }

    @Override
    public List<PmsQualityIssueDO> getQualityIssueList() {
        return qualityIssueMapper.selectList(null);
    }

}