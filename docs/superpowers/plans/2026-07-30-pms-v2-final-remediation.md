# PMS V2 Final Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 V2 清单回归测试中仍不满足或被基础故障阻塞的项目管理功能，并完成前后端测试、构建、浏览器回归和 GitHub 推送。

**Architecture:** 将日期、周范围、工期和阶段汇总提取为前端纯函数，以可重复的 Node 单元测试先锁定行为；业务持久化仍由现有 PMS API 和 Service 负责。先修影响全部流程的列表与模板数据，再修页面一致性，最后验证上传、审核和变更闭环。

**Tech Stack:** Vue 3、TypeScript、Element Plus、dayjs、Spring Boot 3、MyBatis Plus、JUnit 5、Maven、pnpm/Vite。

---

### Task 1: 日期与甘特图计算

**Files:**
- Create: `yudao-ui-admin-vue3/src/views/pms/pms-date-utils.ts`
- Create: `yudao-ui-admin-vue3/scripts/test-pms-date-utils.mjs`
- Modify: `yudao-ui-admin-vue3/src/views/pms/project-detail/OverviewTab.vue`
- Modify: `yudao-ui-admin-vue3/src/views/pms/project-detail/GanttTab.vue`
- Modify: `yudao-ui-admin-vue3/src/views/pms/project-detail/TaskListTab.vue`
- Modify: `yudao-ui-admin-vue3/src/views/pms/task/index.vue`

- [ ] **Step 1: 写失败测试**：断言 2026-07-30 的周范围为 2026-07-27～2026-08-02、08-04 不属于本周、08-04～08-09 工期为 6 天、阶段汇总忽略空日期且取最小/最大值。
- [ ] **Step 2: 验证失败**：运行 `node scripts/test-pms-date-utils.mjs`，预期因模块或导出不存在而失败。
- [ ] **Step 3: 最小实现**：导出 `getWeekRange`、`isDateInRange`、`inclusiveDuration`、`summarizeStageDates`，全部按本地日历日计算。
- [ ] **Step 4: 页面接入**：概览用周一至周日；甘特图用阶段汇总和包含首尾日工期；任务列表日期统一 `YYYY-MM-DD`。
- [ ] **Step 5: 验证并提交**：运行日期测试、`pnpm ts:check`，提交 `fix: correct PMS date and gantt calculations`。

### Task 2: 项目列表与模板创建

**Files:**
- Modify: `yudao-ui-admin-vue3/src/views/pms/project/index.vue`
- Modify: `yudao-ui-admin-vue3/src/views/pms/project-create/index.vue`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/project/impl/ProjectServiceImpl.java`
- Modify: `yudao-source/yudao-module-pms/src/test/java/cn/iocoder/yudao/module/pms/service/project/impl/ProjectBundleServiceTest.java`

- [ ] **Step 1: 写失败测试**：覆盖未传项目状态时不错误过滤、项目详情可见时列表同样可见、模板实例任务日期/责任人为空且任务间责任人互不覆盖。
- [ ] **Step 2: 验证失败**：运行 `mvn -pl yudao-module-pms -Dtest=ProjectBundleServiceTest test`，确认失败来自当前过滤或实例化行为。
- [ ] **Step 3: 修复根因**：规范化列表查询参数和权限条件；模板卡片数量取真实明细；模板实例仅复制阶段结构和任务名称。
- [ ] **Step 4: 修复交互**：移除阶段筛选、偏移日期、批量设工期；卡片复选框与批量归档按钮联动；项目经理不可直接从成员中删除。
- [ ] **Step 5: 验证并提交**：运行后端目标测试、前端类型检查，提交 `fix: restore project list and template creation flow`。

### Task 3: 任务、质量和日期校验

**Files:**
- Modify: `yudao-ui-admin-vue3/src/views/pms/project-detail/TaskDetailDrawer.vue`
- Modify: `yudao-ui-admin-vue3/src/views/pms/project-detail/TaskListTab.vue`
- Modify: `yudao-ui-admin-vue3/src/views/pms/project-detail/QualityTab.vue`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/task/impl/TaskServiceImpl.java`
- Modify: `yudao-source/yudao-module-pms/src/test/java/cn/iocoder/yudao/module/pms/service/task/impl/TaskServiceImplTest.java`

- [ ] **Step 1: 写失败测试**：结束早于开始拒绝保存；只填一个日期拒绝保存；完成任务进度为 100；质量分类、责任人、发现日期可回显。
- [ ] **Step 2: 验证失败**：运行 `mvn -pl yudao-module-pms -Dtest=TaskServiceImplTest test`。
- [ ] **Step 3: 最小修复**：后端统一校验任务日期并在完成时写入 100；前端移除冗余展示并增加质量编辑入口。
- [ ] **Step 4: 验证并提交**：运行目标测试和类型检查，提交 `fix: enforce task and quality consistency`。

### Task 4: 输出物、文档、审核和变更闭环

**Files:**
- Modify: `yudao-ui-admin-vue3/src/views/pms/project-detail/DocumentsTab.vue`
- Modify: `yudao-ui-admin-vue3/src/views/pms/project-detail/ReviewCenterTab.vue`
- Modify: `yudao-ui-admin-vue3/src/views/pms/project-detail/ChangesTab.vue`
- Modify: `yudao-ui-admin-vue3/src/api/pms/task/index.ts`
- Modify: `yudao-ui-admin-vue3/src/api/pms/change/index.ts`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/changerecord/impl/ChangeRecordServiceImpl.java`
- Modify: `yudao-source/yudao-module-pms/src/test/java/cn/iocoder/yudao/module/pms/service/changerecord/impl/ChangeRecordReviewTest.java`

- [ ] **Step 1: 写失败测试**：变更批准只执行一次并记录结果；驳回保留审核结果；附件刷新从服务端返回持久化文件。
- [ ] **Step 2: 验证失败**：运行 `mvn -pl yudao-module-pms -Dtest=ChangeRecordReviewTest test`。
- [ ] **Step 3: 修复闭环**：API 方法与 Controller 对齐；批准后自动执行并保证幂等；通知失败记录但不回滚审核；文档页聚合任务输出物。
- [ ] **Step 4: 验证并提交**：运行目标测试、类型检查，提交 `fix: complete PMS review change and output flows`。

### Task 5: 全量验证与 GitHub 发布

**Files:**
- Modify: `docs/superpowers/plans/2026-07-30-pms-v2-final-remediation.md`
- Create: `docs/testing/PMS-V2-remediation-verification-20260730.md`

- [ ] **Step 1: 前端验证**：运行 `pnpm ts:check`、`pnpm lint`、`pnpm build:prod`。
- [ ] **Step 2: 后端验证**：运行 `mvn -pl yudao-module-pms -am test` 和相关模块打包。
- [ ] **Step 3: 浏览器回归**：在服务器测试环境按 22 条清单复测，记录满足、部分满足、阻塞和截图证据。
- [ ] **Step 4: 检查提交范围**：确认 `yudao.zip`、`application-local.yaml`、构建产物和凭据不在提交中。
- [ ] **Step 5: 推送 GitHub**：将分支同步至服务器，通过服务器执行 `git push -u origin codex/pms-v2-final-fixes`，创建 PR 或提供分支链接。
