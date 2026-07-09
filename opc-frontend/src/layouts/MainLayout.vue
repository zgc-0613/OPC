<template>
  <div
    class="app-shell archive-shell"
    :class="[{ 'home-shell': isHome, 'sidebar-collapsed': sidebarCollapsed }, routeClass]"
  >
    <aside class="sidebar site-sidebar archive-sidebar" aria-label="主导航">
      <div class="sidebar-head">
        <RouterLink class="brand archive-brand" to="/" aria-label="SoloFirm Index 首页">
          <span class="brand-mark archive-brand-mark">SI</span>
          <span class="brand-text">
            <strong>SoloFirm Index</strong>
            <small>壹企经纬</small>
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
        <RouterLink to="/" aria-label="首页概览">
          <span>首页概览</span>
          <small>Index overview</small>
        </RouterLink>
        <RouterLink to="/regions" aria-label="地区目录">
          <span>地区目录</span>
          <small>Region directory</small>
        </RouterLink>
        <RouterLink to="/policies" aria-label="政策索引">
          <span>政策索引</span>
          <small>Policy archive</small>
        </RouterLink>
        <RouterLink to="/cases" aria-label="案例索引">
          <span>案例索引</span>
          <small>Case archive</small>
        </RouterLink>
        <RouterLink to="/sources" aria-label="来源台账">
          <span>来源台账</span>
          <small>Source ledger</small>
        </RouterLink>
      </nav>

    </aside>

    <main class="main archive-main">
      <header v-if="!isHome" class="topbar archive-topbar">
        <div class="topbar-motion-field" aria-hidden="true">
          <span class="motion-line line-a"></span>
          <span class="motion-line line-b"></span>
          <span class="motion-node node-a"></span>
          <span class="motion-node node-b"></span>
          <span class="motion-node node-c"></span>
        </div>
        <div>
          <span class="caption">AI + OPC policy and case index</span>
          <h1>{{ routeTitle }}</h1>
          <p>{{ routeSubtitle }}</p>
        </div>
        <div class="topbar-status">
          <span></span>
          公开索引模式
        </div>
      </header>

      <section class="content content-shell">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const isHome = computed(() => route.name === 'home')
const sidebarCollapsed = ref(false)
const routeClass = computed(() => (route.name ? `route-${route.name}` : ''))

const routeTitle = computed(() => {
  const titles = {
    home: '首页概览',
    'region-directory': '地区目录',
    'policy-list': '政策索引',
    'policy-detail': '政策详情',
    'case-list': '案例索引',
    'case-detail': '案例详情',
    'source-ledger': '来源台账',
  }
  return titles[route.name] || 'SoloFirm Index'
})

const routeSubtitle = computed(() => {
  const subtitles = {
    home: '汇总全国 AI + OPC 相关政策、案例、来源与地区覆盖情况。',
    'region-directory': '按省份查看政策与案例资料覆盖情况，方便判断重点地区。',
    'policy-list': '按地区、政策类型和关键词快速定位可引用资料。',
    'policy-detail': '查看政策摘要、支持措施、来源链接和关键字段。',
    'case-list': '沉淀一人公司和 AI 创业案例，便于后续分析。',
    'case-detail': '查看案例主体、模式、工具和成果记录。',
    'source-ledger': '查看来源链接、文件名、访问日期和状态，保证资料可追溯。',
  }
  return subtitles[route.name] || '资料库工作台'
})
</script>
