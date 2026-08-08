package cn.iocoder.yudao.module.pms.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 任务审核策略枚举（#3 任务派发审核）
 *
 * 生效优先级：任务级 pms_task.review_policy > 项目级 pms_project.review_policy > 默认 need_review
 *
 * - need_review  提交后进入待审核，由审核人通过/驳回（默认，与线上既有行为一致）
 * - self_review  提交即视为自审通过，直接置为已完成（适用于个人事务型任务）
 * - skip         跳过审核环节，等价于 self_review，语义上表示「该项目不做任务审核」
 */
@Getter
@AllArgsConstructor
public enum PmsReviewPolicyEnum {

    /**
     * 需要审核人审核
     */
    NEED_REVIEW("need_review", "需要审核"),

    /**
     * 提交即自审通过
     */
    SELF_REVIEW("self_review", "提交即通过"),

    /**
     * 跳过审核
     */
    SKIP("skip", "跳过审核"),
    ;

    /**
     * 落库值
     */
    private final String policy;

    /**
     * 中文标签
     */
    private final String label;

    /**
     * 判断值是否合法
     */
    public static boolean isValid(String policy) {
        if (policy == null) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(e -> e.getPolicy().equals(policy));
    }

    /**
     * 归一化：非法 / 为空一律回落到 need_review，保证线上行为不被脏数据改变
     */
    public static String normalize(String policy) {
        return isValid(policy) ? policy : NEED_REVIEW.getPolicy();
    }

    /**
     * 该策略是否需要走审核人环节
     */
    public static boolean needReview(String policy) {
        return NEED_REVIEW.getPolicy().equals(normalize(policy));
    }

}

