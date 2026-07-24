# Project Task DingTalk Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build project-level default DingTalk rules that tasks inherit, with notifications only for overdue tasks, task-change review, and task-completion review.

**Architecture:** Extend the existing notification-rule table with project/task scope and resolve the effective rule in one backend service. Create projects, members, tasks, and default rules through one transactional bundle endpoint so member selection precedes task creation. Route task-change and completion state transitions through explicit service methods that trigger DingTalk only after the transaction commits.

**Tech Stack:** Spring Boot 3, MyBatis Plus, MySQL 8, JUnit 5/Mockito, Vue 3, TypeScript, Element Plus, pnpm/Vite.

---

## Remote-only execution boundary

- Work only on `ai@10.1.3.32`; do not clone, build, or run source on the user's Mac.
- Create remote branch `codex/project-task-notify-rules` and remote worktree `/home/ai/projects/rdpms-worktrees/project-task-notify-rules`.
- Preserve `/home/ai/projects/rdpms/server.log`, `yudao-server.jar.bak.20260723055700`, and `yudao-ui-admin-vue3/.npmrc`.
- Apply database migration only after backend tests and a schema backup.
- Do not restart the running service until backend packaging and frontend build pass.

### Task 1: Establish the remote worktree and baseline

**Files:**
- Verify: all tracked files

- [ ] **Step 1: Create an isolated remote worktree**

```bash
ssh ai@10.1.3.32
cd /home/ai/projects/rdpms
git worktree add -b codex/project-task-notify-rules \
  /home/ai/projects/rdpms-worktrees/project-task-notify-rules main
```

Expected: a clean worktree at the new path; the running checkout remains untouched.

- [ ] **Step 2: Run the backend baseline**

```bash
cd /home/ai/projects/rdpms-worktrees/project-task-notify-rules/yudao-source
/opt/apache-maven-3.9.9/bin/mvn -pl yudao-module-pms -am test -DskipTests=false
```

Expected: BUILD SUCCESS. Record any pre-existing test failure before changing code.

- [ ] **Step 3: Run the frontend baseline**

```bash
export PATH=/usr/local/node-v22.18.0-linux-x64/bin:$PATH
cd /home/ai/projects/rdpms-worktrees/project-task-notify-rules/yudao-ui-admin-vue3
pnpm ts:check
pnpm build:local
```

Expected: both commands exit 0.

### Task 2: Add scoped notification-rule persistence

**Files:**
- Create: `yudao-source/sql/mysql/pms_project_task_notify_rule_20260724.sql`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/dal/dataobject/notifyrule/PmsNotifyRuleDO.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/dal/dataobject/notifylog/PmsNotifyLogDO.java`
- Test: `yudao-source/yudao-module-pms/src/test/java/cn/iocoder/yudao/module/pms/service/notifyrule/NotifyRuleServiceImplTest.java`

- [ ] **Step 1: Write a failing rule-scope test**

```java
@Test
void resolveEffectiveRules_taskOverrideWins() {
    PmsNotifyRuleDO projectRule = rule("task_overdue", "project", 10L, null, true);
    PmsNotifyRuleDO taskOverride = rule("task_overdue", "task", 10L, 20L, false);
    when(mapper.selectList(any())).thenReturn(List.of(projectRule, taskOverride));

    assertFalse(service.resolveEffectiveRules(10L, 20L).get("task_overdue").getEnabled());
}
```

- [ ] **Step 2: Verify the test fails**

Run:

```bash
/opt/apache-maven-3.9.9/bin/mvn -pl yudao-module-pms \
  -Dtest=NotifyRuleServiceImplTest#resolveEffectiveRules_taskOverrideWins test
```

Expected: FAIL because scoped fields and `resolveEffectiveRules` do not exist.

- [ ] **Step 3: Add the migration and fields**

```sql
ALTER TABLE pms_notify_rule
  ADD COLUMN rule_scope varchar(16) NOT NULL DEFAULT 'global' COMMENT 'global/project/task',
  ADD COLUMN project_id bigint NULL,
  ADD COLUMN task_id bigint NULL,
  ADD COLUMN enabled bit NOT NULL DEFAULT b'1';

