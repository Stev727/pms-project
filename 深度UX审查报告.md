# PMS 研发项目管理系统 - 深度用户体验审查报告

> **审查日期**: 2025-07-11  
> **审查范围**: 全系统前端页面 - 信息完整性、按钮功能性、前后端逻辑正确性  
> **审查方法**: 逐文件静态代码分析 + 数据流追踪 + 交叉引用验证

---

## 一、审查摘要

| 维度 | 发现数 | 致命 | 严重 | 一般 | 建议 |
|------|--------|------|------|------|------|
| 信息完整性 | 12 | 1 | 4 | 4 | 3 |
| 按钮功能性 | 10 | 1 | 3 | 3 | 3 |
| 前后端逻辑 | 14 | 2 | 5 | 5 | 2 |
| 跨页面一致性 | 6 | 0 | 2 | 3 | 1 |
| **总计** | **42** | **4** | **14** | **15** | **9** |

---

## 二、致命问题 (Fatal)

### FATAL-1: `getProject(Number(projectId))` — 雪花ID精度丢失导致项目详情加载失败

**文件**: `project-detail/index.vue:372`  
**代码**:
```typescript
getProject(Number(projectId.value))  // ❌ 致命！
```

**问题**: `projectId` 来自路由参数 `route.params.id`，是 19 位雪花 ID。虽然 `transformResponse` 将其转为字符串，但此处用 `Number()` 转回数字导致末位精度丢失。例如 `9007199254740993` → `9007199254740992`。

**影响**: 项目详情页加载错误数据或 404。这是线上用户看到的致命 bug。

**修复**:
```typescript
getProject(projectId.value)  // 直接传字符串
```

---

### FATAL-2: 路由缺少 PMS 页面定义 — 用户无法通过菜单访问 PMS 模块

**文件**: `router/modules/remaining.ts`  
**问题**: 整个 `remaining.ts` 中没有任何 PMS 路由定义（`/pms/project`、`/pms/project-detail/:id`、`/pms/project-create`、`/pms/dict`）。PMS 路由依赖于后端动态菜单生成，但剩余路由中没有兜底定义。

**影响**: 如果后端菜单 API 返回数据有问题，PMS 所有页面将完全无法访问。而且动态导入的 chunk 错误时没有 fallback。

**修复**: 在 `remaining.ts` 中添加 PMS 剩余路由定义：
```typescript
{
  path: '/pms',
  component: Layout,
  name: 'Pms',
  meta: { hidden: true },
  children: [
    {
      path: 'project-detail/:id',
      component: () => import('@/views/pms/project-detail/index.vue'),
      name: 'PmsProjectDetail',
      meta: { title: '项目详情', noCache: true, hidden: true, canTo: true }
    },
    {
      path: 'project-create',
      component: () => import('@/views/pms/project-create/index.vue'),
      name: 'PmsProjectCreate',
      meta: { title: '新建项目', noCache: true, hidden: true, canTo: true }
    },
    {
      path: 'dict',
      component: () => import('@/views/pms/dict/index.vue'),
      name: 'PmsDict',
      meta: { title: '字典管理', noCache: true, hidden: true, canTo: true }
    }
  ]
}
```

---

### FATAL-3: 用户名解析全部硬编码为 `用户${id}` — 用户看到的是数字 ID

**涉及文件** (共 5 处):
| 文件 | 行号 | 代码 |
|------|------|------|
| `TaskListTab.vue` | 332 | `task.mainOwnerId ? '用户${task.mainOwnerId}' : '未分配'` |
| `project/index.vue` | 356 | `project.projectManagerId ? '用户${project.projectManagerId}' : '未分配'` |
| `project-detail/index.vue` | 405-407 | fallback: `'用户${item.projectManagerId}'` |
| `MembersTab.vue` | 124 | `userList.value.find(...)?.nickname \|\| '用户${userId}'` |
| `project-create/index.vue` | 462 | `userList.value.find(...)?.nickname \|\| '-'` |

