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

      <main class="research-desk" data-layout="bounded-workspace" aria-label="Assistant 研究对话">
        <AssistantResearchProfile
          v-model="profile" :editable="!currentSession" :regions="regions" :industries="industries"
          :readiness="readiness" :readiness-loading="readinessLoading" :readiness-error="readinessError"
          :industry-resolution-loading="industryResolutionLoading" :industry-resolution-error="industryResolutionError"
          :industry-resolution-rejected="industryResolutionRejected"
          :industry-suggestion="industrySuggestion" :agent-ready="agentReady" :provider-label="providerLabel"
          @confirm-industry="confirmSuggestedIndustry" @reject-industry="rejectSuggestedIndustry" @fork="forkResearch"
        />

        <section v-if="!agentReady" class="desk-notice" role="status"><BrainCircuit :size="22" /><div><strong>智能体运行时尚未启用</strong><p>管理员需要启用模型 Provider 与 Agent Runtime；本页不会回退到虚假回答。</p></div></section>

        <AssistantConversation
          ref="conversation" :messages="messages" :run="currentRun" :has-more="hasMoreMessages"
          :evidence-run-id="currentRun?.runId" :evidence-items="evidenceItems"
          :evidence-summary="evidenceSummary"
          :evidence-loading="evidenceLoading" :evidence-error="evidenceError"
          :loading-older="loadingOlder" :draft-mode="!currentSession" :network-status="networkStatus" :cancelling="cancelling"
          @load-older="loadOlderMessages" @prefill="prefillQuestion($event)" @citations="openCitations" @process="openProcess"
          @evidence="openEvidenceDrawer"
          @cancel="cancelRun" @retry="retryLast" @resume="resumePolling"
        />

        <p v-if="composerError" class="composer-error" role="alert">{{ composerError }}</p>
        <AssistantComposer
          ref="composerControl" v-model="composer" :disabled="Boolean(composerDisabledReason)" :disabled-reason="composerDisabledReason"
          :sending="submitting" :running="runActive" :new-research="!currentSession" :usage="usage" @send="sendMessage" @cancel="cancelRun"
        />
      </main>
    </div>

    <AssistantCitationDrawer :open="drawerOpen" :restore-focus="drawerRestoreFocus" :mode="drawerMode" :citations="drawerCitations" :run="drawerRun" :evidence-run-id="currentRun?.runId" :evidence-items="evidenceItems" :evidence-summary="evidenceSummary" :loading="drawerMode === 'evidence' ? evidenceLoading : drawerLoading" :error="drawerMode === 'evidence' ? evidenceError : drawerError" @close="closeDrawer" />
    <p v-if="toast" class="assistant-toast" role="status">{{ toast }}</p>
  </div>
</template>

<script setup>
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { AlertTriangle, BrainCircuit, RefreshCw } from 'lucide-vue-next'
import AssistantCitationDrawer from '@/components/assistant/AssistantCitationDrawer.vue'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'
import AssistantConversation from '@/components/assistant/AssistantConversation.vue'
import AssistantHistorySidebar from '@/components/assistant/AssistantHistorySidebar.vue'
import AssistantResearchProfile from '@/components/assistant/AssistantResearchProfile.vue'
import {
  archiveResearchSessionExplicit, cancelResearchRun, checkEntrepreneurshipReadiness,
  getAiCapabilities, getResearchHistory, getResearchMessages, getResearchRun, getResearchRunEvidence, getResearchSession, getResearchUsage,
  permanentlyDeleteResearchSession, resolveIndustryWithAi, restoreResearchSession, sendResearchMessage, startResearchSession, trashResearchSession,
  unarchiveResearchSession, updateResearchSession,
} from '@/api/ai'
import { getRegions } from '@/api/region'
import { getIndustryTags } from '@/api/tag'
import { getUserProfile } from '@/api/auth'
import { createAssistantDraftStore } from '@/composables/useAssistantDrafts'
import {
  confirmIndustrySuggestion, createCanonicalFingerprint, createLatestRequestGate, decideIndustryResolution,
  industrySuggestionKey, isDeterministicRequestFailure, readinessPresentation,
} from '@/utils/assistantWorkflow'
import { mergeMessagePages } from '@/utils/assistantWorkspace'

