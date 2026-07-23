<template>
  <main
    class="login-shell user-login-shell"
    :class="authMode === 'login' ? 'is-login-view' : 'is-register-view'"
  >
    <section class="login-panel">
      <div class="login-visual" aria-hidden="true">
        <span class="login-orbit"></span>
        <h2>连接你的 OPC 研究与创业资料空间</h2>
        <p>使用账号密码进入资料空间；邮箱验证码只用于注册时确认邮箱所有权。</p>
      </div>

      <div class="login-form-side">
      <div class="login-brand">
        <RouterLink class="brand" to="/">
          <BrandMark />
          <span class="brand-text">
            <strong>SoloFirm</strong>
            <small>{{ authMode === 'login' ? '用户登录' : '账号注册' }}</small>
          </span>
        </RouterLink>
      </div>

      <div class="login-copy">
        <h1 :class="{ 'is-register': authMode === 'register' }">{{ modeTitle }}</h1>
        <p>{{ modeDescription }}</p>
      </div>

      <p v-if="accessNotice" class="login-access-notice" role="status">{{ accessNotice }}</p>

      <div v-if="currentUser" class="login-status-card">
        <strong>{{ currentUser.username }}</strong>
        <span>{{ currentUser.email }}</span>
        <button class="button button-ghost" type="button" @click="handleLogout">退出当前账号</button>
      </div>

      <form v-else class="login-form" :class="{ 'is-login-mode': authMode === 'login' }" novalidate @submit.prevent="submitAuth">
        <label class="login-identity-field">
          <span>{{ authMode === 'login' ? '用户名或邮箱' : '用户名' }}</span>
          <input
            v-if="authMode === 'login'"
            v-model.trim="form.identifier"
            type="text"
            autocomplete="username"
            placeholder="请输入用户名或邮箱"
          />
          <input
            v-else
            v-model.trim="form.username"
            type="text"
            autocomplete="username"
            placeholder="请输入用户名"
          />
        </label>
        <label class="login-email-field" :class="{ 'is-reserved': authMode === 'login' }" :aria-hidden="authMode === 'login'">
          <span>邮箱</span>
          <input
            v-model.trim="form.email"
            type="email"
            autocomplete="email"
            placeholder="请输入邮箱"
            :disabled="authMode === 'login'"
            :tabindex="authMode === 'register' ? 0 : -1"
          />
        </label>
        <label class="login-password-field">
          <span id="user-password-label">密码</span>
          <div class="login-password-control">
            <input
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              aria-labelledby="user-password-label"
              :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'"
              minlength="8"
              maxlength="64"
              :placeholder="authMode === 'login' ? '请输入密码' : '设置 8-64 位密码'"
            />
            <button
              class="login-password-toggle"
              type="button"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              :title="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <component :is="showPassword ? EyeOff : Eye" :size="17" aria-hidden="true" />
            </button>
          </div>
        </label>
        <div class="login-altcha-row" :aria-hidden="authMode === 'login'">
          <altcha-widget
            v-if="authMode === 'register' && altchaEnabled && !emailCodeSent"
            ref="altchaWidget"
            auto="off"
            challenge="/api/auth/altcha/challenge"
            display="standard"
            language="zh-cn"
            name="altcha"
            type="checkbox"
            workers="2"
            @statechange="handleAltchaStateChange"
          ></altcha-widget>
          <div v-else-if="authMode === 'register' && emailCodeSent" class="login-altcha-complete" role="status">
            <span><CheckCircle :size="20" aria-hidden="true" />人机验证已完成，本次注册无需再次验证</span>
            <button type="button" @click="prepareEmailCodeResend">
              <RotateCcw :size="15" aria-hidden="true" />重新发送
            </button>
          </div>
        </div>
        <div class="login-code-row" :class="{ 'is-reserved': authMode === 'login' }" :aria-hidden="authMode === 'login'">
          <label>
            <span>邮箱验证码</span>
            <input
              v-model.trim="form.code"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              placeholder="6 位验证码"
              :disabled="authMode === 'login'"
              :tabindex="authMode === 'register' ? 0 : -1"
            />
          </label>
          <button class="button button-ghost" type="button" :disabled="authMode === 'login' || sending || cooldown > 0 || emailCodeSent || altchaIncomplete" :tabindex="authMode === 'register' ? 0 : -1" @click="sendCode">
            {{ codeButtonText }}
          </button>
        </div>
        <button class="button" type="submit" :disabled="submitting">{{ submitButtonText }}</button>
        <p v-if="devCode" class="login-dev-code">开发模式验证码：{{ devCode }}</p>
        <p v-if="message" class="success">{{ message }}</p>
        <p v-if="error" class="error">{{ error }}</p>
      </form>

      <div v-if="!currentUser" class="login-mode-switch">
        <span>{{ authMode === 'login' ? '没有账号？' : '已有账号？' }}</span>
        <button type="button" @click="switchAuthMode">
          {{ authMode === 'login' ? '点击注册' : '返回登录' }}
        </button>
      </div>

      <div class="login-footnote">
        <RouterLink to="/">返回首页</RouterLink>
      </div>
      </div>
    </section>
    <footer class="login-page-copyright">
      Copyright &copy; 2026 <RouterLink to="/">SoloFirm<sup>&reg;</sup></RouterLink> - All rights reserved
    </footer>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { CheckCircle, Eye, EyeOff, RotateCcw } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'
import { showAuthSuccessTransition } from '@/utils/authTransition'
import {
  getUserProfile,
  getAltchaConfig,
  isUserAuthenticated,
  loginUser,
  logoutUser,
  registerUser,
  sendUserEmailCode,
} from '@/api/auth'

