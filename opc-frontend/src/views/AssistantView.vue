<template>
  <div class="assistant-page">
    <section v-if="pageLoading" class="page-state" role="status"><span class="spinner"></span><div><strong>正在准备研究工作台</strong><p>读取研究画像、历史会话与智能体状态。</p></div></section>
    <section v-else-if="pageError" class="page-state is-error" role="alert"><AlertTriangle :size="22" /><div><strong>研究助手暂时无法读取</strong><p>{{ pageError }}</p></div><button class="secondary-command" type="button" @click="loadPage"><RefreshCw :size="16" />重新读取</button></section>

    <div v-else class="assistant-workspace" :class="{ 'history-collapsed': historyCollapsed }">
      <AssistantHistorySidebar
        :items="historyItems" :scope="historyScope" :search-query="historyQuery" :selected-id="selectedSessionId"
        :loading="historyLoading" :searching="historySearching" :has-more="historyHasMore" :error="historyError"
        :collapsed="historyCollapsed" :mobile-open="mobileHistoryOpen"
        @toggle="toggleHistory" @close-mobile="closeMobileHistory" @new="startNewResearch" @search="scheduleHistorySearch"
        @scope="changeHistoryScope" @select="selectHistorySession" @load-more="loadHistory(false)"
        @rename="renameSession" @pin="pinSession" @archive="archiveSession" @unarchive="unarchiveSession"
        @trash="trashSession" @restore="restoreSession" @purge="purgeSession"
      />

      <main class="research-desk" aria-labelledby="assistant-title">
        <header class="desk-header">
          <button ref="mobileHistoryButton" class="mobile-history-command" type="button" aria-label="打开研究历史" @click="mobileHistoryOpen = true"><Menu :size="20" /></button>
          <div><span class="caption">SOLOFIRM RESEARCH DESK</span><h1 id="assistant-title">{{ currentSession?.title || '新研究' }}</h1></div>
          <div class="desk-status"><span :class="{ ready: agentReady }"></span><div><strong>{{ currentSession ? sessionStatusLabel : '本地草稿' }}</strong><small>{{ currentSession ? formatDate(currentSession.lastMessageAt || currentSession.updatedAt) : '首条问题发送后保存' }}</small></div></div>
        </header>

        <AssistantResearchProfile
          v-model="profile" :editable="!currentSession" :regions="regions" :industries="industries"
          :readiness="readiness" :readiness-loading="readinessLoading" :readiness-error="readinessError"
          :agent-ready="agentReady" :provider-label="providerLabel" @fork="forkResearch"
        />

        <section v-if="!agentReady" class="desk-notice" role="status"><BrainCircuit :size="22" /><div><strong>智能体运行时尚未启用</strong><p>管理员需要启用模型 Provider 与 Agent Runtime；本页不会回退到虚假回答。</p></div></section>

        <AssistantConversation
          ref="conversation" :messages="messages" :run="currentRun" :has-more="hasMoreMessages"
          :loading-older="loadingOlder" :draft-mode="!currentSession" :network-status="networkStatus" :cancelling="cancelling"
          @load-older="loadOlderMessages" @prefill="prefillQuestion" @citations="openCitations" @process="openProcess"
          @cancel="cancelRun" @retry="retryLast" @resume="resumePolling"
        />

        <p v-if="composerError" class="composer-error" role="alert">{{ composerError }}</p>
        <AssistantComposer
          ref="composerControl" v-model="composer" :disabled="Boolean(composerDisabledReason)" :disabled-reason="composerDisabledReason"
          :sending="submitting" :running="runActive" :usage="usage" @send="sendMessage" @cancel="cancelRun"
        />
      </main>
    </div>

    <AssistantCitationDrawer :open="drawerOpen" :mode="drawerMode" :citations="drawerCitations" :run="drawerRun" :loading="drawerLoading" :error="drawerError" @close="closeDrawer" />
    <p v-if="toast" class="assistant-toast" role="status">{{ toast }}</p>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { AlertTriangle, BrainCircuit, Menu, RefreshCw } from 'lucide-vue-next'