const USER_NAMESPACE = `user:${getUserProfile()?.userId || 'anonymous'}`
const STORAGE_PREFIX = `opc_assistant:${USER_NAMESPACE}:`
const SESSION_KEY = `${STORAGE_PREFIX}selected-session`
const SIDEBAR_KEY = 'opc_assistant_history_collapsed'
const PROFILE_KEY = `${STORAGE_PREFIX}new-profile-v3`
const TERMINAL = new Set(['completed', 'clarification_needed', 'evidence_insufficient', 'failed', 'cancelled', 'expired'])
const defaultProfile = () => ({ ventureType: 'solo_company', regionId: '', industryTagId: '', industry: '', stage: 'validation', budgetRange: 'under_100k', goal: '', existingResources: '' })
const drafts = createAssistantDraftStore(globalThis.localStorage, USER_NAMESPACE)
const historyGate = createLatestRequestGate()
const sessionGate = createLatestRequestGate()
const messagePageGate = createLatestRequestGate()
const industryGate = createLatestRequestGate()
const readinessGate = createLatestRequestGate()
const processGate = createLatestRequestGate()
const evidenceGate = createLatestRequestGate()
const emit = defineEmits(['workspace-title', 'workspace-status'])
const evidenceOpenRequest = inject('assistant-evidence-request', ref(0))
const historyControl = inject('assistant-history-control', {
  request: ref(0),
  restoreFocus: () => {},
})

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
const selectedSessionId = ref('')
const currentSession = ref(null)
const messages = ref([])
const nextBeforeSequence = ref(null)
const hasMoreMessages = ref(false)
const loadingOlder = ref(false)
const activeRun = ref(null)
const latestRun = ref(null)
const evidenceItems = ref([])
const evidenceSummary = ref(null)
const evidenceLoading = ref(false)
const evidenceError = ref('')
const composer = ref(drafts.load(null))
const requestedIntent = ref('auto')
const starterPrompt = ref('')
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
const evidenceDependencyKey = computed(() => JSON.stringify([
  String(profile.value.regionId || ''),
  String(profile.value.industryTagId || ''),
  normalizeEvidenceIndustry(profile.value.industry),
]))
const readiness = ref(null)
const readinessLoading = ref(false)
const readinessError = ref('')
const industryResolutionLoading = ref(false)
const industryResolutionError = ref('')
const industryResolutionRejected = ref('')
const industrySuggestion = ref(null)
const rejectedIndustryKey = ref('')
const rejectedIndustryQuery = ref('')
const networkStatus = ref('connected')
const drawerOpen = ref(false)
const drawerRestoreFocus = ref(true)
const drawerMode = ref('citations')
const drawerCitations = ref([])
const drawerRun = ref(null)
const drawerLoading = ref(false)
const drawerError = ref('')
const toast = ref('')
let historyTimer
let readinessTimer
let industryTimer
let pollingTimer
let pollingGeneration = 0
let pollingFailures = 0
let toastTimer

const agentReady = computed(() => Boolean(capabilities.value?.provider?.available && capabilities.value?.capabilities?.some((item) => item.id === 'agent-runtime' && item.available)))
const providerLabel = computed(() => capabilities.value?.provider ? `${capabilities.value.provider.provider} / ${capabilities.value.provider.model}` : '等待管理员配置')
const quotaExhausted = computed(() => Boolean(usage.value && !usage.value.unlimited && Number(usage.value.remainingTokens) <= 0))
const currentRun = computed(() => activeRun.value || latestRun.value)
const workspaceTitle = computed(() => currentSession.value?.title || '新研究')
const workspaceStatus = computed(() => ({
  label: currentSession.value ? sessionStatusLabel.value : '本地草稿',
  ready: Boolean(agentReady.value),
}))
const evidenceRefreshKey = computed(() => currentRun.value?.runId
  ? `${currentRun.value.runId}:${currentRun.value.toolCallCount || 0}:${currentRun.value.status || ''}`
  : '')
