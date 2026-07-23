import request from '@/config/axios'

export interface ProjectMemberVO {
  memberId: string
  projectId?: string
  userId?: string  // 雪花ID，前端统一使用 string 避免 JS 精度丢失
  roleCode?: string
  isExternal?: boolean
  joinTime?: string
  quitTime?: string
  status?: string
  createTime?: string
}

export const createProjectMember = (data: ProjectMemberVO) => {
  return request.post({ url: '/pms/project-member/create', data })
}

export const updateProjectMember = (data: ProjectMemberVO) => {
  return request.put({ url: '/pms/project-member/update', data })
}

export const deleteProjectMember = (id: string | number) => {
  return request.delete({ url: '/pms/project-member/delete?id=' + id })
}

export const getProjectMember = (id: string | number) => {
  return request.get({ url: '/pms/project-member/get?id=' + id })
}

export const getProjectMemberList = () => {
  return request.get({ url: '/pms/project-member/list' })
}
