/**
 * PMS 全局用户名解析 composable
 * 解决多处硬编码 `用户${id}` 的问题
 * 使用单例模式，所有组件共享同一份用户数据
 *
 * P1-02: 新增远程搜索功能，支持 el-select remote 模式
 * 使用 300ms debounce 避免频繁请求
 */
import { ref } from 'vue'
import { getSimpleUserList, getSimpleUserListByNickname } from '@/api/system/user'

// 全局单例
const userList = ref<any[]>([])
const loaded = ref(false)
let loadingPromise: Promise<void> | null = null

// 远程搜索相关
let debounceTimer: ReturnType<typeof setTimeout> | null = null
const remoteUserList = ref<any[]>([])
const remoteLoading = ref(false)

export function useUserNames() {
  /**
   * 确保用户列表已加载（幂等）
   */
  async function ensureLoaded(): Promise<void> {
    if (loaded.value) return
    if (loadingPromise) return loadingPromise
    loadingPromise = (async () => {
      try {
        const users = await getSimpleUserList()
        userList.value = (users as any[]) || []
        loaded.value = true
      } catch {
        userList.value = []
      }
    })()
    return loadingPromise
  }

  /**
   * 远程搜索用户（用于 el-select 的 remote-method）
   * P1-02: 300ms debounce，避免频繁请求
   */
  function searchUsers(query: string) {
    if (debounceTimer) clearTimeout(debounceTimer)
    if (!query || query.trim() === '') {
      remoteUserList.value = []
      return
    }
    remoteLoading.value = true
    debounceTimer = setTimeout(async () => {
      try {
        const users = await getSimpleUserListByNickname(query.trim())
        remoteUserList.value = (users as any[]) || []
      } catch {
        remoteUserList.value = []
      } finally {
        remoteLoading.value = false
      }
    }, 300)
  }

  /**
   * 根据用户ID获取昵称
   * 注意：全程使用字符串比较，避免 Number() 转换雪花ID导致精度丢失
   */
  function getUserName(userId?: number | string): string {
    if (userId === undefined || userId === null || userId === '') return '未分配'
    const idStr = String(userId)
    const user = userList.value.find((u: any) => String(u.id) === idStr)
    return user?.nickname || `用户${idStr}`
  }

  /**
   * 批量获取用户昵称，用指定分隔符连接
   */
  function getUserNames(ids?: (number | string)[], separator = ', '): string {
    if (!ids || ids.length === 0) return '-'
    return ids.map(id => getUserName(id)).join(separator)
  }

  /**
   * 根据逗号分隔的ID字符串获取用户昵称
   */
  function getUserNamesFromStr(idsStr?: string, separator = ', '): string {
    if (!idsStr) return '-'
    const ids = idsStr.split(',').map(s => s.trim()).filter(Boolean)
    return getUserNames(ids, separator)
  }

  /**
   * 获取原始用户列表（用于 el-select 选项）
   */
  function getRawUserList() {
    return userList
  }

  return {
    userList,
    loaded,
    ensureLoaded,
    getUserName,
    getUserNames,
    getUserNamesFromStr,
    getRawUserList,
    // P1-02: 远程搜索
    remoteUserList,
    remoteLoading,
    searchUsers
  }
}
