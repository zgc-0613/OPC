<template>
  <header class="assistant-session-header">
    <div class="session-heading">
      <span class="caption">{{ isNew ? 'NEW RESEARCH' : 'CURRENT RESEARCH' }}</span>
      <h1>{{ title }}</h1>
      <p v-if="scope">{{ scope }}</p>
    </div>
    <nav class="session-header-actions" aria-label="当前研究操作">
      <button data-testid="session-open-research-conditions" type="button" :aria-expanded="conditionsOpen ? 'true' : 'false'" @click="$emit('conditions', $event)"><SlidersHorizontal :size="16" aria-hidden="true" /><span>研究条件</span></button>
      <button data-testid="session-open-research-materials" type="button" :disabled="!hasRun" @click="$emit('materials', $event)"><BookOpenText :size="16" aria-hidden="true" /><span>引用与材料</span></button>
      <button data-testid="session-open-research-reports" type="button" :disabled="!sessionId" @click="$emit('reports', $event)"><FileText :size="16" aria-hidden="true" /><span>报告</span></button>
    </nav>
  </header>
</template>

<script setup>
import { BookOpenText, FileText, SlidersHorizontal } from 'lucide-vue-next'

defineProps({
  title: { type: String, default: '新研究' },
  scope: { type: String, default: '' },
  sessionId: { type: [String, Number], default: null },
  hasRun: Boolean,
  isNew: Boolean,
  conditionsOpen: Boolean,
})
defineEmits(['conditions', 'materials', 'reports'])
</script>
<style scoped>
.caption { color: #5f665f !important; }
.assistant-session-header{display:flex;align-items:flex-start;justify-content:space-between;gap:24px;min-width:0;padding:20px max(24px,calc((100% - 880px)/2));border-bottom:1px solid #d0d4ce;background:#fbfbf7}.session-heading{min-width:0}.caption{display:block;color:#717870;font-family:'Bookman Old Style',Georgia,serif;font-size:.63rem;font-weight:700}.session-heading h1{max-width:65ch;margin:5px 0 0;overflow-wrap:anywhere;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:1.2rem;font-weight:600;line-height:1.3}.session-heading p{max-width:65ch;margin:5px 0 0;overflow-wrap:anywhere;color:#697169;font-size:.72rem;line-height:1.5}.session-header-actions{display:flex;flex:0 0 auto;align-items:center;gap:4px}.session-header-actions button{display:inline-flex;align-items:center;justify-content:center;gap:6px;min-height:44px;padding:0 9px;border:1px solid transparent;border-radius:3px;background:transparent;color:#4e564e;font:inherit;font-size:.69rem;font-weight:700;white-space:nowrap}.session-header-actions button:focus-visible{outline:2px solid rgba(74,82,74,.38);outline-offset:2px}.session-header-actions button:active{transform:translateY(1px)}.session-header-actions button:disabled{color:#a0a69f;cursor:not-allowed}@media (hover: hover) and (pointer: fine){.session-header-actions button:hover:not(:disabled){border-color:#bfc5bd;background:#eef0eb;color:#282d28}}@media(max-width:840px){.assistant-session-header{padding:16px 20px}.session-header-actions span{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}.session-header-actions button{width:44px;padding:0}}@media(max-width:640px){.assistant-session-header{align-items:flex-start;gap:12px;padding:13px 14px}.session-heading h1{font-size:1.05rem}.session-heading p{font-size:.68rem}.session-header-actions{gap:0}.session-header-actions button{width:44px}}@media(prefers-reduced-motion:reduce){.session-header-actions button{transition:none}}
</style>
