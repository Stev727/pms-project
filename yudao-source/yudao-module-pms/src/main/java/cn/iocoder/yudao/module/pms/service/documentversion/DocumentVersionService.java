package cn.iocoder.yudao.module.pms.service.documentversion;

import cn.iocoder.yudao.module.pms.dal.dataobject.documentversion.PmsDocumentVersionDO;
import java.util.List;

/**
 * 文档版本 Service 接口
 */
public interface DocumentVersionService {

    Long createDocumentVersion(PmsDocumentVersionDO entity);

    void updateDocumentVersion(PmsDocumentVersionDO entity);

    void deleteDocumentVersion(Long id);

    PmsDocumentVersionDO getDocumentVersion(Long id);

    List<PmsDocumentVersionDO> getDocumentVersionList();

}