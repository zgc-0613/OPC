<template>
  <div class="app-shell">
    <div class="scene-bg motion-field" aria-hidden="true">
      <span class="scene-grid"></span>
      <span class="scene-line scene-line-a scan-line horizontal"></span>
      <span class="scene-line scene-line-b scan-line vertical"></span>
    </div>

    <aside class="sidebar site-sidebar" aria-label="主导航">
      <RouterLink class="brand" to="/" aria-label="SoloFirm Index 首页">
        <span class="brand-mark">S</span>
        <span class="brand-text">
          <strong>SoloFirm Index</strong>
          <small>壹企经纬</small>
        </span>
      </RouterLink>

      <nav class="nav side-nav" aria-label="页面导航">
        <RouterLink to="/">
          <span>首页概览</span>
          <small>统计与最近更新</small>
        </RouterLink>
        <RouterLink to="/regions">
          <span>地区目录</span>
          <small>省份资料覆盖</small>
        </RouterLink>
        <RouterLink to="/policies">
          <span>政策索引</span>
          <small>地区、类型、标签检索</small>
        </RouterLink>
        <RouterLink to="/cases">
          <span>案例索引</span>
          <small>应用场景与典型案例</small>
        </RouterLink>
        <RouterLink to="/sources">
          <span>来源台账</span>
          <small>出处、文件、访问日期</small>
        </RouterLink>
      </nav>
    </aside>

    <main class="main">
      <header class="topbar">
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
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

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