import AssistantCitationDrawer from '@/components/assistant/AssistantCitationDrawer.vue'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'
import AssistantConversation from '@/components/assistant/AssistantConversation.vue'
import AssistantHistorySidebar from '@/components/assistant/AssistantHistorySidebar.vue'
import AssistantResearchProfile from '@/components/assistant/AssistantResearchProfile.vue'
import {
  archiveResearchSessionExplicit, cancelResearchRun, checkEntrepreneurshipReadiness, createResearchSession,
  getAiCapabilities, getResearchHistory, getResearchMessages, getResearchRun, getResearchSession, getResearchUsage,
  permanentlyDeleteResearchSession, restoreResearchSession, sendResearchMessage, trashResearchSession,
  unarchiveResearchSession, updateResearchSession,
} from '@/api/ai'
import { getRegions } from '@/api/region'
import { getIndustryTags } from '@/api/tag'
import { createAssistantDraftStore } from '@/composables/useAssistantDrafts'
import { createLatestRequestGate } from '@/utils/assistantWorkflow'
import { mergeMessagePages } from '@/utils/assistantWorkspace'

const SESSION_KEY = 'opc_agent_session_id'
const SIDEBAR_KEY = 'opc_assistant_history_collapsed'
const PROFILE_KEY = 'opc_assistant_new_profile_v3'
const TERMINAL = new Set(['completed', 'clarification_needed', 'evidence_insufficient', 'failed', 'cancelled', 'expired'])
const defaultProfile = () => ({ ventureType: 'solo_company', regionId: '', industryTagId: '', industry: '', stage: 'validation', budgetRange: 'under_100k', goal: '', existingResources: '' })
const drafts = createAssistantDraftStore()
const historyGate = createLatestRequestGate()

const pageLoading = ref(true)
const pageError = ref('')
const historyItems = ref([])
const historyScope = ref('active')
const historyQuery = ref('')
const historyCursor = ref(null)
const historyHasMore = ref(false)
const historyLoading = ref(false)
const historySearching = ref(false)
const historyError = ref('')
const historyCollapsed = ref(localStorage.getItem(SIDEBAR_KEY) === '1')
const mobileHistoryOpen = ref(false)
const mobileHistoryButton = ref(null)
const selectedSessionId = ref('')
const currentSession = ref(null)
const messages = ref([])
const nextBeforeSequence = ref(null)
const hasMoreMessages = ref(false)
const loadingOlder = ref(false)
const currentRun = ref(null)
const lastQuestion = ref('')
const composer = ref(drafts.load(null))
const composerError = ref('')
const submitting = ref(false)
const cancelling = ref(false)
const conversation = ref(null)
const composerControl = ref(null)
const regions = ref([])
const industries = ref([])
const capabilities = ref(null)
const usage = ref(null)
const profile = ref(restoreNewProfile())
const readiness = ref(null)
const readinessLoading = ref(false)
const readinessError = ref('')
const networkStatus = ref('connected')
const drawerOpen = ref(false)
const drawerMode = ref('citations')
const drawerCitations = ref([])
const drawerRun = ref(null)
const drawerLoading = ref(false)
const drawerError = ref('')
const toast = ref('')
let historyTimer
let readinessTimer
let pollingTimer
let pollingGeneration = 0
let pollingFailures = 0
let toastTimer

const agentReady = computed(() => Boolean(capabilities.value?.provider?.available && capabilities.value?.capabilities?.some((item) => item.id === 'agent-runtime' && item.available)))
const providerLabel = computed(() => capabilities.value?.provider ? `${capabilities.value.provider.provider} / ${capabilities.value.provider.model}` : '等待管理员配置')
const runActive = computed(() => Boolean(currentRun.value && !TERMINAL.has(currentRun.value.status)))
const sessionStatusLabel = computed(() => currentSession.value?.deletedAt ? '回收站' : currentSession.value?.status === 'archived' ? '已归档' : runActive.value ? '研究进行中' : '当前会话')
const composerDisabledReason = computed(() => {
  if (!agentReady.value) return '智能体暂不可用'
  if (currentSession.value?.deletedAt) return '回收站会话不能发送消息'
  if (currentSession.value?.status === 'archived') return '归档会话仅供查阅'
  if (runActive.value) return ''
  return ''
})

