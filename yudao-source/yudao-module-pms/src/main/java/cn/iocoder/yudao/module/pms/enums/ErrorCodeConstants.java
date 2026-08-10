package cn.iocoder.yudao.module.pms.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    // ========== 存量错误码 000-008（原样保留，勿改） ==========
    ErrorCode PROJECT_REQUIRED = new ErrorCode(1_040_000_000, "项目信息不能为空");
    ErrorCode TASK_OWNER_NOT_MEMBER = new ErrorCode(1_040_000_001, "任务负责人必须是项目成员");
    ErrorCode TASK_NOT_EXISTS = new ErrorCode(1_040_000_002, "任务不存在");
    ErrorCode TASK_STATUS_INVALID = new ErrorCode(1_040_000_003, "任务当前状态不允许此操作");
    ErrorCode CHANGE_NOT_EXISTS = new ErrorCode(1_040_000_004, "变更记录不存在");
    ErrorCode CHANGE_STATUS_INVALID = new ErrorCode(1_040_000_005, "变更当前状态不允许此操作");
    ErrorCode PROJECT_MANAGER_REQUIRED = new ErrorCode(1_040_000_006, "只有项目经理可以执行此操作");
    ErrorCode DINGTALK_NOTIFY_FAILED = new ErrorCode(1_040_000_007, "钉钉通知发送失败，任务未派发");
    ErrorCode TASK_DATE_INVALID = new ErrorCode(1_040_000_008, "计划开始和计划结束必须同时填写，且结束日期不能早于开始日期");

    // ========== #2 项目权限分级 009-014 ==========
    ErrorCode PROJECT_PERMISSION_DENIED = new ErrorCode(1_040_000_009, "您在该项目内没有执行此操作的权限");
    ErrorCode PROJECT_ROLE_NOT_EXISTS = new ErrorCode(1_040_000_010, "项目角色不存在");
    ErrorCode PROJECT_ROLE_CODE_DUPLICATE = new ErrorCode(1_040_000_011, "该项目下角色编码已存在");
    ErrorCode PROJECT_ROLE_CODE_INVALID = new ErrorCode(1_040_000_012, "角色编码格式非法，需为小写字母开头的字母数字下划线组合");
    ErrorCode PROJECT_ROLE_SYSTEM_UNDELETABLE = new ErrorCode(1_040_000_013, "系统内置角色不允许删除");
    ErrorCode PROJECT_ROLE_IN_USE = new ErrorCode(1_040_000_014, "该角色仍有成员使用，请先调整成员角色");

    // ========== #1 子任务层级 015-018 ==========
    ErrorCode TASK_PARENT_NOT_EXISTS = new ErrorCode(1_040_000_015, "父任务不存在");
    ErrorCode TASK_LEVEL_EXCEED = new ErrorCode(1_040_000_016, "子任务层级超过上限（最多 3 级）");
    ErrorCode TASK_PARENT_CIRCULAR = new ErrorCode(1_040_000_017, "父任务设置存在循环引用");
    ErrorCode TASK_HAS_CHILDREN = new ErrorCode(1_040_000_018, "该任务存在子任务，请先删除子任务");

    // ========== #3 任务派发审核 019-021 ==========
    ErrorCode TASK_REVIEW_STATUS_INVALID = new ErrorCode(1_040_000_019, "任务当前审核状态不允许此操作");
    ErrorCode TASK_REVIEWER_REQUIRED = new ErrorCode(1_040_000_020, "任务未指定审核人，无法提交审核");
    ErrorCode TASK_REVIEW_COMMENT_REQUIRED = new ErrorCode(1_040_000_021, "驳回时必须填写驳回原因");
    ErrorCode TASK_ACCEPT_PERMISSION_DENIED = new ErrorCode(1_040_000_032, "只有任务负责人或项目管理人员可以接收任务");
    ErrorCode TASK_ACCEPT_SIGN_EXPIRED = new ErrorCode(1_040_000_033, "接收链接已过期，请重新获取");
    ErrorCode TASK_ACCEPT_SIGN_INVALID = new ErrorCode(1_040_000_034, "接收链接无效");
    ErrorCode TASK_REVIEW_ACTION_INVALID = new ErrorCode(1_040_000_035, "审核动作非法，仅支持 approve / reject");

    // ========== #6 文件预览 022-025 ==========
    ErrorCode DOCUMENT_NOT_EXISTS = new ErrorCode(1_040_000_022, "文档不存在");
    ErrorCode DOCUMENT_PREVIEW_DENIED = new ErrorCode(1_040_000_023, "您没有该文档的预览权限");
    ErrorCode DOCUMENT_PREVIEW_UNSUPPORTED = new ErrorCode(1_040_000_024, "该文件类型不支持在线预览，请下载后查看");
    ErrorCode DOCUMENT_CONVERT_FAILED = new ErrorCode(1_040_000_025, "文档转换失败，请稍后重试或下载原件");

    // ========== #7 文档权限 026 ==========
    ErrorCode DOCUMENT_DOWNLOAD_DENIED = new ErrorCode(1_040_000_026, "您没有该文档的下载权限");

    // ========== #8 质量问题导入 027-029 ==========
    ErrorCode QUALITY_IMPORT_FILE_EMPTY = new ErrorCode(1_040_000_027, "导入文件为空或无有效数据行");
    ErrorCode QUALITY_IMPORT_VALIDATE_FAILED = new ErrorCode(1_040_000_028, "导入数据校验未通过，请下载错误文件修正后重试");
    ErrorCode QUALITY_ISSUE_NOT_EXISTS = new ErrorCode(1_040_000_029, "质量问题不存在");

    // ========== #10 物料跟踪 030 ==========
    ErrorCode MATERIAL_NOT_EXISTS = new ErrorCode(1_040_000_030, "物料跟踪记录不存在");

    // ========== #4 消息提醒 031 ==========
    ErrorCode DINGTALK_TODO_FAILED = new ErrorCode(1_040_000_031, "钉钉待办创建失败");

}

