package cn.iocoder.yudao.module.pms.service.document;

import cn.iocoder.yudao.module.pms.dal.dataobject.document.PmsDocumentDO;
import java.util.List;

/**
 * 文档 Service 接口
 */
public interface DocumentService {

    Long createDocument(PmsDocumentDO entity);

    void updateDocument(PmsDocumentDO entity);

    void deleteDocument(Long id);

    PmsDocumentDO getDocument(Long id);

    List<PmsDocumentDO> getDocumentList();

}