const runActive = computed(() => Boolean(activeRun.value && !TERMINAL.has(activeRun.value.status)))
const sessionStatusLabel = computed(() => currentSession.value?.deletedAt ? '回收站' : currentSession.value?.status === 'archived' ? '已归档' : runActive.value ? '研究进行中' : '当前会话')
const composerDisabledReason = computed(() => {
  if (!agentReady.value) return '智能体暂不可用'
  if (currentSession.value?.deletedAt) return '回收站会话不能发送消息'
  if (currentSession.value?.status === 'archived') return '归档会话仅供查阅'
  if (runActive.value) return ''
  if (quotaExhausted.value) return '今日研究额度已用尽'
  if (!currentSession.value) {
    if (!profile.value.regionId) return '请先选择所在地区'
    if (!profile.value.industryTagId && !profile.value.industry) return '请先设置目标行业'
    if (industryResolutionLoading.value) return '正在匹配目标行业'
    if (industrySuggestion.value) return '请先确认目标行业'
    if (industryResolutionError.value) return industryResolutionError.value
    const presentation = readinessPresentation(readiness.value?.readinessStatus, {
      loading: readinessLoading.value,
      error: Boolean(readinessError.value),
    })
    if (!presentation.canSubmit) {
      if (readinessLoading.value) return '正在核验证据'
      if (readinessError.value) return readinessError.value
      if (readiness.value?.readinessStatus === 'insufficient') return '当前证据不足，请调整地区或行业'
      return '等待证据预检'
    }
  }
  return ''
})

watch(workspaceTitle, (title) => emit('workspace-title', title), { immediate: true })
watch(workspaceStatus, (status) => emit('workspace-status', status), { immediate: true })
watch(evidenceOpenRequest, openEvidenceDrawer)
watch(historyControl.request, openMobileHistory)

onMounted(loadPage)
onBeforeUnmount(() => { clearTimeout(historyTimer); clearTimeout(industryTimer); clearTimeout(readinessTimer); clearTimeout(pollingTimer); clearTimeout(toastTimer); pollingGeneration += 1 })
watch(profile, (value) => {
  if (!currentSession.value) localStorage.setItem(PROFILE_KEY, JSON.stringify(value))
}, { deep: true })
watch(evidenceDependencyKey, scheduleIndustryResolution)
watch(evidenceRefreshKey, loadCurrentEvidence)
watch(composer, (value) => {
  drafts.save(currentSession.value?.sessionId ?? null, value)
  if (requestedIntent.value !== 'auto' && value !== starterPrompt.value) {
    requestedIntent.value = 'auto'
    starterPrompt.value = ''
  }
})

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
    scheduleIndustryResolution()
  }
}

