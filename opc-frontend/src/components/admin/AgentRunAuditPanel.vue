<template>
  <section class="agent-audit" aria-labelledby="agent-audit-title">
    <header class="agent-audit__head">
      <div>
        <span class="caption">AGENT RUN AUDIT</span>
        <h3 id="agent-audit-title">智能体运行记录</h3>
        <p>仅展示脱敏运行元数据与工具审计，不显示私人问题、提示词或模型原始响应。</p>
      </div>
      <button class="button button-ghost" type="button" :disabled="loading" @click="loadRuns">
        <RefreshCw :size="16" />{{ loading ? '读取中' : '刷新记录' }}
      </button>
    </header>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div v-else-if="loading" class="agent-audit__state" role="status">正在读取智能体运行记录...</div>
    <div v-else-if="!runs.length" class="agent-audit__state">暂无智能体运行记录。</div>
    <div v-else class="agent-audit__table-wrap">
      <table>
        <thead><tr><th>运行</th><th>用户 / 会话</th><th>状态</th><th>Provider / 模型</th><th>轮次 / 工具</th><th>Token</th><th>耗时</th><th>创建时间</th><th><span class="sr-only">详情</span></th></tr></thead>
        <tbody>
          <tr v-for="run in runs" :key="run.runId">
            <td><strong>#{{ run.runId }}</strong><small>{{ run.requestId || '无 request ID' }}</small></td>
            <td><span>{{ run.maskedUser }}</span><small>Session #{{ run.sessionId || '-' }}</small></td>
            <td><span class="agent-audit__status" :class="`is-${run.status}`">{{ statusLabel(run.status) }}</span><small>{{ run.diagnosticCode || run.finishReason || '-' }}</small></td>
            <td><span>{{ run.provider || '-' }}</span><small>{{ run.model || '-' }}</small></td>
            <td>{{ run.modelRounds || 0 }} / {{ run.toolCallCount || 0 }}</td>
            <td><strong>{{ run.totalTokens || 0 }}</strong><small>{{ run.promptTokens || 0 }} + {{ run.completionTokens || 0 }}</small></td>
            <td>{{ run.latencyMs || 0 }} ms</td>
            <td>{{ formatDate(run.createdAt) }}</td>
            <td><button class="agent-audit__inspect" type="button" :data-run-id="run.runId" :aria-label="`查看运行 ${run.runId}`" @click="inspect(run.runId)"><ChevronRight :size="17" /></button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <section v-if="detail" class="agent-audit__detail" aria-labelledby="agent-audit-detail-title">
      <header><div><span class="caption">RUN #{{ detail.run.runId }}</span><h4 id="agent-audit-detail-title">工具执行摘要</h4></div><button class="agent-audit__inspect" type="button" aria-label="关闭运行详情" @click="detail = null"><X :size="17" /></button></header>
      <p v-if="!detail.tools?.length" class="agent-audit__state">本次运行没有工具调用。</p>
      <ol v-else>
        <li v-for="tool in detail.tools" :key="tool.toolCallId">
          <span>{{ String(tool.stepNo).padStart(2, '0') }}</span>
          <div><strong>{{ tool.toolName }}</strong><small>{{ statusLabel(tool.status) }} · {{ tool.evidenceCount || 0 }} 条证据 · {{ tool.latencyMs || 0 }} ms</small><code>{{ shortHash(tool.evidenceHash) }}</code></div>
        </li>
      </ol>
    </section>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ChevronRight, RefreshCw, X } from 'lucide-vue-next'
import { getAdminAgentRun, getAdminAgentRuns } from '@/api/ai'

const runs = ref([])
const detail = ref(null)
const loading = ref(false)
const error = ref('')

onMounted(loadRuns)

async function loadRuns() {
  loading.value = true
  error.value = ''
  try { runs.value = await getAdminAgentRuns(50) || [] }
  catch (reason) { error.value = reason.message || '智能体运行记录读取失败。' }
  finally { loading.value = false }
}

async function inspect(runId) {
  error.value = ''
  try { detail.value = await getAdminAgentRun(runId) }
  catch (reason) { error.value = reason.message || '智能体运行详情读取失败。' }
}

function statusLabel(value) {
  return { completed: '已完成', evidence_insufficient: '证据不足', failed: '失败', cancelled: '已取消', expired: '已过期', running: '运行中', clarification_needed: '待补充' }[value] || value || '-'
}
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }
function shortHash(value) { return value ? `${value.slice(0, 12)}...` : '无 evidence hash' }
</script>

<style scoped>
.agent-audit{display:grid;gap:16px;padding-top:24px;border-top:1px solid #c9cec7;color:#282d28}.agent-audit__head,.agent-audit__detail>header{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}.agent-audit h3,.agent-audit h4{margin:5px 0 0;font-family:'Noto Serif SC',STSong,SimSun,serif;font-weight:500;letter-spacing:0}.agent-audit h3{font-size:1.18rem}.agent-audit h4{font-size:1rem}.agent-audit__head p{max-width:700px;margin:7px 0 0;color:#6b716b;font-size:.78rem;line-height:1.6}.agent-audit__head .button{display:inline-flex;align-items:center;gap:6px;min-height:40px}.agent-audit__state{padding:22px 0;color:#747a73}.agent-audit__table-wrap{max-width:100%;overflow-x:auto;border-top:1px solid #c9cec7;border-bottom:1px solid #c9cec7}.agent-audit table{width:100%;min-width:970px;border-collapse:collapse;text-align:left}.agent-audit th{padding:10px 9px;color:#6c726b;font-size:.66rem;font-weight:700}.agent-audit td{padding:11px 9px;border-top:1px solid #d8dbd6;vertical-align:top;font-size:.73rem}.agent-audit td :is(strong,span,small){display:block}.agent-audit td small{margin-top:3px;color:#777d76;overflow-wrap:anywhere}.agent-audit__status{font-weight:700}.agent-audit__status.is-completed{color:#356045}.agent-audit__status.is-failed,.agent-audit__status.is-expired{color:#834a41}.agent-audit__status.is-running{color:#7a6234}.agent-audit__inspect{display:inline-grid;place-items:center;width:36px;height:36px;border:1px solid #bec4bc;border-radius:3px;background:#fafaf7;color:#343a34}.agent-audit__detail{display:grid;gap:12px;padding:18px 0 0;border-top:1px solid #c9cec7}.agent-audit__detail ol{margin:0;padding:0;list-style:none}.agent-audit__detail li{display:grid;grid-template-columns:28px minmax(0,1fr);gap:10px;padding:13px 0;border-top:1px solid #d6dad4}.agent-audit__detail li>span{color:#797f78;font-family:'Bookman Old Style',Georgia,serif;font-size:.7rem}.agent-audit__detail li div{display:grid;gap:3px}.agent-audit__detail small{color:#70776f}.agent-audit__detail code{color:#555c55;font-size:.68rem;overflow-wrap:anywhere}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}@media(hover:hover){.agent-audit__inspect:hover{background:#eceee9}}@media(max-width:640px){.agent-audit__head{align-items:stretch;flex-direction:column}.agent-audit__head .button{justify-content:center;min-height:44px}.agent-audit__inspect{width:44px;height:44px}}
</style>