onMounted(loadPage)
onBeforeUnmount(() => { clearTimeout(historyTimer); clearTimeout(readinessTimer); clearTimeout(pollingTimer); clearTimeout(toastTimer); pollingGeneration += 1 })
watch(profile, (value) => { if (!currentSession.value) localStorage.setItem(PROFILE_KEY, JSON.stringify(value)); scheduleReadiness() }, { deep: true })
watch(composer, (value) => drafts.save(currentSession.value?.sessionId ?? null, value))

async function loadPage() {
  pageLoading.value = true
  pageError.value = ''
  try {
    const [regionData, industryData, capabilityData, usageData] = await Promise.all([getRegions(), getIndustryTags(), getAiCapabilities(), getResearchUsage()])
    regions.value = regionData || []
    industries.value = industryData || []
    capabilities.value = capabilityData || {}
    usage.value = usageData || null
    await loadHistory(true)
    const stored = Number(localStorage.getItem(SESSION_KEY))
    if (stored > 0) {
      try { await loadSession(stored) } catch { localStorage.removeItem(SESSION_KEY); startNewResearch() }
    } else if (historyItems.value[0]) {
      await loadSession(historyItems.value[0].sessionId)
    } else startNewResearch()
  } catch (error) {
    pageError.value = error.message || '研究工作台暂时无法读取'
  } finally {
    pageLoading.value = false
    scheduleReadiness()
  }
}

async function loadHistory(reset = true) {
  const requestId = historyGate.begin()
  historyLoading.value = true
  historyError.value = ''
  try {
    const page = await getResearchHistory({ scope: historyScope.value, q: historyQuery.value.trim(), cursor: reset ? null : historyCursor.value, limit: 30 })
    if (!historyGate.isCurrent(requestId)) return
    historyItems.value = reset ? (page.items || []) : mergeBySession(historyItems.value, page.items || [])
    historyCursor.value = page.nextCursor || null
    historyHasMore.value = Boolean(page.hasMore)
  } catch (error) {
    if (historyGate.isCurrent(requestId)) historyError.value = error.message || '历史记录读取失败'
    throw error
  } finally {
    if (historyGate.isCurrent(requestId)) { historyLoading.value = false; historySearching.value = false }
  }
}
function scheduleHistorySearch(value) {
  historyQuery.value = value
  historySearching.value = true
  clearTimeout(historyTimer)
  historyTimer = window.setTimeout(() => loadHistory(true).catch(() => {}), 250)
}
async function changeHistoryScope(scope) {
  historyScope.value = scope
  historyQuery.value = ''
  await loadHistory(true).catch(() => {})
}
function toggleHistory() { historyCollapsed.value = !historyCollapsed.value; localStorage.setItem(SIDEBAR_KEY, historyCollapsed.value ? '1' : '0') }
async function closeMobileHistory() { mobileHistoryOpen.value = false; await nextTick(); mobileHistoryButton.value?.focus() }
async function selectHistorySession(session) { await loadSession(session.sessionId); closeMobileHistory() }

function startNewResearch(profileSeed = null) {
  saveCurrentDraft()
  stopPolling()
  currentSession.value = null
  selectedSessionId.value = ''
  messages.value = []
  currentRun.value = null
  nextBeforeSequence.value = null
  hasMoreMessages.value = false
  composerError.value = ''
  if (profileSeed) profile.value = { ...defaultProfile(), ...profileSeed }
  else profile.value = restoreNewProfile()
  composer.value = drafts.load(null)
  localStorage.removeItem(SESSION_KEY)
  mobileHistoryOpen.value = false
  nextTick(() => composerControl.value?.focus())
}
function forkResearch() { startNewResearch({ ...profile.value }) }

async function loadSession(sessionId) {
  saveCurrentDraft()
  stopPolling()
  composerError.value = ''
  const detail = await getResearchSession(sessionId)
  currentSession.value = detail.session
  selectedSessionId.value = String(sessionId)
  messages.value = detail.messages || []
  nextBeforeSequence.value = detail.nextBeforeSequence ?? null
  hasMoreMessages.value = Boolean(detail.hasMoreMessages)
  currentRun.value = detail.activeRun || detail.latestRun || null
  profile.value = normalizeProfile(detail.session?.profile)
  composer.value = drafts.load(sessionId)
  localStorage.setItem(SESSION_KEY, String(sessionId))
  if (detail.activeRun?.runId) startPolling(detail.activeRun.runId)
  await conversation.value?.scrollToEnd('auto')
  scheduleReadiness()
}