**问题**: 
- `TaskListTab.vue` 和 `project/index.vue` 完全没有加载用户列表，**100% 显示为 `用户123456`** 这种无意义文本
- `project-detail/index.vue` 虽然加载了 `userList`，但仅用于 `getManagerName()` 函数，`TaskListTab` 内部仍用自己的硬编码
- `MembersTab.vue` 正确加载了用户列表做查找，但 fallback 仍是硬编码

**影响**: 用户在所有页面看到的负责人/成员名称都是数字 ID，完全无法识别真实身份。这是最影响用户体验的问题。

**修复**: 
1. `TaskListTab.vue` 需要接收 `userList` prop 或自行加载用户列表
2. `project/index.vue` 需要在 `loadList()` 中同时加载用户列表

---

### FATAL-4: 快捷筛选"我负责的"和"我参与的"完全没有实现

**文件**: `project/index.vue:248-251`  
**代码**:
```typescript
if (quickFilter.value === 'mine') {
  // TODO: 当前用户ID比对
} else if (quickFilter.value === 'involved') {
  // TODO: 当前用户参与的项目
}
```

**问题**: 用户点击"我负责的"或"我参与的"快捷筛选按钮后，列表没有任何过滤效果，仍然是全部项目。

**影响**: 核心 UX 功能完全不可用，给用户造成"系统坏了"的印象。

---

## 三、严重问题 (Severe)

### SEVERE-1: 任务状态流转中 `accept` 和 `start` 都从 `not_started` → `in_progress`，语义重叠

**文件**: `TaskListTab.vue:262-271`  
**问题**: 
- `accept: { from: ['not_started'], to: 'in_progress' }` 
- `start: { from: ['not_started'], to: 'in_progress' }`

两个操作从相同状态到相同目标状态，但语义不同（"接收"vs"开始"）。界面上两个按钮会同时显示，用户困惑。

**修复**: 
- `accept` 应该是一个独立的"已接收"中间状态，或
- 将 `start` 改为只能从 `in_progress` 状态触发（移除 `not_started`），这样逻辑变为：先接收(not_started→in_progress)→开始(in_progress→in_progress)

---

### SEVERE-2: 看板视图的"已延期"列逻辑有缺陷

**文件**: `project-detail/index.vue:350-364`  
**代码**:
```typescript
if (status === 'delayed') {
  return calcDelayDays(t.planEndDate, t.completeStatus) > 0 && t.completeStatus !== 'completed'
}
if (status === 'completed' || status === 'in_progress' || status === 'not_started' || status === 'paused') {
  if (calcDelayDays(t.planEndDate, t.completeStatus) > 0 && t.completeStatus !== 'completed') return false
  return t.completeStatus === status
}
```

**问题**: 
- 处于 `delayed` 状态的任务会同时出现在"已延期"列和"进行中"列（因为 `delayed` 不是 `completed/in_progress/not_started/paused` 之一，不会进入第二个分支被过滤）
- `reported`、`pending_review` 等状态的任务不会出现在任何列中

---

### SEVERE-3: 文档上传成功后 `versionNo` 硬编码为 `'1.0'`，多次上传同名文件不递增

**文件**: `DocumentsTab.vue:208-216`  
**问题**: 每次上传都设 `versionNo: '1.0'`。如果用户多次上传同一文档的新版本，版本号不会递增。

**修复**: 上传前检查同名文档，自动递增版本号。

---

### SEVERE-4: `handleBatchDownload` 未实现 — 用户点击后只显示"开发中"

**文件**: `DocumentsTab.vue:198-201`  
**问题**: 批量下载按钮存在且可选择文件，但点击后只提示"批量下载功能开发中"。用户会认为系统有 bug。

**修复**: 实现真正的批量下载（逐个触发下载或打包 zip），或者在功能未完成前隐藏按钮。

---

### SEVERE-5: 变更记录的数据映射字段名严重不一致

**文件**: `ChangesTab.vue:230-248`  
**代码**:
```typescript
changeNumber: item.changeCode || '',
title: item.changeDescription || '',
type: item.changeType || '',
status: item.approvalStatus || '',
```

