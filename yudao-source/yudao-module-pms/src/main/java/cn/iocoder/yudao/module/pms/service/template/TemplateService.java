package cn.iocoder.yudao.module.pms.service.template;

import cn.iocoder.yudao.module.pms.dal.dataobject.template.PmsTemplateDO;
import java.util.List;

/**
 * 模板 Service 接口
 */
public interface TemplateService {

    Long createTemplate(PmsTemplateDO entity);

    void updateTemplate(PmsTemplateDO entity);

    void deleteTemplate(Long id);

    PmsTemplateDO getTemplate(Long id);

    List<PmsTemplateDO> getTemplateList();

}