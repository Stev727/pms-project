import { ref } from 'vue'
import { getMyProjectPermissions } from '@/api/pms/permission'

/**
 * 项目级权限 Composable（#2 权限分级）
 *
 * 与 checkPermi（yudao 菜单级权限）的分工：
 * - checkPermi(['pms:task:create'])  → 能不能进这个功能（全局菜单级）
 * - can('task_create')               → 在当前项目里能不能干这件事（项目级）
 *
 * 典型用法：两者叠加
 *   v-if="checkPermi(['pms:material:create']) && can('material_add')"
 *
 * 设计要点：
 * 1. 全局单例缓存，同一项目只请求一次，多个 Tab 共享
 * 2. 权限未加载完成时 can() 返回 false（保守策略，避免闪现无权按钮）
 * 3. projectId 一律按 string 处理，避免雪花ID精度丢失
 */

// 全局缓存：projectId -> 权限点集合
const permCache = ref<Map<string, Set<string>>>(new Map())
// 进行中的请求，避免同一项目并发重复请求
const pendingMap = new Map<string, Promise<Set<string>>>()

export function useProjectPerm() {
  const permKeys = ref<Set<string>>(new Set())
  const permLoading = ref(false)
  const permLoaded = ref(false)

  /**
   * 加载指定项目的权限点集合
   * @param projectId 项目ID
   * @param force 是否强制刷新（权限矩阵保存后调用）
   */
  async function loadPerm(projectId: string | number, force = false): Promise<Set<string>> {
    const pid = String(projectId || '')
    if (!pid) {
      permKeys.value = new Set()
      permLoaded.value = true
      return permKeys.value
    }

    if (!force && permCache.value.has(pid)) {
      permKeys.value = permCache.value.get(pid)!
      permLoaded.value = true
      return permKeys.value
    }

    if (!force && pendingMap.has(pid)) {
      const result = await pendingMap.get(pid)!
      permKeys.value = result
      permLoaded.value = true
      return result
    }

    permLoading.value = true
    const task = (async (): Promise<Set<string>> => {
      try {
        const res = await getMyProjectPermissions(pid)
        const set = new Set<string>((res as string[]) || [])
        permCache.value.set(pid, set)
        return set
      } catch {
        // 请求失败按无权限处理，宁可少显示按钮也不误放权限
        const empty = new Set<string>()
        permCache.value.set(pid, empty)
        return empty
      } finally {
        permLoading.value = false
        pendingMap.delete(pid)
      }
    })()

    pendingMap.set(pid, task)
    const result = await task
    permKeys.value = result
    permLoaded.value = true
    return result
  }

  /**
   * 判断是否拥有某权限点
   * @param permKey 权限点，如 material_add / task_review
   */
  function can(permKey: string): boolean {
    if (!permKey) return false
    return permKeys.value.has(permKey)
  }

  /**
   * 是否拥有任意一个权限点
   */
  function canAny(...keys: string[]): boolean {
    return keys.some((k) => permKeys.value.has(k))
  }

  /**
   * 是否拥有全部权限点
   */
  function canAll(...keys: string[]): boolean {
    return keys.every((k) => permKeys.value.has(k))
  }

  /**
   * 清除缓存。不传 projectId 则清空全部
   */
  function clearPermCache(projectId?: string | number) {
    if (projectId) {
      permCache.value.delete(String(projectId))
    } else {
      permCache.value.clear()
    }
  }

  return {
    permKeys,
    permLoading,
    permLoaded,
    loadPerm,
    can,
    canAny,
    canAll,
    clearPermCache
  }
}

/** 权限点常量，避免各处硬编码字符串拼错 */
export const PERM = {
  TASK_CREATE: 'task_create',
  TASK_EDIT: 'task_edit',
  TASK_DELETE: 'task_delete',
  TASK_ASSIGN: 'task_assign',
  TASK_REVIEW: 'task_review',

  DOCUMENT_VIEW: 'document_view',
  DOCUMENT_PREVIEW: 'document_preview',
  DOCUMENT_DOWNLOAD: 'document_download',
  DOCUMENT_UPLOAD: 'document_upload',
  DOCUMENT_DELETE: 'document_delete',
  DOCUMENT_MANAGE_PERM: 'document_manage_perm',

  MATERIAL_VIEW: 'material_view',
  MATERIAL_ADD: 'material_add',
  MATERIAL_EDIT: 'material_edit',
  MATERIAL_DELETE: 'material_delete',

  QUALITY_VIEW: 'quality_view',
  QUALITY_ADD: 'quality_add',
  QUALITY_EDIT: 'quality_edit',
  QUALITY_DELETE: 'quality_delete',
  QUALITY_IMPORT: 'quality_import',

  MEMBER_MANAGE: 'member_manage',
  PROJECT_EDIT: 'project_edit',
  PERMISSION_MANAGE: 'permission_manage'
} as const

