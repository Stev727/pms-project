package cn.iocoder.yudao.module.pms.controller.admin.notifymode;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.controller.admin.notifymode.vo.NotifyModeRespVO;
import cn.iocoder.yudao.module.pms.controller.admin.notifymode.vo.NotifyModeSaveReqVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifymode.PmsNotifyModeDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.notifyrule.PmsNotifyRuleDO;
import cn.iocoder.yudao.module.pms.dal.mysql.notifymode.NotifyModeMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.notifyrule.NotifyRuleMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 通知模式")
@RestController
@RequestMapping("/pms/notify-mode")
public class NotifyModeController {
    @Resource private NotifyModeMapper modeMapper;
    @Resource private NotifyRuleMapper ruleMapper;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('pms:notify:query')")
    public CommonResult<List<PmsNotifyModeDO>> list() {
        return success(modeMapper.selectList(null));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('pms:notify:query')")
    public CommonResult<NotifyModeRespVO> get(@RequestParam Long id) {
        NotifyModeRespVO result = new NotifyModeRespVO();
        result.setMode(modeMapper.selectById(id));
        result.setRules(ruleMapper.selectList(PmsNotifyRuleDO::getModeId, id));
        return success(result);
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('pms:notify:create')")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Long> create(@RequestBody NotifyModeSaveReqVO request) {
        PmsNotifyModeDO mode = request.getMode();
        modeMapper.insert(mode);
        saveRules(mode.getModeId(), request.getRules());
        return success(mode.getModeId());
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('pms:notify:update')")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Boolean> update(@RequestBody NotifyModeSaveReqVO request) {
        modeMapper.updateById(request.getMode());
        ruleMapper.delete(PmsNotifyRuleDO::getModeId, request.getMode().getModeId());
        saveRules(request.getMode().getModeId(), request.getRules());
        return success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('pms:notify:delete')")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Boolean> delete(@RequestParam Long id) {
        ruleMapper.delete(PmsNotifyRuleDO::getModeId, id);
        modeMapper.deleteById(id);
        return success(true);
    }

    private void saveRules(Long modeId, List<PmsNotifyRuleDO> rules) {
        if (rules == null) return;
        for (PmsNotifyRuleDO rule : rules) {
            rule.setRuleId(null);
            rule.setModeId(modeId);
            rule.setSourceModeId(null);
            rule.setScopeType("mode");
            rule.setProjectId(null);
            rule.setTaskId(null);
            ruleMapper.insert(rule);
        }
    }
}
