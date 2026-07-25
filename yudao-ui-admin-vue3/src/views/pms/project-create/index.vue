<template>
  <div class="pms-project-create">
    <ContentWrap>
      <!-- 步骤条 (3 steps) -->
      <el-steps :active="currentStep" finish-status="success" align-center class="mb-30px" :key="currentStep">
        <el-step
          title="选择模板与成员"
          :status="currentStep === 0 ? 'process' : currentStep > 0 ? 'finish' : 'wait'"
        >
          <template #icon><el-icon><Document /></el-icon></template>
        </el-step>
        <el-step
          title="调整任务"
          :status="currentStep === 1 ? 'process' : currentStep > 1 ? 'finish' : 'wait'"
        >
          <template #icon><el-icon><List /></el-icon></template>
        </el-step>
        <el-step
          title="规则与确认"
          :status="currentStep === 2 ? 'process' : 'wait'"
        >
          <template #icon><el-icon><Check /></el-icon></template>
        </el-step>
      </el-steps>

      <!-- 步骤1: 选择模板 + 填写信息 + 选择成员 -->
      <div v-if="currentStep === 0" class="step-content">
        <!-- 模板选择 -->
        <div class="section-title">选择项目模板</div>
        <div class="template-cards">
          <div
            v-for="tpl in templateList"
            :key="tpl.projectId"
            class="template-card"
            :class="{ selected: selectedTemplate === tpl.projectId }"
            @click="selectTemplate(tpl.projectId)"
          >
            <el-icon class="template-icon"><Document /></el-icon>
            <div class="template-name">{{ tpl.projectName }}</div>
            <div class="template-desc">{{ tpl.description || '标准研发流程模板' }}</div>
            <div class="template-stats">
              <el-tag size="small" type="info">{{ getTemplateStageCount(tpl.projectId) }} 阶段</el-tag>
              <el-tag size="small" type="info">{{ getTemplateTaskCount(tpl.projectId) }} 任务</el-tag>
            </div>
            <el-icon v-if="selectedTemplate === tpl.projectId" class="check-icon"><CircleCheckFilled /></el-icon>
          </div>
        </div>

        <!-- 模板预览 -->
        <div v-if="previewTasks.length" class="template-preview">
          <div class="section-title">模板预览（{{ previewTasks.length }} 个任务）</div>
          <el-table :data="previewTasks" border size="small" max-height="360">
            <el-table-column prop="stageName" label="阶段" width="100">
              <template #default="{ row }">
                <el-tag :color="getPhaseColor(row.stageName)" effect="dark" size="small">{{ row.stageName }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="taskName" label="任务名称" min-width="200" />
            <el-table-column prop="roleName" label="负责角色" width="120" />
            <el-table-column prop="cycle" label="工期(天)" width="90" align="center" />
          </el-table>
        </div>

        <!-- 项目基本信息 -->
        <el-divider content-position="left">项目基本信息</el-divider>
        <el-form ref="formRef" :model="projectForm" :rules="formRules" label-width="100px" class="project-form">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="项目名称" prop="projectName">
                <el-input v-model="projectForm.projectName" placeholder="请输入项目名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="项目编号">
                <el-input v-model="projectForm.projectCode" placeholder="自动生成或手动填写" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="项目类型" prop="projectType">
                <el-select v-model="projectForm.projectType" placeholder="请选择" class="w-full">
                  <el-option v-for="opt in projectTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="优先级" prop="priority">
                <el-select v-model="projectForm.priority" placeholder="请选择" class="w-full">
                  <el-option v-for="opt in priorityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="项目经理" prop="projectManagerId">
                <el-select v-model="projectForm.projectManagerId" filterable remote placeholder="请选择" class="w-full" :remote-method="searchUsers" :loading="remoteLoading" @change="onManagerChange">
                  <el-option v-for="u in remoteUserList" :key="u.id" :label="`${u.nickname}`" :value="String(u.id)" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属部门" prop="deptId">
                <el-tree-select
                  v-model="projectForm.deptId"
                  :data="deptTree"
                  :props="{ value: 'id', label: 'name', children: 'children' }"
                  check-strictly clearable filterable
                  placeholder="请选择部门"
                  class="w-full"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="计划开始" prop="planStartDate">
                <el-date-picker v-model="projectForm.planStartDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="计划结束" prop="planEndDate">
                <el-date-picker v-model="projectForm.planEndDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="项目预算">
                <el-input-number v-model="projectForm.budget" :min="0" :precision="2" placeholder="元" class="w-full" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="重点项目">
                <el-switch v-model="projectForm.isKeyProject" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="项目描述">
            <el-input v-model="projectForm.description" type="textarea" :rows="3" placeholder="项目背景、目标等" />
          </el-form-item>
        </el-form>

        <!-- 项目成员 -->
        <el-divider content-position="left">项目成员</el-divider>
        <div class="member-toolbar">
          <span class="member-summary">已选择 {{ selectedMembers.length }} 名成员（项目经理会自动加入）</span>
          <el-button type="primary" size="small" @click="handleAddMember">
            <el-icon><Plus /></el-icon> 添加成员
          </el-button>
        </div>
        <el-table :data="selectedMembers" border size="small" v-if="selectedMembers.length">
          <el-table-column label="成员" min-width="140">
            <template #default="{ row }">
              <span>{{ getManagerName(row.userId) }}</span>
              <el-tag v-if="row.isExternal" size="small" type="warning" style="margin-left: 6px">外部</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="项目角色" width="200">
            <template #default="{ row }">
              <el-select v-model="row.roleCode" size="small" class="w-full" :disabled="row.roleCode === 'pm'">
                <el-option label="项目经理" value="pm" />
                <el-option label="部门负责人" value="dept_head" />
                <el-option label="主责任人" value="main_owner" />
                <el-option label="协助人" value="helper" />
                <el-option label="管理层" value="management" />
                <el-option label="外部成员" value="external" />
                <el-option label="系统管理员" value="admin" />
                <el-option label="开发工程师" value="developer" />
                <el-option label="硬件工程师" value="hw_engineer" />
                <el-option label="软件工程师" value="sw_engineer" />
                <el-option label="结构工程师" value="mechanical_engineer" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row, $index }">
              <el-button link type="danger" size="small" @click="removeMember($index)" v-if="row.roleCode !== 'pm'">移除</el-button>
              <span v-else style="font-size: 12px; color: #86909C">自动</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂未添加成员，请点击&quot;添加成员&quot;" :image-size="60" />

        <!-- 添加成员弹窗 -->
        <el-dialog v-model="showMemberDialog" title="添加成员" width="480px">
          <el-form label-width="80px">
            <el-form-item label="成员" required>
              <el-select v-model="memberForm.userId" filterable remote placeholder="搜索用户名" class="w-full" :remote-method="searchUsers" :loading="remoteLoading">
                <el-option v-for="u in dialogUserList" :key="u.id" :label="u.nickname" :value="String(u.id)" />
              </el-select>
            </el-form-item>
            <el-form-item label="项目角色" required>
              <el-select v-model="memberForm.roleCode" class="w-full">
                <el-option label="项目经理" value="pm" />
                <el-option label="部门负责人" value="dept_head" />
                <el-option label="主责任人" value="main_owner" />
                <el-option label="协助人" value="helper" />
                <el-option label="管理层" value="management" />
                <el-option label="外部成员" value="external" />
                <el-option label="系统管理员" value="admin" />
                <el-option label="开发工程师" value="developer" />
                <el-option label="硬件工程师" value="hw_engineer" />
                <el-option label="软件工程师" value="sw_engineer" />
                <el-option label="结构工程师" value="mechanical_engineer" />
              </el-select>
            </el-form-item>
            <el-form-item label="是否外部">
              <el-switch v-model="memberForm.isExternal" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showMemberDialog = false">取消</el-button>
            <el-button type="primary" @click="confirmAddMember">添加</el-button>
          </template>
        </el-dialog>

        <div class="step-footer">
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :disabled="!selectedTemplate" @click="nextStep">
            下一步 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 步骤2: 调整任务 -->
      <div v-if="currentStep === 1" class="step-content">
        <!-- 批量操作栏 -->
        <div class="batch-toolbar">
          <div class="batch-actions">
            <span class="batch-label">批量操作：</span>
            <el-select v-model="batchStage" placeholder="选择阶段" size="small" style="width: 130px" clearable>
              <el-option v-for="s in stageList" :key="s.stageName" :label="s.stageName" :value="s.stageName" />
            </el-select>
            <el-select v-model="batchAction" size="small" style="width: 130px" placeholder="选择操作">
              <el-option label="偏移日期" value="offset_date" />
              <el-option label="批量设责任人" value="set_owner" />
              <el-option label="批量设优先级" value="set_priority" />
              <el-option label="批量设工期" value="set_cycle" />
            </el-select>
            <template v-if="batchAction === 'offset_date'">
              <el-input-number v-model="batchOffsetDays" :min="-365" :max="365" size="small" style="width: 100px" />
              <span style="font-size: 13px; color: #86909C">天（正数延后，负数提前）</span>
            </template>
            <template v-if="batchAction === 'set_owner'">
              <el-select v-model="batchOwner" filterable placeholder="选择责任人" size="small" style="width: 150px">
                <el-option v-for="u in projectMemberUsers" :key="u.id" :label="`${u.nickname}`" :value="String(u.id)" />
              </el-select>
            </template>
            <template v-if="batchAction === 'set_priority'">
              <el-select v-model="batchPriority" size="small" style="width: 100px">
                <el-option v-for="opt in priorityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </template>
            <template v-if="batchAction === 'set_cycle'">
              <el-input-number v-model="batchCycle" :min="1" :max="365" size="small" style="width: 100px" />
              <span style="font-size: 13px; color: #86909C">天</span>
            </template>
            <el-button type="primary" size="small" @click="applyBatchAction" :disabled="!batchStage || !batchAction">
              应用
            </el-button>
          </div>
        </div>

        <div class="task-toolbar">
          <span class="task-summary">基于模板已生成 <b>{{ adjustedTasks.length }}</b> 个任务</span>
          <el-button type="primary" size="small" @click="showAddTask = true">
            <el-icon><Plus /></el-icon> 添加任务
          </el-button>
        </div>

        <el-table :data="stageGroups" border size="small" row-key="stageName" default-expand-all
          :tree-props="{ children: 'tasks' }">
          <el-table-column prop="taskName" label="任务名称" min-width="200">
            <template #default="{ row }">
              <el-icon v-if="row.isStage" :color="getPhaseColor(row.stageName)"><Folder /></el-icon>
              <el-icon v-else-if="row.isMilestone" color="#FF7D00"><Star /></el-icon>
              <span :style="{ fontWeight: row.isStage ? 'bold' : 'normal' }">{{ row.taskName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="负责角色" width="120">
            <template #default="{ row }">
              <span v-if="!row.isStage">{{ getTaskRoleName(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="责任人" width="100">
            <template #default="{ row }">
              <span v-if="!row.isStage">{{ row.ownerName || (row.mainOwnerId ? getManagerName(row.mainOwnerId) : '未指派') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="计划开始" width="110">
            <template #default="{ row }">
              <span v-if="!row.isStage" style="font-size: 12px">{{ row.planStartDate || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="计划结束" width="110">
            <template #default="{ row }">
              <span v-if="!row.isStage" style="font-size: 12px">{{ row.planEndDate || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="cycle" label="工期(天)" width="90" align="center" />
          <el-table-column prop="priority" label="优先级" width="80">
            <template #default="{ row }">
              <span v-if="!row.isStage" :style="{ color: priorityMap[row.priority]?.color }">
                {{ priorityMap[row.priority]?.label || '普通' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row, $index }">
              <template v-if="!row.isStage">
                <el-button link type="primary" size="small" @click="editTask(row, $index)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeTask(row)">删除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>

        <div class="task-stats-bar">
          <span>任务总数: <b>{{ adjustedTasks.length }}</b></span>
          <span>里程碑: <b>{{ adjustedTasks.filter(t => t.isMilestone).length }}</b></span>
          <span>预估总工期: <b>{{ totalCycleDays }}</b> 天</span>
        </div>

        <div class="step-footer">
          <el-button @click="prevStep"><el-icon><ArrowLeft /></el-icon> 上一步</el-button>
          <el-button type="primary" @click="nextStep">下一步 <el-icon><ArrowRight /></el-icon></el-button>
        </div>

        <!-- 添加/编辑任务弹窗 -->
        <el-dialog v-model="showAddTask" :title="editingTask ? '编辑任务' : '添加任务'" width="560px">
          <el-form ref="taskFormRef" :model="taskForm" label-width="90px">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="任务名称" required>
                  <el-input v-model="taskForm.taskName" placeholder="请输入" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="所属阶段" required>
                  <el-select v-model="taskForm.stageName" placeholder="请选择" class="w-full">
                    <el-option v-for="s in stageList" :key="s.stageName" :label="s.stageName" :value="s.stageName" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="计划开始">
                  <el-date-picker v-model="taskForm.planStartDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="计划结束">
                  <el-date-picker v-model="taskForm.planEndDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" :disabled-date="disabledEndDate" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item v-if="false" label="工期(天)">
              <el-input-number v-model="taskForm.cycle" :min="1" :max="365" class="w-full" />
            </el-form-item>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="优先级">
                  <el-select v-model="taskForm.priority" class="w-full">
                    <el-option v-for="opt in priorityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="里程碑">
                  <el-switch v-model="taskForm.isMilestone" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="责任人">
                  <el-select v-model="taskForm.mainOwnerId" filterable placeholder="选择责任人" class="w-full" clearable>
                    <el-option v-for="u in projectMemberUsers" :key="u.id" :label="`${u.nickname}`" :value="String(u.id)" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="协助人">
                  <el-select v-model="taskForm.helperIds" multiple filterable placeholder="可选协助人" class="w-full">
                    <el-option v-for="u in projectMemberUsers" :key="u.id" :label="`${u.nickname}`" :value="String(u.id)" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="任务描述">
              <el-input v-model="taskForm.description" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="输出物要求">
              <el-input v-model="taskForm.outputRequirement" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showAddTask = false">取消</el-button>
            <el-button type="primary" @click="confirmAddTask">确定</el-button>
          </template>
        </el-dialog>
      </div>

      <!-- 步骤3: 默认规则与确认 -->
      <div v-if="currentStep === 2" class="step-content">
        <el-card shadow="never" class="confirm-card mb-16px">
          <template #header><span class="card-title">项目默认规则</span></template>
          <el-form-item label="通知模式" required>
            <el-select v-model="selectedNotifyModeId" placeholder="请选择通知模式" class="w-full">
              <el-option v-for="mode in notifyModeList" :key="mode.modeId" :label="mode.modeName" :value="mode.modeId" />
            </el-select>
          </el-form-item>
          <div class="text-gray">创建后复制所选模式的规则作为项目快照；后续修改公共模式不会影响本项目。</div>
        </el-card>
        <el-alert title="请确认以下项目信息无误，提交后将自动创建项目并生成任务" type="info" :closable="false" show-icon class="mb-20px" />

        <el-card shadow="never" class="confirm-card">
          <template #header><span class="card-title">项目信息</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="项目名称">{{ projectForm.projectName }}</el-descriptions-item>
            <el-descriptions-item label="项目编号">{{ projectForm.projectCode || '自动生成' }}</el-descriptions-item>
            <el-descriptions-item label="项目类型">{{ getTypeLabel(projectForm.projectType) }}</el-descriptions-item>
            <el-descriptions-item label="优先级">{{ priorityMap[projectForm.priority]?.label || '-' }}</el-descriptions-item>
            <el-descriptions-item label="项目经理">{{ getManagerName(projectForm.projectManagerId) }}</el-descriptions-item>
            <el-descriptions-item label="所属部门">{{ getDeptName(projectForm.deptId) }}</el-descriptions-item>
            <el-descriptions-item label="计划周期">{{ projectForm.planStartDate }} ~ {{ projectForm.planEndDate }} ({{ totalDays }}天)</el-descriptions-item>
            <el-descriptions-item label="项目预算">{{ projectForm.budget ? '￥' + projectForm.budget : '-' }}</el-descriptions-item>
            <el-descriptions-item label="重点项目">{{ projectForm.isKeyProject ? '是' : '否' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" class="confirm-card mt-16px">
          <template #header><span class="card-title">项目成员（{{ selectedMembers.length }} 人）</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item v-for="m in selectedMembers" :key="m.userId" :label="getManagerName(m.userId)">
              {{ getRoleLabel(m.roleCode) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" class="confirm-card mt-16px">
          <template #header><span class="card-title">任务概览</span></template>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="总任务数">{{ adjustedTasks.length }}</el-descriptions-item>
            <el-descriptions-item label="里程碑数">{{ adjustedTasks.filter(t => t.isMilestone).length }}</el-descriptions-item>
            <el-descriptions-item label="阶段数">{{ stageGroups.length }}</el-descriptions-item>
          </el-descriptions>
          <div class="stage-summary">
            <el-tag v-for="sg in stageGroups" :key="sg.stageName" :color="getPhaseColor(sg.stageName)" effect="dark" size="small" class="mr-8px">
              {{ sg.stageName }} ({{ sg.tasks?.length || 0 }})
            </el-tag>
          </div>
        </el-card>

        <div class="checkbox-area">
          <el-checkbox v-model="notifyMembers">创建后自动发送通知给项目成员</el-checkbox>
          <el-checkbox v-model="autoGantt">创建后自动生成甘特图</el-checkbox>
        </div>

        <div class="step-footer">
          <el-button @click="prevStep"><el-icon><ArrowLeft /></el-icon> 上一步</el-button>
          <el-button type="primary" :loading="submitting" @click="submitCreate">
            <el-icon v-if="!submitting"><Check /></el-icon> 提交创建
          </el-button>
        </div>
      </div>
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Document, List, CircleCheckFilled, ArrowRight, ArrowLeft, Plus, Folder, Star, Check } from '@element-plus/icons-vue'
import { getProjectList, getProject, createProjectBundle, ProjectVO } from '@/api/pms/project'
import { getStageList, StageVO } from '@/api/pms/stage'
import { getTaskList, TaskVO } from '@/api/pms/task'
import { useUserNames } from '@/hooks/pms/useUserNames'
import { getSimpleDeptList } from '@/api/system/dept'
import { getNotifyModeList } from '@/api/pms/notify'
import {
  phaseColorMap, projectTypeOptions, taskTypeOptions, priorityOptions,
  priorityMap, calcDuration
} from '../pms-utils'

defineOptions({ name: 'PmsProjectCreate' })

const router = useRouter()
const currentStep = ref(0)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const notifyMembers = ref(true)
const autoGantt = ref(true)
const notifyModeList = ref<any[]>([])
const selectedNotifyModeId = ref<string | number>()

// 模板数据
const templateList = ref<ProjectVO[]>([])
const selectedTemplate = ref<number | string>()
const previewTasks = ref<any[]>([])
const templateStageMap = ref<Record<string | number, number>>({})
const templateTaskMap = ref<Record<string | number, number>>({})

// 项目成员 (带角色)
const selectedMembers = ref<Array<{
  userId: string
  roleCode: string
  isExternal: boolean
  status: string
}>>([])

// 添加成员弹窗
const showMemberDialog = ref(false)
const memberForm = reactive({
  userId: undefined as string | undefined,
  roleCode: 'helper',
  isExternal: false
})

// 项目表单
const projectForm = reactive({
  projectName: '',
  projectCode: '',
  projectType: '',
  priority: 'normal',
  projectManagerId: undefined as string | undefined,
  deptId: undefined as number | undefined,
  planStartDate: '',
  planEndDate: '',
  budget: undefined as number | undefined,
  isKeyProject: false,
  description: ''
})

const formRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  priority: [{ required: false }],
  projectManagerId: [{ required: true, message: '请选择项目经理', trigger: 'change' }],
  deptId: [{ required: false }],
  planStartDate: [{ required: false }],
  planEndDate: [
    { required: false },
    {
      validator: (rule, value, callback) => {
        if (value && projectForm.planStartDate && new Date(value) <= new Date(projectForm.planStartDate)) {
          callback(new Error('计划结束日期必须大于计划开始日期'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

// 用户和部门
const { userList, ensureLoaded: ensureUsersLoaded, searchUsers, remoteUserList, remoteLoading } = useUserNames()
const deptTree = ref<any[]>([])

// 已选成员的用户信息（供任务调整步骤使用）
const projectMemberUsers = computed(() => {
  const ids = new Set(selectedMembers.value.map(m => String(m.userId)))
  return userList.value.filter(u => ids.has(String(u.id)))
})

// 可添加的成员（排除已选）
const availableUsers = computed(() => {
  const memberIds = new Set(selectedMembers.value.map(m => String(m.userId)))
  return userList.value.filter(u => !memberIds.has(String(u.id)))
})

// 弹窗中可选用户（远程搜索结果中排除已选成员）
const dialogUserList = computed(() => {
  const memberIds = new Set(selectedMembers.value.map(m => String(m.userId)))
  return remoteUserList.value.filter(u => !memberIds.has(String(u.id)))
})

// 角色标签
const roleLabelMap: Record<string, string> = {
  pm: '项目经理', dept_head: '部门负责人', main_owner: '主责任人', helper: '协助人',
  management: '管理层', external: '外部成员', admin: '系统管理员',
  developer: '开发工程师', hw_engineer: '硬件工程师', sw_engineer: '软件工程师',
  mechanical_engineer: '结构工程师', project_manager: '项目经理', member: '成员'
}
function getRoleLabel(code: string): string { return roleLabelMap[code] || code || '-' }

// 根据用户ID查找项目角色名
function getRoleNameByUserId(userId?: string | number): string {
  if (!userId) return ''
  const idStr = String(userId)
  const member = selectedMembers.value.find(m => String(m.userId) === idStr)
  if (member) return getRoleLabel(member.roleCode)
  return ''
}

// 获取任务的角色名（优先根据责任人反查）
function getTaskRoleName(row: any): string {
  if (row.mainOwnerId) {
    const autoRole = getRoleNameByUserId(row.mainOwnerId)
    if (autoRole) return autoRole
  }
  return row.roleName || '-'
}

// 项目经理变更时自动同步到成员列表
function onManagerChange(val: string) {
  if (!val) return
  const idStr = String(val)
  // 移除旧的 PM 成员
  const oldPmIdx = selectedMembers.value.findIndex(m => m.roleCode === 'pm')
  if (oldPmIdx > -1) selectedMembers.value.splice(oldPmIdx, 1)
  // 添加新的 PM 成员
  const existing = selectedMembers.value.find(m => String(m.userId) === idStr)
  if (existing) {
    existing.roleCode = 'pm'
  } else {
    selectedMembers.value.unshift({ userId: idStr, roleCode: 'pm', isExternal: false, status: 'active' })
  }
}

// 添加成员
function handleAddMember() {
  memberForm.userId = undefined
  memberForm.roleCode = 'helper'
  memberForm.isExternal = false
  searchUsers('')
  showMemberDialog.value = true
}

function confirmAddMember() {
  if (!memberForm.userId) { ElMessage.warning('请选择成员'); return }
  const idStr = String(memberForm.userId)
  if (selectedMembers.value.find(m => String(m.userId) === idStr)) {
    ElMessage.warning('该用户已在成员列表中')
    return
  }
  selectedMembers.value.push({
    userId: idStr,
    roleCode: memberForm.roleCode,
    isExternal: memberForm.isExternal,
    status: 'active'
  })
  showMemberDialog.value = false
  ElMessage.success('成员已添加')
}

function removeMember(index: number) {
  const member = selectedMembers.value[index]
  if (member.roleCode === 'pm') {
    ElMessage.warning('项目经理不可移除')
    return
  }
  selectedMembers.value.splice(index, 1)
}

// 任务数据
const stageList = ref<StageVO[]>([])
const adjustedTasks = ref<any[]>([])
const showAddTask = ref(false)
const editingTask = ref<any>(null)
const taskForm = reactive({
  taskName: '',
  stageName: '',
  taskType: 'design',
  cycle: 5,
  planStartDate: '',
  planEndDate: '',
  priority: 'normal',
  isMilestone: false,
  mainOwnerId: undefined as number | undefined,
  helperIds: [] as number[],
  description: '',
  outputRequirement: '',
  roleName: ''
})

  // 工期自动计算：日期变化算工期，工期变化算结束日期
  let _autoCalc = false
  watch(() => taskForm.planStartDate, (v) => {
    if (_autoCalc) return
    if (v && taskForm.planEndDate) {
      _autoCalc = true
      taskForm.cycle = calcDuration(v, taskForm.planEndDate)
      _autoCalc = false
    } else if (v && taskForm.cycle > 0 && !taskForm.planEndDate) {
      _autoCalc = true
      taskForm.planEndDate = addDays(v, taskForm.cycle - 1)
      _autoCalc = false
    }
  })
  watch(() => taskForm.planEndDate, (v) => {
    if (_autoCalc) return
    if (v && taskForm.planStartDate) {
      _autoCalc = true
      taskForm.cycle = calcDuration(taskForm.planStartDate, v)
      _autoCalc = false
    }
  })
  watch(() => taskForm.cycle, (v) => {
    if (_autoCalc) return
    if (v && v > 0 && taskForm.planStartDate) {
      _autoCalc = true
      taskForm.planEndDate = addDays(taskForm.planStartDate, v - 1)
      _autoCalc = false
    }
  })

// 禁用早于计划开始日期的结束日期
function disabledEndDate(date: Date) {
  if (!taskForm.planStartDate) return false
  return date.getTime() < new Date(taskForm.planStartDate + ' 00:00:00').getTime()
}

// 计算属性
const stageGroups = computed(() => {
  const groups: Record<string, any[]> = {}
  adjustedTasks.value.forEach(t => {
    const stage = t.stageName || '未分组'
    if (!groups[stage]) groups[stage] = []
    groups[stage].push(t)
  })
  return Object.entries(groups).map(([stageName, tasks]) => ({
    stageName,
    taskName: stageName + ` (${tasks.length} 个任务)`,
    isStage: true,
    tasks: tasks.map(t => ({ ...t, roleName: t.roleName || '未指定' }))
  }))
})

const totalCycleDays = computed(() => {
  return adjustedTasks.value.reduce((sum, t) => sum + (t.cycle || 0), 0)
})

const totalDays = computed(() => {
  if (!projectForm.planStartDate || !projectForm.planEndDate) return 0
  return calcDuration(projectForm.planStartDate, projectForm.planEndDate)
})

// 方法
function getPhaseColor(stageName: string): string {
  return phaseColorMap[stageName]?.color || '#2468F2'
}

function getTypeLabel(type: string): string {
  return projectTypeOptions.find(o => o.value === type)?.label || type || '-'
}

function getManagerName(id?: number | string): string {
  if (id === undefined || id === null) return '-'
  const idStr = String(id)
  return userList.value.find(u => String(u.id) === idStr)?.nickname || '-'
}

function getDeptName(id?: number | string): string {
  if (!id) return '-'
  const idStr = String(id)
  const findDept = (nodes: any[]): any => {
    for (const n of nodes || []) {
      if (String(n.id) === idStr) return n
      if (n.children) {
        const found = findDept(n.children)
        if (found) return found
      }
    }
    return null
  }
  return findDept(deptTree.value)?.name || '-'
}

async function selectTemplate(id: number | string) {
  selectedTemplate.value = id
  try {
    const tpl = await getProject(String(id))
    previewTasks.value = (tpl as any)?.tasks || []
    await loadTemplateTasks(id)
  } catch (e) {
    console.error('预览模板失败', e)
  }
}

async function loadTemplateTasks(templateId: number | string) {
  const tplId = String(templateId)
  try {
    const [stages, tasks] = await Promise.all([getStageList(), getTaskList({ projectType: 'standard_template' })])
    const tplStages = (stages as StageVO[]).filter(s => String(s.projectId) === tplId)
    stageList.value = tplStages
    const tplTasks = (tasks as TaskVO[]).filter(t => String(t.projectId) === tplId)
    adjustedTasks.value = tplTasks.map(t => {
      const matchedStage = tplStages.find(s => String(s.stageId) === String(t.stageId))
      return {
        ...t,
        stageName: matchedStage?.stageName || '未分组',
        stageId: matchedStage?.stageId ?? t.stageId,
        roleName: 'PM',
        cycle: t.cycle || 5,
        priority: t.priority || 'normal',
        isMilestone: !!t.isMilestone
      }
    })
    if (tplTasks.length === 0 && String(selectedTemplate.value) === tplId) {
      ElMessage.warning("当前模板没有已保存的任务明细，请先到模板管理补充并保存任务")
    }
    templateStageMap.value[tplId] = tplStages.length
    templateTaskMap.value[tplId] = tplTasks.length
  } catch (e) {
    console.error('加载模板任务失败', e)
    adjustedTasks.value = []
  }
}

function getTemplateStageCount(projectId?: number | string) {
  if (!projectId) return 0
  return templateStageMap.value[String(projectId)] || 0
}

function getTemplateTaskCount(projectId?: number | string) {
  if (!projectId) return 0
  return templateTaskMap.value[String(projectId)] || 0
}

function nextStep() {
  if (currentStep.value === 0) {
    // 验证表单
    formRef.value?.validate(valid => {
      if (!valid) return
      // 确保项目经理在成员列表中
      const pmId = String(projectForm.projectManagerId)
      if (pmId && !selectedMembers.value.find(m => String(m.userId) === pmId)) {
        selectedMembers.value.unshift({ userId: pmId, roleCode: 'pm', isExternal: false, status: 'active' })
      }
      if (selectedMembers.value.length === 0) {
        ElMessage.warning('请至少添加一名项目成员')
        return
      }
      currentStep.value++
    })
    return
  }
  currentStep.value++
}

function prevStep() {
  if (currentStep.value === 1 && adjustedTasks.value.length > 0) {
    ElMessageBox.confirm(
      '返回上一步更换模板将丢失当前所有任务调整，是否继续？',
      '警告',
      { confirmButtonText: '继续返回', cancelButtonText: '取消', type: 'warning' }
    ).then(() => {
      adjustedTasks.value = []
      currentStep.value--
    }).catch(() => {})
  } else {
    currentStep.value--
  }
}

function goBack() {
  router.push('/pms/project')
}

function editTask(row: any, _index: number) {
  editingTask.value = row
  Object.assign(taskForm, {
    taskName: row.taskName,
    stageName: row.stageName,
    taskType: row.taskType || 'design',
    cycle: row.cycle || 5,
    planStartDate: row.planStartDate || '',
    planEndDate: row.planEndDate || '',
    priority: row.priority || 'normal',
    isMilestone: row.isMilestone || false,
    mainOwnerId: row.mainOwnerId || undefined,
    helperIds: row.helperIds || [],
    description: row.description || '',
    outputRequirement: row.outputRequirement || ''
  })
  showAddTask.value = true
}

function removeTask(row: any) {
  ElMessageBox.confirm(`确认删除任务「${row.taskName}」？`, '提示', { type: 'warning' }).then(() => {
    const idx = adjustedTasks.value.findIndex(t => t.taskId === row.taskId)
    if (idx > -1) adjustedTasks.value.splice(idx, 1)
    ElMessage.success('任务已删除')
  }).catch(() => {})
}

function confirmAddTask() {
  if (!taskForm.taskName) {
    ElMessage.warning('请输入任务名称')
    return
  }
  if (taskForm.planStartDate && taskForm.planEndDate && taskForm.planEndDate < taskForm.planStartDate) {
    ElMessage.warning('计划结束日期不能小于计划开始日期')
    return
  }
  if (editingTask.value) {
    Object.assign(editingTask.value, { ...taskForm })
    // 根据责任人自动带出角色
    if (taskForm.mainOwnerId) {
      const autoRole = getRoleNameByUserId(taskForm.mainOwnerId)
      if (autoRole) editingTask.value.roleName = autoRole
    }
    editingTask.value = null
  } else {
    const newTask: any = {
      taskId: Date.now(),
      ...taskForm
    }
    // 根据责任人自动带出角色
    if (taskForm.mainOwnerId) {
      const autoRole = getRoleNameByUserId(taskForm.mainOwnerId)
      if (autoRole) newTask.roleName = autoRole
    }
    adjustedTasks.value.push(newTask)
  }
  showAddTask.value = false
  Object.assign(taskForm, {
    taskName: '', stageName: '', taskType: 'design', cycle: 5,
    planStartDate: '', planEndDate: '',
    priority: 'normal', isMilestone: false, mainOwnerId: undefined, helperIds: [],
    description: '', outputRequirement: ''
  })
}

// 批量操作
const batchStage = ref('')
const batchAction = ref('')
const batchOffsetDays = ref(0)
const batchOwner = ref<number>()
const batchPriority = ref('normal')
const batchCycle = ref(5)

function applyBatchAction() {
  if (!batchStage.value || !batchAction.value) {
    ElMessage.warning('请选择阶段和操作')
    return
  }
  const stageTasks = adjustedTasks.value.filter(t => t.stageName === batchStage.value)
  if (stageTasks.length === 0) {
    ElMessage.warning(`阶段「${batchStage.value}」下没有任务`)
    return
  }

  const addDays = (dateStr: string, days: number): string => {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    d.setDate(d.getDate() + days)
    return d.toISOString().split('T')[0]
  }

  switch (batchAction.value) {
    case 'offset_date':
      for (const t of stageTasks) {
        if (t.planStartDate) t.planStartDate = addDays(t.planStartDate, batchOffsetDays.value)
        if (t.planEndDate) t.planEndDate = addDays(t.planEndDate, batchOffsetDays.value)
      }
      ElMessage.success(`已偏移 ${stageTasks.length} 个任务`)
      break
    case 'set_owner':
      if (!batchOwner.value) { ElMessage.warning('请选择责任人'); return }
      const ownerName = userList.value.find(u => String(u.id) === String(batchOwner.value))?.nickname || `用户${batchOwner.value}`
      const ownerRole = getRoleNameByUserId(batchOwner.value)
      for (const t of stageTasks) {
        t.mainOwnerId = batchOwner.value
        t.ownerName = ownerName
        if (ownerRole) t.roleName = ownerRole
      }
      ElMessage.success(`已设置 ${stageTasks.length} 个任务的责任人`)
      break
    case 'set_priority':
      for (const t of stageTasks) t.priority = batchPriority.value
      ElMessage.success(`已设置 ${stageTasks.length} 个任务的优先级`)
      break
    case 'set_cycle':
      for (const t of stageTasks) t.cycle = batchCycle.value
      ElMessage.success(`已设置 ${stageTasks.length} 个任务的工期`)
      break
  }
  batchAction.value = ''
}

// 草稿保存
const DRAFT_KEY = 'pms_project_create_draft'

function saveDraft() {
  const draft = {
    currentStep: currentStep.value,
    projectForm: { ...projectForm },
    selectedMembers: selectedMembers.value,
    selectedNotifyModeId: selectedNotifyModeId.value,
    selectedTemplate: selectedTemplate.value,
    adjustedTasks: adjustedTasks.value,
    savedAt: new Date().toISOString()
  }
  localStorage.setItem(DRAFT_KEY, JSON.stringify(draft))
}

function loadDraft(): boolean {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return false
    const draft = JSON.parse(raw)
    if (!draft.savedAt) return false
    if (Date.now() - new Date(draft.savedAt).getTime() > 24 * 60 * 60 * 1000) {
      localStorage.removeItem(DRAFT_KEY)
      return false
    }
    return true
  } catch { return false }
}

function restoreDraft() {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return
    const draft = JSON.parse(raw)
    currentStep.value = draft.currentStep || 0
    Object.assign(projectForm, draft.projectForm || {})
    // 兼容旧草稿：selectedMemberIds -> selectedMembers
    if (draft.selectedMembers) {
      selectedMembers.value = draft.selectedMembers
    } else if (draft.selectedMemberIds) {
      selectedMembers.value = draft.selectedMemberIds.map((id: string) => ({
        userId: String(id),
        roleCode: String(id) === String(draft.projectForm?.projectManagerId) ? 'pm' : 'member',
        isExternal: false,
        status: 'active'
      }))
    }
    selectedNotifyModeId.value = draft.selectedNotifyModeId
    selectedTemplate.value = draft.selectedTemplate
    adjustedTasks.value = draft.adjustedTasks || []
    ElMessage.success('已恢复未完成的创建')
  } catch { /* ignore */ }
}

function clearDraft() {
  stopAutoSaveDraft()
  localStorage.removeItem(DRAFT_KEY)
}

let draftTimer: any = null
function startAutoSaveDraft() {
  draftTimer = setInterval(saveDraft, 30000)
}
function stopAutoSaveDraft() {
  if (draftTimer) { clearInterval(draftTimer); draftTimer = null }
}

const addDays = (dateStr: string, days: number): string => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  d.setDate(d.getDate() + days)
  return d.toISOString().split('T')[0]
}

const calcTaskPlanEndDate = (task: any): string => {
  const end = task.planEndDate || addDays(projectForm.planStartDate, task.cycle || 5)
  if (projectForm.planEndDate && end > projectForm.planEndDate) {
    return projectForm.planEndDate
  }
  return end
}

async function submitCreate() {
  if (!selectedNotifyModeId.value) { ElMessage.warning("请选择通知模式"); return }
  submitting.value = true
  try {
    const tasks = adjustedTasks.value.map(t => ({
      taskName: t.taskName,
      stageId: t.stageId,
      taskType: t.taskType || 'design',
      cycle: t.cycle || 5,
      priority: t.priority || 'normal',
      isMilestone: !!t.isMilestone,
      mainOwnerId: t.mainOwnerId,
      helperIds: Array.isArray(t.helperIds) && t.helperIds.length > 0 ? t.helperIds.join(',') : null,
      description: t.description || '',
      outputRequirement: t.outputRequirement || '',
      planStartDate: t.planStartDate || projectForm.planStartDate,
      planEndDate: calcTaskPlanEndDate(t),
      roleName: t.roleName
    }))
    const members = selectedMembers.value.map(m => ({
      userId: String(m.userId),
      roleCode: m.roleCode,
      isExternal: m.isExternal,
      status: m.status || 'active'
    }))
    const projectId = await createProjectBundle({
      project: { ...projectForm, createMethod: 'template', templateId: selectedTemplate.value as number, status: 'initiating' },
      members, tasks, notifyModeId: selectedNotifyModeId.value
    })
    clearDraft()
    ElMessage.success('项目创建成功！')
    if (!projectId) {
      ElMessage.error('创建成功但未获取到项目ID，请刷新列表查看')
      router.push('/pms/project')
      return
    }
    router.push(`/pms/project-detail/${projectId}`)
  } catch (e) {
    console.error('创建项目失败', e)
    ElMessage.error('创建项目失败，请重试')
  } finally {
    submitting.value = false
  }
}

// 加载初始数据
onMounted(async () => {
  if (loadDraft()) {
    ElMessageBox.confirm(
      '检测到未完成的创建草稿，是否继续？',
      '恢复草稿',
      { confirmButtonText: '继续创建', cancelButtonText: '重新开始', type: 'info' }
    ).then(() => {
      restoreDraft()
      if (selectedTemplate.value) {
        selectTemplate(selectedTemplate.value)
      }
    }).catch(() => {
      clearDraft()
    })
  }

  startAutoSaveDraft()

  try {
    notifyModeList.value = ((await getNotifyModeList()) || []).filter((m: any) => m.status === "enabled")
    selectedNotifyModeId.value = notifyModeList.value.find((m: any) => m.defaultFlag)?.modeId || notifyModeList.value[0]?.modeId
  } catch (e) { ElMessage.error("加载通知模式失败") }
  try {
    const projects = await getProjectList({ projectType: 'standard_template' })
    templateList.value = ((projects as ProjectVO[]) || []).filter(
      p => p.projectType === 'standard_template'
    )
  } catch (e) {
    console.error('加载模板列表失败', e)
    ElMessage.error('加载模板列表失败')
  }
  try {
    await ensureUsersLoaded()
  } catch (e) {
    console.error('加载用户列表失败', e)
  }
  try {
    const depts = await getSimpleDeptList()
    deptTree.value = depts || []
  } catch (e) {
    console.error('加载部门列表失败', e)
  }
  try {
    const [stages, tasks] = await Promise.all([getStageList(), getTaskList({ projectType: 'standard_template' })])
    for (const tpl of templateList.value) {
      if (!tpl.projectId) continue
      const templateId = String(tpl.projectId)
      templateStageMap.value[templateId] = (stages || []).filter(s => String(s.projectId) === templateId).length
      templateTaskMap.value[templateId] = (tasks || []).filter(t => String(t.projectId) === templateId).length
    }
  } catch (e) {
    console.error('加载模板统计失败', e)
  }
})

onUnmounted(() => {
  stopAutoSaveDraft()
})
</script>

<style scoped lang="scss">
.pms-project-create {
  .step-content { min-height: 400px; }

  .section-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; color: var(--el-text-color-primary); }

  .template-cards {
    display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 24px;
    .template-card {
      width: 220px; padding: 20px; border: 2px solid var(--el-border-color-light);
      border-radius: 8px; cursor: pointer; text-align: center; position: relative;
      transition: all 0.2s;
      &:hover { border-color: var(--el-color-primary-light-5); box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
      &.selected { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
      .template-icon { font-size: 36px; color: var(--el-color-primary); margin-bottom: 8px; }
      .template-name { font-size: 16px; font-weight: 600; margin-bottom: 4px; }
      .template-desc { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 8px; }
      .template-stats { display: flex; gap: 8px; justify-content: center; }
      .check-icon {
        position: absolute; top: 8px; right: 8px; font-size: 20px; color: var(--el-color-primary);
      }
    }
  }

  .template-preview {
    .section-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
  }

  .member-toolbar {
    display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;
    .member-summary { font-size: 14px; color: var(--el-text-color-secondary); }
  }

  .step-footer {
    display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; padding-top: 16px;
    border-top: 1px solid var(--el-border-color-lighter);
  }

  .task-toolbar {
    display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
    .task-summary { font-size: 14px; }
  }

  .task-stats-bar {
    display: flex; gap: 24px; margin-top: 16px; padding: 12px 16px;
    background: var(--el-fill-color-light); border-radius: 4px;
    span { font-size: 14px; }
  }

  .confirm-card {
    .card-title { font-weight: 600; }
  }

  .stage-summary { margin-top: 12px; }

  .checkbox-area {
    margin-top: 20px; display: flex; flex-direction: column; gap: 8px;
  }
}
</style>
