<template>
  <div class="assistant-page">
    <section v-if="pageLoading" class="assistant-page-state" role="status">
      <span class="assistant-spinner" aria-hidden="true"></span>
      <div><strong>正在准备研究工作台</strong><p>读取研究画像、历史会话与智能体状态。</p></div>
    </section>

    <section v-else-if="pageError" class="assistant-page-state is-error" role="alert">
      <AlertTriangle :size="22" />
      <div><strong>研究助手暂时无法读取</strong><p>{{ pageError }}</p></div>
      <button class="button button-ghost" type="button" @click="loadPage"><RefreshCw :size="16" />重新读取</button>
    </section>

    <div v-else class="assistant-workspace">
      <aside class="assistant-profile" aria-labelledby="assistant-profile-title">
        <header class="assistant-profile-head">
          <div><span class="caption">PROFILE / 01</span><h2 id="assistant-profile-title">创业画像</h2></div>
          <SlidersHorizontal :size="20" />
        </header>
        <p class="assistant-profile-intro">画像随新研究会话保存，用于限定地区、行业和研究目标。</p>

        <form class="assistant-profile-form" @submit.prevent="createSession">
          <label><span>创业类型</span>
            <select v-model="profile.ventureType" :disabled="busy">
              <option value="solo_company">一人公司</option><option value="individual_business">个体经营</option>
              <option value="small_team">小型创业团队</option><option value="exploring">尚在探索</option>
            </select>
          </label>
          <label><span>所在地区</span>
            <select v-model="profile.regionId" :disabled="busy">
              <option value="">暂未确定</option>
              <option v-for="region in regions" :key="region.id" :value="String(region.id)">{{ region.name }}</option>
            </select>
          </label>
          <label><span>目标行业</span>
            <div class="assistant-industry-input">
              <input v-model="industryQuery" maxlength="80" list="assistant-industries" placeholder="选择或输入行业" :disabled="busy" @input="syncIndustry" />
              <Search :size="16" />
              <datalist id="assistant-industries"><option v-for="item in industries" :key="item.tagId" :value="item.name" /></datalist>
            </div>
            <small v-if="profile.industryTagId" class="assistant-confirmed">已确认规范行业：{{ profile.industry }}</small>
            <button v-else-if="profile.industry" class="assistant-inline-action" type="button" :disabled="industryLoading" @click="recommendIndustry">
              <Sparkles :size="14" />{{ industryLoading ? '正在识别' : 'AI 推荐规范行业' }}
            </button>
          </label>
          <div v-if="industrySuggestion" class="assistant-suggestion" role="status">
            <div><span>建议行业</span><strong>{{ industrySuggestion.name }}</strong><small>{{ formatPercent(industrySuggestion.confidence) }}</small></div>
            <div><button class="button" type="button" @click="confirmIndustry">确认</button><button class="button button-ghost" type="button" @click="rejectIndustry">忽略</button></div>
          </div>
          <div class="assistant-form-pair">
            <label><span>当前阶段</span><select v-model="profile.stage" :disabled="busy">
              <option value="idea">想法形成</option><option value="validation">需求验证</option>
              <option value="early_operation">早期运营</option><option value="growth">增长阶段</option>
            </select></label>
            <label><span>可投入预算</span><select v-model="profile.budgetRange" :disabled="busy">
              <option value="under_100k">10 万元以内</option><option value="100k_500k">10-50 万元</option>
              <option value="500k_1m">50-100 万元</option><option value="over_1m">100 万元以上</option>
              <option value="undecided">尚未确定</option>
            </select></label>
          </div>
          <label><span>当前目标</span><textarea v-model.trim="profile.goal" rows="3" maxlength="200" placeholder="例如：验证首批付费客户" :disabled="busy"></textarea></label>
          <label><span>已有资源 <small>可选</small></span><textarea v-model.trim="profile.existingResources" rows="3" maxlength="300" placeholder="产品原型、客户线索或行业经验" :disabled="busy"></textarea></label>

          <section class="assistant-readiness" aria-live="polite">
            <div class="assistant-readiness-row" :class="{ ready: agentReady }"><span></span><div><strong>{{ agentReady ? 'Agent Runtime 已启用' : 'Agent Runtime 未启用' }}</strong><small>{{ providerLabel }}</small></div></div>
            <div class="assistant-readiness-row" :class="readinessClass"><span></span><div><strong>{{ readinessTitle }}</strong><small v-if="readiness">案例 {{ readiness.verifiedCaseCount || 0 }} · 选入政策 {{ readiness.selectedPolicyCount ?? readiness.verifiedPolicyCount ?? 0 }} · 直接行业政策 {{ readiness.directIndustryPolicyCount || 0 }} · 通用政策 {{ readiness.generalPolicyCount || 0 }} · 来源 {{ readiness.verifiedSourceCount || 0 }}</small><small v-else>{{ readinessError || '选择地区与行业后自动核验' }}</small></div></div>
            <ul v-if="readiness?.reasons?.length"><li v-for="reason in readiness.reasons" :key="reason">{{ reason }}</li></ul>
          </section>

          <button class="button assistant-new-session" type="submit" :disabled="busy">
            <Plus :size="17" />{{ creatingSession ? '正在创建' : '按当前画像新建会话' }}
          </button>
        </form>
      </aside>

      <main class="assistant-thread" aria-labelledby="assistant-thread-title">
        <header class="assistant-thread-head">
          <div><span class="caption">SOLOFIRM RESEARCH DESK</span><h2 id="assistant-thread-title">研究对话</h2></div>
          <div class="assistant-session-actions">
            <label class="assistant-session-select"><History :size="16" /><span class="sr-only">历史会话</span>
              <select v-model="selectedSessionId" :disabled="busy || !sessions.length" @change="selectSession">
                <option value="">选择历史会话</option><option v-for="item in sessions" :key="item.sessionId" :value="String(item.sessionId)">{{ item.title }}</option>
              </select>
            </label>
            <button class="icon-button" type="button" title="新建研究会话" :disabled="busy" @click="createSession"><Plus :size="18" /></button>
            <button class="icon-button" type="button" title="归档当前会话" :disabled="busy || !currentSession" @click="archiveSession"><Archive :size="18" /></button>
          </div>
        </header>

        <section v-if="!agentReady" class="assistant-empty is-disabled" role="status">
          <BrainCircuit :size="30" /><div><strong>智能体运行时尚未启用</strong><p>管理员需要启用模型 Provider 与 Agent Runtime；本页不会回退到虚假回答。</p></div>
        </section>
        <section v-else-if="sessionLoading" class="assistant-empty" role="status"><span class="assistant-spinner"></span><div><strong>正在恢复研究会话</strong><p>读取消息与最近运行状态。</p></div></section>
        <section v-else-if="!currentSession" class="assistant-empty">
          <FileSearch :size="30" /><div><span class="caption">START A RESEARCH SESSION</span><h3>先用左侧画像建立研究边界。</h3><p>信息不足时只会追问一项；信息充分后才会检索已核验案例和政策。</p></div>
        </section>

        <template v-else>
          <div ref="transcriptEl" class="assistant-transcript" aria-live="polite">
            <article v-for="message in messages" :key="message.messageId" class="assistant-message" :class="`is-${message.role}`">
              <header><span>{{ message.role === 'user' ? '你的问题' : 'SOLOFIRM 智能体' }}</span><time>{{ formatDate(message.createdAt) }}</time></header>
              <p>{{ message.content }}</p>
              <footer v-if="message.role === 'assistant'">
                <button v-if="message.citations?.length" type="button" class="assistant-citation-trigger" @click="openEvidence(message)">
                  <BookOpen :size="15" />{{ message.citations.length }} 条引用
                </button>
                <span><Sparkles :size="14" />AI 生成内容，请核对原始来源</span>
              </footer>
            </article>
          </div>

          <section v-if="currentRun && !isTerminal(currentRun.status)" class="assistant-run-status" role="status">
            <div class="assistant-run-status-head"><span class="assistant-pulse"></span><div><strong>{{ currentRun.visibleProgress || stageLabel(currentRun.currentStage) }}</strong><small>Run #{{ currentRun.runId }}</small></div><button class="button button-ghost" type="button" :disabled="cancelling" @click="cancelRun"><Square :size="14" />{{ cancelling ? '正在取消' : '取消' }}</button></div>
            <div v-if="currentRun.tools?.length" class="assistant-tool-list">
              <div v-for="tool in currentRun.tools" :key="tool.toolCallId"><span>{{ toolLabel(tool.toolName) }}</span><small>{{ tool.status }} · {{ tool.evidenceCount }} 条证据 · {{ tool.latencyMs || 0 }} ms</small><RouterLink v-if="toolRoute(tool.toolName)" :to="toolRoute(tool.toolName)">查看资料<ArrowRight :size="13" /></RouterLink></div>
            </div>
          </section>

          <section v-else-if="currentRun && ['failed','expired','cancelled'].includes(currentRun.status)" class="assistant-run-error" role="alert">
            <AlertTriangle :size="19" /><div><strong>{{ diagnosticTitle(currentRun) }}</strong><p>{{ diagnosticMessage(currentRun) }}</p></div><button class="button button-ghost" type="button" :disabled="busy || !lastQuestion" @click="retryLast"><RefreshCw :size="15" />重新提交</button>
          </section>

          <section v-if="currentRun && isTerminal(currentRun.status)" class="assistant-run-meta">
            <span>{{ currentRun.provider }} / {{ currentRun.model }}</span><span>{{ currentRun.tokenUsage?.totalTokens || 0 }} tokens</span><span>{{ currentRun.toolCallCount || 0 }} 次工具</span><span>{{ currentRun.latencyMs || 0 }} ms</span>
          </section>

          <form class="assistant-composer" @submit.prevent="sendMessage">
            <label for="assistant-message">{{ sessionArchived ? '会话已归档' : '继续研究' }} <span>{{ composer.length }} / 2000</span></label>
            <div><textarea id="assistant-message" v-model="composer" rows="3" maxlength="2000" :placeholder="sessionArchived ? '归档会话仅供查阅' : '输入明确的研究问题'" :disabled="busy || sessionArchived" required></textarea><button class="button" type="submit" :disabled="busy || sessionArchived || !composer.trim()" aria-label="发送研究问题"><LoaderCircle v-if="submitting" class="assistant-spin-icon" :size="18" /><Send v-else :size="18" /></button></div>
          </form>
        </template>

        <aside v-if="evidenceMessage" class="assistant-evidence-drawer" aria-labelledby="assistant-evidence-title">
          <header><div><span class="caption">VERIFIED SOURCES</span><h3 id="assistant-evidence-title">引用依据</h3></div><button class="icon-button" type="button" title="关闭证据" @click="evidenceMessage = null"><X :size="18" /></button></header>
          <ol><li v-for="(citation, index) in evidenceMessage.citations" :key="`${citation.sourceId}-${index}`"><span>{{ String(index + 1).padStart(2, '0') }}</span><div><strong>{{ citation.title }}</strong><small>{{ citation.publisher || `来源 #${citation.sourceId}` }}</small><p>{{ citation.claim }}</p><a :href="citation.url" target="_blank" rel="noreferrer">查看原始来源<ExternalLink :size="14" /></a></div></li></ol>
        </aside>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { AlertTriangle, Archive, ArrowRight, BookOpen, BrainCircuit, ExternalLink, FileSearch, History, LoaderCircle, Plus, RefreshCw, Search, Send, SlidersHorizontal, Sparkles, Square, X } from 'lucide-vue-next'