const route = useRoute()
const accessNotice = computed(() => route.query.reason === 'ai-login-required'
  ? '请先登录后使用智能体案例分析。登录完成后将返回原页面。'
  : '')

const form = ref({
  identifier: '',
  username: '',
  email: '',
  password: '',
  code: '',
})
const currentUser = ref(isUserAuthenticated() ? getUserProfile() : null)
const authMode = ref('login')
const altchaWidget = ref(null)
const altchaEnabled = ref(false)
const altchaConfigReady = ref(false)
const altchaState = ref('unverified')
const altchaPayload = ref('')
const sending = ref(false)
const submitting = ref(false)
const cooldown = ref(0)
const message = ref('')
const error = ref('')
const devCode = ref('')
const emailCodeSent = ref(false)
const showPassword = ref(false)
let timer = null

onMounted(loadAltchaConfig)

const modeTitle = computed(() => (authMode.value === 'login' ? '登录 SoloFirm' : '创建 SoloFirm 账号'))
const modeDescription = computed(() => (
  authMode.value === 'login'
    ? '使用用户名或邮箱和密码进入资料空间。'
    : '填写用户名、邮箱和密码，再用邮箱验证码完成注册。'
))
const submitButtonText = computed(() => {
  if (submitting.value) {
    return authMode.value === 'login' ? '正在登录...' : '正在注册...'
  }
  return authMode.value === 'login' ? '完成登录' : '创建账号'
})

const codeButtonText = computed(() => {
  if (sending.value) {
    return '发送中'
  }
  if (cooldown.value > 0) {
    return `${cooldown.value}s`
  }
  if (emailCodeSent.value) {
    return '验证码已发送'
  }
  if (altchaIncomplete.value) {
    return '请先完成验证'
  }
  return '获取验证码'
})

const altchaIncomplete = computed(() => (
  authMode.value === 'register'
  && altchaConfigReady.value
  && altchaEnabled.value
  && (altchaState.value !== 'verified' || !altchaPayload.value)
))

async function sendCode() {
  resetNotice()
  if (!form.value.email) {
    error.value = '请先填写邮箱'
    return
  }
  if (authMode.value === 'register' && !altchaConfigReady.value) {
    error.value = '注册验证服务暂时不可用，请稍后再试'
    return
  }
  sending.value = true
  try {
    if (authMode.value === 'register' && altchaEnabled.value) {
      if (altchaState.value !== 'verified' || !altchaPayload.value) {
        throw new Error('请先完成人机验证')
      }
    }
    const result = await sendUserEmailCode(form.value.email, altchaPayload.value)
    devCode.value = result.devCode || ''
    emailCodeSent.value = true
    message.value = result.devCode ? '验证码已生成，本地开发模式可直接使用下方验证码。' : '验证码已发送，请查看邮箱。'
    startCooldown()
  } catch (err) {
    error.value = err.message || '验证码发送失败'
  } finally {
    resetAltcha()
    sending.value = false
  }
}

function handleAltchaStateChange(event) {
  const state = event?.detail?.state || 'unverified'
  altchaState.value = state
  altchaPayload.value = state === 'verified' && typeof event?.detail?.payload === 'string'
    ? event.detail.payload
    : ''
}

function resetAltcha() {
  altchaState.value = 'unverified'
  altchaPayload.value = ''
  altchaWidget.value?.reset()
}

function prepareEmailCodeResend() {
  emailCodeSent.value = false
  form.value.code = ''
  resetAltcha()
}

async function loadAltchaConfig() {
  try {
    const config = await getAltchaConfig()
    if (config?.enabled) {
      await import('altcha')
      await import('altcha/i18n/zh-cn')
    }
    altchaEnabled.value = Boolean(config?.enabled)
    altchaConfigReady.value = true
  } catch {
    altchaEnabled.value = false
    altchaConfigReady.value = false
  }
}

async function submitAuth() {
  resetNotice()
  if (authMode.value === 'login') {
    if (!form.value.identifier || !form.value.password) {
      error.value = '请填写用户名或邮箱和密码'
      return
    }
  } else {
    if (!form.value.username || !form.value.email || !form.value.password || !form.value.code) {
      error.value = '请完整填写用户名、邮箱、密码和邮箱验证码'
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)) {
      error.value = '请输入有效的邮箱地址'
      return
    }
    if (form.value.password.length < 8 || form.value.password.length > 64) {
      error.value = '密码长度必须为 8-64 位'
      return
    }
  }
  submitting.value = true
  try {
    const user = authMode.value === 'login'
      ? await loginUser(form.value.identifier, form.value.password)
      : await registerUser({
        username: form.value.username,
        email: form.value.email,
        password: form.value.password,
        code: form.value.code,
    })
    currentUser.value = getUserProfile() || user
    showAuthSuccessTransition(authMode.value, '/')
  } catch (err) {
    error.value = err.message || (authMode.value === 'login' ? '登录失败，请检查账号和密码' : '注册失败，请检查填写内容')
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

function switchAuthMode() {
  authMode.value = authMode.value === 'login' ? 'register' : 'login'
  form.value.identifier = ''
  form.value.username = ''
  form.value.email = ''
  form.value.password = ''
  form.value.code = ''
  devCode.value = ''
  emailCodeSent.value = false
  showPassword.value = false
  resetAltcha()
  resetNotice()
}

watch(
  () => form.value.email,
  (value, previous) => {
    if (emailCodeSent.value && previous && value !== previous) {
      emailCodeSent.value = false
      form.value.code = ''
      resetAltcha()
    }
  },
)

onBeforeUnmount(() => {
  clearInterval(timer)
})
</script>
