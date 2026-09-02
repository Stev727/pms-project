package cn.iocoder.yudao.module.pms.service.template;

import cn.iocoder.yudao.module.pms.controller.admin.template.vo.TemplateStageTaskImportExcel;
import cn.iocoder.yudao.module.pms.controller.admin.template.vo.TemplateStageTaskImportRespVO;
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

    // ==================== 阶段任务 Excel 导入（模板=standard_template 项目） ====================

    /**
     * 生成模板阶段任务的导入模板行数据（预填当前模板已有阶段任务，便于增量修改）
     *
     * @param projectId 模板项目ID（project_type=standard_template）
     * @return Excel 行数据（当前无数据时返回示例行）
     */
    List<TemplateStageTaskImportExcel> getStageTaskTemplateRows(Long projectId);

    /**
     * Excel 批量导入模板阶段任务（全量覆盖：事务内软删现有阶段/任务后按文件重建）
     *
     * @param projectId 模板项目ID（project_type=standard_template）
     * @param rows      解析后的 Excel 行
     * @return 校验结果：失败时含错误行明细，成功时含重建数量
     */
    TemplateStageTaskImportRespVO importStageTask(Long projectId, List<TemplateStageTaskImportExcel> rows);
}