async function loadOlderMessages() {
  if (!currentSession.value || !hasMoreMessages.value || loadingOlder.value) return
  loadingOlder.value = true
  const snapshot = conversation.value?.scrollSnapshot()
  try {
    const page = await getResearchMessages(currentSession.value.sessionId, { beforeSequence: nextBeforeSequence.value, limit: 50 })
    messages.value = mergeMessagePages(messages.value, page.items || [])
    nextBeforeSequence.value = page.nextBeforeSequence ?? null
    hasMoreMessages.value = Boolean(page.hasMore)
    if (snapshot) await conversation.value?.restoreSnapshot(snapshot)
  } catch (error) { showToast(error.message || '更早消息读取失败') } finally { loadingOlder.value = false }
}

async function sendMessage() {
  const content = composer.value.trim()
  if (!content || submitting.value || runActive.value || !agentReady.value) return
  submitting.value = true
  composerError.value = ''
  lastQuestion.value = content
  let sessionId = currentSession.value?.sessionId
  try {
    if (!sessionId) {
      const created = await createResearchSession({ profile: serializeProfile(profile.value) })
      currentSession.value = created
      sessionId = created.sessionId
      selectedSessionId.value = String(sessionId)
      localStorage.setItem(SESSION_KEY, String(sessionId))
      drafts.save(sessionId, content)
    }
    const receipt = await sendResearchMessage(sessionId, { content, idempotencyKey: newIdempotencyKey() })
    composer.value = ''
    drafts.clear(sessionId)
    drafts.clear(null)
    currentRun.value = { runId: receipt.runId, sessionId: receipt.sessionId, status: receipt.status, currentStage: receipt.status, tools: [] }
    await loadSession(sessionId)
    if (!runActive.value && receipt.runId) startPolling(receipt.runId)
    await loadHistory(true).catch(() => {})
  } catch (error) {
    composerError.value = error.message || '研究请求提交失败；当前会话已保留，可以重试。'
    if (sessionId) drafts.save(sessionId, content)
  } finally { submitting.value = false }
}
function prefillQuestion(value) { composer.value = value; nextTick(() => composerControl.value?.focus()) }
function retryLast() { if (lastQuestion.value) { composer.value = lastQuestion.value; currentRun.value = null; nextTick(sendMessage) } }

function startPolling(runId, initialDelay = 300) {
  stopPolling()
  const generation = ++pollingGeneration
  pollingFailures = 0
  networkStatus.value = 'connected'
  const poll = async () => {
    try {
      const run = await getResearchRun(runId)
      if (generation !== pollingGeneration) return
      currentRun.value = run
      pollingFailures = 0
      networkStatus.value = 'connected'
      if (TERMINAL.has(run.status)) {
        const latest = await getResearchSession(run.sessionId)
        if (generation !== pollingGeneration) return
        messages.value = latest.messages || messages.value
        nextBeforeSequence.value = latest.nextBeforeSequence ?? null
        hasMoreMessages.value = Boolean(latest.hasMoreMessages)
        currentSession.value = latest.session
        currentRun.value = run
        usage.value = await getResearchUsage().catch(() => usage.value)
        await loadHistory(true).catch(() => {})
        await conversation.value?.scrollToEnd('smooth')
        return
      }
      pollingTimer = window.setTimeout(poll, 1200)
    } catch {
      if (generation !== pollingGeneration) return
      pollingFailures += 1
      const delays = [1000, 2000, 4000, 8000, 10000]
      if (pollingFailures > delays.length) { networkStatus.value = 'paused'; return }
      networkStatus.value = 'recovering'
      pollingTimer = window.setTimeout(poll, delays[pollingFailures - 1])
    }
  }
  pollingTimer = window.setTimeout(poll, initialDelay)
}
function resumePolling() { if (currentRun.value?.runId) startPolling(currentRun.value.runId, 0) }
function stopPolling() { clearTimeout(pollingTimer); pollingGeneration += 1; networkStatus.value = 'connected' }
async function cancelRun() {
  if (!currentRun.value?.runId || cancelling.value) return
  cancelling.value = true
  try { stopPolling(); currentRun.value = await cancelResearchRun(currentRun.value.runId); if (currentSession.value) await refreshSessionMessages() } catch (error) { showToast(error.message || '取消失败') } finally { cancelling.value = false }
}
async function refreshSessionMessages() {
  const detail = await getResearchSession(currentSession.value.sessionId)
  messages.value = detail.messages || []
  nextBeforeSequence.value = detail.nextBeforeSequence ?? null
  hasMoreMessages.value = Boolean(detail.hasMoreMessages)
  currentSession.value = detail.session
}

