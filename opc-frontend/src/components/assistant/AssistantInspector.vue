<template>
  <Teleport to="body" :disabled="!isMobile">
    <Transition :name="motion ? 'assistant-inspector' : ''">
    <div v-if="open" ref="layer" class="assistant-inspector-layer">
      <button v-if="isMobile" class="assistant-inspector-backdrop" type="button" :aria-label="`关闭${title}`" @click="$emit('close', $event)"></button>
      <aside
        ref="panel"
        class="assistant-inspector"
        :role="isMobile ? 'dialog' : 'complementary'"
        :aria-modal="isMobile ? 'true' : undefined"
        aria-labelledby="assistant-inspector-title"
        @keydown="handleKeydown"
      >
        <header>
          <div>
            <span class="caption">{{ caption }}</span>
            <h2 id="assistant-inspector-title">{{ title }}</h2>
          </div>
          <button ref="closeButton" class="assistant-inspector-close" type="button" :aria-label="`关闭${title}`" @click="$emit('close', $event)"><X :size="19" /></button>
        </header>
        <div class="assistant-inspector-body"><slot /></div>
      </aside>
    </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { X } from 'lucide-vue-next'
import { isolateDialogBranch, trapFocus } from '@/utils/focusTrap'

const props = defineProps({
  open: Boolean,
  title: { type: String, default: '研究详情' },
  caption: { type: String, default: 'RESEARCH DETAILS' },
  restoreFocus: { type: Boolean, default: true },
  motion: Boolean,
})
const emit = defineEmits(['close'])
const layer = ref(null)
const panel = ref(null)
const closeButton = ref(null)
const isMobile = ref(false)
let mediaQuery = null
let previousFocus = null
let previousBodyOverflow = ''
let releaseIsolation = () => {}

function syncViewport() {
  isMobile.value = Boolean(mediaQuery?.matches)
}

onMounted(() => {
  mediaQuery = window.matchMedia?.('(max-width: 1023px)') || null
  syncViewport()
  mediaQuery?.addEventListener?.('change', syncViewport)
  if (props.open) openMobileInspector()
})

watch(() => props.open, async (open) => {
  if (!open) {
    restoreModalState()
    return
  }
  await openMobileInspector()
})

async function openMobileInspector() {
  await nextTick()
  if (!isMobile.value) return
  if (previousFocus) return
  previousFocus = document.activeElement
  previousBodyOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  releaseIsolation()
  releaseIsolation = isolateDialogBranch(layer.value)
  closeButton.value?.focus()
}

function restoreModalState() {
  releaseIsolation()
  releaseIsolation = () => {}
  if (!isMobile.value) return
  document.body.style.overflow = previousBodyOverflow
  if (props.restoreFocus) previousFocus?.focus?.()
  previousFocus = null
}

