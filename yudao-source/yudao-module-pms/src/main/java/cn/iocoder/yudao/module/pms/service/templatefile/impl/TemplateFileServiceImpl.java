package cn.iocoder.yudao.module.pms.service.templatefile.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.templatefile.PmsTemplateFileDO;
import cn.iocoder.yudao.module.pms.dal.mysql.templatefile.TemplateFileMapper;
import cn.iocoder.yudao.module.pms.service.templatefile.TemplateFileService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class TemplateFileServiceImpl implements TemplateFileService {

    @Resource
    private TemplateFileMapper templateFileMapper;

    @Override
    public Long createTemplateFile(PmsTemplateFileDO entity) {
        templateFileMapper.insert(entity);
        return entity.getFileId();
    }

    @Override
    public void updateTemplateFile(PmsTemplateFileDO entity) {
        templateFileMapper.updateById(entity);
    }

    @Override
    public void deleteTemplateFile(Long id) {
        templateFileMapper.deleteById(id);
    }

    @Override
    public PmsTemplateFileDO getTemplateFile(Long id) {
        return templateFileMapper.selectById(id);
    }

    @Override
    public List<PmsTemplateFileDO> getTemplateFileList() {
        return templateFileMapper.selectList(null);
    }

}