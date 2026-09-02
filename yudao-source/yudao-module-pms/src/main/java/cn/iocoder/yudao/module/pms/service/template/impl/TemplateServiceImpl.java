package cn.iocoder.yudao.module.pms.service.template.impl;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.controller.admin.template.vo.TemplateStageTaskImportErrorExcel;
import cn.iocoder.yudao.module.pms.controller.admin.template.vo.TemplateStageTaskImportExcel;
import cn.iocoder.yudao.module.pms.controller.admin.template.vo.TemplateStageTaskImportRespVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.project.PmsProjectDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.projectstage.PmsProjectStageDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.dal.dataobject.template.PmsTemplateDO;
import cn.iocoder.yudao.module.pms.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.projectstage.ProjectStageMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.template.TemplateMapper;
import cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pms.service.template.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 模板 Service 实现
 *
 * ============================ 改造说明 ============================
 * 新增（模板阶段任务 Excel 批量导入）：
 *  - getStageTaskTemplateRows：导出当前模板的阶段任务为 Excel 行（模板预填）
 *  - importStageTask：全量覆盖导入（事务内软删现有阶段/任务 → 按文件重建）
 *  模板 = project_type='standard_template' 的 pms_project，
 *  阶段存 pms_project_stage、任务存 pms_task（与前端模板管理页一致）
 * ==================================================================
 */
@Slf4j
@Service
public class TemplateServiceImpl implements TemplateService {

    /** 模板项目类型标识（pms_project.project_type） */
    private static final String TEMPLATE_PROJECT_TYPE = "standard_template";

    /** 任务类型：Excel 输入（中文全称/简称/英文值，去空格） → 字典 value */
    private static final Map<String, String> TASK_TYPE_ALIAS;

    /** 任务类型：字典 value → Excel 中文标签（模板下载预填时用） */
    private static final Map<String, String> TASK_TYPE_LABEL;

    static {
        Map<String, String> alias = new HashMap<>();
        alias.put("设计任务", "design"); alias.put("设计", "design"); alias.put("design", "design");
        alias.put("评审任务", "review"); alias.put("评审", "review"); alias.put("review", "review");
        alias.put("测试任务", "testing"); alias.put("测试", "testing"); alias.put("testing", "testing");
        alias.put("采购任务", "procurement"); alias.put("采购", "procurement"); alias.put("procurement", "procurement");
        alias.put("试制任务", "prototyping"); alias.put("试制", "prototyping"); alias.put("打样", "prototyping"); alias.put("prototyping", "prototyping");
        alias.put("文档任务", "documentation"); alias.put("文档", "documentation"); alias.put("documentation", "documentation");
        alias.put("审批任务", "approval"); alias.put("审批", "approval"); alias.put("approval", "approval");
        alias.put("供应商协同", "supplier_synergy"); alias.put("供方协同", "supplier_synergy"); alias.put("supplier_synergy", "supplier_synergy");
        alias.put("其他", "other"); alias.put("other", "other");
        TASK_TYPE_ALIAS = Collections.unmodifiableMap(alias);

        Map<String, String> label = new HashMap<>();
        label.put("design", "设计任务"); label.put("review", "评审任务"); label.put("testing", "测试任务");
        label.put("procurement", "采购任务"); label.put("prototyping", "试制任务"); label.put("documentation", "文档任务");
        label.put("approval", "审批任务"); label.put("supplier_synergy", "供应商协同"); label.put("other", "其他");
        TASK_TYPE_LABEL = Collections.unmodifiableMap(label);
    }

    @Resource
    private TemplateMapper templateMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ProjectStageMapper projectStageMapper;
    @Resource
    private TaskMapper taskMapper;

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

    // ==================== 阶段任务 Excel 导入 ====================

