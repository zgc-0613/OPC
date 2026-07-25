<template>
  <section v-if="run" class="run-progress" :class="`is-${run.status}`" :role="isError ? 'alert' : 'status'">
    <div class="run-main">
      <span v-if="!terminal" class="run-pulse" aria-hidden="true"></span>
      <AlertTriangle v-else-if="isError" :size="18" />
      <CheckCircle2 v-else-if="run.status === 'completed'" :size="18" />
      <div>
        <strong>{{ title }}</strong>
        <p v-if="description">{{ description }}</p>
        <small v-if="networkStatus === 'recovering'">连接中断，正在恢复最后已知状态</small>
        <small v-else-if="networkStatus === 'paused'">自动恢复已暂停，可手动继续读取</small>
      </div>
      <button v-if="!terminal" class="text-command" type="button" :disabled="cancelling" @click="$emit('cancel')"><Square :size="14" />{{ cancelling ? '正在取消' : '取消' }}</button>
      <button v-else-if="isError" class="text-command" type="button" @click="$emit('retry')"><RefreshCw :size="14" />安全重试</button>
      <button v-if="networkStatus === 'paused'" class="text-command" type="button" @click="$emit('resume')"><Wifi :size="14" />恢复连接</button>
    </div>
    <div v-if="run.tools?.length" class="run-tools">
      <span v-for="tool in run.tools" :key="tool.toolCallId"><strong>{{ toolLabel(tool.toolName) }}</strong><small>{{ tool.evidenceCount || 0 }} 条证据 · {{ tool.latencyMs || 0 }} ms</small></span>
    </div>
    <footer v-if="terminal && (run.provider || run.tokenUsage)">
      <span>{{ run.provider || 'not_called' }}<template v-if="run.model"> / {{ run.model }}</template></span>
      <span>{{ run.tokenUsage?.totalTokens || 0 }} tokens</span><span>{{ run.toolCallCount || 0 }} 次工具</span><span v-if="run.completedAt">{{ formatTime(run.completedAt) }}</span>
    </footer>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { AlertTriangle, CheckCircle2, RefreshCw, Square, Wifi } from 'lucide-vue-next'

const props = defineProps({ run: { type: Object, default: null }, networkStatus: { type: String, default: 'connected' }, cancelling: Boolean })
defineEmits(['cancel', 'retry', 'resume'])
const terminalStates = new Set(['completed', 'clarification_needed', 'evidence_insufficient', 'failed', 'cancelled', 'expired'])
const terminal = computed(() => terminalStates.has(props.run?.status))
const isError = computed(() => ['failed', 'cancelled', 'expired', 'evidence_insufficient'].includes(props.run?.status))
const title = computed(() => {
  if (!props.run) return ''
  const status = {
    completed: '研究已完成', clarification_needed: '需要补充研究条件', evidence_insufficient: '当前证据不足',
    failed: '研究运行未完成', cancelled: '研究运行已取消', expired: '研究运行已过期',
  }[props.run.status]
  return status || props.run.visibleProgress || stageLabel(props.run.currentStage)
})
const description = computed(() => {
  if (!props.run) return ''
  if (props.run.status === 'evidence_insufficient') return '请调整地区、行业或研究范围后重新提交。'
  const errors = {
    TRUNCATED_RESPONSE: '模型输出被截断，请缩小问题范围后重试。', MISSING_CITATIONS: '回答缺少合法引用，结果未展示。',
    UNKNOWN_SOURCE_ID: '模型引用了本次研究之外的来源，结果未展示。', PROVIDER_RATE_LIMIT: '模型服务繁忙，请稍后重试。',
    PROVIDER_TIMEOUT: '模型服务响应超时。', EVIDENCE_CHANGED: '研究期间证据发生变化，请重新提交。',
  }
  return errors[props.run.diagnosticCode] || (terminal.value ? props.run.visibleProgress : '')
})
function stageLabel(stage) { return ({ received: '正在理解问题', planning: '正在制定研究步骤', waiting_for_model: '正在理解问题', tool_requested: '正在准备检索', tool_running: '正在检索并核验证据', synthesizing: '正在整理回答' }[stage] || '正在处理研究任务') }
function toolLabel(name) { return ({ search_cases: '检索案例', search_policies: '检索政策', get_source: '核验来源', compare_cases: '比较案例' }[name] || name) }
function formatTime(value) { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }
</script>

<style scoped>
.run-progress{max-width:880px;margin:6px auto 20px;padding:15px 0;border-top:1px solid #c9cdc7;border-bottom:1px solid #c9cdc7}.run-main{display:flex;align-items:flex-start;gap:11px}.run-main>div{min-width:0;flex:1}.run-main strong{font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:.88rem;font-weight:600}.run-main p{margin:4px 0 0;color:#5c635c;font-size:.76rem;line-height:1.55}.run-main small{display:block;margin-top:4px;color:#7a5a31;font-size:.68rem}.run-pulse{width:9px;height:9px;margin-top:5px;border-radius:50%;background:#3d684a;animation:pulse 1.4s ease-in-out infinite}.text-command{display:flex;align-items:center;gap:6px;min-height:38px;padding:0 8px;border:0;background:transparent;color:#333933}.is-failed,.is-cancelled,.is-expired,.is-evidence_insufficient{color:#6e302c}.run-tools{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px 18px;margin:12px 0 0 20px}.run-tools span{display:grid;gap:2px;padding-top:8px;border-top:1px solid #d8dad5}.run-tools strong{font-size:.71rem}.run-tools small{color:#747a73;font-size:.64rem}.run-progress footer{display:flex;flex-wrap:wrap;gap:6px 16px;margin:12px 0 0 20px;color:#777d76;font-family:'Bookman Old Style',Georgia,serif;font-size:.63rem}@keyframes pulse{50%{opacity:.35;transform:scale(.8)}}@media(max-width:640px){.run-main{flex-wrap:wrap}.run-main .text-command{min-height:44px}.run-tools{grid-template-columns:1fr;margin-left:0}.run-progress footer{margin-left:0}}@media(prefers-reduced-motion:reduce){.run-pulse{animation:none}}
</style>
