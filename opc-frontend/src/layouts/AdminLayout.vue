<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink class="admin-brand" to="/admin">
        <span>OPC</span>
        <strong>管理后台</strong>
      </RouterLink>

      <nav class="admin-nav">
        <RouterLink to="/admin">后台首页</RouterLink>
        <RouterLink to="/admin/policies">政策管理</RouterLink>
        <RouterLink to="/admin/cases">案例管理</RouterLink>
        <RouterLink to="/admin/sources">来源管理</RouterLink>
        <RouterLink to="/admin/tags">标签管理</RouterLink>
      </nav>

      <RouterLink class="admin-back" to="/">返回前台</RouterLink>
    </aside>

    <main class="admin-main">
      <header class="admin-topbar">
        <div>
          <span class="caption">ADMIN WORKSPACE</span>
          <h1>{{ routeTitle }}</h1>
        </div>
        <span class="admin-mode">MVP 管理模式</span>
      </header>

      <section class="admin-content">
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
    'admin-home': '后台首页',
    'admin-policies': '政策管理',
    'admin-cases': '案例管理',
    'admin-sources': '来源管理',
    'admin-tags': '标签管理',
  }
  return titles[route.name] || '管理后台'
})
</script>
