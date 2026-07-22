package cn.iocoder.yudao.module.pms.service.externalworklog;

import cn.iocoder.yudao.module.pms.dal.dataobject.externalworklog.PmsExternalWorklogDO;
import java.util.List;

/**
 * 外部工时 Service 接口
 */
public interface ExternalWorklogService {

    Long createExternalWorklog(PmsExternalWorklogDO entity);

    void updateExternalWorklog(PmsExternalWorklogDO entity);

    void deleteExternalWorklog(Long id);

    PmsExternalWorklogDO getExternalWorklog(Long id);

    List<PmsExternalWorklogDO> getExternalWorklogList();

}