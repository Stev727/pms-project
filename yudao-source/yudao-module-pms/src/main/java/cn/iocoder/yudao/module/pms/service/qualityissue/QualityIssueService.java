package cn.iocoder.yudao.module.pms.service.qualityissue;

import cn.iocoder.yudao.module.pms.controller.admin.qualityissue.vo.QualityIssueImportExcel;
import cn.iocoder.yudao.module.pms.controller.admin.qualityissue.vo.QualityIssueImportRespVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.qualityissue.PmsQualityIssueDO;

import java.util.List;

/**
 * 质量问题 Service 接口
 *
 * 改造说明（#8）：
 *  - 新增 {@link #importQualityIssueList}：Excel 整批校验 + 整批回滚 + 逐条触发责任人通知
 *  - 新增 {@link #getQualityIssueListByProjectId}：项目详情页质量 Tab 按项目拉取
 */
public interface QualityIssueService {

    Long createQualityIssue(PmsQualityIssueDO entity);

    void updateQualityIssue(PmsQualityIssueDO entity);

    void deleteQualityIssue(Long id);

    PmsQualityIssueDO getQualityIssue(Long id);

    List<PmsQualityIssueDO> getQualityIssueList();

    /**
     * 按项目ID查询质量问题列表（#8 项目详情页质量 Tab 用）
     */
    List<PmsQualityIssueDO> getQualityIssueListByProjectId(Long projectId);

    /**
     * Excel 批量导入质量问题（#8）
     *
     * 业务规则：
     *  1. 整批校验：姓名/任务名/必填项校验，任意一行错则整批不落库
     *  2. 整批回滚：插入阶段异常自动回滚（@Transactional）
     *  3. 逐条通知：批量插入成功且事务提交后，逐条触发责任人钉钉通知（通知失败仅告警，不影响业务数据）
     *
     * @param projectId 目标项目ID（导入归属项目）
     * @param rows     Excel 解析后的行数据
     * @return 导入结果（含失败行错误信息，供 Controller 生成错误 Excel 下载）
     */
    QualityIssueImportRespVO importQualityIssueList(Long projectId, List<QualityIssueImportExcel> rows);

}

