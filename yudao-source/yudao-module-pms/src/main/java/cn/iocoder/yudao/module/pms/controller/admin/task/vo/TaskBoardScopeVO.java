package cn.iocoder.yudao.module.pms.controller.admin.task.vo;

import lombok.Data;

import java.util.List;

/**
 * 我的任务看板：当前登录用户可查看的人员范围（权限判定结果）
 *
 * 权限模型（三级）：
 *   - 管理员（拥有 pms:board:admin 权限点）：可查看所有人任务，allowedUserIds 为 null 表示「全部」
 *   - 领导（当前用户有下属）：可查看本人 + 其递归下属，allowedUserIds = [本人, 下属...]
 *   - 非领导（无下属且非管理员）：仅可查看本人，allowedUserIds = [本人]
 *
 * 前端据此限制「人员」下拉可选范围，并决定默认视图；后端 boardQuery 也按此做安全兜底。
 */
@Data
public class TaskBoardScopeVO {

    /**
     * 是否管理员（拥有 pms:board:admin 权限点）
     */
    private Boolean isAdmin;

    /**
     * 是否领导（当前用户存在下属）；领导可查看本人 + 下属
     */
    private Boolean isLeader;

    /**
     * 当前登录用户ID
     */
    private Long loginUserId;

    /**
     * 允许查看的人员ID集合；null 表示「全部」（管理员）
     */
    private List<Long> allowedUserIds;
}
