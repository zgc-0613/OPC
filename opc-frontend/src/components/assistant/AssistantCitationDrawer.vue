<template>
  <Teleport to="body">
    <div v-if="open" class="drawer-layer" @keydown.esc="$emit('close')">
      <button class="drawer-backdrop" type="button" aria-label="关闭详情" @click="$emit('close')"></button>
      <aside ref="drawer" class="citation-drawer" role="dialog" aria-modal="true" :aria-labelledby="mode === 'citations' ? 'citation-title' : 'process-title'">
        <header>
          <div><span class="caption">{{ mode === 'citations' ? 'VERIFIED SOURCES' : 'RESEARCH TRACE' }}</span><h2 :id="mode === 'citations' ? 'citation-title' : 'process-title'">{{ mode === 'citations' ? '引用依据' : '研究过程' }}</h2></div>
          <button ref="closeButton" type="button" aria-label="关闭详情" @click="$emit('close')"><X :size="19" /></button>
        </header>
        <ol v-if="mode === 'citations'" class="citation-list">
          <li v-for="(citation, index) in citations" :key="`${citation.sourceId}-${index}`"><span>{{ String(index + 1).padStart(2, '0') }}</span><div><strong>{{ citation.title }}</strong><small>{{ citation.publisher || `来源 #${citation.sourceId}` }}</small><p>{{ citation.claim }}</p><em>{{ citation.verificationStatus || '已核验来源' }}</em><a v-if="safeUrl(citation.url)" :href="citation.url" target="_blank" rel="noopener noreferrer">查看原始来源<ExternalLink :size="14" /></a></div></li>
        </ol>
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
import { nextTick, ref, watch } from 'vue'
import { ExternalLink, X } from 'lucide-vue-next'

const props = defineProps({ open: Boolean, mode: { type: String, default: 'citations' }, citations: { type: Array, default: () => [] }, run: { type: Object, default: null }, loading: Boolean, error: { type: String, default: '' } })
defineEmits(['close'])
const closeButton = ref(null)
watch(() => props.open, async (open) => { if (open) { await nextTick(); closeButton.value?.focus() } })
function safeUrl(value) { try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) } catch { return false } }
function toolName(name) { return ({ search_cases: '检索案例', search_policies: '检索政策', get_source: '核验来源', compare_cases: '比较案例' }[name] || name) }
</script>

<style scoped>
.drawer-layer{position:fixed;z-index:100;inset:0}.drawer-backdrop{position:absolute;inset:0;width:100%;border:0;background:rgba(25,28,25,.28)}.citation-drawer{position:absolute;top:0;right:0;bottom:0;width:min(430px,94vw);overflow:auto;border-left:1px solid #adb3ac;background:#fbfbf7;color:#252a25;animation:drawer-in .2s ease-out}.citation-drawer>header{position:sticky;z-index:2;top:0;display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding:20px;border-bottom:1px solid #ccd0ca;background:#fbfbf7}.citation-drawer h2{margin:5px 0 0;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:1.2rem;font-weight:500}.caption{color:#727972;font-family:'Bookman Old Style',Georgia,serif;font-size:.64rem;font-weight:700}.citation-drawer header button{display:grid;place-items:center;width:44px;height:44px;border:0;background:transparent}.citation-list,.process-body>ol{margin:0;padding:0 20px 30px;list-style:none}.citation-list li,.process-body li{display:grid;grid-template-columns:30px minmax(0,1fr);gap:10px;padding:18px 0;border-bottom:1px solid #d3d7d1}.citation-list li>span,.process-body li>span{color:#777d76;font-family:'Bookman Old Style',Georgia,serif;font-size:.7rem}.citation-list li div{display:grid;gap:5px}.citation-list strong{overflow-wrap:anywhere}.citation-list small{color:#767c75}.citation-list p{margin:5px 0;color:#505750;line-height:1.6}.citation-list em{color:#3b6549;font-size:.68rem;font-style:normal}.citation-list a{display:inline-flex;align-items:center;gap:5px;color:#315c42;font-size:.73rem;font-weight:700}.process-body>p{padding:20px;color:#6c736c}.process-summary{display:grid;grid-template-columns:1fr 1fr;gap:1px;margin:20px;border:1px solid #d0d4ce;background:#d0d4ce}.process-summary span{display:grid;gap:3px;padding:12px;background:#fbfbf7;font-size:.75rem}.process-summary small{color:#777d76;font-size:.62rem}.process-body li div{display:grid;gap:4px}.process-body li small{color:#777d76}.process-empty{padding-top:0!important}@keyframes drawer-in{from{opacity:0;transform:translateX(16px)}to{opacity:1;transform:translateX(0)}}@media(prefers-reduced-motion:reduce){.citation-drawer{animation:none}}
</style>
