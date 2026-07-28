<template>
  <Teleport to="body">
    <div v-if="open" ref="layer" class="drawer-layer">
      <button class="drawer-backdrop" type="button" aria-label="关闭详情" @click="$emit('close')"></button>
      <aside ref="drawer" class="citation-drawer" role="dialog" aria-modal="true" :aria-labelledby="drawerTitleId" @keydown="handleKeydown">
        <header>
          <div><span class="caption">{{ drawerCaption }}</span><h2 :id="drawerTitleId">{{ drawerTitle }}</h2></div>
          <button ref="closeButton" type="button" aria-label="关闭详情" @click="$emit('close')"><X :size="19" /></button>
        </header>
        <div v-if="mode === 'citations'" class="citation-body">
          <p v-if="loading" class="drawer-state" role="status">正在核对本次运行的引用依据…</p>
          <p v-if="error" class="drawer-state is-error" role="alert">{{ error }}</p>
          <ol class="citation-list">
            <li v-for="(citation, index) in citations" :id="`citation-${citation.citationId}`" :key="citation.citationId || `${citation.sourceId}-${index}`" :data-run-id="citation.runId" :data-source-id="citation.sourceId"><span>{{ String(index + 1).padStart(2, '0') }}</span><div><strong>{{ citation.title }}</strong><small>{{ citation.publisher || `来源 #${citation.sourceId}` }}</small><p>{{ citation.claim }}</p><em>{{ citation.verificationStatus || '已核验来源' }}</em><a v-if="safeUrl(citation.url)" :href="safeUrl(citation.url)" target="_blank" rel="noopener noreferrer">查看原始来源<ExternalLink :size="14" /></a></div></li>
          </ol>
        </div>
        <div v-else-if="mode === 'evidence'" class="evidence-body">
          <AssistantEvidencePanel
            title-id="evidence-drawer-panel-title"
            item-id-prefix="drawer-evidence"
            :run-id="evidenceRunId"
            :items="evidenceItems"
            :summary="evidenceSummary"
            :loading="loading"
            :error="error"
          />
        </div>
        <div v-else class="process-body">
          <p v-if="loading">正在读取研究过程…</p><p v-else-if="error" role="alert">{{ error }}</p>
          <template v-else-if="run">
            <div class="process-summary"><span><small>状态</small>{{ run.status }}</span><span><small>模型</small>{{ run.provider }} / {{ run.model }}</span><span><small>Token</small>{{ run.tokenUsage?.totalTokens || 0 }}</span><span><small>耗时</small>{{ run.latencyMs || 0 }} ms</span></div>
            <ol><li v-for="(tool, index) in run.tools || []" :key="tool.toolCallId"><span>{{ String(index + 1).padStart(2, '0') }}</span><div><strong>{{ toolName(tool.toolName) }}</strong><small>{{ tool.status }} · {{ tool.evidenceCount || 0 }} 条证据 · {{ tool.latencyMs || 0 }} ms</small></div></li></ol>
            <p v-if="!run.tools?.length" class="process-empty">本次回答没有调用检索工具。</p>
          </template>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ExternalLink, X } from 'lucide-vue-next'
import AssistantEvidencePanel from './AssistantEvidencePanel.vue'
import { isolateDialogBranch, trapFocus } from '@/utils/focusTrap'

const props = defineProps({ open: Boolean, restoreFocus: { type: Boolean, default: true }, mode: { type: String, default: 'citations' }, citations: { type: Array, default: () => [] }, run: { type: Object, default: null }, evidenceRunId: { type: [Number, String], default: null }, evidenceItems: { type: Array, default: () => [] }, evidenceSummary: { type: Object, default: null }, loading: Boolean, error: { type: String, default: '' } })
const emit = defineEmits(['close'])
const layer = ref(null)
const drawer = ref(null)
const closeButton = ref(null)
let previousFocus = null
let previousBodyOverflow = ''
let releaseIsolation = () => {}
const drawerTitleId = computed(() => ({ citations: 'citation-title', evidence: 'evidence-drawer-title' }[props.mode] || 'process-title'))
const drawerTitle = computed(() => ({ citations: '引用依据', evidence: '研究资料' }[props.mode] || '研究过程'))
const drawerCaption = computed(() => ({ citations: 'VERIFIED SOURCES', evidence: 'RESEARCH MATERIALS' }[props.mode] || 'RESEARCH TRACE'))
watch(() => props.open, async (open) => {
  if (open) {
    previousFocus = document.activeElement
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    await nextTick()
    releaseIsolation()
    releaseIsolation = isolateDialogBranch(layer.value)
    closeButton.value?.focus()
  } else {
    releaseIsolation()
    releaseIsolation = () => {}
    document.body.style.overflow = previousBodyOverflow
    if (props.restoreFocus) previousFocus?.focus?.()
    previousFocus = null
  }
})
onBeforeUnmount(() => {
  releaseIsolation()
  document.body.style.overflow = previousBodyOverflow
})
function handleKeydown(event) {
  if (event.key === 'Escape') emit('close')
  else trapFocus(event, drawer.value)
}
function safeUrl(value) {
  try {
    const url = new URL(String(value || ''))
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : ''
  } catch {
    return ''
  }
}
function toolName(name) { return ({ search_cases: '检索案例', search_policies: '检索政策', get_source: '核验来源', compare_cases: '比较案例' }[name] || name) }
</script>

