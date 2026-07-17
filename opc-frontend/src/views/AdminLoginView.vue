<template>
  <main class="login-shell admin-login-shell">
    <section class="login-panel">
      <div class="login-visual" aria-hidden="true">
        <span class="login-orbit"></span>
        <span class="login-kicker">Admin Workspace</span>
        <h2>维护可追溯的政策、案例与来源数据</h2>
        <p>管理员入口仅用于数据录入、校对、标签维护和平台内容管理。</p>
        <div class="login-visual-tags">
          <span>Manage</span>
          <span>Review</span>
          <span>Export</span>
        </div>
      </div>

      <div class="login-form-side">
      <div class="login-brand">
        <RouterLink class="brand" to="/">
          <span class="brand-mark">S</span>
          <span class="brand-text">
            <strong>SoloFirm</strong>
            <small>管理员登录</small>
          </span>
        </RouterLink>
      </div>

      <div class="login-copy">
        <span class="caption">admin access</span>
        <h1>管理后台入口</h1>
        <p>请输入管理员密码，进入政策、案例、来源与标签维护后台。</p>
      </div>

      <form class="login-form" @submit.prevent="submitLogin">
        <label>
          <span>管理员密码</span>
          <input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
        </label>
        <button class="button" type="submit">进入后台</button>
        <p v-if="error" class="error">{{ error }}</p>
      </form>

      <div class="login-footnote">
        <RouterLink to="/">返回前台</RouterLink>
        <strong>Admin</strong>
      </div>
      </div>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { loginAdmin } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const password = ref('')
const error = ref('')

function submitLogin() {
  error.value = ''
  if (!loginAdmin(password.value)) {
    error.value = '密码不正确，请重新输入。'
    password.value = ''
    return
  }
  router.replace(route.query.redirect || '/admin')
}
</script>
