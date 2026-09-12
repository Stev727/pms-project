package cn.iocoder.yudao.module.pms.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.dal.dataobject.task.PmsTaskDO;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBoardVO;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBoardScopeVO;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskExportExcel;
import cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskWeeklyReportVO;
import cn.iocoder.yudao.module.pms.service.task.TaskService;
import cn.iocoder.yudao.module.pms.enums.PmsPermKeyEnum;
import cn.iocoder.yudao.module.pms.service.projectpermission.ProjectPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 任务 Controller
 *
 * ============================ 改造说明 ============================
 * 版本：v2（在线上原文件基础上改造，原有 9 个端点路径 / 参数 / 权限一个未改）
 * 新增端点：
 *   【#1 子任务层级】
 *     GET  /pms/task/children?parentTaskId=   查直接子任务
 *     GET  /pms/task/tree?projectId=          查项目全部任务（前端组树，不做「只看我的」过滤）
 *     PUT  /pms/task/progress?taskId=&progress= 进度填报，自动汇总父任务进度
 *   【#3 任务派发审核】
 *     POST /pms/task/submit-review?taskId=
 *     POST /pms/task/approve-review?taskId=&reviewComment=
 *     POST /pms/task/reject-review?taskId=&reviewComment=（原因必填）
 *     GET  /pms/task/my-review-list?projectId=&reviewStatus=
 *
 * 权限点复用既有 pms:task:query / pms:task:update，无需新增菜单 SQL。
 * ==================================================================
 */
@Tag(name = "管理后台 - 任务")
@RestController
@RequestMapping("/pms/task")
@Validated
public class TaskController {

    @Resource
    private TaskService taskService;

    @Autowired(required = false)
    private ProjectPermissionService projectPermissionService;

    /**
     * 项目级权限校验兜底（与 QualityIssueController 一致）：
     * 日常任务（projectId=null）跳过，仅菜单级 @PreAuthorize 把关。
     */
    private void requireProjectPerm(Long projectId, String permKey) {
        if (projectPermissionService == null || projectId == null) {
            return;
        }
        projectPermissionService.checkPermission(projectId, permKey);
    }

    private Long getTaskProjectId(Long taskId) {
        if (taskId == null) return null;
        PmsTaskDO task = taskService.getTask(taskId);
        return task == null ? null : task.getProjectId();
    }

    @PostMapping("/create")
    @Operation(summary = "创建任务")
    @PreAuthorize("@ss.hasPermission('pms:task:create') or @ss.hasPermission('pms:template:query')")
    public CommonResult<Long> create(@RequestBody PmsTaskDO entity) {
        requireProjectPerm(entity.getProjectId(), PmsPermKeyEnum.TASK_CREATE.getKey());
        return success(taskService.createTask(entity));
    }

    @GetMapping("/reviewer-of")
    @Operation(summary = "查询某用户的直属领导ID（日常任务审核人预校验）")
    @Parameter(name = "userId", description = "用户ID", required = true)
    public CommonResult<Long> reviewerOf(@RequestParam("userId") Long userId) {
        return success(taskService.resolveReviewerOf(userId));
    }

    @PostMapping("/dispatch")
    @Operation(summary = "派发任务并通知负责人")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> dispatch(@RequestParam("taskId") Long taskId) {
        requireProjectPerm(getTaskProjectId(taskId), PmsPermKeyEnum.TASK_ASSIGN.getKey());
        taskService.dispatchTask(taskId);
        return success(true);
    }

    @PostMapping("/batch-dispatch")
    @Operation(summary = "批量派发任务（逐条独立成败，按负责人聚合钉钉通知）")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBatchDispatchRespVO> batchDispatch(
            @RequestBody cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskBatchDispatchReqVO reqVO) {
        // 逐条项目级权限校验（与单条派发一致，任一无权限即 403）
        for (Long taskId : reqVO.getTaskIds()) {
            requireProjectPerm(getTaskProjectId(taskId), PmsPermKeyEnum.TASK_ASSIGN.getKey());
        }
        return success(taskService.batchDispatch(reqVO.getTaskIds(), reqVO.getDefaultOwnerId()));
    }