async function renameSession({ session, title }) { await mutateSession(() => updateResearchSession(session.sessionId, { title }), '标题已更新') }
async function pinSession(session) { await mutateSession(() => updateResearchSession(session.sessionId, { pinned: !session.pinned }), session.pinned ? '已取消置顶' : '已置顶') }
async function archiveSession(session) { await mutateSession(() => archiveResearchSessionExplicit(session.sessionId), '会话已归档', session) }
async function unarchiveSession(session) { await mutateSession(() => unarchiveResearchSession(session.sessionId), '会话已恢复到当前研究', session) }
async function trashSession(session) { await mutateSession(() => trashResearchSession(session.sessionId), '会话已移入回收站', session) }
async function restoreSession(session) { await mutateSession(() => restoreResearchSession(session.sessionId), '会话已恢复', session) }
async function purgeSession(session) { await mutateSession(() => permanentlyDeleteResearchSession(session.sessionId), '对话内容已永久删除', session) }
async function mutateSession(action, message, affected = null) {
  try {
    await action()
    if (affected && String(affected.sessionId) === String(currentSession.value?.sessionId)) startNewResearch()
    await loadHistory(true)
    showToast(message)
  } catch (error) { showToast(error.message || '会话操作失败') }
}

function scheduleReadiness() {
  clearTimeout(readinessTimer)
  readiness.value = null
  readinessError.value = ''
  if (!profile.value.regionId || (!profile.value.industryTagId && !profile.value.industry)) return
  readinessTimer = window.setTimeout(checkReadiness, 420)
}
async function checkReadiness() {
  readinessLoading.value = true
  try { readiness.value = await checkEntrepreneurshipReadiness({ regionId: Number(profile.value.regionId), industryTagId: profile.value.industryTagId ? Number(profile.value.industryTagId) : undefined, industry: profile.value.industry || undefined }) } catch (error) { readinessError.value = error.message || '证据预检失败' } finally { readinessLoading.value = false }
}

function openCitations(message) { drawerMode.value = 'citations'; drawerCitations.value = message.citations || []; drawerRun.value = null; drawerError.value = ''; drawerOpen.value = true }
async function openProcess(message) {
  drawerMode.value = 'process'; drawerCitations.value = []; drawerRun.value = null; drawerError.value = ''; drawerLoading.value = true; drawerOpen.value = true
  try { drawerRun.value = await getResearchRun(message.runId) } catch (error) { drawerError.value = error.message || '研究过程读取失败' } finally { drawerLoading.value = false }
}
function closeDrawer() { drawerOpen.value = false; drawerRun.value = null; drawerError.value = '' }
function saveCurrentDraft() { drafts.save(currentSession.value?.sessionId ?? null, composer.value) }
function serializeProfile(value) { return { ventureType: value.ventureType, regionId: value.regionId ? Number(value.regionId) : undefined, industryTagId: value.industryTagId ? Number(value.industryTagId) : undefined, industry: value.industry || undefined, stage: value.stage, budgetRange: value.budgetRange, goal: value.goal || undefined, resources: value.existingResources || undefined } }
function normalizeProfile(value = {}) { return { ...defaultProfile(), ...value, regionId: value.regionId == null ? '' : String(value.regionId), industryTagId: value.industryTagId == null ? '' : String(value.industryTagId), existingResources: value.resources || value.existingResources || '' } }
function restoreNewProfile() { try { return normalizeProfile(JSON.parse(localStorage.getItem(PROFILE_KEY) || '{}')) } catch { localStorage.removeItem(PROFILE_KEY); return defaultProfile() } }
function mergeBySession(current, incoming) { const rows = new Map(current.map((item) => [String(item.sessionId), item])); incoming.forEach((item) => rows.set(String(item.sessionId), item)); return [...rows.values()] }
function newIdempotencyKey() { return globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID().replaceAll('-', '') : `agent_${Date.now()}_${Math.random().toString(36).slice(2)}` }
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未发送问题' }
function showToast(message) { toast.value = message; clearTimeout(toastTimer); toastTimer = window.setTimeout(() => { toast.value = '' }, 2200) }
</script>

