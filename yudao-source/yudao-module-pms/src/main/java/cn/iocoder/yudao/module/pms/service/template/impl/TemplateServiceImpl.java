package cn.iocoder.yudao.module.pms.service.template.impl;

import cn.iocoder.yudao.module.pms.dal.dataobject.template.PmsTemplateDO;
import cn.iocoder.yudao.module.pms.dal.mysql.template.TemplateMapper;
import cn.iocoder.yudao.module.pms.service.template.TemplateService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

@Service
public class TemplateServiceImpl implements TemplateService {

    @Resource
    private TemplateMapper templateMapper;

    @Override
    public Long createTemplate(PmsTemplateDO entity) {
        templateMapper.insert(entity);
        return entity.getTemplateId();
    }

    @Override
    public void updateTemplate(PmsTemplateDO entity) {
        templateMapper.updateById(entity);
    }

    @Override
    public void deleteTemplate(Long id) {
        templateMapper.deleteById(id);
    }

    @Override
    public PmsTemplateDO getTemplate(Long id) {
        return templateMapper.selectById(id);
    }

    @Override
    public List<PmsTemplateDO> getTemplateList() {
        return templateMapper.selectList(null);
    }

}