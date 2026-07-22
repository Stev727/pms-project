package cn.iocoder.yudao.module.pms.job;

import cn.iocoder.yudao.module.pms.service.dingtalk.DingTalkNotifyService;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PMS 钉钉通知定时任务
 * 每日 09:00 执行通知检查（T-3 提醒、逾期升级等）
 */
@Component
@Slf4j
public class PmsDingTalkNotifyJob {

    @Resource
    private DingTalkNotifyService dingTalkNotifyService;

    /**
     * 每日 09:00 执行通知检查
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void executeDailyNotify() {
        log.info("[PmsDingTalkNotifyJob] 开始执行每日通知检查...");
        try {
            dingTalkNotifyService.executeDailyNotifyCheck();
        } catch (Exception e) {
            log.error("[PmsDingTalkNotifyJob] 每日通知检查异常", e);
        }
        log.info("[PmsDingTalkNotifyJob] 每日通知检查完成");
    }
}