**问题**: 前端使用 `item.type`、`item.status`、`item.title` 等简化字段名，但 API 返回的是 `changeType`、`approvalStatus`、`changeDescription`。映射是做了，但**变更详情抽屉中直接使用了 `selected.type`、`selected.status`** 等映射后的字段。如果后端返回的 `changeType` 和前端 `changeTypes` 的 key 不匹配（如后端返回 `schedule_change` 而前端定义 `schedule`），状态标签将显示原始值。

---

### SEVERE-6: 创建任务弹窗的 `taskType`、`priority` 等字段使用硬编码选项而非字典数据

**文件**: `project-detail/index.vue:189-198`  
**问题**: 任务创建弹窗直接使用 `taskTypeOptions` 和 `priorityOptions` 硬编码常量，没有调用 `getDynamicTaskTypeOptions()` 从字典维护数据加载。与字典管理页面脱节。

---

### SEVERE-7: `MembersTab` 编辑成员时 `userId` 选择器可以修改，可能导致数据不一致

**文件**: `MembersTab.vue:50-52`  
**问题**: 编辑模式下，`el-select` 没有 `disabled` 属性。用户可以修改已有成员的 userId，实际上是换了一个人，但 memberId 不变，导致数据混乱。

**修复**: 编辑模式下禁用 `userId` 选择器。

---

### SEVERE-8: 审批通过/驳回操作缺少二次确认

**文件**: `ApprovalTab.vue:145-153`  
**问题**: 点击"通过"或"驳回"按钮直接执行，没有确认弹窗。审批是严肃操作，误点可能导致严重后果。

**修复**: 添加 `ElMessageBox.confirm` 确认。

---

### SEVERE-9: 项目创建 `submitCreate` 返回的 `projectId` 可能因类型问题导致跳转失败

**文件**: `project-create/index.vue:714-719`  
**代码**:
```typescript
const res = await createProject(projectData)
const projectId = (res as any)?.projectId || (res as any)
router.push(`/pms/project-detail/${projectId}`)
```

**问题**: 
1. 如果后端返回的是整个 project 对象且 projectId 是 19 位数字，经过 `transformResponse` 后变成字符串，但 fallback `(res as any)` 可能导致拿到整个对象而非 ID
2. `getProject(Number(projectId))` 的精度问题（FATAL-1）会导致跳转后详情加载失败

---

### SEVERE-10: `quality/index.ts` API 的 `issueId` 类型不一致

**文件**: `api/pms/quality/index.ts:4`  
**代码**: `issueId: string | number`

**问题**: 与其他 API 文件不同，`QualityIssueVO` 的 `issueId` 是 `string | number` 联合类型，但 `deleteQualityIssue` 和 `getQualityIssue` 参数类型是 `number`。类型不一致。

---

### SEVERE-11: 甘特图 Tab 缺少 `GanttTab` 组件的加载状态和错误处理

**文件**: `project-detail/index.vue:122-127`  
**问题**: 甘特图直接渲染 `GanttTab`，没有 loading 状态包裹。如果 `dhtmlx-gantt` 初始化失败（如 DOM 未就绪），用户会看到空白区域而没有错误提示。

---

### SEVERE-12: `MembersTab.loadMembers()` 调用 `getProjectMemberList()` 无参数 — 拉取全量数据再前端过滤

**文件**: `MembersTab.vue:185-186`  
**代码**:
```typescript
const data = await getProjectMemberList()
memberList.value = ((data as any[]) || []).filter(m => String(m.projectId) === String(props.projectId))
```

**问题**: API 没有按 `projectId` 筛选参数，每次都拉取全量成员数据再前端过滤。项目多时性能差。

---

### SEVERE-13: 字典管理页面数据仅存 localStorage — 多用户/多设备不同步

**文件**: `dict/index.vue:250-264`  
**问题**: 字典数据仅存储在浏览器 localStorage，不同用户、不同设备之间的字典修改不会同步。刷新页面、换浏览器后数据丢失（除非同一设备）。

**影响**: 字典管理失去意义 — 一个人修改了任务类型，其他人完全看不到。

---

### SEVERE-14: `approval/index.ts` API 的 `deleteApprovalRecord` 参数类型为 `number` — 雪花 ID 精度丢失

