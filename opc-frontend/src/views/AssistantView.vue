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
          :forking="forking"
          @confirm-industry="confirmSuggestedIndustry" @reject-industry="rejectSuggestedIndustry" @fork="forkResearch"
        />

        <AssistantResearchTask
          v-model="researchTask"
          :editable="!currentSession && !branchResearchDraft && !analyticsResearchDraft"
          :case-options="researchCaseOptions" :source-options="researchSourceOptions"
          :loading="researchCandidatesLoading" :candidate-error="researchCandidatesError"
        />

        <p v-if="analyticsResearchDraft" class="analytics-research-context" role="status">
          <span>数据看板条件</span><strong>{{ analyticsResearchDraft.metricLabel }}</strong><small>数据版本 {{ analyticsResearchDraft.dataVersion }}</small>
        </p>

        <section v-if="branchResearchDraft" class="branch-research-context" data-testid="branch-research-context" aria-labelledby="branch-research-context-title">
          <div>
            <span>研究分支</span>
            <strong id="branch-research-context-title">来自运行 #{{ branchResearchDraft.sourceRunId }}</strong>
            <small>{{ branchCitationLabel }}</small>
          </div>
          <p>{{ branchResearchDraft.resultSummary }}</p>
          <button type="button" aria-label="移除研究分支条件" @click="discardBranchResearchDraft"><X :size="16" /></button>
        </section>

        <AssistantResearchPreferences :can-apply="!currentSession" @apply="applyResearchPreferences" />

        <AssistantReportsPanel :session-id="currentSession?.sessionId" :run="currentRun" @re-research="restartReportResearch" />

        <section v-if="!agentReady" class="desk-notice" role="status"><BrainCircuit :size="22" /><div><strong>智能体运行时尚未启用</strong><p>管理员需要启用模型 Provider 与 Agent Runtime；本页不会回退到虚假回答。</p></div></section>

        <AssistantConversation
          ref="conversation" :messages="messages" :run="currentRun" :has-more="hasMoreMessages"
          :evidence-run-id="currentRun?.runId" :evidence-items="evidenceItems"
          :evidence-summary="evidenceSummary"
          :evidence-loading="evidenceLoading" :evidence-error="evidenceError"
          :loading-older="loadingOlder" :draft-mode="!currentSession" :network-status="networkStatus" :cancelling="cancelling"
          :terminal-sync-status="terminalSyncStatus"
          @load-older="loadOlderMessages" @prefill="prefillQuestion($event)" @citations="openCitations" @process="openProcess"
          @evidence="openEvidenceDrawer"
          @cancel="cancelRun" @retry="retryLast" @resume="resumePolling"
        />

        <AssistantRunFeedback :run="currentRun" />

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
import { useRoute, useRouter } from 'vue-router'
import { AlertTriangle, BrainCircuit, RefreshCw, X } from 'lucide-vue-next'
import AssistantCitationDrawer from '@/components/assistant/AssistantCitationDrawer.vue'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'
import AssistantConversation from '@/components/assistant/AssistantConversation.vue'
import AssistantHistorySidebar from '@/components/assistant/AssistantHistorySidebar.vue'
import AssistantResearchProfile from '@/components/assistant/AssistantResearchProfile.vue'
import AssistantResearchTask from '@/components/assistant/AssistantResearchTask.vue'
import AssistantResearchPreferences from '@/components/assistant/AssistantResearchPreferences.vue'
import AssistantReportsPanel from '@/components/assistant/AssistantReportsPanel.vue'
import AssistantRunFeedback from '@/components/assistant/AssistantRunFeedback.vue'
import {
  archiveResearchSessionExplicit, cancelResearchRun, checkEntrepreneurshipReadiness,
  getAiCapabilities, getResearchBranchMaterial, getResearchHistory, getResearchMessages, getResearchRun, getResearchRunEvidence, getResearchSession, getResearchUsage,
  permanentlyDeleteResearchSession, resolveIndustryWithAi, restoreResearchSession, sendResearchMessage, startResearchFromAnalytics, startResearchSession, trashResearchSession,
  unarchiveResearchSession, updateResearchSession,
} from '@/api/ai'
import { getRegions } from '@/api/region'
import { getIndustryTags } from '@/api/tag'
import { getCases } from '@/api/case'
import { getSources } from '@/api/source'
import { getUserProfile } from '@/api/auth'
import { createAssistantDraftStore } from '@/composables/useAssistantDrafts'
import { clearAnalyticsResearchDraft, readAnalyticsResearchDraft } from '@/composables/useAnalyticsResearchHandoff'
import {
  confirmIndustrySuggestion, createCanonicalFingerprint, createLatestRequestGate, decideIndustryResolution,
  industrySuggestionKey, isDeterministicRequestFailure, readinessPresentation,
} from '@/utils/assistantWorkflow'
import { mergeMessagePages } from '@/utils/assistantWorkspace'

