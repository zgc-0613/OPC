<template>
  <aside
    ref="drawer"
    class="history-sidebar"
    :class="{ 'is-collapsed': collapsed, 'is-mobile-open': mobileOpen, 'is-mobile-motion': mobileMotion, 'is-motion-collapsing': motionPhase === 'collapsing', 'is-motion-expanding': motionPhase === 'expanding' }"
    :aria-label="mobileOpen ? '研究历史抽屉' : '研究历史'"
    :role="mobileOpen ? 'dialog' : undefined"
    :aria-modal="mobileOpen ? 'true' : undefined"
    :aria-hidden="mobileHidden ? 'true' : undefined"
    :inert="mobileHidden ? '' : undefined"
    @keydown="handleDrawerKeydown"
  >
    <header class="history-sidebar-head">
      <button class="sidebar-toggle" type="button" :aria-label="collapsed && !mobileOpen ? '展开历史栏' : '收起历史栏'" :aria-expanded="!collapsed || mobileOpen" @click="toggleHistory">
        <PanelLeftOpen v-if="collapsed" :size="19" /><PanelLeftClose v-else :size="19" />
      </button>
      <strong v-if="contentVisible">研究历史</strong>
      <button v-if="mobileOpen" ref="mobileClose" class="sidebar-close" type="button" aria-label="关闭历史抽屉" @click="emit('close-mobile', $event)"><X :size="20" /></button>
    </header>

    <button class="new-research" type="button" @click="emit('new')"><Plus class="new-research-icon" :size="18" /><span v-if="contentVisible">新建研究</span></button>

    <div v-if="contentVisible" class="history-extended-content" :aria-hidden="motionContentHidden ? 'true' : undefined" :inert="motionContentHidden ? '' : undefined">
      <label class="history-search">
        <Search :size="16" aria-hidden="true" />
        <span class="sr-only">搜索历史</span>
        <input :value="searchQuery" maxlength="100" placeholder="搜索标题或消息" @input="emit('search', $event.target.value)" />
        <LoaderCircle v-if="searching" class="spin" :size="15" aria-label="正在搜索" />
      </label>

      <div class="history-scopes" aria-label="历史范围">
        <button v-for="option in scopes" :key="option.value" type="button" :class="{ active: scope === option.value }" :aria-pressed="scope === option.value" @click="emit('scope', option.value)">
          <component :is="option.icon" :size="15" />{{ option.label }}
        </button>
      </div>

      <div class="history-list" :aria-busy="loading">
        <p v-if="error" class="history-error" role="alert">{{ error }}</p>
        <template v-for="group in groups" :key="group.label">
          <h3>{{ group.label }}</h3>
          <div
            v-for="session in group.items"
            :key="session.sessionId"
            class="history-row"
            :class="{ selected: String(session.sessionId) === String(selectedId) }"
          >
            <input
              v-if="renamingId === session.sessionId"
              ref="renameInputs"
              v-model="renameTitle"
              class="history-rename"
              maxlength="80"
              aria-label="会话标题"
              @keydown.enter.prevent="commitRename(session)"
              @keydown.esc.prevent="cancelRename"
              @blur="cancelRename"
            />
            <button v-else class="history-row-main" type="button" :aria-current="String(session.sessionId) === String(selectedId) ? 'page' : undefined" @click="emit('select', session, $event)">
              <span>{{ session.title }}</span>
              <small>{{ rowMeta(session) }}</small>
            </button>
            <AssistantSessionMenu
              :session="session"
              :scope="scope"
              @rename="beginRename(session)"
              @pin="emit('pin', session)"
              @archive="emit('archive', session)"
              @unarchive="emit('unarchive', session)"
              @trash="emit('trash', session)"
              @restore="emit('restore', session)"
              @purge="emit('purge', session)"
            />
          </div>
        </template>
        <div v-if="!loading && !items.length" class="history-empty">
          <History :size="22" /><p>{{ emptyText }}</p>
        </div>
        <button v-if="hasMore" class="load-more" type="button" :disabled="loading" @click="emit('load-more')">{{ loading ? '正在读取' : '加载更多' }}</button>
      </div>
    </div>
  </aside>
  <Transition :name="mobileMotion ? 'history-backdrop' : ''">
    <button v-if="mobileOpen" ref="backdrop" class="history-backdrop" type="button" aria-label="关闭历史抽屉" @click="emit('close-mobile', $event)"></button>
  </Transition>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Archive, History, LoaderCircle, PanelLeftClose, PanelLeftOpen, Plus, Search, Trash2, X } from 'lucide-vue-next'
