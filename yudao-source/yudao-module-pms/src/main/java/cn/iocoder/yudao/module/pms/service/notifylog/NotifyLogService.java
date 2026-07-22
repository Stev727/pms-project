package cn.iocoder.yudao.module.pms.service.notifylog;

import cn.iocoder.yudao.module.pms.dal.dataobject.notifylog.PmsNotifyLogDO;
import java.util.List;

/**
 * 通知日志 Service 接口
 */
public interface NotifyLogService {

    Long createNotifyLog(PmsNotifyLogDO entity);

    void updateNotifyLog(PmsNotifyLogDO entity);

    void deleteNotifyLog(Long id);

    PmsNotifyLogDO getNotifyLog(Long id);

    List<PmsNotifyLogDO> getNotifyLogList();

}