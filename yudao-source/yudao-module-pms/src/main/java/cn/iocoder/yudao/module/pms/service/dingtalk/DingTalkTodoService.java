package cn.iocoder.yudao.module.pms.service.dingtalk;

import java.util.List;

/**
 * 钉钉待办 Service（#4 钉钉待办）
 *
 * <p>调用新版钉钉 API：
 * <ul>
 *   <li>创建：{@code POST https://api.dingtalk.com/v1.0/todo/users/{unionId}/tasks}</li>
 *   <li>完成：{@code PUT  https://api.dingtalk.com/v1.0/todo/users/{unionId}/tasks/{taskId}}</li>
 * </ul>
 *
 * <p>鉴权方式：新版 API 使用 {@code x-acs-dingtalk-access-token} header。
 * 该 token 与老版 {@code /gettoken} 接口拿到的 access_token 是同一个值，
 * 直接复用 {@link DingTalkApiService#getAccessToken()} 即可。
 *
 * <p>失败降级：
 * <ul>
 *   <li>待办创建/完成失败不抛异常中断业务，仅记日志 + 落 pms_dingtalk_todo 失败记录</li>
 *   <li>调用方收到 false 时可继续后续逻辑</li>
 * </ul>
 */
public interface DingTalkTodoService {

    /**
     * 为某用户创建一条钉钉待办（关联到某 PMS 任务）。
     *
     * <p>幂等性：同一 (bizTaskId, userId) 已有"成功创建"记录时跳过，避免重复创建。
     *
     * @param bizTaskId 关联业务任务ID（pms_task.task_id）
     * @param userId    接收人系统用户ID
     * @param title     待办标题
     * @param content   待办内容
     * @return true 创建成功（含"已存在跳过"视为成功）；false 失败（已落 failed 记录）
     */
    boolean createTodoForTask(Long bizTaskId, Long userId, String title, String content);

    /**
     * 把某 PMS 任务关联的全部钉钉待办标记为完成。
     * 仅处理 status=pending 的记录，failed 的不会被处理（避免无效 RPC）。
     *
     * @param bizTaskId 业务任务ID
     * @return 实际成功完成的数量
     */
    int completeTodoByTask(Long bizTaskId);

    /**
     * 把某 PMS 任务关联、指定用户的钉钉待办标记为完成。
     * 用于"任务审核通过 → 标记负责人待办完成"。
     *
     * @param bizTaskId 业务任务ID
     * @param userId    系统用户ID
     * @return true 成功；false 失败（无记录或调用失败）
     */
    boolean completeTodoByTaskAndUser(Long bizTaskId, Long userId);

    /**
     * 查询某任务已创建的待办列表（运维/排错用）。
     */
    List<cn.iocoder.yudao.module.pms.dal.dataobject.dingtalk.PmsDingTalkTodoDO> getTodoListByTask(Long bizTaskId);

}