async function loadHistory(reset = true, staleRefresh = false) {
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
    if (!historyGate.isCurrent(requestId)) return
    if (!reset && !staleRefresh && error?.diagnosticCode === 'HISTORY_CURSOR_STALE') {
      historyItems.value = []
      historyCursor.value = null
      historyHasMore.value = false
      showToast('历史记录已更新')
      try { return await loadHistory(true, true) } catch { return }
    }
    historyError.value = error.message || '历史记录读取失败'
    if (reset) throw error
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
function openMobileHistory() { closeDrawer(false); mobileHistoryOpen.value = true }
async function closeMobileHistory() { mobileHistoryOpen.value = false; await nextTick(); historyControl.restoreFocus() }
async function selectHistorySession(session) { await loadSession(session.sessionId); closeMobileHistory() }

function startNewResearch(profileSeed = null) {
  sessionGate.begin()
  evidenceGate.begin()
  messagePageGate.begin()
  closeDrawer(false)
  saveCurrentDraft()
  stopPolling()
  currentSession.value = null
  selectedSessionId.value = ''
  messages.value = []
  activeRun.value = null
  latestRun.value = null
  evidenceItems.value = []
  evidenceSummary.value = null
  evidenceLoading.value = false
  evidenceError.value = ''
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
  const requestId = sessionGate.begin()
  messagePageGate.begin()
  evidenceGate.begin()
  evidenceItems.value = []
  evidenceSummary.value = null
  evidenceLoading.value = false
  evidenceError.value = ''
  closeDrawer(false)
  loadingOlder.value = false
  saveCurrentDraft()
  stopPolling()
  composerError.value = ''
  const detail = await getResearchSession(sessionId)
  if (!sessionGate.isCurrent(requestId)) return false
  currentSession.value = detail.session
  selectedSessionId.value = String(sessionId)
  messages.value = detail.messages || []
  nextBeforeSequence.value = detail.nextBeforeSequence ?? null
  hasMoreMessages.value = Boolean(detail.hasMoreMessages)
  activeRun.value = detail.activeRun || null
  latestRun.value = detail.latestRun || null
  profile.value = normalizeProfile(detail.session?.profile)
  composer.value = drafts.load(sessionId)
  localStorage.setItem(SESSION_KEY, String(sessionId))
  if (detail.activeRun?.runId) startPolling(detail.activeRun.runId)
  await conversation.value?.scrollToEnd('auto')
  scheduleIndustryResolution()
  return true
}

async function loadCurrentEvidence() {
  const runId = currentRun.value?.runId
  const requestId = evidenceGate.begin()
  if (!runId) {
    evidenceItems.value = []
    evidenceSummary.value = null
    evidenceLoading.value = false
    evidenceError.value = ''
    return
  }
  evidenceLoading.value = true
  evidenceError.value = ''
  try {
    const evidence = await getResearchRunEvidence(runId)
    if (!evidenceGate.isCurrent(requestId) || String(currentRun.value?.runId) !== String(runId)) return
    const runMessage = [...messages.value].reverse().find((message) => (
      message.role === 'assistant' && String(message.runId || '') === String(runId)
    ))
    const citationBySource = new Map((runMessage?.citations || []).map((citation, index) => [
      String(citation.sourceId),
      {
        citationId: citation.citationId || `${runId}:${citation.sourceId}:${index + 1}`,
        citationIndex: index + 1,
      },
    ]))
    evidenceItems.value = (evidence?.items || []).map((item) => ({
      ...item,
      runId,
      ...(citationBySource.get(String(item.sourceId)) || {}),
    }))
    evidenceSummary.value = evidence ? {
      availableCount: Number(evidence.availableCount || 0),
      totalCount: Number(evidence.totalCount || 0),
      unavailableCount: Number(evidence.unavailableCount || 0),
      availableGroups: evidence.availableGroups || {},
      totalGroups: evidence.totalGroups || evidence.groups || {},
    } : null
  } catch (error) {
    if (!evidenceGate.isCurrent(requestId) || String(currentRun.value?.runId) !== String(runId)) return
    evidenceItems.value = []
    evidenceSummary.value = null
    evidenceError.value = error.message || '研究资料读取失败'
  } finally {
    if (evidenceGate.isCurrent(requestId)) evidenceLoading.value = false
  }
}

async function loadOlderMessages() {
  if (!currentSession.value || !hasMoreMessages.value || loadingOlder.value) return
  const requestId = messagePageGate.begin()
  const sessionId = currentSession.value.sessionId
  loadingOlder.value = true
  const snapshot = conversation.value?.scrollSnapshot()
  try {
    const page = await getResearchMessages(sessionId, { beforeSequence: nextBeforeSequence.value, limit: 50 })
    if (!messagePageGate.isCurrent(requestId) || String(currentSession.value?.sessionId) !== String(sessionId)) return
    messages.value = mergeMessagePages(messages.value, page.items || [])
    nextBeforeSequence.value = page.nextBeforeSequence ?? null
    hasMoreMessages.value = Boolean(page.hasMore)
    if (snapshot) await conversation.value?.restoreSnapshot(snapshot)
  } catch (error) {
    if (messagePageGate.isCurrent(requestId)) showToast(error.message || '更早消息读取失败')
  } finally {
    if (messagePageGate.isCurrent(requestId)) loadingOlder.value = false
  }
}

async function sendMessage() {
  const content = composer.value.trim()
  if (!content || submitting.value || runActive.value || !agentReady.value || composerDisabledReason.value) return
  submitting.value = true
  composerError.value = ''
  let sessionId = currentSession.value?.sessionId
  const draftSessionId = sessionId ?? null
  const selectionRequestId = sessionGate.begin()
  try {
    let receipt
    let startedSession = null
    if (!sessionId) {
      const startProfile = serializeProfile(profile.value)
      const fingerprint = createCanonicalFingerprint({ profile: startProfile, content, requestedIntent: requestedIntent.value })
      const pending = drafts.loadPendingStart()
      const idempotencyKey = pending?.fingerprint === fingerprint ? pending.idempotencyKey : newIdempotencyKey()
      drafts.savePendingStart({ idempotencyKey, fingerprint })
      try {
        receipt = await startResearchSession({ profile: startProfile, content, requestedIntent: requestedIntent.value, idempotencyKey })
        drafts.clearPendingStart()
      } catch (error) {
        if (isDeterministicRequestFailure(error)) drafts.clearPendingStart()
        throw error
      }
      startedSession = receipt.session
      sessionId = receipt.session.sessionId
    } else {
      const fingerprint = createCanonicalFingerprint({ sessionId, content })
      const pending = drafts.loadPendingMessage(sessionId)
      const idempotencyKey = pending?.fingerprint === fingerprint ? pending.idempotencyKey : newIdempotencyKey()
      drafts.savePendingMessage(sessionId, { idempotencyKey, fingerprint })
      try {
        receipt = await sendResearchMessage(sessionId, { content, requestedIntent: 'auto', idempotencyKey })
        drafts.clearPendingMessage(sessionId)
      } catch (error) {
        if (isDeterministicRequestFailure(error)) drafts.clearPendingMessage(sessionId)
        throw error
      }
    }
    if (!sessionGate.isCurrent(selectionRequestId)) {
      drafts.clear(draftSessionId)
      await loadHistory(true).catch(() => {})
      return
    }
    if (startedSession) {
      currentSession.value = startedSession
      selectedSessionId.value = String(sessionId)
      localStorage.setItem(SESSION_KEY, String(sessionId))
      drafts.save(sessionId, content)
    }
    composer.value = ''
    requestedIntent.value = 'auto'
    starterPrompt.value = ''
    drafts.clear(sessionId)
    drafts.clear(null)
    const receivedRun = { runId: receipt.runId, sessionId, status: receipt.status, currentStage: receipt.status, tools: [] }
    if (TERMINAL.has(receipt.status)) latestRun.value = receivedRun
    else activeRun.value = receivedRun
    await loadSession(sessionId)
    if (!runActive.value && receipt.runId) startPolling(receipt.runId)
    await loadHistory(true).catch(() => {})
  } catch (error) {
    if (sessionGate.isCurrent(selectionRequestId)) composerError.value = error.message || '研究请求提交失败；当前会话已保留，可以重试。'
    drafts.save(draftSessionId, content)
  } finally { submitting.value = false }
}
function prefillQuestion(value) {
  const payload = typeof value === 'string' ? { prompt: value, requestedIntent: 'auto' } : value
  requestedIntent.value = payload?.requestedIntent || 'auto'
  starterPrompt.value = payload?.prompt || ''
  composer.value = starterPrompt.value
  nextTick(() => composerControl.value?.focus())
}
function retryLast() {
  const content = String(currentRun.value?.retryContent || '').trim()
  if (!content) return
  composer.value = content
  activeRun.value = null
  latestRun.value = null
  nextTick(sendMessage)
}

function startPolling(runId, initialDelay = 300) {
  stopPolling()
  const generation = ++pollingGeneration
  pollingFailures = 0
  networkStatus.value = 'connected'
  const poll = async () => {
    try {
      const run = await getResearchRun(runId)
      if (generation !== pollingGeneration) return
      if (TERMINAL.has(run.status)) {
        activeRun.value = null
        latestRun.value = run
      } else activeRun.value = run
      pollingFailures = 0
      networkStatus.value = 'connected'
      if (TERMINAL.has(run.status)) {
        const followIncoming = conversation.value?.isNearBottom() ?? true
        const latest = await getResearchSession(run.sessionId)
        if (generation !== pollingGeneration) return
        messages.value = latest.messages || messages.value
        nextBeforeSequence.value = latest.nextBeforeSequence ?? null
        hasMoreMessages.value = Boolean(latest.hasMoreMessages)
        currentSession.value = latest.session
        activeRun.value = null
        latestRun.value = run
        usage.value = await getResearchUsage().catch(() => usage.value)
        await loadHistory(true).catch(() => {})
        await conversation.value?.applyIncoming(followIncoming, 'smooth')
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
function resumePolling() { if (activeRun.value?.runId) startPolling(activeRun.value.runId, 0) }
function stopPolling() { clearTimeout(pollingTimer); pollingGeneration += 1; networkStatus.value = 'connected' }
async function cancelRun() {
  if (!activeRun.value?.runId || cancelling.value) return
  const requestId = sessionGate.begin()
  const sessionId = currentSession.value?.sessionId
  const runId = activeRun.value.runId
  cancelling.value = true
  try {
    stopPolling()
    const cancelledRun = await cancelResearchRun(runId)
    if (!sessionGate.isCurrent(requestId) || String(currentSession.value?.sessionId) !== String(sessionId)) return
    latestRun.value = cancelledRun
    activeRun.value = null
    if (sessionId) await refreshSessionMessages(sessionId, requestId)
  } catch (error) {
    if (sessionGate.isCurrent(requestId) && String(currentSession.value?.sessionId) === String(sessionId)) {
      showToast(error.message || '取消失败')
      if (String(activeRun.value?.runId) === String(runId)) startPolling(runId, 0)
    }
  } finally { cancelling.value = false }
}
async function refreshSessionMessages(sessionId, requestId) {
  const detail = await getResearchSession(sessionId)
  if (!sessionGate.isCurrent(requestId) || String(currentSession.value?.sessionId) !== String(sessionId)) return
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

function scheduleIndustryResolution() {
  clearTimeout(industryTimer)
  const requestId = industryGate.begin()
  const query = profile.value.industry.trim()
  const rejectedQueryMatches = Boolean(rejectedIndustryQuery.value && rejectedIndustryQuery.value === query)
  industryResolutionLoading.value = false
  industryResolutionError.value = ''
  industrySuggestion.value = null
  if (!rejectedQueryMatches) {
    rejectedIndustryKey.value = ''
    rejectedIndustryQuery.value = ''
    industryResolutionRejected.value = ''
  }
  if (currentSession.value || profile.value.industryTagId || !profile.value.industry) {
    scheduleReadiness()
    return
  }
  if (rejectedQueryMatches) {
    scheduleReadiness()
    return
  }
  clearTimeout(readinessTimer)
  readinessGate.begin()
  readiness.value = null
  readinessError.value = ''
  industryResolutionLoading.value = true
  industryTimer = window.setTimeout(() => resolveIndustry(query, requestId), 320)
}
function normalizeEvidenceIndustry(value) {
  return String(value || '').trim().replace(/\s+/g, ' ')
}
async function resolveIndustry(query, requestId) {
  try {
    const resolution = await resolveIndustryWithAi(query)
    if (!industryGate.isCurrent(requestId)) return
    const decision = decideIndustryResolution(resolution, query, profile.value.industryTagId, rejectedIndustryKey.value)
    if (decision.action === 'accept') {
      const selection = decision.selection
      profile.value = { ...profile.value, industryTagId: selection.industryTagId, industry: selection.industry }
      return
    }
    if (decision.action === 'confirm') industrySuggestion.value = decision.suggestion
    else if (decision.action === 'rejected') scheduleReadiness()
    else industryResolutionError.value = '暂时无法匹配标准行业，请调整输入后重试'
  } catch (error) {
    if (industryGate.isCurrent(requestId)) industryResolutionError.value = error.message || '目标行业匹配失败'
  } finally {
    if (industryGate.isCurrent(requestId)) industryResolutionLoading.value = false
  }
}
function confirmSuggestedIndustry() {
  if (!industrySuggestion.value) return
  const selection = confirmIndustrySuggestion(industrySuggestion.value)
  rejectedIndustryKey.value = ''
  rejectedIndustryQuery.value = ''
  industryResolutionRejected.value = ''
  industrySuggestion.value = null
  profile.value = { ...profile.value, industryTagId: selection.industryTagId, industry: selection.industry }
}
function rejectSuggestedIndustry() {
  if (!industrySuggestion.value) return
  rejectedIndustryKey.value = industrySuggestionKey(industrySuggestion.value)
  rejectedIndustryQuery.value = profile.value.industry.trim()
  industryResolutionRejected.value = `已保留“${industrySuggestion.value.originalText || profile.value.industry}”，未采用建议匹配`
  industrySuggestion.value = null
  scheduleReadiness()
}
function scheduleReadiness() {
  clearTimeout(readinessTimer)
  const requestId = readinessGate.begin()
  readiness.value = null
  readinessError.value = ''
  readinessLoading.value = false
  if (!profile.value.regionId || (!profile.value.industryTagId && !profile.value.industry) || industryResolutionLoading.value || industrySuggestion.value) return
  const payload = { regionId: Number(profile.value.regionId), industryTagId: profile.value.industryTagId ? Number(profile.value.industryTagId) : undefined, industry: profile.value.industry || undefined }
  readinessLoading.value = true
  readinessTimer = window.setTimeout(() => checkReadiness(payload, requestId), 420)
}
async function checkReadiness(payload, requestId) {
  try {
    const result = await checkEntrepreneurshipReadiness(payload)
    if (readinessGate.isCurrent(requestId)) readiness.value = result
  } catch (error) {
    if (readinessGate.isCurrent(requestId)) readinessError.value = error.message || '证据预检失败'
  } finally {
    if (readinessGate.isCurrent(requestId)) readinessLoading.value = false
  }
}

async function openCitations(message) {
  const requestId = processGate.begin()
  const runId = message.runId
  const baseCitations = (message.citations || []).map((citation, index) => ({
    ...citation,
    runId,
    citationId: citation.citationId || `${runId || 'legacy'}:${citation.sourceId}:${index + 1}`,
  }))
  drawerRestoreFocus.value = true
  drawerMode.value = 'citations'
  drawerCitations.value = baseCitations
  drawerRun.value = null
  drawerError.value = ''
  drawerLoading.value = Boolean(runId)
  drawerOpen.value = true
  if (!runId) return
  try {
    const evidence = await getResearchRunEvidence(runId)
    if (!processGate.isCurrent(requestId)) return
    const bySource = new Map((evidence?.items || []).map((item) => [String(item.sourceId), item]))
    drawerCitations.value = baseCitations.map((citation) => {
      const item = bySource.get(String(citation.sourceId))
      return {
        ...citation,
        title: item?.title || citation.title || `来源 #${citation.sourceId}`,
        publisher: item?.publisher || citation.publisher || '',
        url: item?.originalUrl || citation.url || '',
        verificationStatus: item?.available ? '已核验且本次运行已授权' : '本次运行资料当前不可用',
        authorized: Boolean(item?.available),
      }
    })
  } catch (error) {
    if (processGate.isCurrent(requestId)) drawerError.value = error.message || '引用依据读取失败'
  } finally {
    if (processGate.isCurrent(requestId)) drawerLoading.value = false
  }
}
async function openProcess(message) {
  const requestId = processGate.begin()
  drawerRestoreFocus.value = true; drawerMode.value = 'process'; drawerCitations.value = []; drawerRun.value = null; drawerError.value = ''; drawerLoading.value = true; drawerOpen.value = true
  try {
    const run = await getResearchRun(message.runId)
    if (processGate.isCurrent(requestId)) drawerRun.value = run
  } catch (error) {
    if (processGate.isCurrent(requestId)) drawerError.value = error.message || '研究过程读取失败'
  } finally {
    if (processGate.isCurrent(requestId)) drawerLoading.value = false
  }
}
function openEvidenceDrawer() {
  mobileHistoryOpen.value = false
  drawerRestoreFocus.value = true
  drawerMode.value = 'evidence'
  drawerCitations.value = []
  drawerRun.value = null
  drawerError.value = ''
  drawerLoading.value = false
  drawerOpen.value = true
}
function closeDrawer(restoreFocus = true) { processGate.begin(); drawerRestoreFocus.value = restoreFocus; drawerOpen.value = false; drawerRun.value = null; drawerError.value = ''; drawerLoading.value = false }
function saveCurrentDraft() { drafts.save(currentSession.value?.sessionId ?? null, composer.value) }
function serializeProfile(value) { return { ventureType: value.ventureType, regionId: value.regionId ? Number(value.regionId) : undefined, industryTagId: value.industryTagId ? Number(value.industryTagId) : undefined, industry: value.industry || undefined, stage: value.stage, budgetRange: value.budgetRange, goal: value.goal || undefined, resources: value.existingResources || undefined } }
function normalizeProfile(value = {}) { return { ...defaultProfile(), ...value, regionId: value.regionId == null ? '' : String(value.regionId), industryTagId: value.industryTagId == null ? '' : String(value.industryTagId), existingResources: value.resources || value.existingResources || '' } }
function restoreNewProfile() { try { return normalizeProfile(JSON.parse(localStorage.getItem(PROFILE_KEY) || '{}')) } catch { localStorage.removeItem(PROFILE_KEY); return defaultProfile() } }
function mergeBySession(current, incoming) { const rows = new Map(current.map((item) => [String(item.sessionId), item])); incoming.forEach((item) => rows.set(String(item.sessionId), item)); return [...rows.values()] }
function newIdempotencyKey() { return globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID().replaceAll('-', '') : `agent_${Date.now()}_${Math.random().toString(36).slice(2)}` }
function showToast(message) { toast.value = message; clearTimeout(toastTimer); toastTimer = window.setTimeout(() => { toast.value = '' }, 2200) }
</script>

<style scoped>
.assistant-page{width:100%;height:100%;min-width:0;min-height:0;color:#20251f;overflow:hidden}.assistant-workspace{display:grid;grid-template-columns:276px minmax(0,1fr);width:100%;height:100%;min-width:0;min-height:0;background:#fbfbf7;overflow:hidden}.assistant-workspace.history-collapsed{grid-template-columns:64px minmax(0,1fr)}.research-desk{display:flex;flex-direction:column;width:100%;height:100%;min-width:0;min-height:0;background:#fbfbf7;overflow:hidden}.desk-notice{display:flex;align-items:flex-start;gap:10px;padding:11px 24px;border-bottom:1px solid #d7c7c2;background:#f7efec;color:#6f3b35;flex:0 0 auto}.desk-notice strong{font-size:.78rem}.desk-notice p{margin:2px 0 0;font-size:.7rem}.composer-error{margin:0;padding:8px max(24px,calc((100% - 880px)/2));border-top:1px solid #d9c0ba;background:#f8efec;color:#703731;font-size:.72rem;flex:0 0 auto}.page-state{display:flex;align-items:center;justify-content:center;gap:16px;min-height:100%;padding:35px;color:#687068}.page-state p{margin:4px 0 0}.page-state.is-error{color:#7b3f36}.spinner{width:22px;height:22px;border:2px solid #c5cac3;border-top-color:#304e38;border-radius:50%;animation:spin .8s linear infinite}.secondary-command{display:flex;align-items:center;gap:7px;min-height:42px;padding:0 13px;border:1px solid #bfc5bd;border-radius:3px;background:#fff;color:#303630}.secondary-command:is(:hover,:focus-visible){border-color:#747b74;background:#f0f1ec}.secondary-command:focus-visible{outline:2px solid rgba(74,82,74,.34);outline-offset:2px}.secondary-command:active{background:#e5e7e1}.assistant-toast{position:fixed;z-index:120;right:22px;bottom:22px;max-width:min(360px,calc(100vw - 28px));margin:0;padding:11px 14px;border:1px solid #afb5ae;border-radius:3px;background:#282d28;color:#fff;font-size:.75rem}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:840px){.assistant-workspace,.assistant-workspace.history-collapsed{grid-template-columns:1fr;height:100%;min-height:0}}@media(max-width:600px){.assistant-workspace,.assistant-workspace.history-collapsed{height:100%}.desk-notice{padding:10px 14px}.composer-error{padding:8px 14px}.assistant-toast{right:14px;bottom:14px}}@media(prefers-reduced-motion:reduce){.spinner{animation:none}}
</style>
