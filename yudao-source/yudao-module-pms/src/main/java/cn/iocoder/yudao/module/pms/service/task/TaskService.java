package cn.iocoder.yudao.module.pms.service.task;

import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import java.util.List;

/**
 * 任务 Service 接口
 */
public interface TaskService {

    Long createTask(PmsTaskDO entity);

    void simulateDingtalkConfirm(Long taskId);

    void dispatchTask(Long taskId);

    void submitCompletion(Long taskId, String actualCompleteDate, String completionNote);

    void reviewCompletion(Long taskId, boolean approved, Long operatorId);

    void updateTask(PmsTaskDO entity);

    void deleteTask(Long id);

    PmsTaskDO getTask(Long id);

    /**
     * 获取任务列表（含权限过滤）
     * 非管理员只能看到自己作为主责任人或协助人的任务
     */
    List<PmsTaskDO> getTaskList();

    /**
     * 获取任务列表（含权限过滤）
     * @param mainOwnerId 主责任人ID（可选）
     * @param projectId 项目ID（可选）
     * @param projectType 项目类型（可选，传 standard_template 时不过滤）
     */
    List<PmsTaskDO> getTaskList(Long mainOwnerId, Long projectId, String projectType);

}
