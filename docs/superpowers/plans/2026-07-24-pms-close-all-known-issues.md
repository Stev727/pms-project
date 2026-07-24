# PMS Known Issues Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close all confirmed PMS workflow defects and expose the required five-step project creation and manager review flows.

**Architecture:** Keep the existing transactional bundle API as the single creation boundary. Enforce project-manager ownership in backend services using the authenticated user ID, surface framework-native service errors, and make the Vue pages call workflow endpoints instead of directly editing terminal statuses.

**Tech Stack:** Spring Boot, MyBatis Plus, JUnit 5, Vue 3, Element Plus, TypeScript, pnpm/ESLint, MySQL.

---

### Task 1: Business errors and project-manager authorization

**Files:**
- Create: `yudao-source/yudao-module-pms/src/main/java/cn/iocoder/yudao/module/pms/enums/ErrorCodeConstants.java`
- Modify: task/change controllers and services
- Test: task/change/project service tests and controller permission test

- [ ] Add failing tests for missing data, invalid state, and non-project-manager review/execute.
- [ ] Run focused Maven tests and confirm the expected failures.
- [ ] Add PMS service error codes and pass authenticated user ID into review/execute services.
- [ ] Verify focused and full PMS tests pass, then commit.

### Task 2: Five-step project creation wizard

**Files:**
- Modify: `yudao-ui-admin-vue3/src/views/pms/project-create/index.vue`
- Modify: `yudao-ui-admin-vue3/src/api/pms/project/index.ts`

- [ ] Replace the current four steps with project information, project members, project tasks, overdue rule, confirmation.
- [ ] Restrict task owner/helper selectors to selected project members.
- [ ] Submit project, members, tasks, and one optional project-level overdue rule through `createProjectBundle`.
- [ ] Remove post-create member writes and create/start notification options.
- [ ] Run targeted ESLint and browser-test the rendered workflow.

### Task 3: Completion and change review UI

**Files:**
- Modify: task/change API wrappers
- Modify: `ReviewCenterTab.vue`, `TaskDetailDrawer.vue`, and change-related project detail components

- [ ] Add API wrappers for completion submit/review and change review/execute.
- [ ] Submit task completion through the workflow endpoint.
- [ ] Show `completion_pending_review` tasks and review them through manager endpoints.
- [ ] Show pending/approved/rejected/executed change actions and call workflow endpoints.
- [ ] Run targeted ESLint and browser-test buttons, states, and error messages.

### Task 4: Deployment and full regression

- [ ] Run focused PMS tests and package the backend.
- [ ] Run frontend targeted lint; record the known Node 20.18 production-build constraint if still present.
- [ ] Back up deployed artifacts, deploy backend and changed Vue sources, and restart safely.
- [ ] Run authenticated API UAT and browser UAT for creation, member selection, change review, completion review, and no unwanted notifications.
- [ ] Commit final verified changes; keep GitHub push pending until requested completion gate.
