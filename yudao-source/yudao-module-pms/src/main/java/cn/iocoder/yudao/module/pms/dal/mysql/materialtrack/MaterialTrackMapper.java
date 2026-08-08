package cn.iocoder.yudao.module.pms.dal.mysql.materialtrack;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.materialtrack.PmsMaterialTrackDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 物料跟踪 Mapper
 *
 * 改造说明（#10 物料跟踪嵌入项目详情）：
 *  - 新增 {@link #selectListByProjectId}：按项目过滤查询，供项目详情页物料 Tab 与 PMO 全局列表共用
 */
@Mapper
public interface MaterialTrackMapper extends BaseMapperX<PmsMaterialTrackDO> {

    /**
     * 按项目ID查询物料跟踪列表（#10 项目详情页物料 Tab 用）
     *  projectId 为 null 时返回全量（PMO 全视角走 Controller 传 null）
     */
    default List<PmsMaterialTrackDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<PmsMaterialTrackDO>()
                .eqIfPresent(PmsMaterialTrackDO::getProjectId, projectId)
                .orderByDesc(PmsMaterialTrackDO::getCreateTime));
    }

}

