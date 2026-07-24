package cn.iocoder.yudao.module.pms.controller.admin.project.vo;

import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectmember.PmsProjectMemberDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectCreateBundleReqVO {
    private PmsProjectDO project;
    private List<PmsProjectMemberDO> members = new ArrayList<>();
    private List<PmsTaskDO> tasks = new ArrayList<>();
    private List<PmsNotifyRuleDO> notifyRules = new ArrayList<>();
}
