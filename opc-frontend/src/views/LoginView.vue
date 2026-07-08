<template>
  <main class="login-shell">
    <section class="login-panel">
      <div class="login-brand">
        <RouterLink class="brand" to="/">
          <span class="brand-mark">S</span>
          <span class="brand-text">
            <strong>SoloFirm Index</strong>
            <small>壹企经纬 / Admin</small>
          </span>
        </RouterLink>
      </div>

      <div class="login-copy">
        <span class="caption">admin access</span>
        <h1>管理员入口</h1>
        <p>请输入管理密码后维护政策、案例、来源与标签数据。</p>
      </div>

      <form class="login-form" @submit.prevent="submitLogin">
        <label>
          <span>管理密码</span>
          <input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
        </label>
        <button class="button" type="submit">进入后台</button>
        <p v-if="error" class="error">{{ error }}</p>
      </form>

      <div class="login-footnote">
        <RouterLink to="/">返回前台</RouterLink>
        <strong>MVP 管理模式</strong>
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