**文件**: `api/pms/approval/index.ts:30-31`  
**问题**: `deleteApprovalRecord(id: number)` — 与其他已修复的 API 不同，approval API 的 delete/get 仍使用 `number` 类型。

---

## 四、一般问题 (Minor)

### MINOR-1: `OverviewTab` 最近动态中，延期任务的时间戳全部显示为"当前时间"

**文件**: `OverviewTab.vue:213`  
**代码**: `time: formatDate(new Date(), 'MM-DD HH:mm')`

**问题**: 延期任务的动态时间显示为页面加载时间，而非实际延期日期。每次刷新时间都变。

---

### MINOR-2: `DocumentsTab` 中 `handleUploadSuccess` 未验证上传文件类型和大小

**文件**: `DocumentsTab.vue:203-219`  
**问题**: `el-upload` 组件没有设置 `accept` 和 `before-upload` 限制。用户可以上传任意类型和大小的文件。

---

### MINOR-3: `QualityTab` 中"关闭"按钮没有二次确认

**文件**: `QualityTab.vue:155-161`  
**问题**: 关闭质量问题没有确认弹窗。

---

### MINOR-4: `ChangesTab` 提交审批时 `approveResult === 'modify'` 处理不完整

**文件**: `ChangesTab.vue:281`  
**代码**: `approveResult.value === 'approve' ? 'approved' : approveResult.value === 'reject' ? 'rejected' : 'pending'`

**问题**: "需修改" (`modify`) 被映射为 `pending`，但用户填写的审批意见没有被传递到 API。

---

### MINOR-5: `project-create/index.vue` 中 `loadTemplateTasks` 在 `onMounted` 中循环调用所有模板

**文件**: `project-create/index.vue:772-776`  
**代码**:
```typescript
for (const tpl of templateList.value) {
  if (tpl.projectId) {
    loadTemplateTasks(tpl.projectId).catch(() => {})
  }
}
```

**问题**: 页面加载时同时请求所有模板的任务数据，如果模板多（如 20+），会产生大量并发请求。而且 `templateList` 的赋值在第 752 行，与第 772 行的循环之间存在竞态条件 — 如果 `getProjectList` 还没返回，`templateList` 为空数组，循环不执行。

---

### MINOR-6: `project/index.vue` 中阶段筛选器使用了不完整的排除列表

**文件**: `project/index.vue:16`  
**代码**:
```html
<el-option v-for="(v, k) in phaseColorMap" :key="k" :label="v.label" :value="k" v-if="!['立项','设计','开发','测试','验收','结项'].includes(k as string)" />
```

**问题**: `phaseColorMap` 同时包含英文 key（`initiation`、`design` 等）和中文 key（`立项`、`设计` 等）。这段代码排除中文 key，但中文 key 也在 map 中。逻辑上应该只遍历英文 key，而非用排除列表。

---

### MINOR-7: `MembersTab` 角色选择器硬编码，未使用字典数据

**文件**: `MembersTab.vue:55-63`  
**问题**: 成员角色下拉选项硬编码在模板中（pm、tech_lead 等），未从 `pms_dict_data` 加载。字典管理页面修改角色后此处不会生效。

---

### MINOR-8: 看板视图中任务卡片点击后打开的抽屉缺少上下文

**文件**: `project-detail/index.vue:105`  
**问题**: 看板卡片点击 `@click="openTaskDrawer(task)"`，但看板列中的 `task` 对象来自 `kanbanColumns` 计算属性，是原始 `projectTasks` 的引用。如果 `projectTasks` 中的任务对象不包含完整的关联数据（如阶段名、负责人名），抽屉中会显示不完整的信息。

---

### MINOR-9: `DocumentsTab` 的 `loadDocuments` 中嵌套调用 `getStageList`

**文件**: `DocumentsTab.vue:228`  
**问题**: `loadDocuments` 内部再次调用 `getStageList()`，但 `onMounted` 中先调用了 `loadTasks()` 再调用 `loadDocuments()`。`loadDocuments` 中的 `stages.value` 过滤依赖 `props.projectId`，但 `getStageList()` 返回全量数据。与 `MembersTab` 同样的问题。