const route = useRoute()
const router = useRouter()
const USER_ID = getUserProfile()?.userId || 'anonymous'
const USER_NAMESPACE = `user:${USER_ID}`
const STORAGE_PREFIX = `opc_assistant:${USER_NAMESPACE}:`
const SESSION_KEY = `${STORAGE_PREFIX}selected-session`
const SIDEBAR_KEY = 'opc_assistant_history_collapsed'
const PROFILE_KEY = `${STORAGE_PREFIX}new-profile-v3`
const BRANCH_DRAFT_KEY = `${STORAGE_PREFIX}branch-draft-v1`
const TERMINAL = new Set(['completed', 'clarification_needed', 'evidence_insufficient', 'failed', 'cancelled', 'expired'])
const POLLING_INTERVAL_MS = 1200
const POLLING_RETRY_DELAYS_MS = [1000, 2000, 4000, 8000, 10000]
const LOCAL_POLLING_MAX_WAIT_MS = 120000
const defaultProfile = () => ({ ventureType: 'solo_company', regionId: '', industryTagId: '', industry: '', stage: 'validation', budgetRange: 'under_100k', goal: '', existingResources: '' })
const PHASE_THREE_TASK_TYPES = new Set(['case_analysis', 'case_comparison', 'technology_assessment', 'policy_lookup', 'source_verification', 'general_research'])
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
const terminalSync = ref(null)
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
const forking = ref(false)
const conversation = ref(null)
const composerControl = ref(null)
const regions = ref([])
const industries = ref([])
const capabilities = ref(null)
const usage = ref(null)
const profile = ref(restoreNewProfile())
const researchTask = ref(createNewResearchTask())
const researchCaseOptions = ref([])
const researchSourceOptions = ref([])
const researchCandidatesLoading = ref(false)
const researchCandidatesError = ref('')
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
const analyticsResearchDraft = ref(null)
const branchResearchDraft = ref(readStoredBranchDraft())
let historyTimer
let readinessTimer
let industryTimer
let pollingTimer
let pollingGeneration = 0
let pollingSession = null
let toastTimer

const agentReady = computed(() => Boolean(capabilities.value?.provider?.available && capabilities.value?.capabilities?.some((item) => item.id === 'agent-runtime' && item.available)))
const providerLabel = computed(() => capabilities.value?.provider ? `${capabilities.value.provider.provider} / ${capabilities.value.provider.model}` : '等待管理员配置')
const quotaExhausted = computed(() => Boolean(usage.value && !usage.value.unlimited && Number(usage.value.remainingTokens) <= 0))
const currentRun = computed(() => activeRun.value || latestRun.value)
const researchTaskValidationError = computed(() => (
  !currentSession.value && !analyticsResearchDraft.value && !branchResearchDraft.value
    ? validateResearchTask(researchTask.value)
    : ''
))
const terminalSyncStatus = computed(() => (
  terminalSync.value && String(terminalSync.value.runId) === String(currentRun.value?.runId || '')
    ? terminalSync.value.status
    : ''
))
const branchCitationLabel = computed(() => {
  const citations = branchResearchDraft.value?.citations || []
  const sources = citations.map((item) => `#${item.sourceId}`).join('、')
  const version = branchResearchDraft.value?.evidenceVersion ? ` · 证据版本 ${branchResearchDraft.value.evidenceVersion}` : ''
  return `${citations.length} 条引用${sources ? `（来源 ${sources}）` : ''}${version}`
})
const workspaceTitle = computed(() => currentSession.value?.title || '新研究')
const workspaceStatus = computed(() => ({
  label: currentSession.value ? sessionStatusLabel.value : '本地草稿',
  ready: Boolean(agentReady.value),
}))
const evidenceRefreshKey = computed(() => currentRun.value?.runId
  ? `${currentRun.value.runId}:${currentRun.value.toolCallCount || 0}:${currentRun.value.status || ''}`
  : '')
