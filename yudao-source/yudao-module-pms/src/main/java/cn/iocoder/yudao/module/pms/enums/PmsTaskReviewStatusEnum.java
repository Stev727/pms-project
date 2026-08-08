package cn.iocoder.yudao.module.pms.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 任务审核状态枚举（#3 任务派发审核）
 *
 * 状态机：
 *   none ──提交审核──▶ submitted ──通过──▶ completed
 *                          │
 *                          └──驳回（必填原因）──▶ rejected ──重新提交──▶ submitted
 *
 * 【与 complete_status 的关系】
 * pms_task.complete_status 是既有的「任务完成状态」，本枚举是新增的「审核状态」，两者并存。
 * TaskServiceImpl 在流转 review_status 时会同步 complete_status，映射关系：
 *   submitted → completion_pending_review
 *   completed → completed
 *   rejected  → in_progress
 * 以保证既有列表 / 看板 / 审核中心的状态标签不错乱。
 */
@Getter
@AllArgsConstructor
public enum PmsTaskReviewStatusEnum {

    /**
     * 未提交审核（初始态）
     */
    NONE("none", "未提交"),

    /**
     * 已提交，等待审核人处理
     */
    SUBMITTED("submitted", "待审核"),

    /**
     * 审核通过
     */
    COMPLETED("completed", "审核通过"),

    /**
     * 审核驳回
     */
    REJECTED("rejected", "已驳回"),
    ;

    /**
     * 落库值
     */
    private final String status;

    /**
     * 中文标签
     */
    private final String label;

    /**
     * 判断值是否合法
     */
    public static boolean isValid(String status) {
        if (status == null) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(e -> e.getStatus().equals(status));
    }

    /**
     * 取中文标签，未知值原样返回
     */
    public static String labelOf(String status) {
        return Arrays.stream(values())
                .filter(e -> e.getStatus().equals(status))
                .map(PmsTaskReviewStatusEnum::getLabel)
                .findFirst()
                .orElse(status);
    }

    /**
     * 是否允许「提交审核」：仅未提交 / 已驳回 可提交
     */
    public static boolean canSubmit(String status) {
        return status == null
                || NONE.getStatus().equals(status)
                || REJECTED.getStatus().equals(status);
    }

    /**
     * 是否允许「通过 / 驳回」：仅待审核可处理
     */
    public static boolean canReview(String status) {
        return SUBMITTED.getStatus().equals(status);
    }

}

