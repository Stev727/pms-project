package cn.iocoder.yudao.module.pms.service.changerecord;

import cn.iocoder.yudao.module.pms.dal.dataobject.changerecord.PmsChangeRecordDO;
import java.util.List;

/**
 * 变更记录 Service 接口
 */
public interface ChangeRecordService {

    Long createChangeRecord(PmsChangeRecordDO entity);

    void reviewChange(Long id, boolean approved, Long approverId);

    void executeApprovedChange(Long id);

    void updateChangeRecord(PmsChangeRecordDO entity);

    void deleteChangeRecord(Long id);

    PmsChangeRecordDO getChangeRecord(Long id);

    List<PmsChangeRecordDO> getChangeRecordList();

}