import { ref } from 'vue'
import { getProjectMemberList } from '@/api/pms/member'
import { getSimpleUserList } from '@/api/system/user'

/**
 * 项目成员 Composable — 按 projectId 获取当前项目的有效成员
 * 用于责任人、协助人等下拉数据源，替代全公司用户列表
 *
 * 核心设计：
 * 1. 内存缓存：同一项目成员数据只加载一次
 * 2. 交集计算：取项目成员 ∩ 系统用户，确保数据一致性
 * 3. 状态过滤：只返回 status=active 的成员
 */

const memberCache = ref<Map<string, any[]>>(new Map())
const loading = ref(false)

export function useProjectMembers() {
  const projectMemberUsers = ref<any[]>([])
  const memberLoading = ref(false)

  /**
   * 加载指定项目的活跃成员用户列表
   * @param projectId 项目ID
   * @returns 该项目的活跃成员用户列表
   */
  async function loadProjectMembers(projectId: string): Promise<any[]> {
    if (!projectId) {
      projectMemberUsers.value = []
      return []
    }

    // 从缓存获取
    if (memberCache.value.has(projectId)) {
      const cached = memberCache.value.get(projectId)!
      projectMemberUsers.value = cached
      return cached
    }

    memberLoading.value = true
    try {
      // 并行获取项目成员和全公司用户，取交集
      const [members, allUsers] = await Promise.all([
        getProjectMemberList(),
        getSimpleUserList()
      ])

      // 构建当前项目活跃成员的 userId 集合
      const memberUserIds = new Set<string>(
        (members as any[])
          .filter(m =>
            String(m.projectId) === String(projectId) &&
            (m.status === 'active' || !m.status)
          )
          .map(m => String(m.userId))
      )

      // 从全公司用户中筛选出项目成员
      const projectUsers = (allUsers as any[]).filter(u =>
        memberUserIds.has(String(u.id))
      )

      memberCache.value.set(projectId, projectUsers)
      projectMemberUsers.value = projectUsers
      return projectUsers
    } catch {
      projectMemberUsers.value = []
      return []
    } finally {
      memberLoading.value = false
    }
  }

  /**
   * 清除缓存
   * @param projectId 不传则清除所有缓存
   */
  function clearMemberCache(projectId?: string) {
    if (projectId) {
      memberCache.value.delete(projectId)
    } else {
      memberCache.value.clear()
    }
  }

  return {
    projectMemberUsers,
    memberLoading,
    loadProjectMembers,
    clearMemberCache
  }
}
