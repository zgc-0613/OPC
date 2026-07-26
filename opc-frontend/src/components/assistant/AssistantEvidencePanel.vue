<template>
  <section class="evidence-panel" aria-labelledby="assistant-evidence-title">
    <details :open="Boolean(items.length || loading || error)">
      <summary>
        <div>
          <span class="caption">RESEARCH MATERIALS</span>
          <h2 id="assistant-evidence-title">研究资料</h2>
        </div>
        <small>{{ summaryLabel }}</small>
        <ChevronDown :size="17" aria-hidden="true" />
      </summary>

      <div class="evidence-content">
        <p v-if="loading" class="evidence-state" role="status">正在整理研究资料…</p>
        <p v-else-if="error" class="evidence-state is-error" role="alert">{{ error }}</p>
        <p v-else-if="!items.length" class="evidence-state" role="status">暂无可展示的研究资料</p>

        <section v-for="group in visibleGroups" v-else :key="group.type" class="evidence-group" :aria-labelledby="`evidence-${group.type}`">
          <header>
            <h3 :id="`evidence-${group.type}`">{{ group.label }}</h3>
            <span>{{ group.items.length }} 项</span>
          </header>
          <article v-for="item in group.items" :id="`evidence-${item.runId || runId}-${item.sourceId}`" :key="`${item.itemType}:${item.itemId}`" class="evidence-item" :class="{ unavailable: !item.available }" :data-run-id="item.runId || runId" :data-source-id="item.sourceId" :data-citation-id="item.citationId || undefined">
            <div class="evidence-item-heading">
              <div>
                <span v-if="item.sourceId" class="citation-reference">引用 [{{ item.sourceId }}]</span>
                <h4>{{ item.title || '未命名资料' }}</h4>
              </div>
              <span class="evidence-status">{{ item.available ? '已核验' : '当前不可用' }}</span>
            </div>
            <p v-if="item.available && item.brief" class="evidence-brief">{{ item.brief }}</p>
            <dl v-if="item.available" class="evidence-meta">
              <template v-if="item.regionName"><dt>地区</dt><dd>{{ item.regionName }}</dd></template>
              <template v-if="item.industry"><dt>类别</dt><dd>{{ item.industry }}</dd></template>
              <template v-if="item.publisher"><dt>发布方</dt><dd>{{ item.publisher }}</dd></template>
              <template v-if="item.sourceTitle"><dt>来源</dt><dd>{{ item.sourceTitle }}</dd></template>
            </dl>
            <p class="match-reason">{{ item.matchReason }}</p>
            <footer v-if="item.available && (item.detailUrl || safeExternalUrl(item.originalUrl))">
              <a v-if="safeInternalPath(item.detailUrl)" :href="safeInternalPath(item.detailUrl)"><FileText :size="15" />站内详情</a>
              <a v-if="safeExternalUrl(item.originalUrl)" :href="safeExternalUrl(item.originalUrl)" target="_blank" rel="noopener noreferrer"><ExternalLink :size="15" />原始来源</a>
            </footer>
          </article>
        </section>
      </div>
    </details>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { ChevronDown, ExternalLink, FileText } from 'lucide-vue-next'

const props = defineProps({
  runId: { type: [Number, String], default: null },
  items: { type: Array, default: () => [] },
  loading: Boolean,
  error: { type: String, default: '' },
})

const groupDefinitions = [
  { type: 'case', label: '案例' },
  { type: 'policy', label: '政策' },
  { type: 'source', label: '原始来源' },
]
const visibleGroups = computed(() => groupDefinitions
  .map((group) => ({ ...group, items: props.items.filter((item) => item?.itemType === group.type) }))
  .filter((group) => group.items.length))
const summaryLabel = computed(() => props.loading ? '整理中' : `${props.items.length} 项`)

function safeExternalUrl(value) {
  try {
    const url = new URL(String(value || ''))
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : ''
  } catch {
    return ''
  }
}
function safeInternalPath(value) {
  const path = String(value || '')
  return /^\/(cases|policies)\/\d+$/.test(path) ? path : ''
}
</script>

<style scoped>
.evidence-panel{max-width:880px;margin:2px auto 20px;border-top:1px solid #c9cdc7;border-bottom:1px solid #c9cdc7;background:#fbfbf7}.evidence-panel details{width:100%}.evidence-panel summary{display:grid;grid-template-columns:minmax(0,1fr) auto 24px;align-items:center;gap:12px;min-height:58px;padding:10px 0;cursor:pointer;list-style:none}.evidence-panel summary::-webkit-details-marker{display:none}.evidence-panel summary h2{margin:3px 0 0;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:.95rem;font-weight:600}.evidence-panel summary>small{color:#747a73;font-size:.68rem}.evidence-panel summary>svg{transition:transform .16s ease}.evidence-panel details[open] summary>svg{transform:rotate(180deg)}.caption{color:#747a73;font-family:'Bookman Old Style',Georgia,serif;font-size:.61rem;font-weight:700}.evidence-content{display:grid;gap:18px;padding:0 0 16px}.evidence-state{margin:0;padding:12px;border:1px solid #d4d7d1;background:#f3f3ed;color:#626962;font-size:.74rem}.evidence-state.is-error{border-color:#d5b9b4;background:#f8efec;color:#703731}.evidence-group{display:grid;gap:0}.evidence-group>header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:0 0 7px;border-bottom:1px solid #d2d5d0}.evidence-group h3{margin:0;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:.82rem}.evidence-group>header span{color:#7a8179;font-size:.64rem}.evidence-item{padding:14px 0;border-bottom:1px solid #e0e2dd}.evidence-item:last-child{border-bottom:0}.evidence-item-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.evidence-item-heading>div{min-width:0}.evidence-item h4{margin:3px 0 0;overflow-wrap:anywhere;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:.83rem}.citation-reference{color:#305b41;font-family:'Bookman Old Style',Georgia,serif;font-size:.62rem;font-weight:700}.evidence-status{flex:0 0 auto;padding:3px 6px;border:1px solid #aeb9ad;color:#426048;font-size:.61rem}.unavailable .evidence-status{border-color:#c9b6b2;color:#713b35}.evidence-brief{margin:8px 0 0;color:#3f463f;font-size:.74rem;line-height:1.65}.evidence-meta{display:flex;flex-wrap:wrap;gap:4px 14px;margin:8px 0 0;font-size:.65rem}.evidence-meta dt{color:#7a8179}.evidence-meta dd{margin:0;color:#4f564f}.match-reason{margin:8px 0 0;color:#6b726b;font-size:.67rem}.evidence-item footer{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}.evidence-item footer a{display:inline-flex;align-items:center;gap:5px;min-height:38px;padding:0 9px;border:1px solid #bfc5bd;border-radius:3px;background:#fbfbf7;color:#303630;text-decoration:none}.evidence-item footer a:is(:hover,:focus-visible){border-color:#747b74;background:#eceee8}.evidence-item footer a:focus-visible,.evidence-panel summary:focus-visible{outline:2px solid rgba(74,82,74,.34);outline-offset:2px}.evidence-item footer a:active{background:#e1e4dc}@media(max-width:640px),(pointer:coarse){.evidence-panel summary{min-height:44px}.evidence-item footer a{min-height:44px}.evidence-item-heading{gap:8px}.evidence-meta{display:grid;grid-template-columns:auto minmax(0,1fr)}}@media(prefers-reduced-motion:reduce){.evidence-panel summary>svg{transition:none}}
</style>