---

### MINOR-10: `export Excel` 和 `copy project` 功能未实现但按钮可见

**文件**: `project/index.vue:349-352` + `project-detail/index.vue:453-456`  
**问题**: "导出Excel"和"复制项目"按钮可见且有权限控制，但点击后只提示"功能开发中"。用户会产生困惑。

**修复**: 使用 `v-if="false"` 或添加 `disabled` + tooltip 说明，或直接隐藏。

---

### MINOR-11: `dict/index.vue` 的 `setInterval` 定时器未在组件卸载时清除

**文件**: `dict/index.vue:262-264`  
**问题**: 每 5 秒执行 `localStorage.setItem` 的定时器没有在 `onUnmounted` 中清除。如果用户频繁切换页面，会积累多个定时器。

---

### MINOR-12: `pms-utils.ts` 中 `projectTypeOptions` 等常量与字典默认数据重复定义

**文件**: `pms-utils.ts:149-173` vs `dict/index.vue:107-184`  
**问题**: 同样的数据在 `pms-utils.ts`（硬编码常量）和 `dict/index.vue`（字典默认数据）中重复维护。修改时容易不一致。

---

### MINOR-13: `ChangesTab` 中 `updateChangeRecord` 只传了 `changeId` 和 `approvalStatus`

**文件**: `ChangesTab.vue:282`  
**代码**:
```typescript
await updateChangeRecord({ changeId: selected.value.changeId, approvalStatus: newStatus } as ChangeRecordVO)
```

**问题**: 审批意见 (`approveOpinion`) 和自动调整 (`autoAdjust`) 的值没有传递给后端。用户填写的审批意见会丢失。

---

### MINOR-14: `project-create/index.vue` 中草稿保存未对 `adjustedTasks` 做深拷贝

**文件**: `project-create/index.vue:653-661`  
**问题**: `saveDraft` 中使用扩展运算符 `{ ...projectForm }` 是浅拷贝，`adjustedTasks: adjustedTasks.value` 是引用赋值。如果用户后续修改任务，草稿中的引用也会变，导致草稿数据被污染。

---

### MINOR-15: `TaskListTab.vue` 中 `filterAssignee === 'involved'` 未实现

**文件**: `TaskListTab.vue:225-228`  
**问题**: "我参与的"筛选选项在下拉框中可见，但选择后无任何效果（只有 `mine` 有 TODO 注释）。

---

## 五、建议优化 (Suggestion)

### SUGGESTION-1: 各 Tab 组件独立加载数据，缺少统一的 loading 协调

各 Tab 组件（Members、Documents、Approval、Quality、Changes）各自在 `onMounted` 中加载数据。当用户快速切换 Tab 时，会同时触发多个 API 请求。建议添加请求去重或取消机制。

---

### SUGGESTION-2: 缺少全局错误边界组件

所有组件的 API 调用都是独立的 try-catch，缺少统一的错误处理。建议在 axios 拦截器层面增加业务错误码的统一处理。

---

### SUGGESTION-3: 看板视图的"已延期"列和任务列表中的延期逻辑不一致

看板使用 `calcDelayDays > 0 && completeStatus !== 'completed'`，任务列表中的状态标签直接使用 `completeStatus === 'delayed'`。两者判定延期的标准不同。

---

### SUGGESTION-4: `ApprovalTab` 审批详情弹窗中没有展示审批历史时间线

目前详情弹窗只展示当前状态信息，没有展示审批流转的历史记录（如：谁在什么时间提交、谁审批通过/驳回）。

---

### SUGGESTION-5: `project-detail/index.vue` 的 `taskStats` 计算属性未被模板使用

**文件**: `project-detail/index.vue:326-341`  
`taskStats` 计算属性计算了 `total`、`completed`、`delayed`、`inProgress`、`notStarted`、`completionRate`、`qualityIssues`、`pendingIssues`，但在模板中没有任何引用。`OverviewTab` 内部自己重新计算了一遍。

---

### SUGGESTION-6: 缺少页面级的空状态引导

项目列表空状态有"新建第一个项目"引导按钮（✅ 好），但其他 Tab（成员、文档、审批等）的空状态只显示 `el-empty`，缺少操作引导。

