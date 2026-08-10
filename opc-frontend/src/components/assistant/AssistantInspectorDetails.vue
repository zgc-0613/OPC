<template>
  <div v-if="mode === 'citations'" class="inspector-citations">
    <p v-if="loading" class="inspector-state" role="status">正在核对本次运行的引用依据</p>
    <p v-else-if="error" class="inspector-state is-error" role="alert">{{ error }}</p>
    <ol v-else class="citation-list">
      <li v-for="(citation, index) in citations" :id="`citation-${citation.citationId}`" :key="citation.citationId || `${citation.sourceId}-${index}`" :data-run-id="citation.runId" :data-source-id="citation.sourceId">
        <span>{{ String(index + 1).padStart(2, '0') }}</span>
        <div>
          <strong>{{ citation.title }}</strong>
          <small>{{ citation.publisher || `来源 #${citation.sourceId}` }}</small>
          <p v-if="citation.claim">{{ citation.claim }}</p>
          <em>{{ citation.verificationStatus || '已核验来源' }}</em>
          <a v-if="safeUrl(citation.url)" :href="safeUrl(citation.url)" target="_blank" rel="noopener noreferrer">查看原始来源<ExternalLink :size="14" /></a>
        </div>
      </li>
    </ol>
    <p v-if="!loading && !error && !citations.length" class="inspector-empty">本次回答没有可展示的引用依据。</p>
  </div>

  <div v-else-if="mode === 'evidence'" class="inspector-evidence">
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

  <div v-else class="inspector-process">
    <p v-if="loading" class="inspector-state" role="status">正在读取研究过程</p>
    <p v-else-if="error" class="inspector-state is-error" role="alert">{{ error }}</p>
    <template v-else-if="run">
      <dl class="process-summary">
        <div><dt>状态</dt><dd>{{ statusLabel(run.status) }}</dd></div>
        <div><dt>模型</dt><dd>{{ run.provider || '未调用' }}<template v-if="run.model"> / {{ run.model }}</template></dd></div>
        <div><dt>Token</dt><dd>{{ run.tokenUsage?.totalTokens || 0 }}</dd></div>
        <div><dt>耗时</dt><dd>{{ run.latencyMs || 0 }} ms</dd></div>
      </dl>
      <ol v-if="run.tools?.length" class="process-list">
        <li v-for="(tool, index) in run.tools" :key="tool.toolCallId || `${tool.toolName}-${index}`">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <div><strong>{{ toolName(tool.toolName) }}</strong><small>{{ tool.status }} · {{ tool.evidenceCount || 0 }} 条证据 · {{ tool.latencyMs || 0 }} ms</small></div>
        </li>
      </ol>
      <p v-else class="inspector-empty">本次回答没有调用检索工具。</p>
    </template>
  </div>
</template>

<script setup>
import { ExternalLink } from 'lucide-vue-next'
import AssistantEvidencePanel from './AssistantEvidencePanel.vue'

defineProps({
  mode: { type: String, default: 'citations' },
  citations: { type: Array, default: () => [] },
  run: { type: Object, default: null },
  evidenceRunId: { type: [Number, String], default: null },
  evidenceItems: { type: Array, default: () => [] },
  evidenceSummary: { type: Object, default: null },
  loading: Boolean,
  error: { type: String, default: '' },
})

function safeUrl(value) {
  try {
    const url = new URL(String(value || ''))
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : ''
  } catch {
    return ''
  }
}

function statusLabel(status) {
  return ({
    completed: '研究已完成', clarification_needed: '需要补充研究条件', evidence_insufficient: '当前证据不足',
    failed: '研究运行未完成', cancelled: '研究运行已取消', expired: '研究运行已过期',
  }[status] || '研究处理中')
}

function toolName(name) {
  return ({ search_cases: '检索案例', search_policies: '检索政策', get_source: '核验来源', compare_cases: '比较案例' }[name] || name)
}
</script>
<style scoped>
.citation-list li > span,
.process-list li > span,
.process-summary dt { color: #5f665f !important; }
.inspector-state,.inspector-empty{margin:0;padding:16px;color:#697169;font-size:.72rem;line-height:1.6}.inspector-state.is-error{color:#703731}.citation-list,.process-list{margin:0;padding:0 16px 24px;list-style:none}.citation-list li,.process-list li{display:grid;grid-template-columns:28px minmax(0,1fr);gap:10px;padding:16px 0;border-bottom:1px solid #d4d8d2}.citation-list li>span,.process-list li>span{color:#777d76;font-family:'Bookman Old Style',Georgia,serif;font-size:.7rem}.citation-list li div,.process-list li div{display:grid;min-width:0;gap:5px}.citation-list strong,.citation-list p,.citation-list small{overflow-wrap:anywhere}.citation-list small,.process-list small{color:#687068;font-size:.68rem}.citation-list p{margin:2px 0;color:#505750;font-size:.73rem;line-height:1.6}.citation-list em{color:#3f684a;font-size:.66rem;font-style:normal}.citation-list a{display:inline-flex;align-items:center;gap:5px;min-height:38px;color:#315c42;font-size:.72rem;font-weight:700;text-decoration:underline;text-underline-offset:3px}.inspector-evidence{padding:12px 16px 24px}.inspector-evidence :deep(.evidence-panel){margin:0}.process-summary{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1px;margin:16px;border:1px solid #d0d4ce;background:#d0d4ce}.process-summary div{min-width:0;padding:11px;background:#fbfbf7}.process-summary dt{color:#717870;font-size:.62rem}.process-summary dd{margin:4px 0 0;overflow-wrap:anywhere;font-size:.72rem;line-height:1.45}.process-list strong{font-size:.75rem}.process-list small{line-height:1.5}@media (hover: hover) and (pointer: fine){.citation-list a:hover{color:#1e4530}}@media(max-width:640px){.citation-list,.process-list{padding-left:14px;padding-right:14px}.citation-list a{min-height:44px}.inspector-evidence{padding-left:14px;padding-right:14px}.process-summary{grid-template-columns:1fr;margin:14px}}@media(prefers-reduced-motion:reduce){.inspector-citations,.inspector-process{scroll-behavior:auto}}
</style>