<style scoped>
.assistant-page{width:100%;min-width:0;color:#20251f}.assistant-workspace{display:grid;grid-template-columns:276px minmax(0,1fr);height:calc(100dvh - 118px);min-height:640px;border:1px solid #c7ccc5;background:#fbfbf7;overflow:hidden}.assistant-workspace.history-collapsed{grid-template-columns:64px minmax(0,1fr)}.research-desk{display:grid;grid-template-rows:auto auto auto minmax(0,1fr) auto auto;min-width:0;min-height:0;background:#fbfbf7}.desk-header{display:flex;align-items:center;gap:14px;min-height:70px;padding:12px 24px;border-bottom:1px solid #cdd1cb}.desk-header>div:nth-child(2){min-width:0;flex:1}.desk-header h1{overflow:hidden;margin:4px 0 0;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:1.15rem;font-weight:500;text-overflow:ellipsis;white-space:nowrap}.caption{color:#727972;font-family:'Bookman Old Style',Georgia,serif;font-size:.63rem;font-weight:700;letter-spacing:0}.desk-status{display:flex;align-items:center;gap:8px}.desk-status>span{width:8px;height:8px;border-radius:50%;background:#80734f}.desk-status>span.ready{background:#3e684a}.desk-status>div{display:grid;gap:2px}.desk-status strong{font-size:.69rem}.desk-status small{color:#7b817a;font-size:.61rem}.mobile-history-command{display:none}.desk-notice{display:flex;align-items:flex-start;gap:10px;padding:11px 24px;border-bottom:1px solid #d7c7c2;background:#f7efec;color:#6f3b35}.desk-notice strong{font-size:.78rem}.desk-notice p{margin:2px 0 0;font-size:.7rem}.composer-error{margin:0;padding:8px max(24px,calc((100% - 880px)/2));border-top:1px solid #d9c0ba;background:#f8efec;color:#703731;font-size:.72rem}.page-state{display:flex;align-items:center;justify-content:center;gap:16px;min-height:440px;padding:35px;color:#687068}.page-state p{margin:4px 0 0}.page-state.is-error{color:#7b3f36}.spinner{width:22px;height:22px;border:2px solid #c5cac3;border-top-color:#304e38;border-radius:50%;animation:spin .8s linear infinite}.secondary-command{display:flex;align-items:center;gap:7px;min-height:42px;padding:0 13px;border:1px solid #bfc5bd;border-radius:3px;background:#fff;color:#303630}.assistant-toast{position:fixed;z-index:120;right:22px;bottom:22px;max-width:min(360px,calc(100vw - 28px));margin:0;padding:11px 14px;border:1px solid #afb5ae;border-radius:3px;background:#282d28;color:#fff;font-size:.75rem}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:840px){.assistant-workspace,.assistant-workspace.history-collapsed{grid-template-columns:1fr;height:calc(100dvh - 86px);min-height:560px}.mobile-history-command{display:grid;place-items:center;width:44px;height:44px;flex:0 0 44px;border:0;background:transparent}.desk-header{padding:10px 14px}.desk-status small{display:none}}@media(max-width:600px){.assistant-workspace,.assistant-workspace.history-collapsed{height:calc(100dvh - 74px);border-right:0;border-left:0}.desk-header{min-height:62px}.desk-header h1{font-size:1rem}.desk-status>div{display:none}.desk-notice{padding:10px 14px}.composer-error{padding:8px 14px}.assistant-toast{right:14px;bottom:14px}}@media(prefers-reduced-motion:reduce){.spinner{animation:none}}
</style>