    @GetMapping("/get-task-import-template")
    @Operation(summary = "下载任务批量导入模板（Sheet1 填写说明 + Sheet2 数据表预填已有阶段）")
    @Parameter(name = "projectId", description = "项目ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public void getTaskImportTemplate(HttpServletResponse response,
                                      @RequestParam("projectId") Long projectId) throws IOException {
        List<cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel> rows =
                taskService.getTaskImportTemplateRows(projectId);
        // Sheet1 填写说明 + Sheet2 数据表（ExcelUtils 只支持单 Sheet，这里手写 FastExcel 多 Sheet）
        List<List<Object>> guide = new java.util.ArrayList<>();
        guide.add(java.util.Collections.singletonList("【任务批量导入模板 — 填写说明】"));
        guide.add(java.util.Collections.singletonList("请在第二个 Sheet「任务导入」中填写数据，本页仅为说明。"));
        guide.add(java.util.Collections.singletonList(""));
        guide.add(java.util.Collections.singletonList("一、阶段怎么填（任务挂在哪个阶段下）"));
        guide.add(java.util.Collections.singletonList("1. 已有阶段：数据表已预填「阶段序号+阶段名称」参考行（任务列为空，可忽略）"));
        guide.add(java.util.Collections.singletonList("   → 任务行只需在「阶段序号(必填)」列填该阶段的序号，任务自动挂到该阶段下"));
        guide.add(java.util.Collections.singletonList("2. 新阶段：单独一行填「阶段序号+阶段名称」，任务列全部留空，导入时自动创建"));
        guide.add(java.util.Collections.singletonList("   （也可在任务行直接填新的序号+名称，效果相同）"));
        guide.add(java.util.Collections.singletonList("3. 阶段名称仅创建新阶段时必填；序号须与已有阶段一致（1、2、3…）"));
        guide.add(java.util.Collections.singletonList(""));
        guide.add(java.util.Collections.singletonList("二、任务序号（选填）"));
        guide.add(java.util.Collections.singletonList("1. 留空：系统按该阶段内的填写顺序自动编号（已有任务之后顺延）"));
        guide.add(java.util.Collections.singletonList("2. 填写：阶段内唯一即可（不要求连续），仅决定阶段内的排序"));
        guide.add(java.util.Collections.singletonList(""));
        guide.add(java.util.Collections.singletonList("三、其他列"));
        guide.add(java.util.Collections.singletonList("1. 父任务名称：留空=顶层任务；填某任务名称=作为其子任务（最多两级，父任务须在同阶段）"));
        guide.add(java.util.Collections.singletonList("2. 任务类型/优先级：填中文名（如 设计/高），留空默认 其他/普通"));
        guide.add(java.util.Collections.singletonList("3. 负责人/协助人：填工号或姓名（精确匹配，重名请用工号）；协助人多人用逗号分隔"));
        guide.add(java.util.Collections.singletonList("4. 计划开始/结束日期：同时填或同时空，格式 2026-09-12"));
        guide.add(java.util.Collections.singletonList(""));
        guide.add(java.util.Collections.singletonList("四、示例"));
        guide.add(java.util.Collections.singletonList("行1: 阶段序号1、任务名称「需求调研」、其余留空 → 挂到序号1的阶段，顶层任务"));
        guide.add(java.util.Collections.singletonList("行2: 阶段序号1、任务名称「客户访谈」、父任务名称「需求调研」 → 成为「需求调研」的子任务"));
        guide.add(java.util.Collections.singletonList("行3: 阶段序号2、阶段名称「上线准备」、任务列留空 → 新建阶段"));
        guide.add(java.util.Collections.singletonList("行4: 阶段序号2、任务名称「数据迁移」 → 挂到新建的「上线准备」阶段"));
        guide.add(java.util.Collections.singletonList(""));
        guide.add(java.util.Collections.singletonList("五、重要规则"));
        guide.add(java.util.Collections.singletonList("1. 追加式导入：只新增任务，不修改已有任务和进度"));
        guide.add(java.util.Collections.singletonList("2. 任一行校验失败 → 整批不导入；错误以 Excel 返回并标红原因"));
        try {
            cn.idev.excel.ExcelWriter writer = cn.idev.excel.FastExcelFactory
                    .write(response.getOutputStream())
                    .autoCloseStream(false)
                    .build();
            cn.idev.excel.write.metadata.WriteSheet guideSheet = cn.idev.excel.FastExcelFactory
                    .writerSheet(0, "填写说明")
                    .head(java.util.Collections.singletonList(
                            java.util.Collections.singletonList("填写说明")))
                    .build();
            writer.write(guide, guideSheet);
            cn.idev.excel.write.metadata.WriteSheet dataSheet = cn.idev.excel.FastExcelFactory
                    .writerSheet(1, "任务导入")
                    .head(cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel.class)
                    .build();
            writer.write(rows, dataSheet);
            writer.finish();
        } finally {
            response.addHeader("Content-Disposition", "attachment;filename="
                    + cn.iocoder.yudao.framework.common.util.http.HttpUtils.encodeUtf8("任务批量导入模板.xlsx"));
            response.setContentType("application/vnd.ms-excel;charset=UTF-8");
        }
    }

