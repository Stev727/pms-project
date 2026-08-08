import request from '@/config/axios'

/**
 * PMS BI 看板部门数据范围 API（#9 BI 看板按部门数据权限）
 *
 * 现状：PMS Dashboard 原本是纯前端实现，直接调 getProjectList/getTaskList/getStageList
 * 全量拉取后渲染图表。这些接口本身已做"自己参与"过滤，部门负责人看不到本部门其他同事的项目。
 * 本组接口由后端 PmsDataScopeService 统一控制可见范围，前端 dashboard 改用本组接口。
 */

/** 部门简单 DTO（与后端 DeptRespDTO 对齐） */
export interface PmsDeptVO {
  id: number
  name: string
  parentId: number
  leaderUserId?: number
  status?: number
}

/** 部门树节点（前端组树用） */
export interface PmsDeptTreeNode extends PmsDeptVO {
  children?: PmsDeptTreeNode[]
}

/**
 * 获取当前用户可见部门树（用于 BI 看板顶部筛选器）。
 * 后端已按数据范围过滤：超管看全部，部门负责人看本部门+下级，普通用户看自己所在部门+下级。
 */
export const getVisibleDeptTree = (): Promise<PmsDeptVO[]> => {
  return request.get({ url: '/pms/dashboard/depts' })
}

/**
 * 获取 BI 看板可见项目列表。
 * @param deptId 可选，部门筛选（含下级部门）
 */
export const getDashboardProjects = (deptId?: number | string): Promise<any[]> => {
  const url = deptId ? `/pms/dashboard/projects?deptId=${deptId}` : '/pms/dashboard/projects'
  return request.get({ url })
}

/**
 * 获取 BI 看板可见任务列表。
 * @param deptId 可选，部门筛选（含下级部门）
 */
export const getDashboardTasks = (deptId?: number | string): Promise<any[]> => {
  const url = deptId ? `/pms/dashboard/tasks?deptId=${deptId}` : '/pms/dashboard/tasks'
  return request.get({ url })
}

/**
 * 获取 BI 看板可见阶段列表。
 * @param deptId 可选，部门筛选（含下级部门）
 */
export const getDashboardStages = (deptId?: number | string): Promise<any[]> => {
  const url = deptId ? `/pms/dashboard/stages?deptId=${deptId}` : '/pms/dashboard/stages'
  return request.get({ url })
}

/**
 * 把扁平部门列表组装成树。
 * 后端返回扁平 List<DeptRespDTO>，前端转成树挂到 el-tree-select。
 */
export const buildDeptTree = (list: PmsDeptVO[] = []): PmsDeptTreeNode[] => {
  if (!list || list.length === 0) return []
  const map = new Map<number, PmsDeptTreeNode>()
  list.forEach((item) => map.set(item.id, { ...item, children: [] }))
  const roots: PmsDeptTreeNode[] = []
  map.forEach((node) => {
    const parent = map.get(node.parentId)
    if (parent) {
      parent.children!.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