const runActive = computed(() => Boolean(activeRun.value && !TERMINAL.has(activeRun.value.status)))
const sessionStatusLabel = computed(() => currentSession.value?.deletedAt ? '回收站' : currentSession.value?.status === 'archived' ? '已归档' : terminalSyncStatus.value ? '结果同步中' : runActive.value ? '研究进行中' : '当前会话')
const composerDisabledReason = computed(() => {
  if (terminalSyncStatus.value) return terminalSyncStatus.value === 'pending' ? '研究结果正在同步' : '研究结果尚未同步，请先同步结果'
  if (!agentReady.value) return '智能体暂不可用'
  if (forking.value) return '正在创建研究分支'
  if (currentSession.value?.deletedAt) return '回收站会话不能发送消息'
  if (currentSession.value?.status === 'archived') return '归档会话仅供查阅'
  if (runActive.value) return ''
  if (quotaExhausted.value) return '今日研究额度已用尽'
  if (!currentSession.value) {
    if (analyticsResearchDraft.value) return ''
    if (researchTaskValidationError.value) return researchTaskValidationError.value
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
onBeforeUnmount(() => { clearTimeout(historyTimer); clearTimeout(industryTimer); clearTimeout(readinessTimer); stopPolling(); clearTimeout(toastTimer) })
watch(profile, (value) => {
  if (!currentSession.value) localStorage.setItem(PROFILE_KEY, JSON.stringify(value))
}, { deep: true })
watch(evidenceDependencyKey, scheduleIndustryResolution)
watch(evidenceRefreshKey, loadCurrentEvidence)
watch(composer, (value) => {
  drafts.save(currentSession.value?.sessionId ?? null, value)
  if (!branchResearchDraft.value && requestedIntent.value !== 'auto' && value !== starterPrompt.value) {
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
    void loadResearchCandidates()
    await loadHistory(true)
    const incomingAnalyticsDraft = route.query.handoff === 'analytics'
      ? readAnalyticsResearchDraft(sessionStorage, USER_ID)
      : null
    const stored = Number(localStorage.getItem(SESSION_KEY))
    if (incomingAnalyticsDraft) {
      startNewResearch(null, { preserveAnalyticsHandoff: true })
    } else if (branchResearchDraft.value) {
      startNewResearch(null, { preserveBranchHandoff: true })
    } else if (stored > 0) {
      try { await loadSession(stored) } catch { localStorage.removeItem(SESSION_KEY); startNewResearch(null, { preserveAnalyticsHandoff: Boolean(incomingAnalyticsDraft) }) }
    } else if (historyItems.value[0]) {
      await loadSession(historyItems.value[0].sessionId)
    } else startNewResearch(null, { preserveAnalyticsHandoff: Boolean(incomingAnalyticsDraft) })
    if (incomingAnalyticsDraft) applyAnalyticsResearchHandoff(incomingAnalyticsDraft)
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
async function selectHistorySession(session) { discardAnalyticsResearchHandoff(); discardBranchResearchDraft(); await loadSession(session.sessionId); closeMobileHistory() }

function startNewResearch(profileSeed = null, { preserveAnalyticsHandoff = false, preserveBranchHandoff = false } = {}) {
  if (!preserveAnalyticsHandoff) discardAnalyticsResearchHandoff()
  if (!preserveBranchHandoff) discardBranchResearchDraft()
  sessionGate.begin()
  evidenceGate.begin()
  messagePageGate.begin()
  closeDrawer(false)
  saveCurrentDraft()
  stopPolling()
  terminalSync.value = null
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
  researchTask.value = preserveBranchHandoff
    ? taskDraftFromContext(branchResearchDraft.value?.taskContext)
    : createNewResearchTask()
  if (profileSeed) profile.value = { ...defaultProfile(), ...profileSeed }
  else profile.value = restoreNewProfile()
  composer.value = drafts.load(null)
  localStorage.removeItem(SESSION_KEY)
  mobileHistoryOpen.value = false
  nextTick(() => composerControl.value?.focus())
}
async function forkResearch() {
  const sourceRunId = currentRun.value?.runId
  const sourceSessionId = currentSession.value?.sessionId
  if (!sourceRunId || !sourceSessionId || forking.value) return
  const profileSeed = { ...profile.value }
  forking.value = true
  try {
    const material = normalizeBranchMaterial(await getResearchBranchMaterial(sourceRunId))
    if (String(currentSession.value?.sessionId) !== String(sourceSessionId)
        || String(currentRun.value?.runId) !== String(sourceRunId)) return
    if (!storeBranchResearchDraft(material)) {
      throw new Error('浏览器存储不可用，研究分支未创建')
    }
    branchResearchDraft.value = material
    startNewResearch(profileSeed, { preserveBranchHandoff: true })
    prefillQuestion({
      prompt: branchStarterPrompt(material),
      requestedIntent: material.taskContext?.taskType || material.requestedIntent || 'auto',
    })
    showToast('已创建独立的本地研究分支，确认目标后发送。')
  } catch (error) {
    showToast(error.message || '研究分支创建失败')
  } finally {
    forking.value = false
  }
}
function restartReportResearch(report) {
  startNewResearch({ ...profile.value })
  prefillQuestion(`请基于当前可用的已核验证据，重新研究并核验报告“${String(report?.title || '').slice(0, 120)}”中的关键结论；说明哪些来源或数据版本发生了变化。`)
  showToast('已创建新的本地研究草稿，确认范围后发送即可开始。')
}
function applyResearchPreferences(preferences) {
  if (currentSession.value) {
    showToast('本次研究条件已固定；请新建研究后再应用长期偏好。')
    return
  }
  const region = regions.value.find((item) => samePreferenceValue(item.name, preferences.commonRegion))
  const industry = industries.value.find((item) => samePreferenceValue(item.name, preferences.commonIndustry))
  const nextProfile = { ...profile.value }
  if (region) nextProfile.regionId = String(region.id)
  if (preferences.commonIndustry?.trim()) {
    nextProfile.industry = preferences.commonIndustry.trim()
    nextProfile.industryTagId = industry ? String(industry.tagId) : ''
  }
  if (['idea', 'validation', 'early_operation', 'growth'].includes(preferences.ventureStage)) nextProfile.stage = preferences.ventureStage
  if (['under_100k', '100k_500k', '500k_1m', 'over_1m', 'undecided'].includes(preferences.budgetRange)) nextProfile.budgetRange = preferences.budgetRange
  if (preferences.existingResources?.trim()) nextProfile.existingResources = preferences.existingResources.trim()
  profile.value = nextProfile
  showToast(region || industry ? '长期偏好已填入新的本地研究草稿。' : '已应用可匹配的长期偏好；请确认地区和行业。')
}

function discardBranchResearchDraft() {
  branchResearchDraft.value = null
  try { sessionStorage.removeItem(BRANCH_DRAFT_KEY) } catch { /* Storage denial is already a local-only failure. */ }
  if (!currentSession.value) {
    requestedIntent.value = 'auto'
    starterPrompt.value = ''
  }
}

function completeBranchResearchDraft() {
  branchResearchDraft.value = null
  try { sessionStorage.removeItem(BRANCH_DRAFT_KEY) } catch { /* No server state depends on this cleanup. */ }
}

function applyAnalyticsResearchHandoff(draft) {
  startNewResearch(null, { preserveAnalyticsHandoff: true })
  analyticsResearchDraft.value = draft
  prefillQuestion({ prompt: draft.userQuestion, requestedIntent: 'general_research' })
  showToast('数据看板条件已带入，确认问题后发送。')
}

function discardAnalyticsResearchHandoff() {
  analyticsResearchDraft.value = null
  clearAnalyticsResearchDraft(sessionStorage, USER_ID)
  clearAnalyticsHandoffRouteMarker()
}

function completeAnalyticsResearchHandoff() {
  analyticsResearchDraft.value = null
  clearAnalyticsResearchDraft(sessionStorage, USER_ID)
  clearAnalyticsHandoffRouteMarker()
}

function clearAnalyticsHandoffRouteMarker() {
  if (route.query.handoff !== 'analytics') return
  const query = { ...route.query }
  delete query.handoff
  router.replace({ query }).catch(() => {})
}

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
  terminalSync.value = null
  composerError.value = ''
  const detail = await getResearchSession(sessionId)
  if (!sessionGate.isCurrent(requestId)) return false
  currentSession.value = detail.session
  researchTask.value = taskDraftFromContext(detail.session?.taskContext)
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
      const analyticsDraft = analyticsResearchDraft.value
      const branchDraft = branchResearchDraft.value
      const startProfile = serializeProfile(profile.value)
      const localTaskContext = analyticsDraft ? null : buildResearchTaskContext(researchTask.value)
      const taskContext = branchDraft?.taskContext || localTaskContext
      const effectiveIntent = branchDraft?.taskContext?.taskType || branchDraft?.requestedIntent || taskContext?.taskType || requestedIntent.value
      const fingerprint = createCanonicalFingerprint(analyticsDraft
        ? { analytics: { metricId: analyticsDraft.metricId, dataVersion: analyticsDraft.dataVersion, filters: analyticsDraft.filters, selectedBucketIds: analyticsDraft.selectedBucketIds }, content }
        : { profile: startProfile, content, requestedIntent: effectiveIntent, taskContext })
      const pending = drafts.loadPendingStart()
      const idempotencyKey = pending?.fingerprint === fingerprint ? pending.idempotencyKey : newIdempotencyKey()
      drafts.savePendingStart({ idempotencyKey, fingerprint })
      try {
        receipt = analyticsDraft
          ? await startResearchFromAnalytics({
            metricId: analyticsDraft.metricId,
            filters: analyticsDraft.filters,
            selectedBucketIds: analyticsDraft.selectedBucketIds,
            dataVersion: analyticsDraft.dataVersion,
            userQuestion: content,
            idempotencyKey,
          })
          : await startResearchSession({
            profile: startProfile,
            content,
            requestedIntent: effectiveIntent,
            idempotencyKey,
            ...(taskContext ? { taskContext } : {}),
          })
        drafts.clearPendingStart()
        if (analyticsDraft) completeAnalyticsResearchHandoff()
        if (branchDraft) completeBranchResearchDraft()
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
    if (sessionGate.isCurrent(selectionRequestId)) {
      composerError.value = error?.diagnosticCode === 'ANALYTICS_DATA_VERSION_STALE'
        ? '数据版本已更新，请返回数据看板刷新后重新带入研究。'
        : error.message || '研究请求提交失败；当前会话已保留，可以重试。'
    }
    drafts.save(draftSessionId, content)
  } finally { submitting.value = false }
}
function prefillQuestion(value) {
  const payload = typeof value === 'string' ? { prompt: value, requestedIntent: 'auto' } : value
  requestedIntent.value = payload?.requestedIntent || 'auto'
  if (!currentSession.value && !branchResearchDraft.value && !analyticsResearchDraft.value && PHASE_THREE_TASK_TYPES.has(requestedIntent.value)) {
    researchTask.value = normalizeResearchTask({ ...researchTask.value, taskType: requestedIntent.value })
  }
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
  const active = activeRun.value
  if (!active || String(active.runId) !== String(runId)) return
  stopPolling()
  const session = {
    runId,
    sessionId: active.sessionId || currentSession.value?.sessionId,
    generation: ++pollingGeneration,
    serverDeadline: parseRunDeadline(active.deadlineAt),
    localDeadline: Date.now() + LOCAL_POLLING_MAX_WAIT_MS,
    lastKnownStatus: active.status,
    lastKnownRun: active,
    retryCount: 0,
    requestSequence: 0,
    timer: undefined,
    deadlineFinalFetchIssued: false,
  }
  pollingSession = session
  networkStatus.value = 'connected'
  schedulePolling(session, initialDelay)
}

function schedulePolling(session, delay) {
  if (!isCurrentPollingSession(session)) return
  clearPollingTimer(session)
  session.timer = window.setTimeout(() => {
    session.timer = undefined
    pollingTimer = undefined
    pollResearchRun(session)
  }, Math.max(0, Number(delay) || 0))
  pollingTimer = session.timer
}

function clearPollingTimer(session = pollingSession) {
  if (session?.timer != null) clearTimeout(session.timer)
  if (!session || session === pollingSession) clearTimeout(pollingTimer)
  if (session) session.timer = undefined
  pollingTimer = undefined
}

function isCurrentPollingSession(session, requestSequence = null) {
  if (!session || pollingSession !== session || session.generation !== pollingGeneration) return false
  if (String(currentSession.value?.sessionId || '') !== String(session.sessionId || '')) return false
  return requestSequence == null || requestSequence === session.requestSequence
}

function parseRunDeadline(value) {
  const timestamp = Date.parse(String(value || ''))
  return Number.isFinite(timestamp) ? timestamp : null
}

function pollingDeadlineReached(session) {
  return Date.now() >= (session.serverDeadline || session.localDeadline)
}

function nextPollingDelay(session) {
  const deadline = session.serverDeadline || session.localDeadline
  return Math.min(POLLING_INTERVAL_MS, Math.max(0, deadline - Date.now()))
}

async function pollResearchRun(session) {
  if (!isCurrentPollingSession(session)) return
  const finalDeadlineFetch = pollingDeadlineReached(session)
  if (finalDeadlineFetch && session.deadlineFinalFetchIssued) {
    pausePollingAtDeadline(session)
    return
  }
  if (finalDeadlineFetch) session.deadlineFinalFetchIssued = true
  const requestSequence = ++session.requestSequence
  try {
    const run = await getResearchRun(session.runId)
    if (!isCurrentPollingSession(session, requestSequence) || String(run?.runId) !== String(session.runId)) return
    session.lastKnownRun = run
    session.lastKnownStatus = run.status
    const serverDeadline = parseRunDeadline(run.deadlineAt)
    if (serverDeadline != null) session.serverDeadline = serverDeadline
    session.retryCount = 0
    networkStatus.value = 'connected'
    if (TERMINAL.has(run.status)) {
      await applyTerminalPollingResult(session, requestSequence, run)
      return
    }
    activeRun.value = run
    if (finalDeadlineFetch || (pollingDeadlineReached(session) && session.deadlineFinalFetchIssued)) {
      pausePollingAtDeadline(session)
      return
    }
    schedulePolling(session, nextPollingDelay(session))
  } catch {
    if (!isCurrentPollingSession(session, requestSequence)) return
    if (finalDeadlineFetch) {
      pausePollingAtDeadline(session)
      return
    }
    session.retryCount += 1
    if (session.retryCount > POLLING_RETRY_DELAYS_MS.length) {
      clearPollingTimer(session)
      networkStatus.value = 'paused'
      return
    }
    networkStatus.value = 'recovering'
    schedulePolling(session, POLLING_RETRY_DELAYS_MS[session.retryCount - 1])
  }
}

function pausePollingAtDeadline(session) {
  clearPollingTimer(session)
  networkStatus.value = session.serverDeadline == null ? 'deadline_unknown' : 'settling'
}

async function applyTerminalPollingResult(session, requestSequence, run) {
  if (!isCurrentPollingSession(session, requestSequence)) return
  activeRun.value = null
  latestRun.value = run
  clearPollingTimer(session)
  networkStatus.value = 'connected'
  const followIncoming = conversation.value?.isNearBottom() ?? true
  terminalSync.value = { runId: run.runId, sessionId: run.sessionId, status: 'pending' }
  try {
    const latest = await getResearchSession(run.sessionId)
    if (!isCurrentTerminalSync(session, requestSequence, run)) return
    messages.value = latest.messages || messages.value
    nextBeforeSequence.value = latest.nextBeforeSequence ?? null
    hasMoreMessages.value = Boolean(latest.hasMoreMessages)
    currentSession.value = latest.session
    activeRun.value = null
    latestRun.value = run
    usage.value = await getResearchUsage().catch(() => usage.value)
    await loadHistory(true).catch(() => {})
    if (!isCurrentTerminalSync(session, requestSequence, run)) return
    terminalSync.value = null
    pollingSession = null
    await conversation.value?.applyIncoming(followIncoming, 'smooth')
  } catch {
    if (!isCurrentTerminalSync(session, requestSequence, run)) return
    terminalSync.value = { runId: run.runId, sessionId: run.sessionId, status: 'failed' }
  }
}

function isCurrentTerminalSync(session, requestSequence, run) {
  return isCurrentPollingSession(session, requestSequence)
    && String(terminalSync.value?.runId || '') === String(run?.runId || '')
    && String(terminalSync.value?.sessionId || '') === String(session.sessionId || '')
}

async function resumePolling() {
  const sync = terminalSync.value
  if (sync?.status === 'failed') {
    const session = pollingSession
    if (!session || String(session.runId) !== String(sync.runId) || !isCurrentPollingSession(session)) return
    terminalSync.value = { ...sync, status: 'pending' }
    const requestSequence = ++session.requestSequence
    try {
      const run = await getResearchRun(session.runId)
      if (!isCurrentTerminalSync(session, requestSequence, run) || String(run?.runId) !== String(session.runId)) return
      session.lastKnownRun = run
      session.lastKnownStatus = run.status
      const serverDeadline = parseRunDeadline(run.deadlineAt)
      if (serverDeadline != null) session.serverDeadline = serverDeadline
      if (!TERMINAL.has(run.status)) {
        terminalSync.value = null
        latestRun.value = null
        activeRun.value = run
        schedulePolling(session, nextPollingDelay(session))
        return
      }
      await applyTerminalPollingResult(session, requestSequence, run)
    } catch {
      if (isCurrentTerminalSync(session, requestSequence, sync)) terminalSync.value = { ...sync, status: 'failed' }
    }
    return
  }
  if (activeRun.value?.runId) startPolling(activeRun.value.runId, 0)
}
function stopPolling() {
  clearPollingTimer(pollingSession)
  pollingSession = null
  pollingGeneration += 1
  networkStatus.value = 'connected'
}
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

async function loadResearchCandidates() {
  researchCandidatesLoading.value = true
  researchCandidatesError.value = ''
  const [caseResult, sourceResult] = await Promise.allSettled([getCases(), getSources()])
  if (caseResult.status === 'fulfilled') researchCaseOptions.value = normalizeResearchCandidates(caseResult.value, 'case')
  if (sourceResult.status === 'fulfilled') researchSourceOptions.value = normalizeResearchCandidates(sourceResult.value, 'source')
  if (caseResult.status === 'rejected' || sourceResult.status === 'rejected') {
    researchCandidatesError.value = '已核验资料暂时无法完整读取，已保留可用选择；稍后可刷新页面重试。'
  }
  researchCandidatesLoading.value = false
}

function normalizeResearchCandidates(value, type) {
  const rows = Array.isArray(value) ? value : Array.isArray(value?.items) ? value.items : []
  const seen = new Set()
  return rows.flatMap((item) => {
    const id = Number(item?.id)
    if (!Number.isSafeInteger(id) || id <= 0 || seen.has(id)) return []
    seen.add(id)
    return [{
      id,
      title: String(item?.title || '').trim() || `${type === 'case' ? '案例' : '来源'} #${id}`,
      regionName: String(item?.regionName || '').trim(),
      publisher: String(item?.publisher || '').trim(),
    }]
  })
}

function createNewResearchTask() {
  return normalizeResearchTask({ taskType: 'general_research', outputDepth: 'standard' })
}

function emptyResearchTask() {
  return { taskType: '', caseIds: [], comparisonDimensions: [], sourceId: '', technologyText: '', outputDepth: 'standard' }
}

function taskDraftFromContext(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return emptyResearchTask()
  return normalizeResearchTask(value)
}

function normalizeResearchTask(value = {}) {
  const taskType = PHASE_THREE_TASK_TYPES.has(value.taskType) ? value.taskType : ''
  const caseIds = [...new Set(Array.isArray(value.caseIds) ? value.caseIds.map(Number).filter(Number.isSafeInteger) : [])].slice(0, 3)
  const allowedDimensions = new Set(['businessModel', 'technicalPath', 'targetCustomer', 'outcome', 'regionalContext', 'evidenceStrength'])
  const comparisonDimensions = [...new Set(Array.isArray(value.comparisonDimensions) ? value.comparisonDimensions.filter((item) => allowedDimensions.has(item)) : [])].slice(0, 3)
  const sourceId = Number(value.sourceId)
  return {
    taskType,
    caseIds: ['case_analysis', 'case_comparison'].includes(taskType) ? caseIds : [],
    comparisonDimensions: taskType === 'case_comparison' ? comparisonDimensions : [],
    sourceId: taskType === 'source_verification' && Number.isSafeInteger(sourceId) && sourceId > 0 ? sourceId : '',
    technologyText: taskType === 'technology_assessment' ? String(value.technologyText || '').trim().slice(0, 120) : '',
    outputDepth: ['concise', 'standard', 'deep'].includes(value.outputDepth) ? value.outputDepth : 'standard',
  }
}

function validateResearchTask(value) {
  const task = normalizeResearchTask(value)
  if (!task.taskType) return '请选择研究任务类型'
  if (task.taskType === 'case_analysis' && task.caseIds.length !== 1) return '单案例分析需要选择一个已核验案例'
  if (task.taskType === 'case_comparison' && task.caseIds.length < 2) return '多案例比较需要选择 2-3 个已核验案例'
  if (task.taskType === 'case_comparison' && !task.comparisonDimensions.length) return '多案例比较至少需要选择一个比较维度'
  if (task.taskType === 'technology_assessment' && !task.technologyText) return '技术路线评估需要填写技术方向'
  return ''
}

function buildResearchTaskContext(value) {
  if (validateResearchTask(value)) return null
  const task = normalizeResearchTask(value)
  const context = {
    version: 'phase3-task-v1',
    taskType: task.taskType,
    caseIds: task.caseIds,
    comparisonDimensions: task.comparisonDimensions,
    outputDepth: task.outputDepth,
  }
  if (task.technologyText) context.technologyText = task.technologyText
  if (task.sourceId) context.sourceId = task.sourceId
  return context
}

function normalizeBranchMaterial(value = {}) {
  const sourceSessionId = Number(value.sourceSessionId)
  const sourceRunId = Number(value.sourceRunId)
  const resultSummary = branchText(value.resultSummary, 2000)
  if (sourceSessionId <= 0 || sourceRunId <= 0 || !resultSummary) throw new Error('研究分支材料无效')
  const taskContext = value.taskContext && typeof value.taskContext === 'object' && !Array.isArray(value.taskContext)
    ? JSON.parse(JSON.stringify(value.taskContext)) : null
  const citations = []
  const sourceIds = new Set()
  for (const item of Array.isArray(value.citations) ? value.citations : []) {
    const sourceId = Number(item?.sourceId)
    const claim = branchText(item?.claim, 300)
    if (sourceId <= 0 || !claim || sourceIds.has(sourceId) || citations.length >= 12) continue
    sourceIds.add(sourceId)
    citations.push({ sourceId, claim })
  }
  return {
    version: 1,
    sourceSessionId,
    sourceRunId,
    requestedIntent: branchText(value.requestedIntent, 40) || taskContext?.taskType || 'auto',
    taskContext,
    taskContextVersion: branchText(value.taskContextVersion, 40),
    taskContextHash: branchText(value.taskContextHash, 128),
    resultSummary,
    citations,
    evidenceVersion: branchText(value.evidenceVersion, 160),
  }
}
function readStoredBranchDraft() {
  try {
    const value = JSON.parse(sessionStorage.getItem(BRANCH_DRAFT_KEY) || 'null')
    return value ? normalizeBranchMaterial(value) : null
  } catch {
    try { sessionStorage.removeItem(BRANCH_DRAFT_KEY) } catch { /* Ignore an unavailable local store. */ }
    return null
  }
}
function storeBranchResearchDraft(value) {
  try {
    const serialized = JSON.stringify(value)
    if (serialized.length > 20000) return false
    sessionStorage.setItem(BRANCH_DRAFT_KEY, serialized)
    return true
  } catch {
    return false
  }
}
function branchStarterPrompt(value) {
  const boundary = value.citations.slice(0, 6)
    .map((item) => `来源 #${item.sourceId}：${branchText(item.claim, 120)}`)
    .join('；')
  return [
    '请基于以下已完成研究创建独立研究分支，并重新核验后再形成事实结论。',
    `已有结果摘要：${branchText(value.resultSummary, 900)}`,
    `原研究引用边界：${boundary || '无可复制引用，请重新检索证据'}`,
    '本分支需要调整或继续研究的方向：',
  ].join('\n').slice(0, 2000)
}
function branchText(value, maxLength) {
  return String(value || '').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, '').trim().slice(0, maxLength)
}
function saveCurrentDraft() { drafts.save(currentSession.value?.sessionId ?? null, composer.value) }
function serializeProfile(value) { return { ventureType: value.ventureType, regionId: value.regionId ? Number(value.regionId) : undefined, industryTagId: value.industryTagId ? Number(value.industryTagId) : undefined, industry: value.industry || undefined, stage: value.stage, budgetRange: value.budgetRange, goal: value.goal || undefined, resources: value.existingResources || undefined } }
function normalizeProfile(value = {}) { return { ...defaultProfile(), ...value, regionId: value.regionId == null ? '' : String(value.regionId), industryTagId: value.industryTagId == null ? '' : String(value.industryTagId), existingResources: value.resources || value.existingResources || '' } }
function samePreferenceValue(left, right) { return String(left || '').trim().toLocaleLowerCase() === String(right || '').trim().toLocaleLowerCase() }
function restoreNewProfile() { try { return normalizeProfile(JSON.parse(localStorage.getItem(PROFILE_KEY) || '{}')) } catch { localStorage.removeItem(PROFILE_KEY); return defaultProfile() } }
function mergeBySession(current, incoming) { const rows = new Map(current.map((item) => [String(item.sessionId), item])); incoming.forEach((item) => rows.set(String(item.sessionId), item)); return [...rows.values()] }
function newIdempotencyKey() { return globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID().replaceAll('-', '') : `agent_${Date.now()}_${Math.random().toString(36).slice(2)}` }
function showToast(message) { toast.value = message; clearTimeout(toastTimer); toastTimer = window.setTimeout(() => { toast.value = '' }, 2200) }
</script>

<style scoped>
.assistant-page{width:100%;height:100%;min-width:0;min-height:0;color:#20251f;overflow:hidden}.assistant-workspace{display:grid;grid-template-columns:276px minmax(0,1fr);width:100%;height:100%;min-width:0;min-height:0;background:#fbfbf7;overflow:hidden}.assistant-workspace.history-collapsed{grid-template-columns:64px minmax(0,1fr)}.research-desk{display:flex;flex-direction:column;width:100%;height:100%;min-width:0;min-height:0;background:#fbfbf7;overflow:hidden}.desk-notice{display:flex;align-items:flex-start;gap:10px;padding:11px 24px;border-bottom:1px solid #d7c7c2;background:#f7efec;color:#6f3b35;flex:0 0 auto}.desk-notice strong{font-size:.78rem}.desk-notice p{margin:2px 0 0;font-size:.7rem}.analytics-research-context{display:flex;flex-wrap:wrap;align-items:center;gap:6px 10px;margin:0;padding:8px max(24px,calc((100% - 880px)/2));border-bottom:1px solid #b9c3b9;background:#eff3ed;color:#36513d;font-size:.7rem;flex:0 0 auto}.analytics-research-context span{font-weight:700}.analytics-research-context strong{color:#242a24;overflow-wrap:anywhere}.analytics-research-context small{overflow-wrap:anywhere;color:#566056}.branch-research-context{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:6px 12px;margin:0;padding:10px max(24px,calc((100% - 880px)/2));border-bottom:1px solid #c4c7c1;background:#f3f3ee;color:#343a34;flex:0 0 auto}.branch-research-context>div{display:flex;flex-wrap:wrap;align-items:baseline;gap:5px 10px;min-width:0}.branch-research-context span{font-size:.68rem;font-weight:700}.branch-research-context strong,.branch-research-context small,.branch-research-context p{overflow-wrap:anywhere}.branch-research-context small{color:#626962;font-size:.68rem}.branch-research-context p{grid-column:1;margin:0;font-size:.72rem;line-height:1.55}.branch-research-context button{grid-column:2;grid-row:1/3;align-self:center;width:44px;height:44px;border:1px solid #b8beb7;border-radius:3px;background:#fbfbf7;color:#3d443d}.branch-research-context button:is(:hover,:focus-visible){border-color:#747b74;background:#e9ebe5}.branch-research-context button:focus-visible{outline:2px solid rgba(74,82,74,.34);outline-offset:2px}.composer-error{margin:0;padding:8px max(24px,calc((100% - 880px)/2));border-top:1px solid #d9c0ba;background:#f8efec;color:#703731;font-size:.72rem;flex:0 0 auto}.page-state{display:flex;align-items:center;justify-content:center;gap:16px;min-height:100%;padding:35px;color:#687068}.page-state p{margin:4px 0 0}.page-state.is-error{color:#7b3f36}.spinner{width:22px;height:22px;border:2px solid #c5cac3;border-top-color:#304e38;border-radius:50%;animation:spin .8s linear infinite}.secondary-command{display:flex;align-items:center;gap:7px;min-height:42px;padding:0 13px;border:1px solid #bfc5bd;border-radius:3px;background:#fff;color:#303630}.secondary-command:is(:hover,:focus-visible){border-color:#747b74;background:#f0f1ec}.secondary-command:focus-visible{outline:2px solid rgba(74,82,74,.34);outline-offset:2px}.secondary-command:active{background:#e5e7e1}.assistant-toast{position:fixed;z-index:120;right:22px;bottom:22px;max-width:min(360px,calc(100vw - 28px));margin:0;padding:11px 14px;border:1px solid #afb5ae;border-radius:3px;background:#282d28;color:#fff;font-size:.75rem}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:840px){.assistant-workspace,.assistant-workspace.history-collapsed{grid-template-columns:1fr;height:100%;min-height:0}}@media(max-width:600px){.assistant-workspace,.assistant-workspace.history-collapsed{height:100%}.desk-notice,.analytics-research-context,.branch-research-context{padding:10px 14px}.composer-error{padding:8px 14px}.assistant-toast{right:14px;bottom:14px}}@media(prefers-reduced-motion:reduce){.spinner{animation:none}}
</style>