import { archiveResearchSession, cancelResearchRun, checkEntrepreneurshipReadiness, createResearchSession, getAiCapabilities, getResearchRun, getResearchSession, getResearchSessions, resolveIndustryWithAi, sendResearchMessage } from '@/api/ai'
import { getRegions } from '@/api/region'
import { getIndustryTags } from '@/api/tag'

const PROFILE_KEY = 'opc_assistant_profile_v2'
const SESSION_KEY = 'opc_agent_session_id'
const TERMINAL = new Set(['completed', 'clarification_needed', 'evidence_insufficient', 'failed', 'cancelled', 'expired'])
const pageLoading = ref(true)
const pageError = ref('')
const regions = ref([])
const industries = ref([])
const capabilities = ref(null)
const sessions = ref([])
const selectedSessionId = ref('')
const currentSession = ref(null)
const messages = ref([])
const currentRun = ref(null)
const composer = ref('')
const lastQuestion = ref('')
const creatingSession = ref(false)
const sessionLoading = ref(false)
const submitting = ref(false)
const cancelling = ref(false)
const evidenceMessage = ref(null)
const transcriptEl = ref(null)
const readiness = ref(null)
const readinessError = ref('')
const readinessLoading = ref(false)
const industryQuery = ref('')
const industrySuggestion = ref(null)
const industryLoading = ref(false)
let readinessTimer
let pollTimer
let pollGeneration = 0

