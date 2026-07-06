<template>
  <div class="app-shell">
    <div class="scene-bg" aria-hidden="true">
      <span class="scene-grid"></span>
      <span class="scene-scan"></span>
      <span class="scene-line scene-line-a"></span>
      <span class="scene-line scene-line-b"></span>
    </div>

    <aside class="sidebar">
      <RouterLink class="brand" to="/">
        <span class="brand-mark">OPC</span>
        <span>
          <strong>政策与案例平台</strong>
          <small>AI + 一人公司资料库</small>
        </span>
      </RouterLink>

      <nav class="nav">
        <RouterLink to="/">
          <span>首页</span>
          <small>统计与最近更新</small>
        </RouterLink>
        <RouterLink to="/policies">
          <span>政策库</span>
          <small>地区、类型、标签检索</small>
        </RouterLink>
        <RouterLink to="/cases">
          <span>案例库</span>
          <small>应用场景与典型案例</small>
        </RouterLink>
      </nav>

      <div class="side-note">
        <span>DATA TRACE</span>
        <strong>来源可追溯</strong>
        <small>原文链接、辅证材料、访问日期统一沉淀。</small>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div>
          <span class="caption">OPC Information Platform</span>
          <h1>{{ routeTitle }}</h1>
          <p>{{ routeSubtitle }}</p>
        </div>
        <div class="topbar-status">
          <span></span>
          本地开发环境
        </div>
      </header>

      <section class="content">
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
    'policy-list': '政策库',
    'policy-detail': '政策详情',
    'case-list': '案例库',
    'case-detail': '案例详情',
  }
  return titles[route.name] || 'OPC 信息平台'
})

const routeSubtitle = computed(() => {
  const subtitles = {
    home: '汇总全国 AI + OPC 相关政策、案例、来源与地区覆盖情况。',
    'policy-list': '按地区、政策类型和关键词快速定位可引用资料。',
    'policy-detail': '查看政策摘要、支持措施、来源链接和关键字段。',
    'case-list': '沉淀一人公司和 AI 创业案例，便于后续分析。',
    'case-detail': '查看案例主体、模式、工具和成果记录。',
  }
  return subtitles[route.name] || '资料库工作台'
})
</script>
