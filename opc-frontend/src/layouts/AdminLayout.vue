<template>
  <div class="admin-shell">
    <aside class="admin-sidebar site-sidebar">
      <RouterLink class="admin-brand brand" to="/admin" aria-label="SoloFirm Index 管理端">
        <span class="brand-mark">S</span>
        <span class="brand-text">
          <strong>SoloFirm Index</strong>
          <small>壹企经纬 / Admin</small>
        </span>
      </RouterLink>

      <nav class="admin-nav side-nav" aria-label="管理导航">
        <RouterLink to="/admin"><span>00</span><b>工作台</b></RouterLink>
        <RouterLink to="/admin/policies"><span>01</span><b>政策管理</b></RouterLink>
        <RouterLink to="/admin/cases"><span>02</span><b>案例管理</b></RouterLink>
        <RouterLink to="/admin/sources"><span>03</span><b>来源管理</b></RouterLink>
        <RouterLink to="/admin/tags"><span>04</span><b>标签管理</b></RouterLink>
      </nav>

      <RouterLink class="admin-back" to="/">返回前台</RouterLink>
      <button class="admin-back admin-logout" type="button" @click="logout">退出登录</button>
    </aside>

    <main class="admin-main">
      <header class="admin-topbar">
        <div>
          <span class="caption">admin workspace</span>
          <h1>{{ routeTitle }}</h1>
          <p class="lede">维护公开平台可检索、可引用、可复核的政策与案例资料。</p>
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
import { useRoute, useRouter } from 'vue-router'
import { logoutAdmin } from '@/api/auth'

const route = useRoute()
const router = useRouter()

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

function logout() {
  logoutAdmin()
  router.replace('/admin/login')
}
</script>