const profile = reactive({ ventureType: 'solo_company', regionId: '', industryTagId: '', industry: '', stage: 'validation', budgetRange: 'under_100k', goal: '', existingResources: '' })
const busy = computed(() => creatingSession.value || sessionLoading.value || submitting.value || Boolean(currentRun.value && !isTerminal(currentRun.value.status)))
const sessionArchived = computed(() => currentSession.value?.status === 'archived')
const agentReady = computed(() => Boolean(capabilities.value?.provider?.available && capabilities.value?.capabilities?.some((item) => item.id === 'agent-runtime' && item.available)))
const providerLabel = computed(() => capabilities.value?.provider ? `${capabilities.value.provider.provider} / ${capabilities.value.provider.model}` : '等待管理员配置')
const readinessTitle = computed(() => readinessLoading.value ? '正在核验证据' : ({ sufficient: '证据充分', partial: '证据有限，可继续', insufficient: '当前证据不足' }[readiness.value?.readinessStatus] || '等待证据预检'))
const readinessClass = computed(() => ({ ready: readiness.value?.readinessStatus === 'sufficient', partial: readiness.value?.readinessStatus === 'partial', error: Boolean(readinessError.value) }))

onMounted(() => { restoreProfile(); loadPage() })
onBeforeUnmount(() => { clearTimeout(readinessTimer); clearTimeout(pollTimer); pollGeneration += 1 })
watch(profile, (value) => sessionStorage.setItem(PROFILE_KEY, JSON.stringify(value)), { deep: true })
watch(() => [profile.regionId, profile.industryTagId, profile.industry], scheduleReadiness)

