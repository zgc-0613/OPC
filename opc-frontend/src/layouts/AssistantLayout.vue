<template>
  <div class="assistant-shell">
    <header class="assistant-topbar">
      <RouterLink class="assistant-brand" to="/" aria-label="返回公开索引">
        <ArrowLeft :size="18" aria-hidden="true" />
        <BrandMark />
        <span><strong>SoloFirm</strong><small>OPC Platform</small></span>
      </RouterLink>

      <div class="assistant-route-heading">
        <p class="assistant-route-title" aria-live="polite">{{ workspaceTitle }}</p>
        <span class="assistant-route-status" :class="{ ready: workspaceStatus.ready }">
          <i aria-hidden="true"></i>{{ workspaceStatus.label }}
        </span>
      </div>

      <nav class="assistant-route-actions" aria-label="Assistant 工作台出口">
        <button ref="historyButton" class="mobile-history-command" type="button" aria-label="打开研究历史" @pointerdown="markHistoryPointer" @keydown="clearHistoryMotion" @click="requestHistory">
          <Menu :size="18" aria-hidden="true" />
          <span>历史</span>
        </button>
        <button type="button" aria-label="打开研究资料" @pointerdown="markEvidencePointer" @keydown="clearEvidenceMotion" @click="requestEvidence">
          <BookOpenText :size="18" aria-hidden="true" />
          <span>资料</span>
        </button>
        <RouterLink to="/account" aria-label="进入个人主页">
          <UserRound :size="18" aria-hidden="true" />
          <span>账户</span>
        </RouterLink>
      </nav>
    </header>

    <main class="assistant-layout-content">
      <RouterView v-slot="{ Component }">
        <component
          :is="Component"
          @workspace-title="workspaceTitle = $event"
          @workspace-status="workspaceStatus = $event"
        />
      </RouterView>
    </main>
  </div>
</template>

<script setup>
import { provide, ref } from 'vue'
import { ArrowLeft, BookOpenText, Menu, UserRound } from 'lucide-vue-next'
import BrandMark from '@/components/BrandMark.vue'

const workspaceTitle = ref('新研究')
const workspaceStatus = ref({ label: '本地草稿', ready: false })
const evidenceRequest = ref(0)
const historyRequest = ref(0)
const evidencePointerMotion = ref(false)
const historyPointerMotion = ref(false)
const historyButton = ref(null)
provide('assistant-evidence-request', evidenceRequest)
provide('assistant-evidence-motion', evidencePointerMotion)
provide('assistant-history-control', {
  request: historyRequest,
  motion: historyPointerMotion,
  restoreFocus: () => historyButton.value?.focus(),
})
function markHistoryPointer() { historyPointerMotion.value = true }
function clearHistoryMotion() { historyPointerMotion.value = false }
function requestHistory(event) {
  if (event?.detail === 0) historyPointerMotion.value = false
  historyRequest.value += 1
}
function markEvidencePointer() { evidencePointerMotion.value = true }
function clearEvidenceMotion() { evidencePointerMotion.value = false }
function requestEvidence(event) {
  if (event?.detail === 0) evidencePointerMotion.value = false
  evidenceRequest.value += 1
}
</script>

<style scoped>
.assistant-shell{display:grid;grid-template-rows:58px minmax(0,1fr);width:100%;height:100dvh;min-width:0;min-height:0;overflow:hidden;background:#f6f6f1;color:#20251f}.assistant-topbar{position:relative;z-index:30;display:grid;grid-template-columns:minmax(190px,1fr) minmax(0,2fr) minmax(190px,1fr);align-items:center;gap:16px;min-width:0;padding:0 16px;border-bottom:1px solid #c9cdc7;background:#fbfbf7}.assistant-brand,.assistant-route-actions a,.assistant-route-actions button{display:inline-flex;align-items:center;gap:8px;min-height:44px;border:1px solid transparent;border-radius:3px;color:#292e29;text-decoration:none}.assistant-brand{justify-self:start}.assistant-brand :deep(.brand-mark){width:24px;height:24px}.assistant-brand>span{display:grid}.assistant-brand strong{font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:.78rem}.assistant-brand small{color:#5f665f;font-size:.58rem}.assistant-route-heading{display:grid;justify-items:center;min-width:0;gap:2px}.assistant-route-title{max-width:100%;min-width:0;margin:0;overflow:hidden;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:.84rem;font-weight:600;text-align:center;text-overflow:ellipsis;white-space:nowrap}.assistant-route-status{display:inline-flex;align-items:center;gap:5px;color:#5f665f;font-size:.58rem}.assistant-route-status i{width:6px;height:6px;border-radius:50%;background:#80734f}.assistant-route-status.ready i{background:#3e684a}.assistant-route-actions{display:flex;justify-self:end;gap:4px}.assistant-route-actions a,.assistant-route-actions button{justify-content:center;min-width:58px;padding:0 8px;background:transparent;font:inherit;font-size:.7rem}.assistant-route-actions .mobile-history-command{display:none}.assistant-brand:focus-visible,.assistant-route-actions :is(a,button):focus-visible{border-color:#bfc5bd;background:#eff0ea}.assistant-brand:focus-visible,.assistant-route-actions :is(a,button):focus-visible{outline:2px solid rgba(74,82,74,.34);outline-offset:2px}.assistant-brand:active,.assistant-route-actions :is(a,button):active{background:#e4e6df}.assistant-layout-content{width:100%;height:100%;min-width:0;min-height:0;overflow:hidden}@media(max-width:840px){.assistant-topbar{grid-template-columns:minmax(150px,1fr) minmax(0,2fr) minmax(150px,1fr)}.assistant-route-actions .mobile-history-command{display:inline-flex}}@media(max-width:640px){.assistant-shell{grid-template-rows:54px minmax(0,1fr)}.assistant-topbar{grid-template-columns:52px minmax(0,1fr) 132px;gap:6px;padding:0 8px}.assistant-brand{justify-content:center;width:44px}.assistant-brand>svg:nth-child(2),.assistant-brand>span{display:none}.assistant-route-actions{gap:0}.assistant-route-actions a,.assistant-route-actions button{min-width:44px;width:44px;padding:0}.assistant-route-actions span{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}.assistant-route-title{font-size:.78rem}.assistant-route-status{font-size:.55rem}.assistant-route-status i{flex:0 0 auto}}@media(prefers-reduced-motion:reduce){.assistant-shell *{scroll-behavior:auto!important}}
@media (hover: hover) and (pointer: fine){.assistant-brand,.assistant-route-actions :is(a,button){transition:background-color var(--duration-fast) ease,border-color var(--duration-fast) ease,color var(--duration-fast) ease}.assistant-brand:hover,.assistant-route-actions :is(a,button):hover{border-color:#bfc5bd;background:#eff0ea}}
@media(prefers-reduced-motion:reduce){.assistant-brand,.assistant-route-actions :is(a,button){transition:none;transform:none!important}}
@media(prefers-reduced-motion:no-preference){.assistant-brand:active,.assistant-route-actions :is(a,button):active{transform:scale(.97)}}
</style>