function handleKeydown(event) {
  if (event.key === 'Escape') {
    emit('close', event)
    return
  }
  if (isMobile.value) trapFocus(event, panel.value)
}

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener?.('change', syncViewport)
  releaseIsolation()
  document.body.style.overflow = previousBodyOverflow
})
</script>
<style scoped>
.caption { color: #5f665f !important; }
.assistant-inspector-layer{min-width:0;min-height:0}.assistant-inspector{display:grid;grid-template-rows:auto minmax(0,1fr);width:100%;height:100%;min-width:0;min-height:0;border-left:1px solid #c9cdc7;background:#f6f6f1;color:#282d28}.assistant-inspector>header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding:18px 16px;border-bottom:1px solid #d0d4ce;background:#fbfbf7}.caption{display:block;color:#727972;font-family:'Bookman Old Style',Georgia,serif;font-size:.63rem;font-weight:700}.assistant-inspector h2{margin:6px 0 0;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:1.05rem;font-weight:600}.assistant-inspector-close{display:grid;place-items:center;width:44px;height:44px;flex:0 0 44px;border:1px solid transparent;border-radius:3px;background:transparent;color:#303630}.assistant-inspector-close:focus-visible{outline:2px solid rgba(74,82,74,.38);outline-offset:2px}.assistant-inspector-close:active{transform:translateY(1px)}.assistant-inspector-body{min-width:0;min-height:0;overflow-x:hidden;overflow-y:auto;overscroll-behavior:contain;scrollbar-gutter:stable}.assistant-inspector-body :deep(.research-profile),.assistant-inspector-body :deep(.research-task),.assistant-inspector-body :deep(.research-preferences),.assistant-inspector-body :deep(.research-reports){max-height:none;border-bottom:1px solid #d0d4ce}.assistant-inspector-body :deep(.research-profile),.assistant-inspector-body :deep(.research-task){padding-left:16px;padding-right:16px}.assistant-inspector-body :deep(.reports-trigger),.assistant-inspector-body :deep(.preferences-trigger){padding-left:16px;padding-right:16px}.assistant-inspector-body :deep(.reports-panel),.assistant-inspector-body :deep(.preferences-panel){max-height:none;padding-left:16px;padding-right:16px}@media (hover: hover) and (pointer: fine){.assistant-inspector-close:hover{border-color:#bfc5bd;background:#eef0eb}}@media(max-width:1023px){.assistant-inspector-layer{position:fixed;z-index:90;inset:0}.assistant-inspector-backdrop{position:absolute;inset:0;width:100%;border:0;background:rgba(25,28,25,.28)}.assistant-inspector{position:absolute;top:0;right:0;bottom:0;width:min(430px,100%);border-left:1px solid #adb3ac}.assistant-inspector>header{padding-top:max(18px,env(safe-area-inset-top))}.assistant-inspector-body{padding-bottom:env(safe-area-inset-bottom)}}@media(max-width:640px){.assistant-inspector{width:min(100%,430px)}.assistant-inspector>header{padding-left:14px;padding-right:14px}.assistant-inspector-body :deep(.research-profile),.assistant-inspector-body :deep(.research-task){padding-left:14px;padding-right:14px}.assistant-inspector-body :deep(.reports-trigger),.assistant-inspector-body :deep(.preferences-trigger){padding-left:14px;padding-right:14px}.assistant-inspector-body :deep(.reports-panel),.assistant-inspector-body :deep(.preferences-panel){padding-left:14px;padding-right:14px}}@media(prefers-reduced-motion:reduce){.assistant-inspector *{scroll-behavior:auto}}
.assistant-inspector-body :deep(.research-profile),.assistant-inspector-body :deep(.research-task),.assistant-inspector-body :deep(.research-preferences),.assistant-inspector-body :deep(.research-reports){max-height:none;overflow:visible}
.assistant-inspector-body :deep(.reports-panel),.assistant-inspector-body :deep(.preferences-panel){max-height:none;overflow:visible}
</style>
<style scoped>
.assistant-inspector-enter-active .assistant-inspector,.assistant-inspector-leave-active .assistant-inspector{will-change:transform,opacity;transition:opacity var(--duration-base) var(--ease-out),transform var(--duration-base) var(--ease-out)}
.assistant-inspector-enter-from .assistant-inspector,.assistant-inspector-leave-to .assistant-inspector{opacity:0;transform:translateX(12px)}
@media (max-width:1023px){.assistant-inspector-enter-active .assistant-inspector-backdrop,.assistant-inspector-leave-active .assistant-inspector-backdrop{transition:opacity var(--duration-fast) var(--ease-out)}.assistant-inspector-enter-from .assistant-inspector-backdrop,.assistant-inspector-leave-to .assistant-inspector-backdrop{opacity:0}.assistant-inspector-enter-from .assistant-inspector,.assistant-inspector-leave-to .assistant-inspector{transform:translateX(100%)}}
@media (hover:hover) and (pointer:fine){.assistant-inspector-close{transition:transform var(--duration-fast) var(--ease-out),border-color var(--duration-fast) ease,background-color var(--duration-fast) ease}.assistant-inspector-close:active{transform:scale(.97)}}
@media (hover:hover) and (pointer:fine){.assistant-inspector :deep(button:not(:disabled)){transition:transform var(--duration-fast) var(--ease-out),background-color var(--duration-fast) ease,border-color var(--duration-fast) ease,color var(--duration-fast) ease}.assistant-inspector :deep(button:not(:disabled):active){transform:scale(.98)}}
@media(prefers-reduced-motion:reduce){.assistant-inspector-enter-active .assistant-inspector,.assistant-inspector-leave-active .assistant-inspector,.assistant-inspector-enter-active .assistant-inspector-backdrop,.assistant-inspector-leave-active .assistant-inspector-backdrop{transition:opacity 100ms var(--ease-out);will-change:auto}.assistant-inspector-enter-from .assistant-inspector,.assistant-inspector-leave-to .assistant-inspector{transform:none}}
@media(prefers-reduced-motion:reduce){.assistant-inspector :deep(button){transition:none;transform:none!important}}
</style>
<style scoped>
.assistant-inspector,
.assistant-inspector > header,
.assistant-inspector-body { background: #fbfbf7; }
.assistant-inspector-close {
  border-color: rgba(101, 109, 100, .28);
  background: rgba(251, 251, 247, .7);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .84), inset 0 -1px 0 rgba(41, 47, 40, .08), 0 1px 1px rgba(32, 37, 31, .06);
  backdrop-filter: blur(12px) saturate(1.08);
  -webkit-backdrop-filter: blur(12px) saturate(1.08);
}
.assistant-inspector-enter-active .assistant-inspector,
.assistant-inspector-leave-active .assistant-inspector { transition-duration: var(--assistant-panel-duration, 360ms); }
@media (max-width: 1023px) {
  .assistant-inspector-enter-active .assistant-inspector,
  .assistant-inspector-leave-active .assistant-inspector { transition-duration: var(--assistant-drawer-duration, 420ms); }
}
@media (hover: hover) and (pointer: fine) {
  .assistant-inspector-close:hover { transform: translateY(-1px); border-color: rgba(82, 91, 81, .64); background: rgba(255, 255, 252, .88); box-shadow: inset 0 1px 0 rgba(255, 255, 255, .94), inset 0 -1px 0 rgba(41, 47, 40, .08), 0 4px 12px rgba(32, 37, 31, .1); }
}
@media (prefers-reduced-motion: reduce) {
  .assistant-inspector-enter-active .assistant-inspector,
  .assistant-inspector-leave-active .assistant-inspector { transition-duration: 100ms; }
}
</style>