async function loadPage() {
  pageLoading.value = true; pageError.value = ''
  try {
    const [regionData, industryData, capabilityData, sessionData] = await Promise.all([getRegions(), getIndustryTags(), getAiCapabilities(), getResearchSessions()])
    regions.value = regionData || []; industries.value = industryData || []; capabilities.value = capabilityData || {}; sessions.value = sessionData || []
    industryQuery.value = profile.industry
    const stored = localStorage.getItem(SESSION_KEY)
    const candidate = sessions.value.find((item) => String(item.sessionId) === String(stored)) || sessions.value.find((item) => item.status === 'active')
    if (candidate) { selectedSessionId.value = String(candidate.sessionId); await loadSession(candidate.sessionId) }
    scheduleReadiness()
  } catch (error) {
    pageError.value = error.message || '研究工作台暂时无法读取'
  } finally {
    pageLoading.value = false
    if (currentSession.value) await scrollToEnd()
  }
}

async function createSession() {
  if (creatingSession.value) return
  creatingSession.value = true; pageError.value = ''
  try {
    const region = regions.value.find((item) => String(item.id) === String(profile.regionId))
    const title = [region?.name, profile.industry].filter(Boolean).join(' · ') || '新研究'
    const session = await createResearchSession({ title, profile: { ventureType: profile.ventureType, regionId: profile.regionId ? Number(profile.regionId) : undefined, industryTagId: profile.industryTagId ? Number(profile.industryTagId) : undefined, industry: profile.industry || undefined, stage: profile.stage, budgetRange: profile.budgetRange, goal: profile.goal || undefined, resources: profile.existingResources || undefined } })
    sessions.value = [session, ...sessions.value.filter((item) => item.sessionId !== session.sessionId)]
    selectedSessionId.value = String(session.sessionId); localStorage.setItem(SESSION_KEY, selectedSessionId.value)
    await loadSession(session.sessionId)
  } catch (error) { pageError.value = error.message || '研究会话创建失败' } finally { creatingSession.value = false }
}

async function selectSession() { if (!selectedSessionId.value) return; await loadSession(Number(selectedSessionId.value)) }
async function loadSession(sessionId) {
  stopPolling(); sessionLoading.value = true; evidenceMessage.value = null
  try {
    const detail = await getResearchSession(sessionId)
    currentSession.value = detail.session; messages.value = detail.messages || []; currentRun.value = detail.activeRun || null
    selectedSessionId.value = String(sessionId); localStorage.setItem(SESSION_KEY, selectedSessionId.value)
    if (detail.activeRun?.runId) startPolling(detail.activeRun.runId)
    await scrollToEnd()
  } catch (error) { pageError.value = error.message || '研究会话读取失败' } finally { sessionLoading.value = false }
}

async function archiveSession() {
  if (!currentSession.value || busy.value) return
  await archiveResearchSession(currentSession.value.sessionId)
  sessions.value = sessions.value.map((item) => item.sessionId === currentSession.value.sessionId ? { ...item, status: 'archived' } : item)
  currentSession.value = null; messages.value = []; currentRun.value = null; selectedSessionId.value = ''; localStorage.removeItem(SESSION_KEY)
}

