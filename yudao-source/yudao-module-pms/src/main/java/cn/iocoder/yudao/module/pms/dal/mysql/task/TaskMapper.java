package cn.iocoder.yudao.module.pms.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 任务 Mapper
 *
 * ============================ 改造说明 ============================
 * 版本：v2（原文件是空接口，本版新增 default 查询方法，未引入 XML）
 * 改造内容：
 *   【#1 子任务层级】selectListByParentTaskId / selectCountByParentTaskId
 *                   / selectListByParentTaskIds
 *   【#3 任务派发审核】selectListByReviewer / selectListByProjectAndReviewStatus
 *
 * 【踩坑提醒】LambdaQueryWrapperX 的 .and()/.or() 返回父类 LambdaQueryWrapper，
 *            不能继续链式调用 X 扩展方法，本文件内所有查询都刻意避开了 and/or。
 * ==================================================================
 */
@Mapper
public interface TaskMapper extends BaseMapperX<PmsTaskDO> {

    /**
     * 查询某个父任务下的直接子任务，按排序号升序
     */
    default List<PmsTaskDO> selectListByParentTaskId(Long parentTaskId) {
        return selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                .eq(PmsTaskDO::getParentTaskId, parentTaskId)
                .orderByAsc(PmsTaskDO::getSortOrder)
                .orderByAsc(PmsTaskDO::getTaskId));
    }

    /**
     * 统计某个父任务下的直接子任务数量。用于删除父任务前的校验
     */
    default Long selectCountByParentTaskId(Long parentTaskId) {
        return selectCount(new LambdaQueryWrapperX<PmsTaskDO>()
                .eq(PmsTaskDO::getParentTaskId, parentTaskId));
    }

    /**
     * 批量查询多个父任务下的子任务。用于一次性构建任务树，避免 N+1 查询
     */
    default List<PmsTaskDO> selectListByParentTaskIds(List<Long> parentTaskIds) {
        if (parentTaskIds == null || parentTaskIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                .in(PmsTaskDO::getParentTaskId, parentTaskIds)
                .orderByAsc(PmsTaskDO::getSortOrder)
                .orderByAsc(PmsTaskDO::getTaskId));
    }

    /**
     * 查询某项目下的全部任务，按排序号升序
     */
    default List<PmsTaskDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                .eq(PmsTaskDO::getProjectId, projectId)
                .orderByAsc(PmsTaskDO::getSortOrder)
                .orderByAsc(PmsTaskDO::getTaskId));
    }

    /**
     * 查询「待某人审核」的任务列表
     *
     * @param reviewerId   审核人ID
     * @param projectId    项目ID，可为空表示全部项目
     * @param reviewStatus 审核状态，可为空表示全部状态
     */
    default List<PmsTaskDO> selectListByReviewer(Long reviewerId, Long projectId, String reviewStatus) {
        return selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                .eqIfPresent(PmsTaskDO::getReviewerId, reviewerId)
                .eqIfPresent(PmsTaskDO::getProjectId, projectId)
                .eqIfPresent(PmsTaskDO::getReviewStatus, reviewStatus)
                .orderByDesc(PmsTaskDO::getUpdateTime));
    }

    /**
     * 按项目 + 审核状态查询任务，供审核中心统计使用
     */
    default List<PmsTaskDO> selectListByProjectAndReviewStatus(Long projectId, String reviewStatus) {
        return selectList(new LambdaQueryWrapperX<PmsTaskDO>()
                .eqIfPresent(PmsTaskDO::getProjectId, projectId)
                .eqIfPresent(PmsTaskDO::getReviewStatus, reviewStatus)
                .orderByAsc(PmsTaskDO::getSortOrder)
                .orderByAsc(PmsTaskDO::getTaskId));
    }

    /**
     * 部门审核中心：查询待审核的日常任务（project_id 为 NULL 表示非项目任务）。
     * reviewerId 为 NULL 时查询全部日常待审任务（超管视角）；否则仅返回指定审核人的任务。
     */
    default List<PmsTaskDO> selectDeptReviewTasks(Long reviewerId, String reviewStatus) {
        LambdaQueryWrapperX<PmsTaskDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.isNull(PmsTaskDO::getProjectId)
                .eq(PmsTaskDO::getReviewStatus, reviewStatus)
                .orderByAsc(PmsTaskDO::getPlanStartDate)
                .orderByAsc(PmsTaskDO::getSortOrder);
        if (reviewerId != null) {
            wrapper.eq(PmsTaskDO::getReviewerId, reviewerId);
        }
        return selectList(wrapper);
    }

}