<style scoped>
.drawer-layer{position:fixed;z-index:100;inset:0}.drawer-backdrop{position:absolute;inset:0;width:100%;border:0;background:rgba(25,28,25,.28)}.citation-drawer{position:absolute;top:0;right:0;bottom:0;width:min(430px,94vw);max-width:100%;overflow-x:hidden;overflow-y:auto;border-left:1px solid #adb3ac;background:#fbfbf7;color:#252a25;animation:drawer-in .2s ease-out}.citation-drawer>header{position:sticky;z-index:2;top:0;display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding:20px;border-bottom:1px solid #ccd0ca;background:#fbfbf7}.citation-drawer h2{margin:5px 0 0;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:1.2rem;font-weight:500}.caption{color:#727972;font-family:'Bookman Old Style',Georgia,serif;font-size:.64rem;font-weight:700}.citation-drawer header button{display:grid;place-items:center;width:44px;height:44px;border:1px solid transparent;background:transparent}.citation-drawer header button:is(:hover,:focus-visible){border-color:#bfc5bd;background:#f0f1ec}.citation-drawer header button:focus-visible{outline:2px solid rgba(74,82,74,.34);outline-offset:2px}.drawer-state{margin:16px 20px 0;padding:10px;border:1px solid #d0d4ce;background:#f2f2ec;color:#5c635c}.drawer-state.is-error{border-color:#d5b9b4;background:#f8efec;color:#703731}.citation-list,.process-body>ol{margin:0;padding:0 20px 30px;list-style:none}.citation-list li,.process-body li{display:grid;grid-template-columns:30px minmax(0,1fr);gap:10px;padding:18px 0;border-bottom:1px solid #d3d7d1}.citation-list li>span,.process-body li>span{color:#777d76;font-family:'Bookman Old Style',Georgia,serif;font-size:.7rem}.citation-list li div{display:grid;min-width:0;gap:5px}.citation-list strong{overflow-wrap:anywhere}.citation-list small{color:#767c75}.citation-list p{margin:5px 0;overflow-wrap:anywhere;color:#505750;line-height:1.6}.citation-list em{color:#3b6549;font-size:.68rem;font-style:normal}.citation-list a{display:inline-flex;align-items:center;gap:5px;min-height:38px;color:#315c42;font-size:.73rem;font-weight:700}.process-body>p{padding:20px;color:#6c736c}.process-summary{display:grid;grid-template-columns:1fr 1fr;gap:1px;margin:20px;border:1px solid #d0d4ce;background:#d0d4ce}.process-summary span{display:grid;gap:3px;padding:12px;background:#fbfbf7;font-size:.75rem}.process-summary small{color:#777d76;font-size:.62rem}.process-body li div{display:grid;gap:4px}.process-body li small{color:#777d76}.process-empty{padding-top:0!important}@keyframes drawer-in{from{opacity:0;transform:translateX(16px)}to{opacity:1;transform:translateX(0)}}@media(max-width:640px),(pointer:coarse){.citation-list a{min-height:44px}.citation-drawer header button{min-width:44px;min-height:44px}}@media (min-width:641px) and (max-width:1023px) and (pointer:coarse){.citation-list a,.citation-drawer header button{min-height:44px}.citation-drawer header button{min-width:44px}}@media(prefers-reduced-motion:reduce){.citation-drawer{animation:none}}
.evidence-body{padding:12px 20px 24px}.evidence-body :deep(.evidence-panel){margin:0}
</style>
