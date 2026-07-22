package cn.iocoder.yudao.module.pms.service.document.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.document.PmsDocumentDO;
import cn.iocoder.yudao.module.pms.dal.mysql.document.DocumentMapper;
import cn.iocoder.yudao.module.pms.service.document.DocumentService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class DocumentServiceImpl implements DocumentService {

    @Resource
    private DocumentMapper documentMapper;

    @Override
    public Long createDocument(PmsDocumentDO entity) {
        documentMapper.insert(entity);
        return entity.getDocumentId();
    }

    @Override
    public void updateDocument(PmsDocumentDO entity) {
        documentMapper.updateById(entity);
    }

    @Override
    public void deleteDocument(Long id) {
        documentMapper.deleteById(id);
    }

    @Override
    public PmsDocumentDO getDocument(Long id) {
        return documentMapper.selectById(id);
    }

    @Override
    public List<PmsDocumentDO> getDocumentList() {
        return documentMapper.selectList(null);
    }

}