    @PostMapping("/import-task")
    @Operation(summary = "Excel 批量导入任务（追加式：只新增不覆盖）")
    @io.swagger.v3.oas.annotations.Parameters({
            @Parameter(name = "file", description = "Excel 文件", required = true),
            @Parameter(name = "projectId", description = "项目ID", required = true)
    })
    @PreAuthorize("@ss.hasPermission('pms:task:create')")
    public void importTask(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                           @RequestParam("projectId") Long projectId,
                           HttpServletResponse response) throws IOException {
        // 项目级任务创建权限（与新建任务一致）
        requireProjectPerm(projectId, PmsPermKeyEnum.TASK_CREATE.getKey());
        // 1. 解析 Excel（模板含「填写说明」Sheet，只读「任务导入」数据 Sheet；单 Sheet 自制文件读第一个）
        List<cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel> rows = new java.util.ArrayList<>();
        cn.idev.excel.ExcelReader excelReader = null;
        try {
            excelReader = cn.idev.excel.FastExcelFactory.read(file.getInputStream(),
                            cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel.class,
                            new cn.idev.excel.read.listener.PageReadListener<cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportExcel>(rows::addAll))
                    .build();
            java.util.List<cn.idev.excel.read.metadata.ReadSheet> sheetList = excelReader.excelExecutor().sheetList();
            Integer targetSheetNo = 0;
            for (cn.idev.excel.read.metadata.ReadSheet sh : sheetList) {
                if ("任务导入".equals(sh.getSheetName())) {
                    targetSheetNo = sh.getSheetNo();
                    break;
                }
            }
            excelReader.read(cn.idev.excel.FastExcelFactory.readSheet(targetSheetNo).build());
        } finally {
            if (excelReader != null) {
                excelReader.finish();
            }
        }
        // 2. 整批校验 + 追加导入（事务内有错不落库）
        cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportRespVO result =
                taskService.importTask(projectId, rows);
        if (result.getSuccess()) {
            // 3a. 校验通过 → 返回 JSON 成功响应
            response.setContentType("application/json;charset=UTF-8");
            CommonResult<cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportRespVO> cr =
                    CommonResult.success(result);
            response.getWriter().write(cn.hutool.json.JSONUtil.toJsonStr(cr));
        } else {
            // 3b. 校验失败 → 输出标红错误 Excel 供用户下载修正后重试
            ExcelUtils.write(response, "任务批量导入错误明细.xlsx", "错误行",
                    cn.iocoder.yudao.module.pms.controller.admin.task.vo.TaskImportErrorExcel.class, result.getFailureRows());
        }
    }