async function sendMessage(forcedText) {
  const content = String(typeof forcedText === 'string' ? forcedText : composer.value).trim()
  if (!content || !currentSession.value || sessionArchived.value || submitting.value || busy.value) return
  submitting.value = true; lastQuestion.value = content
  try {
    const receipt = await sendResearchMessage(currentSession.value.sessionId, { content, idempotencyKey: newIdempotencyKey() })
    composer.value = ''; await loadSession(currentSession.value.sessionId)
    currentRun.value = { runId: receipt.runId, sessionId: receipt.sessionId, status: receipt.status, currentStage: receipt.status, tools: [] }
    startPolling(receipt.runId)
  } catch (error) {
    currentRun.value = { status: 'failed', diagnosticCode: error.diagnosticCode || '', visibleProgress: error.message || '研究请求失败', tools: [] }
  } finally { submitting.value = false }
}

function retryLast() { if (lastQuestion.value) { currentRun.value = null; sendMessage(lastQuestion.value) } }
async function cancelRun() {
  if (!currentRun.value?.runId || cancelling.value) return
  cancelling.value = true
  try {
    const cancelledRun = await cancelResearchRun(currentRun.value.runId)
    stopPolling()
    await loadSession(currentSession.value.sessionId)
    currentRun.value = cancelledRun
  } finally { cancelling.value = false }
}

function startPolling(runId) {
  stopPolling(); const generation = ++pollGeneration
  const poll = async () => {
    try {
      const run = await getResearchRun(runId)
      if (generation !== pollGeneration) return
      currentRun.value = run
      if (isTerminal(run.status)) { await loadSession(run.sessionId); currentRun.value = run; return }
    } catch (error) {
      if (generation === pollGeneration) currentRun.value = { runId, status: 'failed', visibleProgress: error.message || '运行状态读取失败', tools: [] }
      return
    }
    pollTimer = window.setTimeout(poll, 1200)
  }
  pollTimer = window.setTimeout(poll, 300)
}
function stopPolling() { clearTimeout(pollTimer); pollGeneration += 1 }
function isTerminal(status) { return TERMINAL.has(status) }

function syncIndustry() {
  profile.industry = industryQuery.value.trim()
  const match = industries.value.find((item) => item.name?.toLowerCase() === profile.industry.toLowerCase())
  profile.industryTagId = match ? String(match.tagId) : ''; industrySuggestion.value = null
}
async function recommendIndustry() {
  if (!profile.industry || industryLoading.value) return
  industryLoading.value = true
  try { const result = await resolveIndustryWithAi(profile.industry); if (result?.tagId) industrySuggestion.value = { ...result, originalText: profile.industry } } finally { industryLoading.value = false }
}
function confirmIndustry() { profile.industryTagId = String(industrySuggestion.value.tagId); profile.industry = industrySuggestion.value.name; industryQuery.value = profile.industry; industrySuggestion.value = null }
function rejectIndustry() { industrySuggestion.value = null }

function scheduleReadiness() {
  clearTimeout(readinessTimer); readiness.value = null; readinessError.value = ''
  if (!profile.regionId || (!profile.industryTagId && !profile.industry)) return
  readinessTimer = window.setTimeout(checkReadiness, 420)
}
async function checkReadiness() {
  readinessLoading.value = true; readinessError.value = ''
  try { readiness.value = await checkEntrepreneurshipReadiness({ regionId: Number(profile.regionId), industryTagId: profile.industryTagId ? Number(profile.industryTagId) : undefined, industry: profile.industry }) } catch (error) { readinessError.value = error.message || '证据预检失败' } finally { readinessLoading.value = false }
}

