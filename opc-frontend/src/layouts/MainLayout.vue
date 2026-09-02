<template>
  <div
    class="app-shell archive-shell"
    :class="[
      {
        'home-shell': isHome,
        'sidebar-collapsed': sidebarCollapsed,
        'mobile-sidebar-open': mobileSidebarOpen,
      },
      routeClass,
    ]"
  >
    <button
      v-if="!isHome"
      class="mobile-menu-button"
      type="button"
      :aria-expanded="mobileSidebarOpen"
      aria-label="打开导航"
      @click="mobileSidebarOpen = true"
    >
      <span></span>
    </button>
    <button
      v-if="!isHome && mobileSidebarOpen"
      class="mobile-menu-backdrop"
      type="button"
      aria-label="关闭导航"
      @click="mobileSidebarOpen = false"
    ></button>
    <aside v-if="!isHome" class="sidebar site-sidebar archive-sidebar" aria-label="主导航">
      <div class="sidebar-head">
        <RouterLink class="brand archive-brand" to="/" aria-label="SoloFirm OPC Platform 首页">
          <BrandMark />
          <span class="brand-text">
            <strong>SoloFirm</strong>
            <small>OPC Platform</small>
          </span>
        </RouterLink>
        <button
          class="sidebar-toggle"
          type="button"
          :aria-label="sidebarCollapsed ? '展开导航栏' : '收起导航栏'"
          :title="sidebarCollapsed ? '展开导航栏' : '收起导航栏'"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          <span></span>
        </button>
      </div>

      <nav class="nav side-nav archive-nav" aria-label="页面导航">
        <RouterLink
          to="/"
          aria-label="首页概览"
          active-class="route-link-active"
          exact-active-class="route-link-exact-active"
          :class="{ 'nav-active': isNavActive('home') }"
        >
          <span>首页概览</span>
          <small>Index overview</small>
        </RouterLink>
        <RouterLink
          to="/analysis"
          aria-label="资料分析"
          active-class="route-link-active"
          exact-active-class="route-link-exact-active"
          :class="{ 'nav-active': isNavActive('analysis') }"
        >
          <span>资料分析</span>
          <small>Data analysis</small>
        </RouterLink>
        <RouterLink
          to="/analytics"
          aria-label="研究数据看板"
          active-class="route-link-active"
          exact-active-class="route-link-exact-active"
          :class="{ 'nav-active': isNavActive('analytics') }"
        >
          <span>研究数据看板</span>
          <small>Research metrics</small>
        </RouterLink>
        <RouterLink
          to="/regions"
          aria-label="地区目录"
          active-class="route-link-active"
          exact-active-class="route-link-exact-active"
          :class="{ 'nav-active': isNavActive('regions') }"
        >
          <span>地区目录</span>
          <small>Region directory</small>
        </RouterLink>
        <RouterLink
          to="/policies"
          aria-label="政策索引"
          active-class="route-link-active"
          exact-active-class="route-link-exact-active"
          :class="{ 'nav-active': isNavActive('policies') }"
        >
          <span>政策索引</span>
          <small>Policy archive</small>
        </RouterLink>
        <RouterLink
          to="/cases"
          aria-label="案例索引"
          active-class="route-link-active"
          exact-active-class="route-link-exact-active"
          :class="{ 'nav-active': isNavActive('cases') }"
        >
          <span>案例索引</span>
          <small>Case archive</small>
        </RouterLink>
        <div
          ref="universityNavGroup"
          class="nav-group university-nav-group"
          :class="{
            'nav-group-open': universityMenuOpen,
            'nav-group-active': universityMenuActive,
          }"
        >
          <button
            type="button"
            class="nav-group-trigger"
            :aria-expanded="universityMenuOpen"
            aria-controls="university-opc-subnav"
            :aria-label="universityMenuOpen ? '收起高校 OPC 子菜单' : '展开高校 OPC 子菜单'"
            @click="universityMenuOpen = !universityMenuOpen"
            @keydown.esc="universityMenuOpen = false"
          >
            <span class="nav-group-copy">
              <span class="nav-group-title">高校 OPC</span>
              <small>University OPC</small>
            </span>
            <ChevronDown :size="16" aria-hidden="true" />
          </button>
          <Transition name="university-submenu">
            <div
              v-if="universityMenuOpen"
              id="university-opc-subnav"
              class="nav-submenu"
            >
              <RouterLink to="/university-opc?tab=communities" :class="{ 'nav-active': universityTabActive('communities') }" @click="universityMenuOpen = false">
                <span class="nav-submenu-copy">
                  <span class="nav-submenu-title">OPC 社区</span>
                  <small>OPC communities</small>
                </span>
              </RouterLink>
              <RouterLink to="/university-opc?tab=support" :class="{ 'nav-active': universityTabActive('support') }" @click="universityMenuOpen = false">
                <span class="nav-submenu-copy">
                  <span class="nav-submenu-title">支持措施</span>
                  <small>Support measures</small>
                </span>
              </RouterLink>
              <RouterLink to="/university-opc?tab=activities" :class="{ 'nav-active': universityTabActive('activities') }" @click="universityMenuOpen = false">
                <span class="nav-submenu-copy">
                  <span class="nav-submenu-title">竞赛活动</span>
                  <small>Competition activities</small>
                </span>
              </RouterLink>
              <RouterLink to="/university-opc?tab=cases" :class="{ 'nav-active': universityTabActive('cases') }" @click="universityMenuOpen = false">
                <span class="nav-submenu-copy">
                  <span class="nav-submenu-title">高校创业案例</span>
                  <small>University venture cases</small>
                </span>
              </RouterLink>
            </div>
          </Transition>
        </div>
        <RouterLink
          to="/sources"
          aria-label="来源台账"
          active-class="route-link-active"
          exact-active-class="route-link-exact-active"
          :class="{ 'nav-active': isNavActive('sources') }"
        >
          <span>来源台账</span>
          <small>Source ledger</small>
        </RouterLink>
      </nav>

      <div class="archive-sidebar-foot">
        <RouterLink
          class="archive-account-entry"
          :class="{ 'is-authenticated': userLoggedIn }"
          :to="accountEntryTarget"
          :aria-label="userLoggedIn ? '进入个人主页' : '登录后进入个人主页'"
          :title="userLoggedIn ? '进入个人主页' : '登录后进入个人主页'"
          @click="mobileSidebarOpen = false"
        >
          <UserRound :size="20" :stroke-width="1.7" aria-hidden="true" />
          <span class="archive-account-copy">
            <strong>个人主页</strong>
            <small>{{ userLoggedIn ? 'Personal account' : 'Login required' }}</small>
          </span>
        </RouterLink>
      </div>
    </aside>

    <main class="main archive-main">
      <header v-if="!isHome" class="public-page-heading">
        <div>
          <h1>{{ routeTitle }}</h1>
          <p>{{ routeSubtitle }}</p>
        </div>
        <div v-if="route.name !== 'analysis-overview' && route.name !== 'analytics-dashboard'" class="topbar-status">
          <span></span>
          公开索引模式
        </div>
      </header>

      <section class="content content-shell">
        <RouterView />
      </section>
    </main>

    <button
      v-if="showBackToTop"
      class="public-back-to-top"
      type="button"
      aria-label="返回页面顶部"
      title="返回顶部"
      @click="scrollToTop"
    >
      <ArrowUp :size="19" aria-hidden="true" />
    </button>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowUp, ChevronDown, UserRound } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'
