package cn.iocoder.yudao.module.pms.dal.mysql.qualityissue;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.qualityissue.PmsQualityIssueDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 质量问题 Mapper
 *
 * 改造说明（#8）：
 *  - 新增 selectListByProjectId：项目详情页「质量」Tab 按项目拉取列表
 *  - 新增 selectByProjectIdAndTaskName：导入时按项目+任务名匹配任务（实际查的是 PmsTaskDO，此处不暴露）
 *  - 沿用 BaseMapperX.insertBatch 进行批量插入（见 ServiceImpl）
 */
@Mapper
public interface QualityIssueMapper extends BaseMapperX<PmsQualityIssueDO> {

    /**
     * 按项目ID查询质量问题列表（#8 项目详情页质量 Tab 用）
     */
    default List<PmsQualityIssueDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<PmsQualityIssueDO>()
                .eqIfPresent(PmsQualityIssueDO::getProjectId, projectId)
                .orderByDesc(PmsQualityIssueDO::getCreateTime));
    }

}

