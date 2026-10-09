package cn.iocoder.yudao.module.pms.service.report;

import cn.iocoder.yudao.module.pms.controller.admin.report.vo.WeeklyReportDeptRespVO;
import cn.iocoder.yudao.module.pms.controller.admin.report.vo.WeeklyReportExportResult;
import cn.iocoder.yudao.module.pms.controller.admin.report.vo.WeeklyReportPageReqVO;
import cn.iocoder.yudao.module.pms.controller.admin.report.vo.WeeklyReportRowVO;

import java.util.List;

/**
 * 周报报表 Service 接口
 *
 * 与现有「周报看板」完全独立：不复用 TaskService，自己持有数据查询逻辑。
 * 设计目标：按部门 + 基准日期 + 人员维度，扁平化展示/导出周报任务。
 *
 * 维度优先级：deptId > userId > 本人
 */
public interface WeeklyReportService {

    /**
     * 分页查询周报报表数据（5 区块扁平化为 List<RowVO>，前端表格用）
     *
     * @param req 分页参数 + 筛选条件
     * @return 当前页数据
     */
    List<WeeklyReportRowVO> getWeeklyReportPage(WeeklyReportPageReqVO req);

    /**
     * 导出周报报表 Excel（5 区块扁平化为 List<ExportVO>，单 Sheet）
     *
     * @param date   基准日期（为空=今天）
     * @param deptId 部门ID（包含下级）；为空时按 userId 走
     * @param userId 人员ID（0=全部仅管理员；空=本人）
     * @return 导出结果（含文件名 + 数据）
     */
    WeeklyReportExportResult exportWeeklyReport(java.time.LocalDate date, Long deptId, Long userId);

    /**
     * 获取当前用户可见部门列表 + 登录人所在部门（前端筛选器用）。
     * 权限走报表自身（pms:task:query），不依赖 BI 看板权限。
     *
     * @return depts = 按数据范围过滤后的部门列表；myDeptId = 登录人所在部门ID（可空）
     */
    WeeklyReportDeptRespVO getVisibleDepts();
}