import AssistantSessionMenu from './AssistantSessionMenu.vue'
import { groupHistorySessions } from '@/utils/assistantWorkspace'
import { isolateDialogBranch, trapFocus } from '@/utils/focusTrap'

const props = defineProps({
  items: { type: Array, default: () => [] }, scope: { type: String, default: 'active' },
  searchQuery: { type: String, default: '' }, selectedId: { type: [String, Number], default: '' },
  loading: Boolean, searching: Boolean, hasMore: Boolean, collapsed: Boolean, mobileOpen: Boolean, mobileMotion: Boolean,
  motionPhase: { type: String, default: '' },
  error: { type: String, default: '' },
})
const emit = defineEmits(['toggle', 'close-mobile', 'new', 'search', 'scope', 'select', 'load-more', 'rename', 'pin', 'archive', 'unarchive', 'trash', 'restore', 'purge'])
const mobileClose = ref(null)
const drawer = ref(null)
const backdrop = ref(null)
const renameInputs = ref([])
const renamingId = ref(null)
const renameTitle = ref('')
const contentVisible = computed(() => !props.collapsed || props.mobileOpen || Boolean(props.motionPhase))
const motionContentHidden = computed(() => Boolean(props.motionPhase) && !props.mobileOpen)
const mediaQuery = ref(null)
const mobileViewport = ref(detectMobileViewport())
const mobileHidden = computed(() => mobileViewport.value && !props.mobileOpen)
const scopes = [
  { value: 'active', label: '当前', icon: History },
  { value: 'archived', label: '归档', icon: Archive },
  { value: 'trash', label: '回收站', icon: Trash2 },
]
const groups = computed(() => props.scope === 'active'
  ? groupHistorySessions(props.items)
  : [{ label: props.scope === 'archived' ? '已归档' : '待清理', items: props.items }].filter((group) => group.items.length))
const emptyText = computed(() => props.searchQuery ? '没有匹配的研究记录' : props.scope === 'trash' ? '回收站为空' : props.scope === 'archived' ? '暂无归档研究' : '还没有研究记录')

let releaseIsolation = () => {}
function updateMobileViewport(event) {
  mobileViewport.value = typeof event?.matches === 'boolean' ? event.matches : detectMobileViewport()
}
onMounted(() => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return
  mediaQuery.value = window.matchMedia('(max-width: 840px)')
  updateMobileViewport(mediaQuery.value)
  mediaQuery.value.addEventListener?.('change', updateMobileViewport)
  mediaQuery.value.addListener?.(updateMobileViewport)
})
onBeforeUnmount(() => {
  mediaQuery.value?.removeEventListener?.('change', updateMobileViewport)
  mediaQuery.value?.removeListener?.(updateMobileViewport)
  releaseIsolation()
})
watch(() => props.mobileOpen, async (open) => {
  if (open) {
    await nextTick()
    releaseIsolation()
    releaseIsolation = isolateDialogBranch(drawer.value, [backdrop.value])
    mobileClose.value?.focus()
  } else {
    releaseIsolation()
    releaseIsolation = () => {}
  }
})

function handleDrawerKeydown(event) {
  if (!props.mobileOpen) return
  if (event.key === 'Escape') emit('close-mobile', event)
  else trapFocus(event, drawer.value)
}

function toggleHistory(event) {
  emit('toggle', event)
}

