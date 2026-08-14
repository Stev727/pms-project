import { usePermissionStoreWithOut } from '@/store/modules/permission'
import { Layout } from '@/utils/routerHelper'

/**
 * 取当前登录用户权限下的第一个「真实菜单页面」路径。
 *
 * 用于登录成功后的默认落点：替代固定的 dashboard 首页（/），
 * 让不同权限的用户登录后直接进入其菜单中第一个可见的业务页面。
 *
 * 过滤规则：
 * - 跳过 hidden（侧边栏不可见）的项
 * - 跳过布局容器（顶层目录 component = Layout，嵌套目录 = ParentLayout）
 * - 要求有真实 component（排除纯 redirect 节点）
 * addRouters 已由 flatMultiLevelRoutes 展平，path 为完整绝对路径。
 */
export function getFirstMenuPath(): string {
  const permissionStore = usePermissionStoreWithOut()
  const routes = permissionStore.getAddRouters
  for (const route of routes) {
    if (route.meta?.hidden) continue
    const comp = route.component as any
    if (!comp) continue
    // 跳过目录/布局容器（顶层目录 = Layout，嵌套目录 = ParentLayout）
    if (comp === Layout || comp?.name === 'ParentLayout') continue
    return route.path
  }
  return '/'
}