function openEvidence(message) { evidenceMessage.value = message }
function toolLabel(name) { return { search_cases: '检索案例', search_policies: '检索政策', get_source: '核验来源', compare_cases: '比较案例' }[name] || name }
function toolRoute(name) { if (name === 'search_cases' || name === 'compare_cases') return '/cases'; if (name === 'search_policies') return '/policies'; return '' }
function stageLabel(stage) { return { received: '正在分析需求', planning: '正在制定研究步骤', waiting_for_model: '正在分析需求', tool_requested: '正在准备检索', tool_running: '正在检索并核验证据', synthesizing: '正在整理回答' }[stage] || '正在处理研究任务' }
function diagnosticTitle(run) { return run.status === 'cancelled' ? '研究运行已取消' : run.status === 'expired' ? '研究运行已过期' : '研究运行未完成' }
function diagnosticMessage(run) { const labels = { TRUNCATED_RESPONSE: '模型输出被截断，请缩小问题范围后重试。', MISSING_CITATIONS: '最终回答缺少合法引用，结果已拒绝展示。', UNKNOWN_SOURCE_ID: '模型引用了当前运行之外的来源。', AGENT_ROUND_LIMIT: '研究达到模型轮次上限。', AGENT_TOOL_LIMIT: '研究达到工具调用上限。', PROVIDER_RATE_LIMIT: '模型服务触发速率限制，请稍后重试。', PROVIDER_TIMEOUT: '模型服务响应超时。', EVIDENCE_CHANGED: '研究期间证据已变化，请重新提交。' }; return labels[run.diagnosticCode] || run.visibleProgress || '请使用新的幂等键安全重试。' }
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '' }
function formatPercent(value) { return Number.isFinite(value) ? `${Math.round(value * 100)}%` : '-' }
function newIdempotencyKey() { return globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID().replaceAll('-', '') : `agent_${Date.now()}_${Math.random().toString(36).slice(2)}` }
async function scrollToEnd() { await nextTick(); transcriptEl.value?.scrollTo({ top: transcriptEl.value.scrollHeight, behavior: reducedMotion() ? 'auto' : 'smooth' }) }
function reducedMotion() { return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches }
function restoreProfile() { try { const saved = JSON.parse(sessionStorage.getItem(PROFILE_KEY) || '{}'); Object.keys(profile).forEach((key) => { if (typeof saved[key] === 'string') profile[key] = saved[key] }); industryQuery.value = profile.industry } catch { sessionStorage.removeItem(PROFILE_KEY) } }
</script>

