<template>
  <details ref="menu" class="session-menu" :class="{ 'motion-enabled': motionEnabled }" @click.stop @pointerdown="enablePointerMotion" @keydown="disableMotion" @toggle="syncOpenState">
    <summary class="icon-command" aria-label="会话操作" title="会话操作">
      <MoreHorizontal :size="17" />
    </summary>
    <div class="session-menu-popover" :aria-hidden="isOpen ? 'false' : 'true'" :inert="isOpen ? undefined : ''">
      <button v-if="scope !== 'trash'" type="button" @click="act('rename')"><Pencil :size="15" />重命名</button>
      <button v-if="scope === 'active'" type="button" @click="act('pin')">
        <PinOff v-if="session.pinned" :size="15" /><Pin v-else :size="15" />
        {{ session.pinned ? '取消置顶' : '置顶' }}
      </button>
      <button v-if="scope === 'active'" type="button" @click="act('archive')"><Archive :size="15" />归档</button>
      <button v-if="scope === 'archived'" type="button" @click="act('unarchive')"><ArchiveRestore :size="15" />取消归档</button>
      <button v-if="scope !== 'trash'" type="button" @click="act('trash')"><Trash2 :size="15" />移入回收站</button>
      <button v-if="scope === 'trash'" type="button" @click="act('restore')"><RotateCcw :size="15" />恢复</button>
      <template v-if="scope === 'trash'">
        <button v-if="!confirming" class="danger" type="button" @click="confirming = true"><Trash2 :size="15" />永久删除内容</button>
        <div v-else class="session-delete-confirm" role="alert">
          <span>删除后无法恢复</span>
          <button class="danger" type="button" @click="act('purge')">确认删除</button>
          <button type="button" @click="confirming = false">取消</button>
        </div>
      </template>
    </div>
  </details>
</template>

<script setup>
import { ref } from 'vue'
import { Archive, ArchiveRestore, MoreHorizontal, Pencil, Pin, PinOff, RotateCcw, Trash2 } from 'lucide-vue-next'

defineProps({ session: { type: Object, required: true }, scope: { type: String, required: true } })
const emit = defineEmits(['rename', 'pin', 'archive', 'unarchive', 'trash', 'restore', 'purge'])
const menu = ref(null)
const isOpen = ref(false)
const motionEnabled = ref(false)
const confirming = ref(false)

function act(name) {
  emit(name)
  if (name !== 'purge' || confirming.value) {
    menu.value?.removeAttribute('open')
    isOpen.value = false
  }
  confirming.value = false
}
function enablePointerMotion() {
  motionEnabled.value = !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}
function disableMotion() { motionEnabled.value = false }
function syncOpenState() { isOpen.value = Boolean(menu.value?.open) }
</script>

<style scoped>
.session-menu{position:relative}.session-menu summary{list-style:none}.session-menu summary::-webkit-details-marker{display:none}.icon-command{display:grid;place-items:center;width:36px;height:36px;border:0;border-radius:3px;background:transparent;color:#4c524c;cursor:pointer}.session-menu-popover{position:absolute;z-index:30;top:38px;right:0;display:grid;width:190px;padding:6px;border:1px solid #c9cdc7;border-radius:4px;background:#fdfdf9;opacity:0;pointer-events:none;transform:translateY(-4px) scale(.98);transform-origin:top right}.session-menu[open] .session-menu-popover{opacity:1;pointer-events:auto;transform:none}.session-menu.motion-enabled .session-menu-popover{transition:opacity var(--duration-fast) var(--ease-out),transform var(--duration-fast) var(--ease-out)}.session-menu-popover>button{display:flex;align-items:center;gap:9px;width:100%;min-height:40px;padding:8px;border:0;border-radius:2px;background:transparent;color:#292e29;text-align:left}.session-menu-popover>button:focus-visible{background:#eceee9}.session-menu-popover .danger{color:#742f2a}.session-delete-confirm{display:grid;gap:5px;padding:8px;border-top:1px solid #d7d9d4}.session-delete-confirm span{color:#6d322e;font-size:.72rem}.session-delete-confirm button{min-height:36px;border:0;background:transparent;text-align:left}@media (hover: hover) and (pointer: fine){.session-menu-popover>button:hover{background:#eceee9}}@media(max-width:720px){.icon-command{width:44px;height:44px}.session-menu-popover{position:fixed;right:14px;bottom:18px;top:auto;width:min(300px,calc(100vw - 28px));padding:10px;transform:translateY(6px) scale(.98);transform-origin:bottom right}.session-menu-popover>button{min-height:44px}}@media(min-width:641px) and (max-width:1023px) and (pointer:coarse){.icon-command{width:44px;height:44px}.session-menu-popover>button,.session-delete-confirm button{min-height:44px}}@media(prefers-reduced-motion:reduce){.session-menu.motion-enabled .session-menu-popover{transition:none}}
</style>