import { isUserAuthenticated } from '@/api/auth'

const route = useRoute()

const isHome = computed(() => route.name === 'home')
const sidebarCollapsed = ref(false)
const mobileSidebarOpen = ref(false)
const universityMenuOpen = ref(route.name === 'university-opc')
const universityNavGroup = ref(null)
const showBackToTop = ref(false)
const routeClass = computed(() => (route.name ? `route-${route.name}` : ''))
const userLoggedIn = computed(() => Boolean(route.fullPath) && isUserAuthenticated())
const accountEntryTarget = computed(() => (userLoggedIn.value ? '/account' : '/login'))
const universityMenuActive = computed(() => isNavActive('university-opc'))

watch(
  () => route.fullPath,
  () => {
    mobileSidebarOpen.value = false
  },
)

watch(
  () => route.name,
  (name, previousName) => {
    if (name === 'university-opc' && previousName !== 'university-opc') {
      universityMenuOpen.value = true
    }
    if (name !== 'university-opc') {
      universityMenuOpen.value = false
    }
  },
)

const routeTitle = computed(() => {
  const titles = {
    home: '首页概览',
    'analysis-overview': '资料分析',
    'analytics-dashboard': '研究数据看板',
    'region-directory': '地区目录',
    'policy-list': '政策索引',
    'policy-detail': '政策详情',
    'case-list': '案例索引',
    'case-detail': '案例详情',
    'university-opc': '高校 OPC',
    'source-ledger': '来源台账',
    'user-account': '个人主页',
  }
  return titles[route.name] || 'SoloFirm'
})

