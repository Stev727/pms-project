package cn.iocoder.yudao.module.pms.service.dingtalk;

import java.util.List;
import java.util.Map;

/**
 * 钉钉通知服务
 * 基于通知规则引擎，支持模板变量替换、T-3提醒、延期升级
 */
public interface DingTalkNotifyService {

    /**
     * 根据通知规则发送通知
     *
     * @param ruleId       通知规则ID
     * @param templateVars 模板变量 (如 task_name, user_name, plan_end_date 等)
     * @param receiverUserIds 接收人系统用户ID列表
     * @return 发送结果
     */
    boolean sendNotifyByRule(Long ruleId, Map<String, Object> templateVars, List<Long> receiverUserIds);

    /**
     * 直接发送通知（不走规则引擎）
     *
     * @param title      标题
     * @param content    内容
     * @param receiverUserIds 接收人系统用户ID列表
     * @param triggerEvent 触发事件
     * @param businessType 业务类型
     * @param businessId   业务ID
     * @return 发送结果
     */
    boolean sendNotifyDirect(String title, String content, List<Long> receiverUserIds,
                             String triggerEvent, String businessType, Long businessId);

    /**
     * 执行每日通知检查
     * 扫描所有任务，根据通知规则触发 T-3 提醒、逾期升级等
     */
    void executeDailyNotifyCheck();

    /**
     * 渲染模板变量
     *
     * @param template 模板字符串（如 "您好 {user_name}，任务「{task_name}」即将到期"）
     * @param vars     变量 Map
     * @return 渲染后的字符串
     */
    String renderTemplate(String template, Map<String, Object> vars);
}
