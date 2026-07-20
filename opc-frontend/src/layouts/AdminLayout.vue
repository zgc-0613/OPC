<template>
  <div class="admin-shell">
    <aside class="admin-sidebar site-sidebar">
      <RouterLink class="admin-brand brand" to="/admin" aria-label="SoloFirm 管理端">
        <BrandMark />
        <span class="brand-text">
          <strong>SoloFirm</strong>
          <small>OPC Platform / Admin</small>
        </span>
      </RouterLink>

      <nav class="admin-nav side-nav" aria-label="管理导航">
        <RouterLink
          to="/admin"
          active-class="admin-route-active"
          exact-active-class="admin-route-exact-active"
          :class="{ 'nav-active': isAdminNavActive('home') }"
        ><LayoutDashboard :size="17" aria-hidden="true" /><b>工作台</b></RouterLink>
        <RouterLink
          to="/admin/policies"
          active-class="admin-route-active"
          exact-active-class="admin-route-exact-active"
          :class="{ 'nav-active': isAdminNavActive('policies') }"
        ><FileText :size="17" aria-hidden="true" /><b>政策管理</b></RouterLink>
        <RouterLink
          to="/admin/cases"
          active-class="admin-route-active"
          exact-active-class="admin-route-exact-active"
          :class="{ 'nav-active': isAdminNavActive('cases') }"
        ><BriefcaseBusiness :size="17" aria-hidden="true" /><b>案例管理</b></RouterLink>
        <RouterLink
          to="/admin/sources"
          active-class="admin-route-active"
          exact-active-class="admin-route-exact-active"
          :class="{ 'nav-active': isAdminNavActive('sources') }"
        ><BookOpenText :size="17" aria-hidden="true" /><b>来源管理</b></RouterLink>
        <RouterLink
          to="/admin/tags"
          active-class="admin-route-active"
          exact-active-class="admin-route-exact-active"
          :class="{ 'nav-active': isAdminNavActive('tags') }"
        ><Tags :size="17" aria-hidden="true" /><b>标签管理</b></RouterLink>
        <RouterLink
          to="/admin/settings"
          active-class="admin-route-active"
          exact-active-class="admin-route-exact-active"
          :class="{ 'nav-active': isAdminNavActive('settings') }"
        ><Settings :size="17" aria-hidden="true" /><b>系统设置</b></RouterLink>
      </nav>

      <a class="admin-back" href="https://findopc.online/"><ArrowLeft :size="16" aria-hidden="true" />返回前台</a>
      <button class="admin-back admin-logout" type="button" @click="logout"><LogOut :size="16" aria-hidden="true" />退出登录</button>
    </aside>

    <main class="admin-main">
      <header class="admin-topbar">
        <div>
          <h1>{{ routeTitle }}</h1>
          <p class="lede">维护公开平台可检索、可引用、可复核的政策与案例资料。</p>
        </div>
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
import {
  ArrowLeft,
  BookOpenText,
  BriefcaseBusiness,
  FileText,
  LayoutDashboard,
  LogOut,
  Settings,
  Tags,
} from 'lucide-vue-next'
import { logoutAdmin } from '@/api/auth'
import BrandMark from '@/components/BrandMark.vue'

const route = useRoute()
const router = useRouter()

const routeTitle = computed(() => {
  const titles = {
    'admin-home': '后台首页',
    'admin-policies': '政策管理',
    'admin-cases': '案例管理',
    'admin-sources': '来源管理',
    'admin-tags': '标签管理',
    'admin-settings': '系统设置',
  }
  return titles[route.name] || '管理后台'
})

function isAdminNavActive(section) {
  const sectionMap = {
    home: ['admin-home'],
    policies: ['admin-policies'],
    cases: ['admin-cases'],
    sources: ['admin-sources'],
    tags: ['admin-tags'],
    settings: ['admin-settings'],
  }
  return sectionMap[section]?.includes(route.name)
}

async function logout() {
  try {
    await logoutAdmin()
  } finally {
    router.replace('/admin/login')
  }
}
</script>
