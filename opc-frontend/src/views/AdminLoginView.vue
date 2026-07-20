<template>
  <main class="login-shell admin-login-shell">
    <section class="login-panel">
      <div class="login-visual" aria-hidden="true">
        <span class="login-orbit"></span>
        <h2>维护可追溯的政策、案例与来源数据</h2>
        <p>
          <span class="admin-login-description-full">管理员入口仅用于数据录入、校对、标签维护和平台内容管理。</span>
          <span class="admin-login-description-compact">管理员入口仅用于平台内容管理。</span>
        </p>
      </div>

      <div class="login-form-side">
      <div class="login-brand">
        <a class="brand" href="https://findopc.online/">
          <BrandMark />
          <span class="brand-text">
            <strong>SoloFirm</strong>
            <small>{{ authMode === 'login' ? '管理员登录' : '注册申请' }}</small>
          </span>
        </a>
      </div>

      <div class="login-copy">
        <h1 :class="{ 'is-register': authMode === 'register' }">{{ modeTitle }}</h1>
        <p>{{ modeDescription }}</p>
      </div>

      <form class="login-form" @submit.prevent="submitAuth">
        <label>
          <span>管理员用户名</span>
          <input v-model.trim="form.username" type="text" autocomplete="username" placeholder="请输入用户名" />
        </label>
        <label>
          <span id="admin-password-label">密码</span>
          <div class="login-password-control">
            <input
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              aria-labelledby="admin-password-label"
              :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'"
              minlength="8"
              maxlength="64"
              placeholder="请输入密码"
            />
            <button class="login-password-toggle" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
              <component :is="showPassword ? EyeOff : Eye" :size="17" aria-hidden="true" />
            </button>
          </div>
        </label>
        <label class="admin-confirm-field" :class="{ 'is-reserved': authMode === 'login' }" :aria-hidden="authMode === 'login'">
          <span>确认密码</span>
          <input
            v-model="form.confirmPassword"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            minlength="8"
            maxlength="64"
            placeholder="再次输入密码"
            :disabled="authMode === 'login'"
            :tabindex="authMode === 'register' ? 0 : -1"
          />
        </label>
        <button class="button" type="submit" :disabled="submitting">{{ submitButtonText }}</button>
        <p v-if="notice" class="success">{{ notice }}</p>
        <p v-if="error" class="error">{{ error }}</p>
      </form>

      <div class="login-mode-switch">
        <span>{{ authMode === 'login' ? '没有管理员账号？' : '已有管理员账号？' }}</span>
        <button type="button" @click="switchMode">{{ authMode === 'login' ? '提交注册申请' : '返回登录' }}</button>
      </div>

      <div class="login-footnote">
        <a href="https://findopc.online/">返回前台</a>
      </div>
      </div>
    </section>
    <footer class="login-page-copyright">
      Copyright &copy; 2026 <a href="https://findopc.online/">SoloFirm<sup>&reg;</sup></a> - All rights reserved
    </footer>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Eye, EyeOff } from 'lucide-vue-next'
import { loginAdmin, submitAdminRegistrationRequest } from '@/api/auth'
import BrandMark from '@/components/BrandMark.vue'

const route = useRoute()
const router = useRouter()
const authMode = ref('login')
const form = ref({ username: '', password: '', confirmPassword: '' })
const error = ref('')
const notice = ref('')
const submitting = ref(false)
const showPassword = ref(false)

const modeTitle = computed(() => (authMode.value === 'login' ? '管理后台入口' : '申请管理员账号'))
const modeDescription = computed(() => (
  authMode.value === 'login'
    ? '使用管理员用户名和密码进入后台。'
    : '提交用户名和密码，等待已有管理员审批后即可登录。'
))
const submitButtonText = computed(() => {
  if (submitting.value) {
    return authMode.value === 'login' ? '正在验证...' : '正在提交...'
  }
  return authMode.value === 'login' ? '进入后台' : '提交注册申请'
})

async function submitAuth() {
  error.value = ''
  notice.value = ''
  if (!form.value.username || !form.value.password) {
    error.value = '请输入管理员用户名和密码。'
    return
  }
  if (authMode.value === 'register' && form.value.password !== form.value.confirmPassword) {
    error.value = '两次输入的密码不一致。'
    return
  }
  submitting.value = true
  try {
    if (authMode.value === 'login') {
      await loginAdmin(form.value.username, form.value.password)
      router.replace(route.query.redirect || '/admin')
    } else {
      await submitAdminRegistrationRequest(form.value.username, form.value.password)
      const submittedUsername = form.value.username
      resetForm()
      form.value.username = submittedUsername
      authMode.value = 'login'
      notice.value = '注册申请已提交，请等待已有管理员审批。'
    }
  } catch (err) {
    error.value = err.message || (authMode.value === 'login' ? '管理员登录失败。' : '注册申请提交失败。')
    form.value.password = ''
    form.value.confirmPassword = ''
  } finally {
    submitting.value = false
  }
}

function switchMode() {
  authMode.value = authMode.value === 'login' ? 'register' : 'login'
  resetForm()
  error.value = ''
  notice.value = ''
}

function resetForm() {
  form.value = { username: '', password: '', confirmPassword: '' }
  showPassword.value = false
}
</script>