const routeSubtitle = computed(() => {
  const subtitles = {
    home: '汇总全国 AI + OPC 相关政策、案例、来源与地区覆盖情况。',
    'analysis-overview': '呈现平台访问热度、地区资料排行、政策趋势走向、来源覆盖度与案例分布概况。',
    'analytics-dashboard': '查看当前可用于研究的已发布、已核验数据指标及其数据版本。',
    'region-directory': '按省份查看政策与案例资料覆盖情况，便于识别资料集中的重点区域。',
    'policy-list': '按地区、政策类型及关键词快速检索目标政策。',
    'policy-detail': '查看政策摘要、支持措施、来源链接和关键字段。',
    'case-list': '汇集一人公司与 AI 创业案例，支持多维度检索与分析。',
    'case-detail': '查看案例主体、模式、工具和成果记录。',
    'university-opc': '展示高校 OPC 社区、支持措施、竞赛活动与高校创业项目。',
    'source-ledger': '查看来源链接、文件名、访问日期及状态，确保资料可追溯、可复核。',
    'user-account': '管理你的 SoloFirm 账号与个人资料空间。',
  }
  return subtitles[route.name] || '资料库工作台'
})

function isNavActive(section) {
  const sectionMap = {
    home: ['home'],
    analysis: ['analysis-overview'],
    analytics: ['analytics-dashboard'],
    regions: ['region-directory'],
    policies: ['policy-list', 'policy-detail'],
    cases: ['case-list', 'case-detail'],
    'university-opc': ['university-opc'],
    sources: ['source-ledger'],
  }
  return sectionMap[section]?.includes(route.name)
}

function universityTabActive(tab) {
  return route.name === 'university-opc' && (route.query?.tab || 'communities') === tab
}

function updateBackToTopVisibility() {
  showBackToTop.value = window.scrollY > 480
}

function scrollToTop() {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' })
}

function closeUniversityMenuOnOutsidePointer(event) {
  if (universityNavGroup.value && !universityNavGroup.value.contains(event.target)) {
    universityMenuOpen.value = false
  }
}

function closeUniversityMenuOnEscape(event) {
  if (event.key === 'Escape') {
    universityMenuOpen.value = false
  }
}

onMounted(() => {
  updateBackToTopVisibility()
  window.addEventListener('scroll', updateBackToTopVisibility, { passive: true })
  document.addEventListener('pointerdown', closeUniversityMenuOnOutsidePointer)
  document.addEventListener('keydown', closeUniversityMenuOnEscape)
})

onUnmounted(() => {
  window.removeEventListener('scroll', updateBackToTopVisibility)
  document.removeEventListener('pointerdown', closeUniversityMenuOnOutsidePointer)
  document.removeEventListener('keydown', closeUniversityMenuOnEscape)
})
</script>
