<script lang="tsx">
/**
 * 顶栏工具条（含 PMS 消息铃铛改造点）
 *
 * ============================ 改造说明（v2）============================
 * 版本：v2（#4 站内消息中心：在原有「系统站内信」Message 后追加 PMS 业务消息铃铛）
 *
 * 改造点：
 *   - 新增 import PmsMessageBell from '@/components/pms/MessageBell/index.vue'
 *   - 在原有 `<Message />` 之后追加 `<PmsMessageBell />`，
 *     v-if 由 checkPermi(['pms:message:list']) 控制
 *
 * 兼容性：原系统站内信 Message 组件、IM 入口、UserInfo 等保持不变；
 * 两套铃铛并存：系统站内信走 /system/notify/* 接口，
 * PMS 业务消息走 /pms/message/* 接口，互不影响。
 * =====================================================================
 */
import { defineComponent, computed } from 'vue'
import router from '@/router'
import { Message } from '@/layout/components/Message'
import { Collapse } from '@/layout/components/Collapse'
import { UserInfo } from '@/layout/components/UserInfo'
import { Screenfull } from '@/layout/components/Screenfull'
import { Breadcrumb } from '@/layout/components/Breadcrumb'
import { SizeDropdown } from '@/layout/components/SizeDropdown'
import { LocaleDropdown } from '@/layout/components/LocaleDropdown'
import RouterSearch from '@/components/RouterSearch/index.vue'
import TenantVisit from '@/layout/components/TenantVisit/index.vue'
import { useSetting } from '@/layout/components/Setting'
import { useAppStore } from '@/store/modules/app'
import { useDesign } from '@/hooks/web/useDesign'
import { Icon } from '@/components/Icon'
import { checkPermi } from '@/utils/permission'
import { isHorizontalMenuLayout, isMixedNavLayout, isTwoColumnLayout } from '@/utils/layout'
// #4 PMS 业务消息铃铛
import PmsMessageBell from '@/components/pms/MessageBell/index.vue'

const { getPrefixCls, variables } = useDesign()

const prefixCls = getPrefixCls('tool-header')

const appStore = useAppStore()

// 面包屑
const breadcrumb = computed(() => appStore.getBreadcrumb)

// 折叠图标
const hamburger = computed(() => appStore.getHamburger)

// 全屏图标
const screenfull = computed(() => appStore.getScreenfull)

// 搜索图片
const search = computed(() => appStore.search)

// 尺寸图标
const size = computed(() => appStore.getSize)

// 布局
const layout = computed(() => appStore.getLayout)

// 多语言图标
const locale = computed(() => appStore.getLocale)

// 消息图标
const message = computed(() => appStore.getMessage)

// IM即时通讯图标
const im = computed(() => appStore.getIm)

// #4 是否显示 PMS 业务消息铃铛（菜单权限点 pms:message:list）
const showPmsMessage = computed(() => checkPermi(['pms:message:list']))

// 租户切换权限
const hasTenantVisitPermission = computed(
  () => import.meta.env.VITE_APP_TENANT_ENABLE === 'true' && checkPermi(['system:tenant:visit'])
)

// 顶部聊天入口：用路由 name resolve 出完整 URL，在新标签页打开 IM 主页
// 场景考虑：IM 是全屏沉浸式壳，如果在当前页 push 会把原来在用的后台管理界面挤掉；开新 Tab 更符合用户预期
const goToChat = () => {
  // 用路由 name resolve 出完整 URL，在新标签页打开 IM 主页
  const { href } = router.resolve({ name: 'ImHome' })
  window.open(href, '_blank')
}

export default defineComponent({
  name: 'ToolHeader',
  setup() {
    const { t } = useI18n()
    const { openSetting } = useSetting()
    const showSidebarControl = computed(
      () => !isHorizontalMenuLayout(layout.value) || isMixedNavLayout(layout.value)
    )
    const showBreadcrumb = computed(() => !isHorizontalMenuLayout(layout.value))

    return () => (
      <div
        id={`${variables.namespace}-tool-header`}
        class={[
          prefixCls,
          'h-[var(--top-tool-height)] relative px-[var(--top-tool-p-x)] flex items-center justify-between',
          'dark:bg-[var(--el-bg-color)]'
        ]}
      >
        {showSidebarControl.value || showBreadcrumb.value ? (
          <div class="h-full flex items-center">
            {showSidebarControl.value && hamburger.value && !isTwoColumnLayout(layout.value) ? (
              <Collapse class="custom-hover" color="var(--top-header-text-color)"></Collapse>
            ) : undefined}
            {showBreadcrumb.value && breadcrumb.value ? (
              <Breadcrumb class="lt-md:hidden"></Breadcrumb>
            ) : undefined}
          </div>
        ) : undefined}
        <div class="h-full flex items-center">
          {hasTenantVisitPermission.value ? <TenantVisit /> : undefined}
          <div
            class="v-setting custom-hover"
            title={t('setting.projectSetting')}
            onClick={openSetting}
          >
            <Icon color="var(--top-header-text-color)" size={18} icon="ep:setting" />
          </div>
          {screenfull.value ? (
            <Screenfull class="custom-hover" color="var(--top-header-text-color)"></Screenfull>
          ) : undefined}
          {search.value ? (
            <RouterSearch isModal={false} color="var(--top-header-text-color)" />
          ) : undefined}
          {size.value ? (
            <SizeDropdown class="custom-hover" color="var(--top-header-text-color)"></SizeDropdown>
          ) : undefined}
          {locale.value ? (
            <LocaleDropdown
              class="custom-hover"
              color="var(--top-header-text-color)"
            ></LocaleDropdown>
          ) : undefined}
          {message.value ? (
            <Message class="custom-hover" color="var(--top-header-text-color)"></Message>
          ) : undefined}
          {/* #4 PMS 业务消息铃铛：与系统站内信并存，菜单权限点控制显示 */}
          {showPmsMessage.value ? (
            <PmsMessageBell class="custom-hover" color="var(--top-header-text-color)" />
          ) : undefined}
          {/* IM 聊天入口 */}
          {im.value ? (
            <div class="custom-hover" onClick={goToChat}>
              <Icon color="var(--top-header-text-color)" size={18} icon="ep:chat-dot-round" />
            </div>
          ) : undefined}
          <UserInfo></UserInfo>
        </div>
      </div>
    )
  }
})
</script>

<style lang="scss" scoped>
$prefix-cls: #{$namespace}-tool-header;

.#{$prefix-cls} {
  transition: left var(--transition-time-02);
}
</style>

