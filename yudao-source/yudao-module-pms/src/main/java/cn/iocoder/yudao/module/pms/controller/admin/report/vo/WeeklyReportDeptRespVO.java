package cn.iocoder.yudao.module.pms.controller.admin.report.vo;

import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 周报报表 - 可见部门列表响应（含登录人默认部门）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeeklyReportDeptRespVO {

    /** 当前用户可见部门列表（按数据范围过滤：超管全部 / 负责人本部门+下级 / 普通人本部门+下级） */
    private List<DeptRespDTO> depts;

    /** 登录人所在部门ID（前端默认选中用；可能为空） */
    private Long myDeptId;
}
