<template>
  <div class="atlas-shell" :class="[routeClass, { 'atlas-shell-home': isHome, 'atlas-menu-open': mobileMenuOpen, 'atlas-sidebar-collapsed': sidebarCollapsed }]">
    <header class="atlas-topbar">
      <RouterLink class="atlas-topbrand" to="/" aria-label="壹企经纬首页"><BrandMark /><strong>壹企经纬</strong><span class="atlas-brand-divider"></span><strong class="atlas-brand-solo">SoloFirm</strong></RouterLink>
      <nav class="atlas-topnav" aria-label="顶部导航">
        <RouterLink v-for="item in topItems" :key="item.to" :to="item.to" :class="{ 'is-active': sectionActive(item.section) }">{{ item.label }}</RouterLink>
      </nav>
      <div class="atlas-top-actions">
        <RouterLink class="atlas-top-cta" to="/analysis">开始探索 <ArrowRight :size="17" /></RouterLink>
        <RouterLink class="atlas-account-link" :to="userLoggedIn ? '/account' : '/login'" :aria-label="userLoggedIn ? '个人主页' : '登录'"><UserRound :size="19" /></RouterLink>
        <button class="atlas-menu-toggle" type="button" :aria-expanded="mobileMenuOpen" :aria-label="mobileMenuOpen ? '关闭导航菜单' : '打开导航菜单'" @click="mobileMenuOpen = !mobileMenuOpen"><Menu v-if="!mobileMenuOpen" :size="22" /><X v-else :size="22" /><span>{{ mobileMenuOpen ? '关闭' : '菜单' }}</span></button>
      </div>
    </header>

    <div v-if="!isHome" class="atlas-workspace">
      <aside class="atlas-sidebar" :class="{ 'is-open': mobileMenuOpen }" aria-label="侧边导航">
        <div class="atlas-sidebar-head">
          <p class="atlas-sidebar-label">WORKSPACE / 工作区</p>
          <button class="atlas-sidebar-collapse" type="button" aria-controls="atlas-side-nav" :aria-expanded="!sidebarCollapsed" :aria-label="sidebarCollapsed ? '展开侧边导航' : '收起侧边导航'" @click="sidebarCollapsed = !sidebarCollapsed"><ChevronsRight v-if="sidebarCollapsed" :size="19" /><ChevronsLeft v-else :size="19" /></button>
        </div>
        <nav id="atlas-side-nav" aria-label="页面导航">
          <RouterLink v-for="item in sideItems" :key="item.to" :to="item.to" :title="item.label" :aria-label="item.label" :class="{ 'is-active': sectionActive(item.section) }" @click="mobileMenuOpen = false">
            <component :is="item.icon" class="atlas-side-icon" :size="20" :stroke-width="1.8" /><span class="atlas-side-copy"><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
          </RouterLink>
        </nav>
        <div class="atlas-sidebar-foot">
          <RouterLink to="/sources">来源台账 <ArrowUpRight :size="15" /></RouterLink>
          <RouterLink :to="userLoggedIn ? '/account' : '/login'">{{ userLoggedIn ? '个人主页' : '登录 / 注册' }} <ArrowUpRight :size="15" /></RouterLink>
        </div>
      </aside>
      <main class="atlas-main">
        <div class="atlas-workspace-surface">
          <div class="atlas-page-heading"><div><h1>{{ routeTitle }}</h1><p>{{ routeSubtitle }}</p></div></div>
          <div class="atlas-content"><RouterView /></div>
        </div>
      </main>
    </div>
    <main v-else class="atlas-home-main"><RouterView /></main>
    <button v-if="showBackToTop" class="atlas-back-top" type="button" aria-label="返回页面顶部" @click="scrollToTop"><ArrowUp :size="19" /></button>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight, ArrowUp, ArrowUpRight, BarChart3, BriefcaseBusiness, ChevronsLeft, ChevronsRight, GraduationCap, House, Landmark, Layers3, Menu, UserRound, X } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'