---

### SUGGESTION-7: 甘特图缺少与任务列表的双向同步

甘特图中的任务拖拽/编辑不会实时反映到任务列表 Tab，需要手动刷新页面。

---

### SUGGESTION-8: 移动端适配缺失

所有页面均未考虑移动端响应式设计。看板视图虽然有 `overflow-x: auto`，但在小屏幕上体验很差。

---

### SUGGESTION-9: `pms-utils.ts` 的 `parseDate` 函数未处理时区问题

日期格式化使用 `new Date(dateStr)` 解析，对于 `YYYY-MM-DD` 格式在某些浏览器中会被当作 UTC 时间，可能导致日期偏移一天。

---

## 六、交叉问题汇总

### 6.1 用户名解析 — 跨 5 个文件的核心问题

| 组件 | 是否加载 userList | 解析方式 | 结果 |
|------|-------------------|----------|------|
| `project-detail/index.vue` | ✅ 加载 | `find + fallback` | 部分正常 |
| `TaskListTab.vue` | ❌ 未加载 | 直接硬编码 `用户${id}` | ❌ 100% 失效 |
| `project/index.vue` | ❌ 未加载 | 直接硬编码 `用户${id}` | ❌ 100% 失效 |
| `MembersTab.vue` | ✅ 加载 | `find + fallback` | ✅ 正常 |
| `project-create/index.vue` | ✅ 加载 | `find + fallback` | ✅ 正常 |

---

### 6.2 API 参数类型不一致

| API 模块 | create/update 参数 | delete/get 参数 | 问题 |
|----------|-------------------|-----------------|------|
| task | `TaskVO` (string) | `string` | ✅ |
| project | `ProjectVO` (string) | `string` | ✅ |
| member | `ProjectMemberVO` (string) | `number` | ❌ delete 用 number |
| approval | `ApprovalRecordVO` (string) | `number` | ❌ delete/get 用 number |
| document | `DocumentVO` (string) | `number` | ❌ delete/get 用 number |
| change | `ChangeRecordVO` (string) | `number` | ❌ delete/get 用 number |
| quality | `QualityIssueVO` (mixed) | `number` | ❌ issueId 类型混乱 |
| stage | `StageVO` (mixed) | `number` | ❌ stageId 类型混乱 |

---

### 6.3 字典数据与硬编码的脱节

| 位置 | 数据来源 | 与字典管理同步 |
|------|----------|---------------|
| `pms-utils.ts` | 硬编码常量 | ❌ |
| `dict/index.vue` | localStorage | ✅ (自身) |
| `project-create/index.vue` | 从 pms-utils 导入 | ❌ |
| `project-detail/index.vue` (创建任务弹窗) | 从 pms-utils 导入 | ❌ |
| `MembersTab.vue` (角色选择) | 模板硬编码 | ❌ |
| `QualityTab.vue` (严重程度/分类) | 组件内硬编码 | ❌ |

---

## 七、优先级修复路线图

### 第一轮（致命 - 立即修复）
1. **FATAL-1**: 修复 `getProject(Number(projectId))` 精度问题
2. **FATAL-2**: 添加 PMS 路由剩余定义
3. **FATAL-3**: 全局用户名解析方案 — 创建 `useUserName` composable 或全局 store
4. **FATAL-4**: 实现"我负责的"/"我参与的"筛选逻辑

### 第二轮（严重 - 本周修复）
5. **SEVERE-1**: 优化任务状态流转逻辑
6. **SEVERE-2**: 修复看板延期列逻辑
7. **SEVERE-4**: 实现或隐藏批量下载
8. **SEVERE-8**: 审批操作添加确认弹窗
9. **SEVERE-12**: 为 member API 添加 projectId 参数
10. **SEVERE-14**: 统一 approval API 的 ID 类型

### 第三轮（一般 + 建议 - 下个迭代）
11. 字典数据后端持久化 (SEVERE-13)
12. 全部 API ID 类型统一为 string
13. 功能未完成的按钮添加 disabled 状态
14. 移动端适配
15. 全局错误边界
