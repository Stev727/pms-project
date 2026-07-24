package cn.iocoder.yudao.module.pms.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {
    ErrorCode PROJECT_REQUIRED = new ErrorCode(1_040_000_000, "项目信息不能为空");
    ErrorCode TASK_OWNER_NOT_MEMBER = new ErrorCode(1_040_000_001, "任务负责人必须是项目成员");
    ErrorCode TASK_NOT_EXISTS = new ErrorCode(1_040_000_002, "任务不存在");
    ErrorCode TASK_STATUS_INVALID = new ErrorCode(1_040_000_003, "任务当前状态不允许此操作");
    ErrorCode CHANGE_NOT_EXISTS = new ErrorCode(1_040_000_004, "变更记录不存在");
    ErrorCode CHANGE_STATUS_INVALID = new ErrorCode(1_040_000_005, "变更当前状态不允许此操作");
    ErrorCode PROJECT_MANAGER_REQUIRED = new ErrorCode(1_040_000_006, "只有项目经理可以执行此操作");
}
