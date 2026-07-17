<template>
  <main class="login-shell user-login-shell">
    <section class="login-panel">
      <div class="login-visual" aria-hidden="true">
        <span class="login-orbit"></span>
        <span class="login-kicker">SoloFirm Account</span>
        <h2>连接你的 OPC 研究与创业资料空间</h2>
        <p>使用邮箱验证码完成登录，后续可承接收藏、浏览记录和个性化分析功能。</p>
        <div class="login-visual-tags">
          <span>Policy</span>
          <span>Case</span>
          <span>Insight</span>
        </div>
      </div>

      <div class="login-form-side">
      <div class="login-brand">
        <RouterLink class="brand" to="/">
          <span class="brand-mark">S</span>
          <span class="brand-text">
            <strong>SoloFirm</strong>
            <small>前台用户登录</small>
          </span>
        </RouterLink>
      </div>

      <div class="login-copy">
        <span class="caption">user access</span>
        <h1>邮箱验证码登录</h1>
        <p>填写邮箱和用户名，获取验证码后即可完成注册或登录。</p>
      </div>

      <div v-if="currentUser" class="login-status-card">
        <strong>{{ currentUser.username }}</strong>
        <span>{{ currentUser.email }}</span>
        <button class="button button-ghost" type="button" @click="handleLogout">退出当前账号</button>
      </div>

      <form v-else class="login-form" @submit.prevent="submitLogin">
        <label>
          <span>用户名</span>
          <input v-model.trim="form.username" type="text" autocomplete="nickname" placeholder="请输入用户名" />
        </label>
        <label>
          <span>邮箱</span>
          <input v-model.trim="form.email" type="email" autocomplete="email" placeholder="请输入邮箱" />
        </label>
        <div class="login-code-row">
          <label>
            <span>验证码</span>
            <input v-model.trim="form.code" inputmode="numeric" maxlength="6" placeholder="6 位验证码" />
          </label>
          <button class="button button-ghost" type="button" :disabled="sending || cooldown > 0" @click="sendCode">
            {{ codeButtonText }}
          </button>
        </div>
        <button class="button" type="submit" :disabled="submitting">完成登录</button>
        <p v-if="devCode" class="login-dev-code">开发模式验证码：{{ devCode }}</p>
        <p v-if="message" class="success">{{ message }}</p>
        <p v-if="error" class="error">{{ error }}</p>
      </form>

      <div class="login-footnote">
        <RouterLink to="/">返回首页</RouterLink>
        <RouterLink to="/admin/login">管理员入口</RouterLink>
      </div>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getUserProfile,
  isUserAuthenticated,
  logoutUser,
  sendUserEmailCode,
  verifyUserEmailCode,
} from '@/api/auth'

const route = useRoute()
const router = useRouter()
const form = ref({
  username: '',
  email: '',
  code: '',
})
const currentUser = ref(isUserAuthenticated() ? getUserProfile() : null)
const sending = ref(false)
const submitting = ref(false)
const cooldown = ref(0)
const message = ref('')
const error = ref('')
const devCode = ref('')
let timer = null

const codeButtonText = computed(() => {
  if (sending.value) {
    return '发送中'
  }
  if (cooldown.value > 0) {
    return `${cooldown.value}s`
  }
  return '获取验证码'
})

async function sendCode() {
  resetNotice()
  if (!form.value.email) {
    error.value = '请先填写邮箱'
    return
  }
  sending.value = true
  try {
    const result = await sendUserEmailCode(form.value.email)
    devCode.value = result.devCode || ''
    message.value = result.devCode ? '验证码已生成，本地开发模式可直接使用下方验证码。' : '验证码已发送，请查看邮箱。'
    startCooldown()
  } catch (err) {
    error.value = err.message || '验证码发送失败'
  } finally {
    sending.value = false
  }
}

async function submitLogin() {
  resetNotice()
  if (!form.value.username || !form.value.email || !form.value.code) {
    error.value = '请完整填写用户名、邮箱和验证码'
    return
  }
  submitting.value = true
  try {
    const user = await verifyUserEmailCode(form.value)
    currentUser.value = getUserProfile() || user
    router.replace(route.query.redirect || '/')
  } catch (err) {
    error.value = err.message || '登录失败，请检查验证码'
  } finally {
    submitting.value = false
  }
}

async function handleLogout() {
  await logoutUser()
  currentUser.value = null
  message.value = '已退出当前账号'
}

function startCooldown() {
  cooldown.value = 60
  clearInterval(timer)
  timer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

function resetNotice() {
  message.value = ''
  error.value = ''
}

onBeforeUnmount(() => {
  clearInterval(timer)
})
</script>