import { isUserAuthenticated } from '@/api/auth'

const route = useRoute()
const routeClass = computed(() => route.name ? `route-${route.name}` : '')
const isHome = computed(() => route.name === 'home')
const mobileMenuOpen = ref(false)
const sidebarCollapsed = ref(false)
const showBackToTop = ref(false)
const userLoggedIn = computed(() => Boolean(route.fullPath) && isUserAuthenticated())
const topItems = [
  { to: '/', label: '首页概览', section: 'home' },
  { to: '/analysis', label: '资料分析', section: 'analysis' },
  { to: '/policies', label: '政策索引', section: 'policies' },
  { to: '/cases', label: '案例索引', section: 'cases' },
  { to: '/platforms', label: '平台集合', section: 'platforms' },
  { to: '/university-opc', label: '高校 OPC', section: 'university' },
]
const sideItems = [
  { to: '/', label: '首页概览', description: '发现更多可能', section: 'home', icon: House },
  { to: '/analysis', label: '资料分析', description: '观察研究数据', section: 'analysis', icon: BarChart3 },
  { to: '/policies', label: '政策索引', description: '解读政策要点', section: 'policies', icon: Landmark },
  { to: '/cases', label: '案例索引', description: '借鉴真实实践', section: 'cases', icon: BriefcaseBusiness },
  { to: '/platforms', label: '平台集合', description: '连接工具资源', section: 'platforms', icon: Layers3 },
  { to: '/university-opc', label: '高校 OPC', description: '探索高校生态', section: 'university', icon: GraduationCap },
]
const sectionRoutes = {
  home: ['home'], analysis: ['analysis-overview', 'analytics-dashboard', 'region-directory'],
  policies: ['policy-list', 'policy-detail'], cases: ['case-list', 'case-detail', 'case-analysis'],
  platforms: ['platform-collection'], university: ['university-opc'],
}
const sectionActive = (section) => sectionRoutes[section].includes(route.name)
const titles = {
  'analysis-overview': '资料分析', 'analytics-dashboard': '资料分析', 'region-directory': '资料分析',
  'policy-list': '政策探索', 'policy-detail': '政策详情', 'case-list': '案例洞察',
  'case-detail': '案例详情', 'case-analysis': '案例分析', 'platform-collection': '平台集合',
  'university-opc': '高校 OPC', 'source-ledger': '来源台账', 'user-account': '个人主页',
}
const subtitles = {
  'analysis-overview': '从资料概览、研究指标和地区分布观察 OPC 生态。',
  'analytics-dashboard': '从资料概览、研究指标和地区分布观察 OPC 生态。',
  'region-directory': '从资料概览、研究指标和地区分布观察 OPC 生态。',
  'policy-list': '从海量政策中，发现与你相关的机会。',
  'policy-detail': '查看政策内容、关键字段与来源依据。',
  'case-list': '观察真实实践，找到可借鉴的创业路径。',
  'case-detail': '查看案例主体、模式、工具与成果记录。',
  'case-analysis': '继续研究案例的模式、证据与启示。',
  'platform-collection': '按分类浏览全国生态、地方服务与政务专区的网站入口。',
  'university-opc': '连接高校社区、支持措施、活动与创业实践。',
  'source-ledger': '核查资料来源与访问记录。',
  'user-account': '管理你的账号与个人资料空间。',
}
const routeTitle = computed(() => titles[route.name] || '壹企经纬')
const routeSubtitle = computed(() => subtitles[route.name] || '以数据洞察世界 · 以专业连接价值')
watch(() => route.fullPath, () => { mobileMenuOpen.value = false })
const updateBackToTop = () => { showBackToTop.value = window.scrollY > 500 }
const scrollToTop = () => window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
onMounted(() => { updateBackToTop(); window.addEventListener('scroll', updateBackToTop, { passive: true }) })
onUnmounted(() => window.removeEventListener('scroll', updateBackToTop))
</script>