ALTER TABLE pms_notify_log
  ADD COLUMN project_id bigint NULL,
  ADD COLUMN task_id bigint NULL,
  ADD COLUMN receiver_user_id bigint NULL,
  ADD COLUMN receiver_name varchar(128) NULL,
  ADD COLUMN dingtalk_user_id varchar(128) NULL,
  ADD COLUMN idempotency_key varchar(160) NULL,
  ADD COLUMN retry_count int NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_pms_notify_log_idempotency
  ON pms_notify_log (tenant_id, idempotency_key, deleted);
```

Map the same columns in the two DO classes using `Long`, `String`, `Boolean`, and `Integer`; keep all snowflake IDs as strings in TypeScript.

- [ ] **Step 4: Implement effective-rule precedence**

```java
public Map<String, PmsNotifyRuleDO> resolveEffectiveRules(Long projectId, Long taskId) {
    List<PmsNotifyRuleDO> rules = notifyRuleMapper.selectScopedRules(projectId, taskId);
    Map<String, PmsNotifyRuleDO> result = new LinkedHashMap<>();
    rules.stream()
        .filter(rule -> "project".equals(rule.getRuleScope()))
        .forEach(rule -> result.put(rule.getTriggerEvent(), rule));
    rules.stream()
        .filter(rule -> "task".equals(rule.getRuleScope()))
        .forEach(rule -> result.put(rule.getTriggerEvent(), rule));
    return result;
}
```

- [ ] **Step 5: Run the focused test and commit**

```bash
/opt/apache-maven-3.9.9/bin/mvn -pl yudao-module-pms -Dtest=NotifyRuleServiceImplTest test
git add yudao-source/sql/mysql/pms_project_task_notify_rule_20260724.sql \
  yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/dal/dataobject/notifyrule \
  yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/dal/dataobject/notifylog \
  yudao-source/yudao-module-pms/src/test
git commit -m "feat: scope notification rules by project and task"
```

Expected: test PASS and one focused commit.

### Task 3: Centralize event notification and recipient resolution

**Files:**
- Create: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/notification/TaskNotificationService.java`
- Create: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/notification/impl/TaskNotificationServiceImpl.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/dingtalk/DingTalkNotifyService.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/dingtalk/impl/DingTalkNotifyServiceImpl.java`
- Test: `yudao-source/yudao-module-pms/src/test/java/cn/iocoder/yudao/module/pms/service/notification/TaskNotificationServiceImplTest.java`

- [ ] **Step 1: Write failing recipient tests**

```java
@Test
void overdue_notifiesOnlyProjectManager() {
    service.notifyEvent(20L, TaskNotifyEvent.TASK_OVERDUE, "overdue:20");
    verify(dingTalkNotifyService).sendEventNotification(
        eq(10L), eq(20L), eq("task_overdue"), eq(List.of(1353L)), anyMap(), eq("overdue:20"));
}

@Test
void completionApproved_notifiesOnlyTaskOwner() {
    service.notifyEvent(20L, TaskNotifyEvent.COMPLETION_APPROVED, "complete-approved:20:7");
    verify(dingTalkNotifyService).sendEventNotification(
        eq(10L), eq(20L), eq("completion_approved"), eq(List.of(2001L)), anyMap(),
        eq("complete-approved:20:7"));
}
```

- [ ] **Step 2: Verify the tests fail**

Run `/opt/apache-maven-3.9.9/bin/mvn -pl yudao-module-pms -Dtest=TaskNotificationServiceImplTest test`.

Expected: FAIL because the service and event enum do not exist.

- [ ] **Step 3: Implement the seven allowed events**

```java
public enum TaskNotifyEvent {
    TASK_OVERDUE,
    CHANGE_SUBMITTED, CHANGE_APPROVED, CHANGE_REJECTED,
    COMPLETION_SUBMITTED, COMPLETION_APPROVED, COMPLETION_REJECTED
}
```

Map `TASK_OVERDUE`, `CHANGE_SUBMITTED`, and `COMPLETION_SUBMITTED` to the project's `projectManagerId`. Map the four review results to the task's `mainOwnerId`. Do not retain any task-created, dispatched, started, T-3, department-head, or director event.

- [ ] **Step 4: Make sending fail closed**

```java
if (dingTalkUsers.size() != receiverUserIds.size()) {
    saveFailedLog(projectId, taskId, event, receiverUserIds,
        "接收人缺少有效钉钉用户映射", idempotencyKey);
    return false;
}
```

Insert one log row per receiver, store the system user and DingTalk user identifiers, and catch duplicate idempotency keys as an already-processed event rather than resending.

- [ ] **Step 5: Run tests and commit**

```bash
/opt/apache-maven-3.9.9/bin/mvn -pl yudao-module-pms \
  -Dtest=TaskNotificationServiceImplTest,DingTalkNotifyServiceImplTest test