    @PostMapping("/submit-completion")
    @Operation(summary = "提交任务完成（进入待审核）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "actualCompleteDate", description = "实际完成日期(yyyy-MM-dd)")
    @Parameter(name = "completionNote", description = "完成说明")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> submitCompletion(
            @RequestParam("taskId") Long taskId,
            @RequestParam(value = "actualCompleteDate", required = false) String actualCompleteDate,
            @RequestParam(value = "completionNote", required = false) String completionNote) {
        taskService.submitCompletion(taskId, actualCompleteDate, completionNote);
        return success(true);
    }

    @PostMapping("/review-completion")
    @Operation(summary = "审核任务完成（通过/驳回）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "approved", description = "是否通过", required = true)
    @Parameter(name = "reviewOpinion", description = "审核意见")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> reviewCompletion(
            @RequestParam("taskId") Long taskId,
            @RequestParam("approved") Boolean approved,
            @RequestParam(value = "reviewOpinion", required = false) String reviewOpinion) {
        requireProjectPerm(getTaskProjectId(taskId), PmsPermKeyEnum.TASK_REVIEW.getKey());
        taskService.reviewCompletion(taskId, approved, reviewOpinion,
                SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PutMapping("/update")
    @Operation(summary = "更新任务")
    @PreAuthorize("@ss.hasPermission('pms:task:update') or @ss.hasPermission('pms:template:query')")
    public CommonResult<Boolean> update(@RequestBody PmsTaskDO entity) {
        requireProjectPerm(entity.getProjectId(), PmsPermKeyEnum.TASK_EDIT.getKey());
        taskService.updateTask(entity);
        return success(true);
    }

    @PostMapping("/simulate-dingtalk-confirm")
    @Operation(summary = "模拟钉钉确认")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> simulateDingtalkConfirm(@RequestParam("taskId") Long taskId) {
        taskService.simulateDingtalkConfirm(taskId);
        return success(true);
    }

    @PostMapping("/accept")
    @Operation(summary = "接收任务（待接收→进行中）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:accept')")
    public CommonResult<Boolean> accept(@RequestParam("taskId") Long taskId) {
        taskService.acceptTask(taskId);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除任务")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:delete') or @ss.hasPermission('pms:template:query')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        requireProjectPerm(getTaskProjectId(id), PmsPermKeyEnum.TASK_DELETE.getKey());
        taskService.deleteTask(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取任务")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<PmsTaskDO> get(@RequestParam("id") Long id) {
        return success(taskService.getTask(id));
    }

    @GetMapping("/weekly-report")
    @Operation(summary = "周报看板聚合查询（上周完成/本周计划/上周延期/上周动态）")
    @Parameter(name = "date", description = "基准日期 yyyy-MM-dd，默认今天；按自然周（周一~周日）计算")
    @Parameter(name = "userId", description = "目标人员ID；为空=本人；0=全部（仅管理员）")
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<TaskWeeklyReportVO> weeklyReport(
            @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(value = "userId", required = false) Long userId) {
        return success(taskService.getWeeklyReport(userId, date));
    }

    @GetMapping("/list")
    @Operation(summary = "获取任务列表（含权限过滤）")
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<List<PmsTaskDO>> list(
            @RequestParam(value = "mainOwnerId", required = false) Long mainOwnerId,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "projectType", required = false) String projectType) {
        List<PmsTaskDO> list = taskService.getTaskList(mainOwnerId, projectId, projectType);
        return success(list);
    }

    // ==================== #1 子任务层级（新增） ====================

    @GetMapping("/children")
    @Operation(summary = "获取直接子任务列表")
    @Parameter(name = "parentTaskId", description = "父任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<List<PmsTaskDO>> children(@RequestParam("parentTaskId") Long parentTaskId) {
        return success(taskService.getSubTaskList(parentTaskId));
    }

    @GetMapping("/tree")
    @Operation(summary = "获取项目全部任务（含层级字段，由前端组装成树）")
    @Parameter(name = "projectId", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<List<PmsTaskDO>> tree(@RequestParam("projectId") Long projectId) {
        return success(taskService.getTaskTreeByProject(projectId));
    }

    @PutMapping("/progress")
    @Operation(summary = "进度填报（自动汇总父任务进度）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "progress", description = "进度 0-100", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> progress(@RequestParam("taskId") Long taskId,
                                          @RequestParam("progress") Integer progress) {
        requireProjectPerm(getTaskProjectId(taskId), PmsPermKeyEnum.TASK_EDIT.getKey());
        taskService.updateTaskProgress(taskId, progress);
        return success(true);
    }

    // ==================== #3 任务派发审核（新增） ====================

    @PostMapping("/submit-review")
    @Operation(summary = "提交审核（in_progress -> submitted）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> submitReview(@RequestParam("taskId") Long taskId) {
        taskService.submitReview(taskId);
        return success(true);
    }

    @PostMapping("/approve-review")
    @Operation(summary = "审核通过（submitted -> completed）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "reviewComment", description = "审核意见")
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> approveReview(
            @RequestParam("taskId") Long taskId,
            @RequestParam(value = "reviewComment", required = false) String reviewComment) {
        requireProjectPerm(getTaskProjectId(taskId), PmsPermKeyEnum.TASK_REVIEW.getKey());
        taskService.approveReview(taskId, reviewComment);
        return success(true);
    }

    @PostMapping("/reject-review")
    @Operation(summary = "审核驳回（submitted -> rejected，原因必填）")
    @Parameter(name = "taskId", description = "任务编号", required = true)
    @Parameter(name = "reviewComment", description = "驳回原因", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:update')")
    public CommonResult<Boolean> rejectReview(
            @RequestParam("taskId") Long taskId,
            @RequestParam("reviewComment") String reviewComment) {
        requireProjectPerm(getTaskProjectId(taskId), PmsPermKeyEnum.TASK_REVIEW.getKey());
        taskService.rejectReview(taskId, reviewComment);
        return success(true);
    }

    @GetMapping("/my-review-list")
    @Operation(summary = "待我审核的任务列表")
    @Parameter(name = "projectId", description = "项目编号，不传表示全部项目")
    @Parameter(name = "reviewStatus", description = "审核状态，默认 submitted")
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public CommonResult<List<PmsTaskDO>> myReviewList(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "reviewStatus", required = false) String reviewStatus) {
        return success(taskService.getMyReviewTaskList(projectId, reviewStatus));
    }

    // ==================== 日常任务 / 我的任务看板（新增） ====================

    @GetMapping("/board")
    @Operation(summary = "我的任务看板聚合查询（历史遗留/项目任务/日常任务）")
    @Parameter(name = "userIds", description = "人员ID列表（逗号分隔字符串），不传按当前用户权限范围默认（管理员=全部 / 其他=本人）")
    @Parameter(name = "dateFrom", description = "范围起点 yyyy-MM-dd", required = true)
    @Parameter(name = "dateTo", description = "范围终点 yyyy-MM-dd", required = true)
    @Parameter(name = "includeSubordinates", description = "是否递归包含下属，默认 true")
    @PreAuthorize("@ss.hasPermission('pms:board:query')")
    public CommonResult<TaskBoardVO> board(
            @RequestParam(value = "userIds", required = false) String userIds,
            @RequestParam("dateFrom") String dateFrom,
            @RequestParam("dateTo") String dateTo,
            @RequestParam(value = "includeSubordinates", defaultValue = "true") boolean includeSubordinates) {
        return success(taskService.boardQuery(userIds, LocalDate.parse(dateFrom), LocalDate.parse(dateTo), includeSubordinates));
    }

    @GetMapping("/board-scope")
    @Operation(summary = "我的任务看板：当前用户可查看的人员范围（权限判定）")
    @PreAuthorize("@ss.hasPermission('pms:board:query')")
    public CommonResult<TaskBoardScopeVO> boardScope() {
        return success(taskService.getBoardScope());
    }

    @GetMapping("/dept-review-list")
    @Operation(summary = "部门审核中心：待我审核的日常任务列表")
    @PreAuthorize("@ss.hasPermission('pms:daily-task:review')")
    public CommonResult<List<PmsTaskDO>> deptReviewList() {
        return success(taskService.getDeptReviewTaskList());
    }

    // ==================== 任务导出（新增） ====================

    @GetMapping("/export")
    @Operation(summary = "导出项目全部任务（Excel，忽略页面筛选）")
    @Parameter(name = "projectId", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:task:query')")
    public void export(HttpServletResponse response,
                       @RequestParam("projectId") Long projectId) throws IOException {
        List<TaskExportExcel> list = taskService.exportTaskByProject(projectId);
        ExcelUtils.write(response, "项目任务.xlsx", "任务", TaskExportExcel.class, list);
    }

}

