import request from '@/config/axios'

/** 项目角色 */
export interface ProjectRoleVO {
  roleId: string // 雪花ID，统一 string 避免精度丢失
  projectId?: string
  roleName?: string
  roleCode?: string
  isSystem?: boolean
  sortOrder?: number
  remark?: string
  memberCount?: number
  createTime?: string
}

/** 权限点 */
export interface PermItemVO {
  permKey: string
  label: string
}

/** 权限点分组 */
export interface PermGroupVO {
  group: string
  items: PermItemVO[]
}

/** 权限矩阵 */
export interface PermMatrixVO {
  projectId: string
  roles: ProjectRoleVO[]
  permGroups: PermGroupVO[]
  /** 已授权项，格式 `${roleId}:${permKey}` */
  grantedPairs: string[]
  editable: boolean
}

/** 获取当前用户在指定项目的权限点集合 */
export const getMyProjectPermissions = (projectId: string | number) => {
  return request.get({ url: '/pms/project-permission/my-permissions?projectId=' + projectId })
}

/** 获取项目权限矩阵 */
export const getPermMatrix = (projectId: string | number) => {
  return request.get({ url: '/pms/project-permission/matrix?projectId=' + projectId })
}

/** 保存项目权限矩阵（整体覆盖） */
export const savePermMatrix = (data: { projectId: string; grantedPairs: string[] }) => {
  return request.post({ url: '/pms/project-permission/save-matrix', data })
}

/** 获取项目角色列表 */
export const getProjectRoleList = (projectId: string | number) => {
  return request.get({ url: '/pms/project-permission/role/list?projectId=' + projectId })
}

/** 创建项目角色 */
export const createProjectRole = (data: ProjectRoleVO) => {
  return request.post({ url: '/pms/project-permission/role/create', data })
}

/** 更新项目角色 */
export const updateProjectRole = (data: ProjectRoleVO) => {
  return request.put({ url: '/pms/project-permission/role/update', data })
}

/** 删除项目角色 */
export const deleteProjectRole = (roleId: string | number) => {
  return request.delete({ url: '/pms/project-permission/role/delete?roleId=' + roleId })
}

/** 按默认模板初始化项目权限（存量项目补数据，幂等） */
export const initProjectPermission = (projectId: string | number) => {
  return request.post({ url: '/pms/project-permission/init?projectId=' + projectId })
}

