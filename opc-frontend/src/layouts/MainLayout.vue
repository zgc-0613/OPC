<template>
  <div
    class="app-shell archive-shell"
    :class="[
      {
        'home-shell': isHome,
        'assistant-route-shell': isAssistant,
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
      <header v-if="!isHome && !isAssistant" class="public-page-heading">
        <div>
          <h1>{{ routeTitle }}</h1>
          <p>{{ routeSubtitle }}</p>
        </div>
        <div v-if="!['analysis-overview', 'assistant'].includes(route.name)" class="topbar-status">
          <span></span>
          公开索引模式
        </div>
      </header>

      <section :class="isAssistant ? 'assistant-content-shell' : 'content content-shell'">
        <RouterView />
      </section>
    </main>

    <button
      v-if="showBackToTop && !isAssistant"
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
import { ArrowUp, UserRound } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'
import { isUserAuthenticated } from '@/api/auth'

const route = useRoute()

const isHome = computed(() => route.name === 'home')
const isAssistant = computed(() => route.name === 'assistant')
const sidebarCollapsed = ref(false)
const mobileSidebarOpen = ref(false)
const showBackToTop = ref(false)
const routeClass = computed(() => (route.name ? `route-${route.name}` : ''))
const userLoggedIn = computed(() => Boolean(route.fullPath) && isUserAuthenticated())
const accountEntryTarget = computed(() => (userLoggedIn.value ? '/account' : '/login'))

watch(
  () => route.fullPath,
  () => {
    mobileSidebarOpen.value = false
  },
)

const routeTitle = computed(() => {
  const titles = {
    home: '首页概览',
    'analysis-overview': '资料分析',
    'region-directory': '地区目录',
    'policy-list': '政策索引',
    'policy-detail': '政策详情',
    'case-list': '案例索引',
    'case-detail': '案例详情',
    assistant: '创业研究助手',
    'source-ledger': '来源台账',
    'user-account': '个人主页',
  }
  return titles[route.name] || 'SoloFirm'
})

const routeSubtitle = computed(() => {
  const subtitles = {
    home: '汇总全国 AI + OPC 相关政策、案例、来源与地区覆盖情况。',
    'analysis-overview': '呈现平台访问热度、地区资料排行、政策趋势走向、来源覆盖度与案例分布概况。',
    'region-directory': '按省份查看政策与案例资料覆盖情况，便于识别资料集中的重点区域。',
    'policy-list': '按地区、政策类型及关键词快速检索目标政策。',
    'policy-detail': '查看政策摘要、支持措施、来源链接和关键字段。',
    'case-list': '汇集一人公司与 AI 创业案例，支持多维度检索与分析。',
    'case-detail': '查看案例主体、模式、工具和成果记录。',
    assistant: '结合创业画像检索本地已核验案例与政策，生成带来源依据的行动建议。',
    'source-ledger': '查看来源链接、文件名、访问日期及状态，确保资料可追溯、可复核。',
    'user-account': '管理你的 SoloFirm 账号与个人资料空间。',
  }
  return subtitles[route.name] || '资料库工作台'
})

function isNavActive(section) {
  const sectionMap = {
    home: ['home'],
    analysis: ['analysis-overview'],
    regions: ['region-directory'],
    policies: ['policy-list', 'policy-detail'],
    cases: ['case-list', 'case-detail'],
    sources: ['source-ledger'],
  }
  return sectionMap[section]?.includes(route.name)
}

function updateBackToTopVisibility() {
  showBackToTop.value = window.scrollY > 480
}

function scrollToTop() {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' })
}

onMounted(() => {
  updateBackToTopVisibility()
  window.addEventListener('scroll', updateBackToTopVisibility, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', updateBackToTopVisibility)
})
</script>

<style scoped>
.assistant-route-shell {
  width: 100%;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  overflow: hidden;
}

.assistant-route-shell .archive-main,
.assistant-content-shell {
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
</style>
