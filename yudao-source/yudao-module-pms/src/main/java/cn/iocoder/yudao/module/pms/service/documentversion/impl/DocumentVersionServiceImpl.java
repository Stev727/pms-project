package cn.iocoder.yudao.module.pms.service.documentversion.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.documentversion.PmsDocumentVersionDO;
import cn.iocoder.yudao.module.pms.dal.mysql.documentversion.DocumentVersionMapper;
import cn.iocoder.yudao.module.pms.service.documentversion.DocumentVersionService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class DocumentVersionServiceImpl implements DocumentVersionService {

    @Resource
    private DocumentVersionMapper documentVersionMapper;

    @Override
    public Long createDocumentVersion(PmsDocumentVersionDO entity) {
        documentVersionMapper.insert(entity);
        return entity.getVersionId();
    }

    @Override
    public void updateDocumentVersion(PmsDocumentVersionDO entity) {
        documentVersionMapper.updateById(entity);
    }

    @Override
    public void deleteDocumentVersion(Long id) {
        documentVersionMapper.deleteById(id);
    }

    @Override
    public PmsDocumentVersionDO getDocumentVersion(Long id) {
        return documentVersionMapper.selectById(id);
    }

    @Override
    public List<PmsDocumentVersionDO> getDocumentVersionList() {
        return documentVersionMapper.selectList(null);
    }

}