    @Override
    public List<TemplateStageTaskImportExcel> getStageTaskTemplateRows(Long projectId) {
        // 1. 校验目标项目是模板
        validateTemplateProject(projectId);

        // 2. 查现有阶段（按 sortOrder）与任务（按 stageId 分组、sortOrder 升序）
        List<PmsProjectStageDO> stages = projectStageMapper.selectListByProjectId(projectId);
        List<PmsTaskDO> tasks = taskMapper.selectListByProjectId(projectId);
        Map<Long, List<PmsTaskDO>> tasksByStage = tasks.stream()
                .filter(t -> t.getStageId() != null)
                .collect(Collectors.groupingBy(PmsTaskDO::getStageId));

        // 3. 组装行（阶段序号 = stage.sortOrder，任务序号 = task.sortOrder）
        List<TemplateStageTaskImportExcel> rows = new ArrayList<>();
        for (PmsProjectStageDO stage : stages) {
            List<PmsTaskDO> stageTasks = tasksByStage
                    .getOrDefault(stage.getStageId(), Collections.emptyList())
                    .stream()
                    .sorted(Comparator.comparing(t -> t.getSortOrder() == null ? 0 : t.getSortOrder()))
                    .collect(Collectors.toList());
            if (stageTasks.isEmpty()) {
                // 阶段无任务也导出一行（仅阶段信息），保证阶段不丢
                rows.add(buildRow(stage.getSortOrder(), stage.getStageName(), null, null, null, null, null, null, null));
                continue;
            }
            for (PmsTaskDO task : stageTasks) {
                rows.add(buildRow(stage.getSortOrder(), stage.getStageName(),
                        task.getSortOrder(), task.getTaskName(),
                        TASK_TYPE_LABEL.getOrDefault(task.getTaskType(), task.getTaskType()),
                        Boolean.TRUE.equals(task.getIsMilestone()) ? "是" : "否",
                        Boolean.TRUE.equals(task.getIsCriticalPath()) ? "是" : "否",
                        task.getCycle(), task.getOutputRequirement()));
            }
        }

        // 4. 空模板给 2 行示例，引导填写格式
        if (rows.isEmpty()) {
            rows.add(TemplateStageTaskImportExcel.builder()
                    .stageNo(1).stageName("示例阶段：需求分析")
                    .taskNo(1).taskName("示例任务：需求调研").taskType("设计任务")
                    .milestone("否").criticalPath("是").cycle(5)
                    .outputRequirement("需求调研报告").build());
            rows.add(TemplateStageTaskImportExcel.builder()
                    .stageNo(2).stageName("示例阶段：方案设计")
                    .taskNo(1).taskName("示例任务：概要设计").taskType("设计任务")
                    .milestone("是").criticalPath("否").cycle(10)
                    .outputRequirement("概要设计说明书").build());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemplateStageTaskImportRespVO importStageTask(Long projectId, List<TemplateStageTaskImportExcel> rows) {
        // 0. 空文件 / 无数据行
        if (rows == null || rows.isEmpty()) {
            throw new ServiceException(ErrorCodeConstants.TEMPLATE_IMPORT_FILE_EMPTY);
        }

        // 1. 校验目标项目是模板（防误导入普通项目）
        validateTemplateProject(projectId);

        // 2. 整批校验
        //    阶段分组（LinkedHashMap 保持行序）：stageNo -> {stageName, 任务行（taskNo -> row）}
        Map<Integer, StageGroup> groups = new LinkedHashMap<>();
        List<TemplateStageTaskImportErrorExcel> failureRows = new ArrayList<>();

        int excelRowNo = 1; // Excel 行号：1 为表头，数据行从 2 开始
        for (TemplateStageTaskImportExcel row : rows) {
            excelRowNo++;
            List<String> errors = new ArrayList<>();

            // 纯阶段行：任务序号与任务名称均空 → 仅声明阶段（无任务的阶段），
            // 跳过任务字段校验。与导出逻辑对齐：无任务阶段导出一行仅含阶段信息的行，
            // 用户下载后不改直接导回应无损通过。
            boolean pureStageRow = row.getTaskNo() == null
                    && (row.getTaskName() == null || row.getTaskName().trim().isEmpty());

            // 2.1 阶段序号：必填正整数
            if (row.getStageNo() == null || row.getStageNo() < 1) {
                errors.add("阶段序号必填且为正整数");
            }
            // 2.2 阶段名称：必填
            if (row.getStageName() == null || row.getStageName().trim().isEmpty()) {
                errors.add("阶段名称必填");
            }
            // 2.3 任务序号：必填正整数（纯阶段行除外）
            if (!pureStageRow && (row.getTaskNo() == null || row.getTaskNo() < 1)) {
                errors.add("任务序号必填且为正整数");
            }
            // 2.4 任务名称：必填（纯阶段行除外）
            if (!pureStageRow && (row.getTaskName() == null || row.getTaskName().trim().isEmpty())) {
                errors.add("任务名称必填");
            }
            String taskTypeValue = null;
            Boolean milestone = null;
            Boolean criticalPath = null;
            if (!pureStageRow) {
                // 2.5 任务类型：可空默认 other，填了必须可识别
                if (row.getTaskType() != null && !row.getTaskType().trim().isEmpty()) {
                    taskTypeValue = TASK_TYPE_ALIAS.get(row.getTaskType().trim());
                    if (taskTypeValue == null) {
                        errors.add("任务类型无法识别：" + row.getTaskType()
                                + "（可选：设计/评审/测试/采购/试制/文档/审批/供应商协同/其他）");
                    }
                }
                // 2.6 里程碑 / 关键路径：是/否，可空默认否
                milestone = parseYesNo(row.getMilestone(), errors, "里程碑");
                criticalPath = parseYesNo(row.getCriticalPath(), errors, "关键路径");
                // 2.7 工期：必填 1-365
                if (row.getCycle() == null) {
                    errors.add("工期必填");
                } else if (row.getCycle() < 1 || row.getCycle() > 365) {
                    errors.add("工期需在 1-365 天之间");
                }
            }

            if (!errors.isEmpty()) {
                failureRows.add(buildErrorRow(row, "第" + excelRowNo + "行：" + String.join("；", errors)));
                continue;
            }

            // 2.8 归组
            StageGroup group = groups.computeIfAbsent(row.getStageNo(), k -> new StageGroup());
            // 组内阶段名称必须一致
            if (group.stageName == null) {
                group.stageName = row.getStageName().trim();
            } else if (!group.stageName.equals(row.getStageName().trim())) {
                failureRows.add(buildErrorRow(row, "第" + excelRowNo + "行：同一阶段序号("
                        + row.getStageNo() + ")的阶段名称必须一致（已出现\"" + group.stageName + "\"）"));
                continue;
            }
            if (group.firstRow == null) {
                group.firstRow = row;
            }
            if (pureStageRow) {
                // 纯阶段行：不登记任务（该阶段可为 0 任务）
                continue;
            }
            // 组内任务序号唯一
            if (group.tasks.containsKey(row.getTaskNo())) {
                failureRows.add(buildErrorRow(row, "第" + excelRowNo + "行：阶段 " + row.getStageNo()
                        + " 内任务序号 " + row.getTaskNo() + " 重复"));
                continue;
            }
            row.setTaskType(taskTypeValue == null ? "other" : taskTypeValue);
            row.setMilestone(Boolean.TRUE.equals(milestone) ? "是" : "否");
            row.setCriticalPath(Boolean.TRUE.equals(criticalPath) ? "是" : "否");
            group.tasks.put(row.getTaskNo(), row);
        }

        // 2.9 结构性校验：阶段序号从 1 开始连续；阶段内任务序号从 1 开始连续
        if (failureRows.isEmpty()) {
            List<Integer> stageNos = new ArrayList<>(groups.keySet());
            for (int i = 0; i < stageNos.size(); i++) {
                if (stageNos.get(i) != i + 1) {
                    // 第一处不连续即报整体错误（挂在首个不连续阶段的行上）
                    int expect = i + 1;
                    StageGroup g = groups.get(stageNos.get(i));
                    TemplateStageTaskImportExcel firstRow = g != null && g.firstRow != null
                            ? g.firstRow : new TemplateStageTaskImportExcel();
                    failureRows.add(buildErrorRow(firstRow,
                            "整体校验：阶段序号需从 1 开始连续（缺少阶段序号 " + expect + "）"));
                    break;
                }
            }
        }
        if (failureRows.isEmpty()) {
            for (Map.Entry<Integer, StageGroup> e : groups.entrySet()) {
                List<Integer> taskNos = new ArrayList<>(e.getValue().tasks.keySet());
                for (int i = 0; i < taskNos.size(); i++) {
                    if (taskNos.get(i) != i + 1) {
                        TemplateStageTaskImportExcel firstRow = e.getValue().tasks.values().iterator().next();
                        failureRows.add(buildErrorRow(firstRow,
                                "整体校验：阶段 " + e.getKey() + " 内任务序号需从 1 开始连续（缺少任务序号 "
                                        + (i + 1) + "）"));
                        break;
                    }
                }
            }
        }

        // 3. 存在错误行 → 整批不落库，返回错误行供 Controller 生成错误 Excel 下载
        if (!failureRows.isEmpty()) {
            log.warn("[importStageTask][projectId={} 共 {} 行校验失败，整批不落库]", projectId, failureRows.size());
            return TemplateStageTaskImportRespVO.builder()
                    .success(false)
                    .stageCount(0)
                    .taskCount(0)
                    .failureRows(failureRows)
                    .build();
        }

        // 4. 全部校验通过 → 全量覆盖重建（软删现有 → 按文件重建）
        //    4.1 软删现有任务与阶段（deleteById 走 @TableLogic 逻辑删除）
        List<PmsTaskDO> oldTasks = taskMapper.selectListByProjectId(projectId);
        if (!oldTasks.isEmpty()) {
            taskMapper.deleteBatchIds(oldTasks.stream().map(PmsTaskDO::getTaskId).collect(Collectors.toList()));
        }
        List<PmsProjectStageDO> oldStages = projectStageMapper.selectListByProjectId(projectId);
        if (!oldStages.isEmpty()) {
            projectStageMapper.deleteBatchIds(oldStages.stream().map(PmsProjectStageDO::getStageId).collect(Collectors.toList()));
        }

        //    4.2 重建阶段（insertBatch 后 ASSIGN_ID 主键回填到 DO）
        List<PmsProjectStageDO> newStages = new ArrayList<>(groups.size());
        for (Map.Entry<Integer, StageGroup> e : groups.entrySet()) {
            PmsProjectStageDO stage = new PmsProjectStageDO();
            stage.setProjectId(projectId);
            stage.setStageName(e.getValue().stageName);
            stage.setSortOrder(e.getKey());
            stage.setStatus("not_started");
            stage.setIsMilestone(false);
            stage.setProgress(0);
            newStages.add(stage);
        }
        projectStageMapper.insertBatch(newStages);

        //    4.3 重建任务（按新 stageId 关联，字段默认值与前端模板编辑页新建任务一致）
        List<PmsTaskDO> newTasks = new ArrayList<>();
        for (int i = 0; i < newStages.size(); i++) {
            PmsProjectStageDO stage = newStages.get(i);
            StageGroup group = groups.get(stage.getSortOrder());
            for (Map.Entry<Integer, TemplateStageTaskImportExcel> t : group.tasks.entrySet()) {
                TemplateStageTaskImportExcel row = t.getValue();
                PmsTaskDO task = new PmsTaskDO();
                task.setProjectId(projectId);
                task.setStageId(stage.getStageId());
                task.setTaskName(row.getTaskName().trim());
                task.setTaskType(row.getTaskType());
                task.setCycle(row.getCycle());
                task.setPriority("normal");
                task.setSortOrder(t.getKey());
                task.setLevel(1);
                task.setIsMilestone("是".equals(row.getMilestone()));
                task.setIsCriticalPath("是".equals(row.getCriticalPath()));
                task.setCompleteStatus("not_started");
                task.setOutputRequirement(row.getOutputRequirement());
                newTasks.add(task);
            }
        }
        if (!newTasks.isEmpty()) {
            taskMapper.insertBatch(newTasks);
        }

        log.info("[importStageTask][projectId={} 全量覆盖完成：{} 个阶段 / {} 个任务]",
                projectId, newStages.size(), newTasks.size());
        return TemplateStageTaskImportRespVO.builder()
                .success(true)
                .stageCount(newStages.size())
                .taskCount(newTasks.size())
                .failureRows(Collections.emptyList())
                .build();
    }

    // ==================== 内部工具 ====================

    /** 校验项目存在且为标准模板 */
    private void validateTemplateProject(Long projectId) {
        if (projectId == null) {
            throw new ServiceException(ErrorCodeConstants.TEMPLATE_PROJECT_NOT_TEMPLATE);
        }
        PmsProjectDO project = projectMapper.selectById(projectId);
        if (project == null || !TEMPLATE_PROJECT_TYPE.equals(project.getProjectType())) {
            throw new ServiceException(ErrorCodeConstants.TEMPLATE_PROJECT_NOT_TEMPLATE);
        }
    }

    /** 解析 是/否 列：空/是/否，其他值报错 */
    private Boolean parseYesNo(String value, List<String> errors, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String v = value.trim();
        if ("是".equals(v) || "Y".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v)) {
            return true;
        }
        if ("否".equals(v) || "N".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
            return false;
        }
        errors.add(fieldName + "仅能填 是/否");
        return false;
    }

    private TemplateStageTaskImportExcel buildRow(Integer stageNo, String stageName, Integer taskNo, String taskName,
                                                  String taskType, String milestone, String criticalPath,
                                                  Integer cycle, String outputRequirement) {
        return TemplateStageTaskImportExcel.builder()
                .stageNo(stageNo).stageName(stageName).taskNo(taskNo).taskName(taskName)
                .taskType(taskType).milestone(milestone).criticalPath(criticalPath)
                .cycle(cycle).outputRequirement(outputRequirement)
                .build();
    }

    private TemplateStageTaskImportErrorExcel buildErrorRow(TemplateStageTaskImportExcel row, String message) {
        if (row == null) {
            row = new TemplateStageTaskImportExcel();
        }
        return TemplateStageTaskImportErrorExcel.builder()
                .errorMessage(message)
                .stageNo(row.getStageNo()).stageName(row.getStageName())
                .taskNo(row.getTaskNo()).taskName(row.getTaskName())
                .taskType(row.getTaskType()).milestone(row.getMilestone())
                .criticalPath(row.getCriticalPath()).cycle(row.getCycle())
                .outputRequirement(row.getOutputRequirement())
                .build();
    }

    /** 阶段分组中间结构 */
    private static class StageGroup {
        String stageName;
        /** 该组首个出现的行（结构性校验报错时定位用，纯阶段组也安全） */
        TemplateStageTaskImportExcel firstRow;
        /** taskNo -> 行（LinkedHashMap 保持插入序） */
        final Map<Integer, TemplateStageTaskImportExcel> tasks = new LinkedHashMap<>();
    }
}
