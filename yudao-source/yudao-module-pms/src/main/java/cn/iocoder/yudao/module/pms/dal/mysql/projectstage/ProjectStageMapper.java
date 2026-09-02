package cn.iocoder.yudao.module.pms.dal.mysql.projectstage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectstage.PmsProjectStageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProjectStageMapper extends BaseMapperX<PmsProjectStageDO> {

    /**
     * 查询项目全部阶段，按排序号升序（模板阶段任务导入用）
     */
    default List<PmsProjectStageDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<PmsProjectStageDO>()
                .eq(PmsProjectStageDO::getProjectId, projectId)
                .orderByAsc(PmsProjectStageDO::getSortOrder));
    }
}