git add yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service \
  yudao-source/yudao-module-pms/src/test
git commit -m "feat: route task events to project manager or owner"
```

### Task 4: Create project, members, tasks, and rules atomically

**Files:**
- Create: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/controller/admin/project/vo/ProjectBundleCreateReqVO.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/controller/admin/project/ProjectController.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/project/ProjectService.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/project/impl/ProjectServiceImpl.java`
- Test: `yudao-source/yudao-module-pms/src/test/java/cn/iocoder/yudao/module/pms/service/project/ProjectServiceImplTest.java`

- [ ] **Step 1: Write failing transaction and membership tests**

```java
@Test
void createBundle_rejectsTaskOwnerOutsideMembers() {
    req.setMembers(List.of(member(1353L, "pm"), member(2001L, "helper")));
    req.setTasks(List.of(task(9999L)));
    assertServiceException(() -> service.createProjectBundle(req), "任务责任人必须是项目成员");
}
```

- [ ] **Step 2: Verify the focused test fails**

Run `/opt/apache-maven-3.9.9/bin/mvn -pl yudao-module-pms -Dtest=ProjectServiceImplTest#createBundle_rejectsTaskOwnerOutsideMembers test`.

- [ ] **Step 3: Implement `POST /pms/project/create-bundle`**

```java
@Transactional(rollbackFor = Exception.class)
public Long createProjectBundle(ProjectBundleCreateReqVO req) {
    Long projectId = createProject(req.toProjectDO());
    createMembers(projectId, req.getMembers(), req.getProjectManagerId());
    validateTaskOwners(req.getTasks(), req.getMembers());
    replaceGeneratedTasks(projectId, req.getTasks());
    createDefaultRules(projectId, req.getNotificationRules());
    return projectId;
}
```

Automatically include the project manager as an active `pm` member, reject duplicate members, and reject inactive/non-member owners and helpers. Default rules contain only the seven approved events.

- [ ] **Step 4: Run service tests and commit**

```bash
/opt/apache-maven-3.9.9/bin/mvn -pl yudao-module-pms -Dtest=ProjectServiceImplTest test
git add yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/controller/admin/project \
  yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/project \
  yudao-source/yudao-module-pms/src/test
git commit -m "feat: create project bundle with members and rules"
```

### Task 5: Enforce task-change and completion review state machines

**Files:**
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/dal/dataobject/changerecord/PmsChangeRecordDO.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/controller/admin/changerecord/ChangeRecordController.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/changerecord/ChangeRecordService.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/changerecord/impl/ChangeRecordServiceImpl.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/controller/admin/task/TaskController.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/task/TaskService.java`
- Modify: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/service/task/impl/TaskServiceImpl.java`
- Test: `yudao-source/yudao-module-pms/src/test/java/cn/iocoder/yudao/module/pms/service/changerecord/ChangeRecordServiceImplTest.java`
- Test: `yudao-source/yudao-module-pms/src/test/java/cn/iocoder/yudao/module/pms/service/task/TaskReviewServiceTest.java`

- [ ] **Step 1: Write failing state-transition tests**

```java
@Test
void approveChange_doesNotMutateTaskUntilManagerExecutes() {
    service.approve(changeId, managerId, "同意");
    verify(taskMapper, never()).updateById(any());
    assertEquals("approved_pending_execution", saved.getChangeStatus());