async function beginRename(session) {
  renamingId.value = session.sessionId
  renameTitle.value = session.title
  await nextTick()
  const input = Array.isArray(renameInputs.value) ? renameInputs.value[0] : renameInputs.value
  input?.focus()
  input?.select()
}
function cancelRename() { renamingId.value = null; renameTitle.value = '' }
function commitRename(session) {
  const title = renameTitle.value.trim()
  if (title && title !== session.title) emit('rename', { session, title })
  cancelRename()
}
function rowMeta(session) {
  if (session.activeRunStatus) return '研究进行中'
  const date = session.lastMessageAt || session.updatedAt || session.createdAt
  return date ? new Date(date).toLocaleDateString('zh-CN') : '尚未发送问题'
}
function detectMobileViewport() {
  if (typeof window === 'undefined') return false
  if (typeof window.matchMedia === 'function') return window.matchMedia('(max-width: 840px)').matches
  return window.innerWidth <= 840
}
</script>

<style scoped>
.history-sidebar {
  position: relative;
  z-index: 12;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  height: 100%;
  overflow: hidden;
  border-right: 1px solid #c9cdc7;
  background: #f2f1eb;
  color: #252a25;
  contain: layout paint;
}
.history-sidebar.is-collapsed:not(.is-motion-collapsing):not(.is-motion-expanding) { align-items: center; }
.history-sidebar-head {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 64px;
  padding: 10px 12px;
  border-bottom: 1px solid #d2d5cf;
}
.history-sidebar-head strong {
  flex: 1;
  font-family: 'Noto Serif SC', STSong, SimSun, serif;
  font-weight: 500;
}
.sidebar-toggle,
.sidebar-close {
  display: grid;
  flex: 0 0 44px;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: #303630;
}
.sidebar-close { margin-left: auto; }
.sidebar-toggle,
.sidebar-close,
.new-research-icon { position: relative; z-index: 2; }
.new-research {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-height: 44px;
  margin: 12px;
  border: 1px solid #252a25;
  border-radius: 3px;
  background: #252a25;
  color: #fff;
}
.is-collapsed:not(.is-motion-collapsing):not(.is-motion-expanding) .new-research { width: 44px; margin: 12px 0; padding: 0; }
.history-extended-content { display: flex; flex: 1 1 auto; flex-direction: column; min-height: 0; }
.history-sidebar-head strong,
.new-research span,
.history-extended-content { transition: opacity var(--assistant-drawer-duration, 500ms) var(--ease-out), transform var(--assistant-drawer-duration, 500ms) var(--ease-out); }
.history-sidebar.is-motion-collapsing :is(.history-sidebar-head strong, .new-research span, .history-extended-content) { opacity: 0; pointer-events: none; transform: translateX(-8px); transition-duration: 120ms; }
.history-sidebar.is-motion-collapsing .new-research span { display: none; }
.history-sidebar.is-motion-collapsing > .new-research { width: 44px; min-width: 44px; margin: 12px 0 12px 10px; padding: 0; }
.history-sidebar.is-motion-expanding :is(.history-sidebar-head strong, .new-research span, .history-extended-content),
.history-sidebar.is-motion-expanding > .new-research { opacity: 0; pointer-events: none; transform: translateX(-8px); animation: history-content-reveal 220ms var(--ease-out) 140ms both; }
.history-sidebar.is-motion-expanding > .new-research { width: 252px; min-width: 0; }
@keyframes history-content-reveal { to { opacity: 1; transform: none; } }
.history-search { position: relative; display: flex; align-items: center; margin: 0 12px; }
.history-search > svg:first-child { position: absolute; left: 11px; color: #596159; }
.history-search input {
  width: 100%;
  height: 42px;
  padding: 0 36px;
  border: 1px solid #aeb4ac;
  border-radius: 3px;
  background: #fbfbf7;
  color: #222;
}
.history-search .spin { position: absolute; right: 11px; }
.history-scopes {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  margin: 10px 12px 4px;
}
.history-scopes button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 38px;
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  color: #525a52;
  font-size: .72rem;
}
.history-scopes button.active { border-color: #262b26; color: #202520; font-weight: 700; }
.history-list { min-height: 0; flex: 1; overflow: auto; padding: 7px 8px 18px; }
.history-list h3 {
  margin: 14px 8px 5px;
  color: #626862;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: .66rem;
  letter-spacing: 0;
}
.history-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 36px;
  align-items: center;
  min-height: 54px;
  border-radius: 3px;
}
.history-row.selected { background: #dedfd9; }
.history-row-main {
  display: grid;
  gap: 3px;
  min-width: 0;
  padding: 8px;
  border: 0;
  background: transparent;
  text-align: left;
}
.history-row-main span {
  overflow: hidden;
  color: #252a25;
  font-family: 'Noto Serif SC', STSong, SimSun, serif;
  font-size: .82rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-row-main small { color: #5f665f; font-size: .64rem; }
.history-rename {
  width: calc(100% - 8px);
  height: 38px;
  margin-left: 4px;
  padding: 0 8px;
  border: 1px solid #737a72;
  background: #fff;
  color: #222;
}
.history-error { margin: 10px 6px; color: #7a3731; font-size: .75rem; }
.history-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 32px 14px;
  color: #626862;
  text-align: center;
}
.history-empty p { margin: 0; font-size: .77rem; }
.load-more {
  width: calc(100% - 12px);
  min-height: 40px;
  margin: 10px 6px;
  border: 1px solid #b4bab3;
  background: #fafaf6;
  color: #303630;
}
.history-backdrop { display: none; }
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
.sidebar-toggle:focus-visible,
.sidebar-close:focus-visible,
.new-research:focus-visible,
.history-search input:focus-visible,
.history-scopes button:focus-visible,
.history-row-main:focus-visible,
.history-rename:focus-visible,
.load-more:focus-visible {
  outline: 2px solid #4f6f58;
  outline-offset: 2px;
}
@media (hover: hover) and (pointer: fine) {
  .sidebar-toggle:hover,
  .sidebar-close:hover,
  .history-scopes button:hover,
  .history-row:hover,
  .load-more:hover { background: #e7e8e2; }
  .new-research:hover { background: #3b413b; }
}
@media (max-width: 840px) {
  .history-sidebar {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    box-sizing: border-box;
    width: min(310px, 88vw);
    min-width: 0;
    height: 100dvh;
    padding-top: env(safe-area-inset-top);
    padding-bottom: env(safe-area-inset-bottom);
    transform: translateX(-104%);
  }
  .history-sidebar.is-collapsed { width: min(310px, 88vw); align-items: stretch; }
  .history-sidebar.is-mobile-open { transform: translateX(0); }
  .history-sidebar.is-mobile-motion { transition: transform var(--assistant-drawer-duration, 500ms) var(--ease-out); }
  .history-backdrop {
    position: fixed;
    z-index: 11;
    inset: 0;
    display: block;
    border: 0;
    background: rgba(24, 27, 24, .35);
  }
  .history-sidebar.is-collapsed:not(.is-motion-collapsing):not(.is-motion-expanding) .new-research { width: calc(100% - 24px); margin: 12px; padding: 0 12px; }
  .history-sidebar.is-collapsed:not(.is-motion-collapsing):not(.is-motion-expanding) .new-research span { display: inline; }
  .history-row { grid-template-columns: minmax(0, 1fr) 44px; }
}
.history-backdrop-enter-active { transition: opacity var(--duration-fast) var(--ease-out); }
.history-backdrop-leave-active { transition: opacity 100ms var(--ease-out); }
.history-backdrop-enter-from,.history-backdrop-leave-to { opacity: 0; }
@media (min-width: 641px) and (max-width: 1023px) and (pointer: coarse) {
  .history-search input,
  .history-scopes button,
  .history-rename,
  .load-more { min-height: 44px; }
  .history-row { grid-template-columns: minmax(0, 1fr) 44px; }
}
@media (prefers-reduced-motion: reduce) {
  .history-sidebar,.history-sidebar.is-motion-collapsing,.history-sidebar.is-motion-expanding,.history-sidebar.is-mobile-motion,.history-backdrop-enter-active,.history-backdrop-leave-active { transition: none; will-change: auto; }
  .history-sidebar-head strong,.new-research span,.history-extended-content,.history-sidebar.is-motion-expanding > .new-research { transition: none; animation: none; will-change: auto; }
}
</style>