<style scoped>
.assistant-page{width:100%;color:#20231f}.assistant-workspace{display:grid;grid-template-columns:minmax(290px,350px) minmax(0,1fr);min-height:calc(100vh - 132px);border:1px solid #c8cdc6;background:var(--paper-surface,#fbfbf7)}
.assistant-profile{min-width:0;padding:24px;border-right:1px solid #c8cdc6;background:var(--paper-canvas,#f3f2ec);overflow:auto}.assistant-profile-head,.assistant-thread-head,.assistant-run-status-head,.assistant-evidence-drawer>header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.assistant-profile h2,.assistant-thread h2,.assistant-evidence-drawer h3{margin:6px 0 0;font-family:'Noto Serif SC',STSong,SimSun,serif;font-weight:500;letter-spacing:0}.caption{color:#6b716a;font-family:'Bookman Old Style',Georgia,serif;font-size:.68rem;font-weight:700;letter-spacing:0}.assistant-profile-intro{margin:17px 0 21px;color:#656b64;font-size:.82rem;line-height:1.65}
.assistant-profile-form{display:grid;gap:15px}.assistant-profile-form label{display:grid;gap:7px;min-width:0}.assistant-profile-form label>span,.assistant-composer>label{color:#4d534d;font-size:.72rem;font-weight:700}.assistant-profile-form label small{font-weight:400}.assistant-profile-form :is(input,select,textarea),.assistant-session-select select,.assistant-composer textarea{width:100%;min-width:0;border:1px solid #bcc2ba;border-radius:3px;background:var(--paper-surface,#fbfbf7);color:#222620}.assistant-profile-form :is(input,select){height:42px;padding:0 11px}.assistant-profile-form textarea,.assistant-composer textarea{padding:10px 11px;line-height:1.55;resize:vertical}.assistant-form-pair{display:grid;grid-template-columns:1fr 1fr;gap:10px}.assistant-industry-input{position:relative}.assistant-industry-input input{padding-right:36px}.assistant-industry-input svg{position:absolute;right:11px;top:13px;color:#737972}.assistant-confirmed{color:#326044}.assistant-inline-action{display:inline-flex;align-items:center;gap:6px;justify-self:start;border:0;background:transparent;color:#475b48;font-size:.72rem}.assistant-suggestion{display:flex;justify-content:space-between;gap:12px;padding:12px;border:1px solid #d4d0c7;background:var(--paper-surface,#fbfbf7)}.assistant-suggestion>div{display:grid;gap:3px}.assistant-suggestion>div:last-child{display:flex;align-items:center}.assistant-suggestion .button{min-height:36px;padding:7px 10px}.assistant-readiness{display:grid;gap:9px;padding:13px 0;border-top:1px solid #cdd1cb;border-bottom:1px solid #cdd1cb}.assistant-readiness-row{display:flex;gap:9px;align-items:flex-start}.assistant-readiness-row>span{width:8px;height:8px;margin-top:5px;border-radius:50%;background:#8f463c}.assistant-readiness-row.ready>span{background:#396348}.assistant-readiness-row.partial>span{background:#8a6c36}.assistant-readiness-row>div{display:grid;gap:2px}.assistant-readiness-row strong{font-size:.77rem}.assistant-readiness-row small{color:#737a72;font-size:.68rem}.assistant-readiness ul{margin:0;padding-left:18px;color:#6d5140;font-size:.69rem;line-height:1.5}.assistant-new-session{min-height:46px;justify-content:center}
.assistant-thread{position:relative;display:grid;grid-template-rows:auto minmax(0,1fr) auto;min-width:0;max-height:calc(100vh - 132px);background:var(--paper-surface,#fbfbf7)}.assistant-thread-head{align-items:center;padding:19px 22px;border-bottom:1px solid #ccd0ca}.assistant-session-actions{display:flex;align-items:center;gap:7px;min-width:0}.assistant-session-select{display:flex;align-items:center;gap:7px;min-width:0}.assistant-session-select select{width:min(270px,28vw);height:40px;padding:0 9px}.icon-button{display:inline-grid;place-items:center;width:40px;height:40px;flex:0 0 40px;border:1px solid #bdc3bb;border-radius:3px;background:#fafaf7;color:#30352f}.icon-button:disabled{opacity:.45}.assistant-transcript{min-height:0;overflow:auto;padding:24px 28px 130px}.assistant-message{max-width:820px;padding:17px 0;border-bottom:1px solid #daddd7}.assistant-message.is-user{margin-left:auto;width:min(84%,720px);border-top:1px solid #aeb3ad}.assistant-message.is-assistant{margin-right:auto;width:min(94%,820px)}.assistant-message header,.assistant-message footer{display:flex;align-items:center;justify-content:space-between;gap:14px}.assistant-message header span{color:#505750;font-size:.69rem;font-weight:800}.assistant-message time{color:#858b84;font-size:.65rem}.assistant-message>p{margin:12px 0 0;white-space:pre-wrap;overflow-wrap:anywhere;color:#272c27;line-height:1.78}.assistant-message footer{justify-content:flex-start;margin-top:13px;color:#747a73;font-size:.68rem}.assistant-message footer span,.assistant-citation-trigger{display:inline-flex;align-items:center;gap:5px}.assistant-citation-trigger{border:0;background:transparent;color:#315c42;font-weight:700}
.assistant-empty,.assistant-page-state{display:flex;align-items:center;justify-content:center;gap:16px;min-height:300px;padding:35px;color:#687068}.assistant-empty h3{margin:7px 0;font-family:'Noto Serif SC',STSong,serif;font-size:1.35rem;font-weight:500}.assistant-empty p,.assistant-page-state p{margin:4px 0 0;max-width:560px;line-height:1.6}.assistant-empty.is-disabled{color:#795047}.assistant-page-state{min-height:420px}.assistant-page-state.is-error{color:#7b3f36}.assistant-spinner{width:22px;height:22px;border:2px solid #c5cac3;border-top-color:#304e38;border-radius:50%;animation:assistant-spin .8s linear infinite}.assistant-spin-icon{animation:assistant-spin .8s linear infinite}
.assistant-run-status,.assistant-run-error{position:absolute;z-index:4;right:22px;bottom:105px;left:22px;padding:14px 16px;border:1px solid #bfc5bd;background:var(--paper-surface,#fbfbf7)}.assistant-run-status-head{align-items:center}.assistant-run-status-head>div{display:grid;flex:1}.assistant-run-status-head small{color:#7b817a}.assistant-pulse{width:9px;height:9px;border-radius:50%;background:#3e694a;animation:assistant-pulse 1.4s ease-in-out infinite}.assistant-tool-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin-top:12px}.assistant-tool-list>div{display:grid;gap:2px;padding:9px 0;border-top:1px solid #d0d4ce}.assistant-tool-list span{font-size:.73rem;font-weight:700}.assistant-tool-list small{color:#727972;font-size:.66rem}.assistant-tool-list a{display:inline-flex;align-items:center;gap:3px;color:#355d43;font-size:.67rem}.assistant-run-error{display:flex;align-items:flex-start;gap:12px;border-color:#d0b9b3;background:#f8efec;color:#743d35}.assistant-run-error>div{flex:1}.assistant-run-error p{margin:4px 0 0;font-size:.76rem}.assistant-run-meta{display:flex;flex-wrap:wrap;gap:8px;padding:8px 22px;border-top:1px solid #d5d9d3;color:#6d736d;font-family:'Bookman Old Style',Georgia,serif;font-size:.66rem}.assistant-run-meta span+span:before{content:'·';margin-right:8px}.assistant-composer{position:absolute;z-index:3;right:0;bottom:0;left:0;padding:13px 22px 18px;border-top:1px solid #cbd0c9;background:var(--paper-surface,#fbfbf7)}.assistant-composer>label{display:flex;justify-content:space-between;margin-bottom:6px}.assistant-composer>div{display:grid;grid-template-columns:minmax(0,1fr) 48px;gap:8px}.assistant-composer textarea{max-height:120px;min-height:64px}.assistant-composer .button{width:48px;min-width:48px;padding:0;justify-content:center}
.assistant-evidence-drawer{position:absolute;z-index:8;top:0;right:0;bottom:0;width:min(390px,92%);overflow:auto;border-left:1px solid #aeb4ad;background:var(--paper-surface,#fbfbf7);animation:drawer-in .2s ease-out}.assistant-evidence-drawer>header{position:sticky;top:0;padding:20px;border-bottom:1px solid #ccd0ca;background:var(--paper-surface,#fbfbf7)}.assistant-evidence-drawer ol{display:grid;gap:0;margin:0;padding:0 20px 30px;list-style:none}.assistant-evidence-drawer li{display:grid;grid-template-columns:28px minmax(0,1fr);gap:10px;padding:18px 0;border-bottom:1px solid #d3d7d1}.assistant-evidence-drawer li>span{font-family:'Bookman Old Style',Georgia,serif;color:#777d76;font-size:.72rem}.assistant-evidence-drawer li div{display:grid;gap:5px}.assistant-evidence-drawer strong{overflow-wrap:anywhere}.assistant-evidence-drawer small{color:#767c75}.assistant-evidence-drawer p{margin:5px 0;color:#505750;line-height:1.6}.assistant-evidence-drawer a{display:inline-flex;align-items:center;gap:5px;color:#315c42;font-size:.73rem;font-weight:700}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
@keyframes assistant-spin{to{transform:rotate(360deg)}}@keyframes assistant-pulse{50%{opacity:.35;transform:scale(.8)}}@keyframes drawer-in{from{opacity:0;transform:translateX(14px)}to{opacity:1;transform:translateX(0)}}
@media (hover:hover){.icon-button:hover:not(:disabled){background:#eceee9}.assistant-citation-trigger:hover,.assistant-inline-action:hover{color:#1f4730}}
@media (max-width:980px){.assistant-workspace{grid-template-columns:1fr}.assistant-profile{border-right:0;border-bottom:1px solid #c8cdc6}.assistant-thread{min-height:680px;max-height:none}.assistant-transcript{max-height:680px}.assistant-session-select select{width:min(300px,42vw)}}
@media (max-width:640px){.assistant-profile{padding:18px}.assistant-thread-head{align-items:flex-start;padding:16px}.assistant-session-actions{flex-wrap:wrap;justify-content:flex-end}.assistant-session-select{order:3;width:100%}.assistant-session-select select{width:100%;min-height:44px}.icon-button{width:44px;height:44px;flex-basis:44px}.assistant-form-pair{grid-template-columns:1fr}.assistant-transcript{padding:16px 16px 145px}.assistant-message.is-user,.assistant-message.is-assistant{width:100%;padding-left:0}.assistant-message.is-user{border-left:0}.assistant-run-status,.assistant-run-error{right:10px;bottom:121px;left:10px}.assistant-tool-list{grid-template-columns:1fr}.assistant-composer{padding:11px 12px 15px}.assistant-composer>div{grid-template-columns:minmax(0,1fr) 48px}.assistant-new-session,.assistant-profile-form :is(input,select),.button{min-height:44px}.assistant-run-meta{padding:8px 14px}}
@media (prefers-reduced-motion:reduce){.assistant-spinner,.assistant-spin-icon,.assistant-pulse,.assistant-evidence-drawer{animation:none!important}.assistant-evidence-drawer{transform:none}}
</style>
