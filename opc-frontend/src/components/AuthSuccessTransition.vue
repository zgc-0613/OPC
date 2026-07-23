<template>
  <Teleport to="body">
    <Transition name="auth-success-overlay">
      <div
        v-if="visible"
        class="auth-success-overlay"
        role="status"
        aria-live="assertive"
        aria-atomic="true"
      >
        <div class="auth-success-content">
          <span class="auth-success-icon" aria-hidden="true">
            <Check :size="30" :stroke-width="1.8" />
          </span>
          <p>{{ mode === 'register' ? 'ACCOUNT CREATED' : 'ACCESS GRANTED' }}</p>
          <h2>{{ mode === 'register' ? '注册成功' : '登录成功' }}</h2>
          <span>正在跳转至首页...</span>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Check } from 'lucide-vue-next'
import { AUTH_SUCCESS_EVENT } from '@/utils/authTransition'

const router = useRouter()
const visible = ref(false)
const mode = ref('login')
let redirectTimer

function handleAuthSuccess(event) {
  window.clearTimeout(redirectTimer)
  mode.value = event.detail?.mode === 'register' ? 'register' : 'login'
  visible.value = true

  redirectTimer = window.setTimeout(async () => {
    try {
      await router.replace(event.detail?.target || '/')
    } finally {
      window.requestAnimationFrame(() => {
        visible.value = false
      })
    }
  }, 1100)
}

onMounted(() => window.addEventListener(AUTH_SUCCESS_EVENT, handleAuthSuccess))

onBeforeUnmount(() => {
  window.clearTimeout(redirectTimer)
  window.removeEventListener(AUTH_SUCCESS_EVENT, handleAuthSuccess)
})
</script>
