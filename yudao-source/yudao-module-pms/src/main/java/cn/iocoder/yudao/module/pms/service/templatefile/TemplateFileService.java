package cn.iocoder.yudao.module.pms.service.templatefile;

import cn.iocoder.yudao.module.pms.dal.dataobject.templatefile.PmsTemplateFileDO;
import java.util.List;

/**
 * 模板附件 Service 接口
 */
public interface TemplateFileService {

    Long createTemplateFile(PmsTemplateFileDO entity);

    void updateTemplateFile(PmsTemplateFileDO entity);

    void deleteTemplateFile(Long id);

    PmsTemplateFileDO getTemplateFile(Long id);

    List<PmsTemplateFileDO> getTemplateFileList();

}