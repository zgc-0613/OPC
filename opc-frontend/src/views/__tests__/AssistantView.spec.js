import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'

const api = vi.hoisted(() => ({
  archive: vi.fn(), cancel: vi.fn(), checkReadiness: vi.fn(), create: vi.fn(), getCapabilities: vi.fn(),
  getBranch: vi.fn(), getEvidence: vi.fn(), getFeedback: vi.fn(), getHistory: vi.fn(), getMessages: vi.fn(), getRun: vi.fn(), getSession: vi.fn(), getUsage: vi.fn(), updateFeedback: vi.fn(),
  getReports: vi.fn(), exportReport: vi.fn(), saveReport: vi.fn(), updateReport: vi.fn(), trashReport: vi.fn(), restoreReport: vi.fn(), permanentReport: vi.fn(), startAnalytics: vi.fn(),
  clearPreferences: vi.fn(), getPreferences: vi.fn(), purge: vi.fn(), resolveIndustry: vi.fn(), restore: vi.fn(), send: vi.fn(), start: vi.fn(), trash: vi.fn(), unarchive: vi.fn(), update: vi.fn(), updatePreferences: vi.fn(),
  getRegions: vi.fn(), getIndustryTags: vi.fn(), getTags: vi.fn(), getCases: vi.fn(), getSources: vi.fn(),
}))
const auth = vi.hoisted(() => ({ profile: { userId: 42, username: 'researcher' } }))
const route = vi.hoisted(() => ({ query: {} }))
const router = vi.hoisted(() => ({ replace: vi.fn().mockResolvedValue() }))

vi.mock('@/api/ai', () => ({
  archiveResearchSessionExplicit: api.archive, cancelResearchRun: api.cancel,
  checkEntrepreneurshipReadiness: api.checkReadiness, createResearchSession: api.create,
  getAiCapabilities: api.getCapabilities, getResearchHistory: api.getHistory,
  getResearchBranchMaterial: api.getBranch,
  getResearchMessages: api.getMessages, getResearchRun: api.getRun, getResearchSession: api.getSession,
  getResearchRunEvidence: api.getEvidence,
  getResearchRunFeedback: api.getFeedback, updateResearchRunFeedback: api.updateFeedback,
  exportResearchReport: api.exportReport, getResearchReports: api.getReports,
  clearResearchPreferences: api.clearPreferences, getResearchPreferences: api.getPreferences,
  getResearchUsage: api.getUsage, permanentlyDeleteResearchSession: api.purge,
  restoreResearchSession: api.restore, sendResearchMessage: api.send,
  restoreResearchReport: api.restoreReport, saveResearchReport: api.saveReport,
  resolveIndustryWithAi: api.resolveIndustry,
  startResearchSession: api.start,
  startResearchFromAnalytics: api.startAnalytics,
  trashResearchSession: api.trash, unarchiveResearchSession: api.unarchive,
  trashResearchReport: api.trashReport, permanentlyDeleteResearchReport: api.permanentReport,
  updateResearchSession: api.update,
  updateResearchReport: api.updateReport,
  updateResearchPreferences: api.updatePreferences,
}))
vi.mock('@/api/region', () => ({ getRegions: api.getRegions }))
vi.mock('@/api/tag', () => ({ getIndustryTags: api.getIndustryTags, getTags: api.getTags }))
vi.mock('@/api/case', () => ({ getCases: api.getCases }))
vi.mock('@/api/source', () => ({ getSources: api.getSources }))
vi.mock('@/api/auth', () => ({ getUserProfile: () => auth.profile }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => router }))

import AssistantView from '@/views/AssistantView.vue'
import assistantSource from '@/views/AssistantView.vue?raw'
import profileSource from '@/components/assistant/AssistantResearchProfile.vue?raw'

const session = (overrides = {}) => ({
  sessionId: 101, title: '湖北人工智能研究', titleMode: 'auto', status: 'active', profile: {},
  pinned: false, createdAt: '2026-07-25T09:00:00', updatedAt: '2026-07-25T10:00:00',
  lastMessageAt: '2026-07-25T10:00:00', ...overrides,
})
const activeRun = {
  runId: 301, sessionId: 101, status: 'running', currentStage: 'tool_running',
  visibleProgress: '正在检索政策', stepCount: 1, toolCallCount: 1, tools: [],
  tokenUsage: { promptTokens: 12, completionTokens: 3, totalTokens: 15 },
}
const SELECTED_SESSION_KEY = 'opc_assistant:user:42:selected-session'
const detail = (overrides = {}) => ({
  session: session(), messages: [], nextBeforeSequence: null, hasMoreMessages: false,
  activeRun: null, latestRun: null, ...overrides,
})
const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}
const latestEventValue = (wrapper, name) => wrapper.emitted(name)?.at(-1)?.[0]
const openConditions = async (wrapper) => {
  const existing = wrapper.find('.assistant-inspector')
  if (!existing.exists()) {
    const starterTrigger = wrapper.find('[data-testid="open-research-conditions"]')
    const sessionTrigger = wrapper.find('[data-testid="session-open-research-conditions"]')
    await (starterTrigger.exists() ? starterTrigger : sessionTrigger).trigger('click')
    await nextTick()
  }
  return wrapper.get('.assistant-inspector')
}
const starterForm = (wrapper) => wrapper.get('.research-starter form')

describe('AssistantView research workspace', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    route.query = {}
    document.body.innerHTML = ''
    Element.prototype.scrollTo = vi.fn()
    window.matchMedia = vi.fn(() => ({ matches: false }))
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: vi.fn().mockResolvedValue() } })

    api.getRegions.mockResolvedValue([{ id: 42, name: '湖北省' }])
    api.getIndustryTags.mockResolvedValue([{ tagId: 7, name: '人工智能应用' }])
    api.getCapabilities.mockResolvedValue({ provider: { available: true, provider: 'deepseek', model: 'deepseek-v4-flash' }, capabilities: [{ id: 'agent-runtime', available: true }] })
    api.getUsage.mockResolvedValue({ usedTokens: 120, limitTokens: 10000, remainingTokens: 9880, unlimited: false })
    api.getPreferences.mockResolvedValue(null)
    api.getCases.mockResolvedValue([
      { id: 11, title: 'Verified case A', regionName: 'Region' },
      { id: 12, title: 'Verified case B', regionName: 'Region' },
    ])
    api.getSources.mockResolvedValue([{ id: 71, title: 'Verified source', publisher: 'Public publisher' }])
    api.getTags.mockResolvedValue([{ id: 91, name: 'Verified technology tag', tagType: 'technology' }])
    api.getHistory.mockResolvedValue({ items: [session()], nextCursor: null, hasMore: false })
    api.getReports.mockResolvedValue([])
    api.getSession.mockResolvedValue(detail())
    api.getRun.mockResolvedValue(activeRun)
    api.getEvidence.mockResolvedValue({ runId: 301, status: 'completed', items: [], groups: {} })
    api.getFeedback.mockResolvedValue(null)
    api.getBranch.mockResolvedValue({
      sourceSessionId: 101,
      sourceRunId: 301,
      requestedIntent: 'case_comparison',
      taskContext: {
        version: 'phase3-task-v1', taskType: 'case_comparison', caseIds: [11, 12],
        comparisonDimensions: ['businessModel'], outputDepth: 'standard',
      },
      taskContextVersion: 'phase3-task-v1',
      taskContextHash: 'frozen-task-context',
      resultSummary: '两个已核验案例采用不同的获客路径。',
      citations: [{ sourceId: 8, claim: '案例一采用渠道合作' }, { sourceId: 9, claim: '案例二采用直接销售' }],
      evidenceVersion: 'evidence-v7',
    })
    api.checkReadiness.mockResolvedValue({ readinessStatus: 'sufficient', verifiedCaseCount: 2, selectedPolicyCount: 3, verifiedSourceCount: 4 })
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('keeps the Prisma Light workspace free of forbidden visual treatments', () => {
    expect(assistantSource).not.toMatch(/linear-gradient|radial-gradient|backdrop-filter|border-left:\s*[2-9]px/i)
    expect(assistantSource).not.toMatch(/box-shadow:\s*0\s+\d{2,}px/i)
  })

  it('leaves the route layout as the only topbar and uses an open workspace surface', () => {
    expect(assistantSource).not.toContain('class="desk-header"')
    expect(assistantSource).not.toContain('id="assistant-title"')
    expect(assistantSource).not.toMatch(/\.assistant-workspace\{[^}]*border:\s*1px\s+solid/)
  })

  it('adapts the research profile to its desk container rather than the viewport', () => {
    expect(profileSource).toMatch(/container-type:\s*inline-size/)
    expect(profileSource).toMatch(/@container\s+research-profile/)
    expect(profileSource).not.toMatch(/repeat\(5\s*,/)
    expect(profileSource).toMatch(/grid-template-columns:\s*repeat\(6\s*,\s*minmax\(0\s*,\s*1fr\)\)/)
    expect(profileSource).toMatch(/\.field-venture.*\.field-region.*\.field-industry.*grid-column:span 2/)
    expect(profileSource).toMatch(/\.field-stage.*\.field-budget.*\.field-goal.*\.field-resources.*grid-column:span 3/)
    expect(profileSource).toMatch(/min-width:521px.*max-width:680px/)
    expect(profileSource).toMatch(/max-height:\s*min\(48dvh\s*,\s*420px\)/)
    expect(profileSource).toMatch(/overflow:\s*auto/)
    expect(assistantSource).toMatch(/\.research-desk\{[^}]*overflow:hidden/)
  })

  it('starts a new mobile research with the editable profile collapsed', async () => {
    window.matchMedia = vi.fn((query) => ({ matches: query === '(max-width: 720px)' }))
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })

    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(wrapper.find('.assistant-inspector').exists()).toBe(false)
    expect(wrapper.get('textarea[aria-label="研究问题"]').exists()).toBe(true)
    const inspector = await openConditions(wrapper)
    expect(inspector.get('details.profile-editor').attributes('open')).toBeUndefined()
    wrapper.unmount()
  })

  it.each([
    ['case_analysis', async (wrapper) => {
      await wrapper.get('[data-testid="task-case-selector"]').setValue('11')
    }, { caseIds: [11], comparisonDimensions: [] }],
    ['case_comparison', async (wrapper) => {
      await wrapper.get('[data-testid="task-case-selector"]').setValue(['11', '12'])
      await wrapper.get('input[value="businessModel"]').setValue(true)
    }, { caseIds: [11, 12], comparisonDimensions: ['businessModel'] }],
    ['technology_assessment', async (wrapper) => {
      await wrapper.get('[data-testid="task-technology-text"]').setValue('controlled technology')
    }, { technologyText: 'controlled technology' }],
    ['policy_lookup', async () => {}, { caseIds: [], comparisonDimensions: [] }],
    ['source_verification', async (wrapper) => {
      await wrapper.get('[data-testid="task-source-selector"]').setValue('71')
    }, { sourceId: 71 }],
    ['general_research', async () => {}, { caseIds: [], comparisonDimensions: [] }],
  ])('starts %s with a controlled Phase Three task context only after send', async (taskType, configure, expectedContext) => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.getRegions.mockResolvedValue([{ id: 42, name: 'Region' }])
    api.getIndustryTags.mockResolvedValue([{ tagId: 7, name: 'AI' }])
    api.getSession.mockResolvedValue(detail({ session: session({ sessionId: 202 }) }))
    api.start.mockResolvedValue({ session: session({ sessionId: 202 }), messageId: 402, runId: 502, status: 'received' })

    const wrapper = mount(AssistantView)
    await flushPromises()
    const inspector = await openConditions(wrapper)
    await inspector.findAll('.profile-fields select')[1].setValue('42')
    await inspector.get('[role="combobox"]').setValue('AI')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()

    expect(api.start).not.toHaveBeenCalled()
    await inspector.get(`[data-testid="research-task-${taskType}"]`).trigger('click')
    await configure(wrapper)
    await wrapper.get('textarea[aria-label="研究问题"]').setValue(`verify ${taskType} controlled research task`)
    await starterForm(wrapper).trigger('submit')
    await flushPromises()

    expect(api.start).toHaveBeenCalledWith(expect.objectContaining({
      requestedIntent: taskType,
      taskContext: expect.objectContaining({
        version: 'phase3-task-v1',
        taskType,
        outputDepth: 'standard',
        ...expectedContext,
      }),
      idempotencyKey: expect.any(String),
    }))
    wrapper.unmount()
  })

  it('submits and restores every technology assessment condition without creating a second run', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.getRegions.mockResolvedValue([{ id: 42, name: 'Region' }])
    api.getIndustryTags.mockResolvedValue([{ tagId: 7, name: 'AI' }])
    api.getTags.mockResolvedValue([{ id: 91, name: '检索增强生成', tagType: 'technology' }])
    const createdSession = session({ sessionId: 212, taskContext: {
      version: 'phase3-task-v1', taskType: 'technology_assessment', caseIds: [], comparisonDimensions: [],
      technologyTagId: 91, technologyText: '私有知识库检索增强生成',
      applicationScenario: '为客服提供可追溯回答', teamCapabilities: '两名全栈工程师',
      timeline: '3_6_months', existingResources: '已有脱敏 FAQ', constraints: '数据不得离开私有网络',
      outputDepth: 'deep',
    } })
    api.start.mockResolvedValue({ session: createdSession, messageId: 412, runId: 512, status: 'received' })
    api.getSession.mockResolvedValue(detail({ session: createdSession }))

    const wrapper = mount(AssistantView)
    await flushPromises()
    const inspector = await openConditions(wrapper)
    await inspector.findAll('.profile-fields select')[1].setValue('42')
    await inspector.get('[role="combobox"]').setValue('AI')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    await inspector.get('[data-testid="research-task-technology_assessment"]').trigger('click')
    await inspector.get('[data-testid="task-technology-tag"]').setValue('91')
    await inspector.get('[data-testid="task-technology-text"]').setValue('私有知识库检索增强生成')
    await inspector.get('[data-testid="task-application-scenario"]').setValue('为客服提供可追溯回答')
    await inspector.get('[data-testid="task-team-capabilities"]').setValue('两名全栈工程师')
    await inspector.get('[data-testid="task-timeline"]').setValue('3_6_months')
    await inspector.get('[data-testid="task-existing-resources"]').setValue('已有脱敏 FAQ')
    await inspector.get('[data-testid="task-constraints"]').setValue('数据不得离开私有网络')
    await inspector.get('[data-testid="task-output-depth"]').setValue('deep')
    await wrapper.get('textarea[aria-label="研究问题"]').setValue('评估这条技术路线')
    await starterForm(wrapper).trigger('submit')
    await flushPromises()

    expect(api.start).toHaveBeenCalledWith(expect.objectContaining({
      requestedIntent: 'technology_assessment',
      taskContext: expect.objectContaining({
        technologyTagId: 91,
        technologyText: '私有知识库检索增强生成',
        applicationScenario: '为客服提供可追溯回答',
        teamCapabilities: '两名全栈工程师',
        timeline: '3_6_months',
        existingResources: '已有脱敏 FAQ',
        constraints: '数据不得离开私有网络',
        outputDepth: 'deep',
      }),
    }))
    expect(api.start).toHaveBeenCalledTimes(1)
    await openConditions(wrapper)
    expect(wrapper.text()).toContain('为客服提供可追溯回答')
    expect(wrapper.text()).toContain('两名全栈工程师')
    expect(wrapper.text()).toContain('3-6 个月')
    wrapper.unmount()
  })

  it('offers run feedback only when the server marks the restored result eligible', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({
      latestRun: { ...activeRun, status: 'completed', currentStage: 'completed', feedbackEligible: true },
    }))

    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(wrapper.find('[data-testid="open-run-feedback"]').exists()).toBe(true)
    expect(api.getFeedback).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('creates a local branch draft without copying the source unsent draft or creating server state', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({
      session: session({
        profile: { regionId: 42, industryTagId: 7, industry: '人工智能应用' },
      }),
      latestRun: { ...activeRun, status: 'completed', currentStage: 'completed' },
    }))

    const wrapper = mount(AssistantView)
    await flushPromises()
    await wrapper.get('textarea[aria-label="研究问题"]').setValue('源会话尚未发送的草稿')
    const inspector = await openConditions(wrapper)
    await inspector.get('button[aria-label="基于当前研究条件新建研究"]').trigger('click')
    await flushPromises()

    expect(api.getBranch).toHaveBeenCalledWith(301)
    expect(api.start).not.toHaveBeenCalled()
    expect(api.create).not.toHaveBeenCalled()
    expect(latestEventValue(wrapper, 'workspace-title')).toBe('新研究')
    await openConditions(wrapper)
    expect(wrapper.get('[data-testid="branch-research-context"]').text()).toContain('两个已核验案例采用不同的获客路径。')
    expect(wrapper.get('[data-testid="branch-research-context"]').text()).toContain('2 条引用')
    expect(wrapper.get('textarea[aria-label="研究问题"]').element.value).not.toContain('源会话尚未发送的草稿')
    expect(localStorage.getItem('opc_assistant:user:42:draft:101')).toBe('源会话尚未发送的草稿')
    expect(JSON.parse(sessionStorage.getItem('opc_assistant:user:42:branch-draft-v1'))).not.toHaveProperty('status')
    wrapper.unmount()
  })

  it('creates an independent session on the first valid send from a branch draft', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const sourceDetail = detail({
      session: session({ profile: { regionId: 42, industryTagId: 7, industry: '人工智能应用' } }),
      latestRun: { ...activeRun, status: 'completed', currentStage: 'completed' },
    })
    const branchSession = session({ sessionId: 202, title: '独立研究分支', profile: { regionId: 42, industryTagId: 7, industry: '人工智能应用' } })
    api.getSession.mockResolvedValueOnce(sourceDetail).mockResolvedValueOnce(detail({ session: branchSession }))
    api.start.mockResolvedValue({ session: branchSession, messageId: 402, runId: 502, status: 'received' })

    const wrapper = mount(AssistantView)
    await flushPromises()
    const inspector = await openConditions(wrapper)
    await inspector.get('button[aria-label="基于当前研究条件新建研究"]').trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    await wrapper.get('textarea[aria-label="研究问题"]').setValue('把比较范围调整到首批付费客户验证')
    await starterForm(wrapper).trigger('submit')
    await flushPromises()

    expect(api.send).not.toHaveBeenCalled()
    expect(api.start).toHaveBeenCalledWith(expect.objectContaining({
      content: '把比较范围调整到首批付费客户验证',
      requestedIntent: 'case_comparison',
      taskContext: expect.objectContaining({
        version: 'phase3-task-v1', taskType: 'case_comparison', caseIds: [11, 12],
      }),
      idempotencyKey: expect.any(String),
    }))
    expect(api.getSession).toHaveBeenLastCalledWith(202)
    expect(sessionStorage.getItem('opc_assistant:user:42:branch-draft-v1')).toBeNull()
    wrapper.unmount()
  })

  it('only applies an explicit long-term preference to the local new-research draft', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.getPreferences.mockResolvedValue({
      memoryEnabled: true,
      commonRegion: '湖北省',
      commonIndustry: '人工智能应用',
      ventureStage: 'validation',
      budgetRange: 'under_100k',
      existingResources: '产品原型',
    })

    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(api.getPreferences).not.toHaveBeenCalled()
    await openConditions(wrapper)
    await wrapper.get('[data-testid="open-research-preferences"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="apply-research-preferences"]').trigger('click')
    await flushPromises()

    expect(api.start).not.toHaveBeenCalled()
    expect(wrapper.findAll('.profile-fields select')[1].element.value).toBe('42')
    expect(wrapper.get('[role="combobox"]').element.value).toBe('人工智能应用')
    expect(JSON.parse(localStorage.getItem('opc_assistant:user:42:new-profile-v3'))).toMatchObject({
      regionId: '42', industryTagId: '7', existingResources: '产品原型',
    })
    wrapper.unmount()
  })

  it('keeps saved reports in the research workspace and reads them only when opened', async () => {
    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(api.getReports).not.toHaveBeenCalled()
    await wrapper.get('[data-testid="session-open-research-reports"]').trigger('click')
    await flushPromises()

    expect(api.getReports).toHaveBeenCalledWith({ scope: 'active', q: '', limit: 30 })
    wrapper.unmount()
  })

  it('exposes one bounded workspace, one conversation scroll owner, and a persistent composer anchor', async () => {
    const wrapper = mount(AssistantView)
    await flushPromises()

    const desk = wrapper.get('main.research-desk')
    const conversation = desk.get('.conversation')
    const composer = desk.get('form.composer')

    expect(desk.attributes('data-layout')).toBe('bounded-workspace')
    expect(conversation.attributes('data-scroll-owner')).toBe('conversation')
    expect(desk.findAll('[data-scroll-owner="conversation"]')).toHaveLength(1)
    expect(composer.attributes('data-workspace-anchor')).toBe('composer')
    expect(conversation.element.parentElement).toBe(desk.element)
    expect(composer.element.parentElement).toBe(desk.element)
    expect(conversation.element.compareDocumentPosition(composer.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    wrapper.unmount()
  })

  it('confirms a fuzzy industry before readiness enables a new research', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.resolveIndustry.mockResolvedValue({
      tagId: 7,
      name: '人工智能应用',
      method: 'fuzzy',
      confidence: 0.9,
      requiresConfirmation: true,
    })

    const wrapper = mount(AssistantView)
    await flushPromises()
    const inspector = await openConditions(wrapper)
    const selects = inspector.findAll('.profile-fields select')
    await selects[1].setValue('42')
    await inspector.get('[role="combobox"]').setValue('人工智能应用行业')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()

    expect(api.resolveIndustry).toHaveBeenCalledWith('人工智能应用行业')
    expect(api.checkReadiness).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('建议匹配“人工智能应用”')
    expect(wrapper.get('textarea[aria-label="研究问题"]').attributes('disabled')).toBeUndefined()

    const accept = inspector.findAll('.industry-resolution-actions button').find((button) => button.text().includes('采用'))
    await accept.trigger('click')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()

    expect(api.checkReadiness).toHaveBeenCalledWith(expect.objectContaining({ regionId: 42, industryTagId: 7, industry: '人工智能应用' }))
    expect(api.checkReadiness).toHaveBeenCalledTimes(1)
    expect(api.resolveIndustry).toHaveBeenCalledTimes(1)
    expect(wrapper.get('textarea[aria-label="研究问题"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('runs readiness once for each region or canonical industry dependency change', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.getRegions.mockResolvedValue([{ id: 42, name: '湖北省' }, { id: 43, name: '湖南省' }])
    api.getIndustryTags.mockResolvedValue([
      { tagId: 7, name: '人工智能应用' },
      { tagId: 8, name: '智能制造' },
    ])

    const wrapper = mount(AssistantView)
    await flushPromises()
    const inspector = await openConditions(wrapper)
    const region = inspector.findAll('.profile-fields select')[1]
    const industry = inspector.get('[role="combobox"]')

    await region.setValue('42')
    await industry.setValue('人工智能应用')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    expect(api.checkReadiness).toHaveBeenCalledTimes(1)
    expect(api.resolveIndustry).not.toHaveBeenCalled()

    api.checkReadiness.mockClear()
    await region.setValue('43')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    expect(api.checkReadiness).toHaveBeenCalledTimes(1)

    api.checkReadiness.mockClear()
    await industry.setValue('智能制造')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    expect(api.checkReadiness).toHaveBeenCalledTimes(1)
    expect(api.checkReadiness).toHaveBeenCalledWith(expect.objectContaining({
      regionId: 43,
      industryTagId: 8,
      industry: '智能制造',
    }))
    wrapper.unmount()
  })

  it('keeps a rejected industry suggestion visible across unrelated profile edits', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.resolveIndustry.mockResolvedValue({
      tagId: 7,
      name: '人工智能应用',
      method: 'fuzzy',
      confidence: 0.9,
      requiresConfirmation: true,
    })

    const wrapper = mount(AssistantView)
    await flushPromises()
    const inspector = await openConditions(wrapper)
    await inspector.findAll('.profile-fields select')[1].setValue('42')
    await inspector.get('[role="combobox"]').setValue('AI 咨询服务')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    const reject = inspector.findAll('.industry-resolution-actions button').find((button) => button.text().includes('保留原始输入'))
    await reject.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('未采用建议匹配')
    await inspector.get('.profile-fields input[maxlength="200"]').setValue('验证首批客户')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()

    expect(wrapper.text()).toContain('未采用建议匹配')
    expect(api.resolveIndustry).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('ignores a stale AI industry response after the user changes the query', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    const first = deferred()
    const second = deferred()
    api.resolveIndustry.mockImplementation((query) => query === '旧行业' ? first.promise : second.promise)

    const wrapper = mount(AssistantView)
    await flushPromises()
    const inspector = await openConditions(wrapper)
    await inspector.findAll('.profile-fields select')[1].setValue('42')
    const combobox = inspector.get('[role="combobox"]')
    await combobox.setValue('旧行业')
    await vi.advanceTimersByTimeAsync(320)
    await combobox.setValue('新行业')
    await vi.advanceTimersByTimeAsync(320)

    second.resolve({ tagId: 8, name: '智能制造', method: 'alias', confidence: 1, requiresConfirmation: false })
    await flushPromises()
    first.resolve({ tagId: 7, name: '人工智能应用', method: 'fuzzy', confidence: 0.8, requiresConfirmation: true })
    await flushPromises()

    expect(inspector.get('[role="combobox"]').element.value).toBe('智能制造')
    expect(wrapper.text()).not.toContain('是否采用“人工智能应用”')
    wrapper.unmount()
  })

  it('keeps only the latest readiness result after profile changes', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.getRegions.mockResolvedValue([{ id: 42, name: '湖北省' }, { id: 43, name: '湖南省' }])
    const first = deferred()
    const second = deferred()
    api.checkReadiness.mockImplementation(({ regionId }) => regionId === 42 ? first.promise : second.promise)

    const wrapper = mount(AssistantView)
    await flushPromises()
    const inspector = await openConditions(wrapper)
    const region = inspector.findAll('.profile-fields select')[1]
    await region.setValue('42')
    await inspector.get('[role="combobox"]').setValue('人工智能应用')
    await vi.advanceTimersByTimeAsync(420)
    await region.setValue('43')
    await vi.advanceTimersByTimeAsync(420)

    second.resolve({ readinessStatus: 'sufficient', verifiedCaseCount: 9, selectedPolicyCount: 4 })
    await flushPromises()
    first.resolve({ readinessStatus: 'insufficient', verifiedCaseCount: 0, selectedPolicyCount: 0 })
    await flushPromises()

    expect(wrapper.text()).toContain('证据充分')
    expect(wrapper.text()).not.toContain('当前证据不足')
    wrapper.unmount()
  })

  it('keeps readiness and submission available while non-evidence profile fields change', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    const wrapper = mount(AssistantView)
    await flushPromises()

    const inspector = await openConditions(wrapper)
    const selects = inspector.findAll('.profile-fields select')
    await selects[1].setValue('42')
    await inspector.get('[role="combobox"]').setValue('人工智能应用')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()

    expect(api.checkReadiness).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('证据充分')
    api.checkReadiness.mockClear()

    await selects[0].setValue('small_team')
    await selects[2].setValue('growth')
    await selects[3].setValue('100k_500k')
    await inspector.get('.field-goal input').setValue('验证首批付费客户')
    await inspector.get('.field-resources textarea').setValue('已有产品原型')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(api.checkReadiness).not.toHaveBeenCalled()
    expect(api.resolveIndustry).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('证据充分')
    expect(wrapper.get('textarea[aria-label="研究问题"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('restores the selected session and resumes its active run after refresh', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({
      messages: [{ messageId: 201, sequenceNo: 1, role: 'user', content: '研究湖北创业机会', status: 'completed', citations: [] }],
      activeRun,
    }))

    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(api.getSession).toHaveBeenCalledWith(101)
    expect(wrapper.text()).toContain('研究湖北创业机会')
    expect(wrapper.text()).toContain('正在检索政策')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(api.getRun).toHaveBeenCalledWith(301)
    wrapper.unmount()
  })

  it('keeps archived sessions readable and disables their composer', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({ session: session({ status: 'archived', archivedAt: '2026-07-25T11:00:00' }) }))

    const wrapper = mount(AssistantView)
    await flushPromises()

    const textarea = wrapper.get('textarea[aria-label="研究问题"]')
    expect(textarea.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('归档会话仅供查阅')
    wrapper.unmount()
  })

  it('shows an independent exhausted-quota state and blocks new submissions', async () => {
    api.getUsage.mockResolvedValue({
      usedTokens: 900,
      reservedTokens: 100,
      remainingTokens: 0,
      dailyLimit: 1000,
      unlimited: false,
      resetAt: '2026-07-26T00:00:00',
    })

    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(wrapper.get('textarea[aria-label="研究问题"]').attributes('disabled')).toBeDefined()
    const quota = wrapper.get('.composer-quota-state')
    expect(quota.attributes('role')).toBe('status')
    expect(quota.text()).toContain('今日研究额度已用尽')
    expect(quota.text()).toContain('已用 900 · 预留 100 · 日上限 1000')
    expect(quota.text()).toContain('2026')
    wrapper.unmount()
  })

  it('starts the first research atomically only when the first question is sent', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.start.mockResolvedValue({
      session: session({ sessionId: 106, title: '新研究', profile: { regionId: 42, industry: '人工智能应用' } }),
      messageId: 202,
      runId: 302,
      status: 'received',
    })
    api.getSession.mockResolvedValue(detail({ session: session({ sessionId: 106, title: '研究湖北人工智能机会' }) }))

    const wrapper = mount(AssistantView)
    await flushPromises()
    expect(api.start).not.toHaveBeenCalled()
    expect(wrapper.find('.research-starter form').exists()).toBe(true)
    expect(wrapper.find('form.composer').exists()).toBe(false)

    const inspector = await openConditions(wrapper)
    const selects = inspector.findAll('.profile-fields select')
    await selects[1].setValue('42')
    await inspector.get('[role="combobox"]').setValue('人工智能应用')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    await wrapper.get('textarea[aria-label="研究问题"]').setValue('请研究湖北人工智能创业机会')
    await wrapper.get('.research-starter form').trigger('submit')
    await flushPromises()

    expect(api.start).toHaveBeenCalledWith(expect.objectContaining({
      profile: expect.objectContaining({ regionId: 42, industryTagId: 7, industry: '人工智能应用' }),
      content: '请研究湖北人工智能创业机会',
      idempotencyKey: expect.stringMatching(/^[A-Za-z0-9_-]{8,64}$/),
    }))
    expect(api.create).not.toHaveBeenCalled()
    expect(api.send).not.toHaveBeenCalled()
    expect(wrapper.find('form.composer').exists()).toBe(true)
    expect(wrapper.get('form.composer textarea').element.value).toBe('')
    wrapper.unmount()
  })

  it('turns a comparison starter into an explicit, controlled comparison task', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.start.mockResolvedValue({
      session: session({ sessionId: 106, title: 'New research', profile: { regionId: 42, industry: 'AI' } }),
      messageId: 202,
      runId: 302,
      status: 'received',
    })
    api.getSession.mockResolvedValue(detail({ session: session({ sessionId: 106 }) }))
    const wrapper = mount(AssistantView)
    await flushPromises()
    const inspector = await openConditions(wrapper)
    const selects = inspector.findAll('.profile-fields select')
    await selects[1].setValue('42')
    await inspector.get('[role="combobox"]').setValue('人工智能应用')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()

    await inspector.get('[data-testid="research-task-case_comparison"]').trigger('click')
    await inspector.get('[data-testid="task-case-selector"]').setValue(['11', '12'])
    await inspector.get('input[value="businessModel"]').setValue(true)
    await wrapper.get('.research-starter textarea').setValue('比较湖北人工智能案例')
    await wrapper.get('.research-starter form').trigger('submit')
    await flushPromises()
    expect(api.start).toHaveBeenLastCalledWith(expect.objectContaining({
      requestedIntent: 'case_comparison',
      taskContext: expect.objectContaining({ caseIds: [11, 12], comparisonDimensions: ['businessModel'] }),
    }))
    wrapper.unmount()

    api.start.mockClear()
    localStorage.clear()
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    const rewritten = mount(AssistantView)
    await flushPromises()
    const rewrittenInspector = await openConditions(rewritten)
    const rewrittenSelects = rewrittenInspector.findAll('.profile-fields select')
    await rewrittenSelects[1].setValue('42')
    await rewrittenInspector.get('[role="combobox"]').setValue('人工智能应用')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    await rewrittenInspector.get('[data-testid="research-task-case_comparison"]').trigger('click')
    await rewrittenInspector.get('[data-testid="task-case-selector"]').setValue(['11', '12'])
    await rewrittenInspector.get('input[value="businessModel"]').setValue(true)
    await rewritten.get('.research-starter textarea').setValue('帮我梳理下一步创业方向')
    await rewritten.get('.research-starter form').trigger('submit')
    await flushPromises()

    expect(api.start).toHaveBeenLastCalledWith(expect.objectContaining({
      requestedIntent: 'case_comparison',
      taskContext: expect.objectContaining({ caseIds: [11, 12], comparisonDimensions: ['businessModel'] }),
    }))
    rewritten.unmount()
  })

  it('reuses the pending start identity after an ambiguous failure and reload', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.start
      .mockRejectedValueOnce(new Error('timeout'))
      .mockResolvedValueOnce({
        session: session({ sessionId: 106, title: '新研究' }),
        messageId: 202,
        runId: 302,
        status: 'received',
      })
    api.getSession.mockResolvedValue(detail({ session: session({ sessionId: 106 }) }))

    const first = mount(AssistantView)
    await flushPromises()
    const firstInspector = await openConditions(first)
    await firstInspector.findAll('.profile-fields select')[1].setValue('42')
    await firstInspector.get('[role="combobox"]').setValue('人工智能应用')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    await first.get('textarea[aria-label="研究问题"]').setValue('请研究湖北人工智能创业机会')
    await first.get('.research-starter form').trigger('submit')
    await flushPromises()
    const firstKey = api.start.mock.calls[0][0].idempotencyKey
    expect(first.get('[role="alert"]').text()).toContain('timeout')
    first.unmount()

    const second = mount(AssistantView)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    expect(second.get('textarea[aria-label="研究问题"]').element.value).toBe('请研究湖北人工智能创业机会')
    await second.get('.research-starter form').trigger('submit')
    await flushPromises()

    expect(api.start.mock.calls[1][0].idempotencyKey).toBe(firstKey)
    second.unmount()
  })

  it('reuses an existing-session message identity after an ambiguous failure and reload', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.send
      .mockRejectedValueOnce(new Error('timeout'))
      .mockResolvedValueOnce({ sessionId: 101, messageId: 203, runId: 303, status: 'received' })
    api.getSession.mockResolvedValue(detail())

    const first = mount(AssistantView)
    await flushPromises()
    await first.get('textarea[aria-label="研究问题"]').setValue('继续核验湖北政策')
    await first.get('form.composer').trigger('submit')
    await flushPromises()
    const firstKey = api.send.mock.calls[0][1].idempotencyKey
    first.unmount()

    const second = mount(AssistantView)
    await flushPromises()
    expect(second.get('textarea[aria-label="研究问题"]').element.value).toBe('继续核验湖北政策')
    await second.get('form.composer').trigger('submit')
    await flushPromises()

    expect(api.send.mock.calls[1][1].idempotencyKey).toBe(firstKey)
    second.unmount()
  })

  it('debounces history search and sends only the latest query', async () => {
    const wrapper = mount(AssistantView)
    await flushPromises()
    api.getHistory.mockClear()
    const input = wrapper.get('.history-search input')

    await input.setValue('湖')
    await vi.advanceTimersByTimeAsync(100)
    await input.setValue('湖北')
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()

    expect(api.getHistory).toHaveBeenCalledTimes(1)
    expect(api.getHistory).toHaveBeenCalledWith(expect.objectContaining({ q: '湖北' }))
    wrapper.unmount()
  })

  it('updates the rail geometry immediately while the grid transition settles', async () => {
    const wrapper = mount(AssistantView)
    await flushPromises()

    const workspace = wrapper.get('.assistant-workspace')
    wrapper.get('.history-sidebar .sidebar-toggle').element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 }))
    await nextTick()

    expect(workspace.classes()).toContain('history-motion-collapsing')
    expect(workspace.classes()).toContain('history-collapsed')

    await vi.advanceTimersByTimeAsync(500)
    await nextTick()

    expect(workspace.classes()).toContain('history-collapsed')
    expect(workspace.classes()).toContain('history-motion-collapsing')

    await vi.advanceTimersByTimeAsync(120)
    await nextTick()
    expect(workspace.classes()).not.toContain('history-motion-collapsing')

    workspace.get('.history-sidebar .sidebar-toggle').element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 }))
    await nextTick()
    expect(workspace.classes()).toContain('history-motion-expanding')
    expect(workspace.classes()).not.toContain('history-collapsed')

    await vi.advanceTimersByTimeAsync(500)
    await nextTick()
    expect(workspace.classes()).not.toContain('history-collapsed')
    expect(workspace.classes()).not.toContain('history-motion-expanding')
    wrapper.unmount()
  })

  it('sends an explicit false pinned value when cancelling a pinned session', async () => {
    const pinnedSession = session({ pinned: true })
    api.getHistory.mockResolvedValue({ items: [pinnedSession], nextCursor: null, hasMore: false })
    api.update.mockResolvedValue({ ...pinnedSession, pinned: false })

    const wrapper = mount(AssistantView)
    await flushPromises()
    await wrapper.get('.session-menu summary').trigger('click')
    await wrapper.findAll('.session-menu-popover>button').find((button) => button.text().includes('取消置顶')).trigger('click')
    await flushPromises()

    expect(api.update).toHaveBeenCalledWith(101, { pinned: false })
    wrapper.unmount()
  })

  it('updates the pinned group as soon as the unpin patch succeeds', async () => {
    const pinnedSession = session({ pinned: true })
    const refresh = deferred()
    api.getHistory.mockResolvedValueOnce({ items: [pinnedSession], nextCursor: null, hasMore: false })
    api.getHistory.mockReturnValueOnce(refresh.promise)
    api.update.mockResolvedValue({ ...pinnedSession, pinned: false })

    const wrapper = mount(AssistantView)
    await flushPromises()
    await wrapper.get('.session-menu summary').trigger('click')
    await wrapper.findAll('.session-menu-popover>button').find((button) => button.text().includes('取消置顶')).trigger('click')
    await flushPromises()

    expect(wrapper.find('.history-list h3').text()).not.toContain('置顶')
    refresh.resolve({ items: [{ ...pinnedSession, pinned: false }], nextCursor: null, hasMore: false })
    await flushPromises()
    wrapper.unmount()
  })

  it('refreshes the first history page once when a metadata cursor becomes stale', async () => {
    const initial = session({ sessionId: 101, title: '当前研究' })
    const refreshed = session({ sessionId: 202, title: '更新后的历史' })
    const stale = Object.assign(new Error('历史记录已更新'), {
      businessCode: 409,
      diagnosticCode: 'HISTORY_CURSOR_STALE',
    })
    api.getHistory
      .mockResolvedValueOnce({ items: [initial], nextCursor: 'cursor-page-2', hasMore: true })
      .mockRejectedValueOnce(stale)
      .mockResolvedValueOnce({ items: [refreshed], nextCursor: null, hasMore: false })

    const wrapper = mount(AssistantView)
    await flushPromises()
    expect(localStorage.getItem(SELECTED_SESSION_KEY)).toBe('101')

    await wrapper.get('.load-more').trigger('click')
    await flushPromises()

    expect(api.getHistory).toHaveBeenCalledTimes(3)
    expect(api.getHistory.mock.calls[1][0]).toMatchObject({ cursor: 'cursor-page-2', q: '', scope: 'active' })
    expect(api.getHistory.mock.calls[2][0]).toMatchObject({ cursor: null, q: '', scope: 'active' })
    expect(wrapper.findAll('.history-row-main').map((row) => row.text())).toEqual([
      expect.stringContaining('更新后的历史'),
    ])
    expect(localStorage.getItem(SELECTED_SESSION_KEY)).toBe('101')
    expect(wrapper.get('.assistant-toast').text()).toContain('历史记录已更新')
    wrapper.unmount()
  })

  it('does not treat an ordinary history network error as a stale cursor', async () => {
    api.getHistory
      .mockResolvedValueOnce({ items: [session()], nextCursor: 'cursor-page-2', hasMore: true })
      .mockRejectedValueOnce(new Error('network unavailable'))

    const wrapper = mount(AssistantView)
    await flushPromises()
    await wrapper.get('.load-more').trigger('click')
    await flushPromises()

    expect(api.getHistory).toHaveBeenCalledTimes(2)
    expect(wrapper.get('.history-error').text()).toContain('network unavailable')
    wrapper.unmount()
  })

  it('keeps the latest selected session when an earlier request resolves last', async () => {
    const sessionA = session({ sessionId: 101, title: '研究 A' })
    const sessionB = session({ sessionId: 102, title: '研究 B' })
    api.getHistory.mockResolvedValue({ items: [sessionA, sessionB], nextCursor: null, hasMore: false })
    api.getSession.mockResolvedValueOnce(detail({ session: sessionA }))
    const wrapper = mount(AssistantView)
    await flushPromises()

    const requestA = deferred()
    const requestB = deferred()
    api.getSession.mockImplementation((sessionId) => sessionId === 101 ? requestA.promise : requestB.promise)
    const rows = wrapper.findAll('.history-row-main')
    await rows.find((row) => row.text().includes('研究 A')).trigger('click')
    await rows.find((row) => row.text().includes('研究 B')).trigger('click')

    requestB.resolve(detail({
      session: sessionB,
      messages: [{ messageId: 220, sequenceNo: 1, role: 'user', content: '会话 B 的问题', citations: [] }],
    }))
    await flushPromises()
    requestA.resolve(detail({
      session: sessionA,
      messages: [{ messageId: 210, sequenceNo: 1, role: 'user', content: '会话 A 的问题', citations: [] }],
    }))
    await flushPromises()

    expect(latestEventValue(wrapper, 'workspace-title')).toBe('研究 B')
    expect(wrapper.text()).toContain('会话 B 的问题')
    expect(wrapper.text()).not.toContain('会话 A 的问题')
    expect(localStorage.getItem(SELECTED_SESSION_KEY)).toBe('102')
    wrapper.unmount()
  })

  it('closes and invalidates an in-flight process drawer when selecting another session', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const sessionA = session({ sessionId: 101, title: '研究 A' })
    const sessionB = session({ sessionId: 102, title: '研究 B' })
    const processA = deferred()
    api.getHistory.mockResolvedValue({ items: [sessionA, sessionB], nextCursor: null, hasMore: false })
    api.getSession
      .mockResolvedValueOnce(detail({
        session: sessionA,
        messages: [{ messageId: 210, sequenceNo: 1, role: 'assistant', content: '会话 A 的回答', runId: 301, citations: [] }],
      }))
      .mockResolvedValueOnce(detail({ session: sessionB, messages: [] }))
    api.getRun.mockReturnValue(processA.promise)

    const wrapper = mount(AssistantView, { attachTo: document.body })
    await flushPromises()
    const processButton = wrapper.findAll('.message footer button').find((button) => button.text().includes('研究过程'))
    await processButton.trigger('click')
    expect(document.querySelector('.assistant-inspector')).not.toBeNull()

    const sessionBRow = wrapper.findAll('.history-row-main').find((row) => row.text().includes('研究 B'))
    await sessionBRow.trigger('click')
    await flushPromises()
    expect(document.querySelector('.assistant-inspector')).toBeNull()

    processA.resolve({ ...activeRun, visibleProgress: '会话 A 的研究过程' })
    await flushPromises()
    expect(document.querySelector('.assistant-inspector')).toBeNull()
    expect(latestEventValue(wrapper, 'workspace-title')).toBe('研究 B')
    wrapper.unmount()
  })

  it('does not return to the submitted session when its receipt arrives after selecting another session', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const sessionA = session({ sessionId: 101, title: '研究 A' })
    const sessionB = session({ sessionId: 102, title: '研究 B' })
    api.getHistory.mockResolvedValue({ items: [sessionA, sessionB], nextCursor: null, hasMore: false })
    api.getSession
      .mockResolvedValueOnce(detail({ session: sessionA }))
      .mockResolvedValueOnce(detail({ session: sessionB, messages: [{ messageId: 220, sequenceNo: 1, role: 'user', content: '会话 B 的问题', citations: [] }] }))
      .mockResolvedValue(detail({ session: sessionA }))
    const receipt = deferred()
    api.send.mockReturnValue(receipt.promise)

    const wrapper = mount(AssistantView)
    await flushPromises()
    await wrapper.get('textarea[aria-label="研究问题"]').setValue('会话 A 的新问题')
    await wrapper.get('form.composer').trigger('submit')
    const sessionBRow = wrapper.findAll('.history-row-main').find((row) => row.text().includes('研究 B'))
    await sessionBRow.trigger('click')
    await flushPromises()
    receipt.resolve({ sessionId: 101, messageId: 211, runId: 311, status: 'received' })
    await flushPromises()

    expect(latestEventValue(wrapper, 'workspace-title')).toBe('研究 B')
    expect(wrapper.text()).toContain('会话 B 的问题')
    expect(localStorage.getItem(SELECTED_SESSION_KEY)).toBe('102')
    wrapper.unmount()
  })

  it('loads older messages with stable de-duplication', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({
      messages: [{ messageId: 4, sequenceNo: 4, role: 'assistant', content: '第四条', citations: [] }, { messageId: 5, sequenceNo: 5, role: 'user', content: '第五条', citations: [] }],
      nextBeforeSequence: 4, hasMoreMessages: true,
    }))
    api.getMessages.mockResolvedValue({
      items: [{ messageId: 2, sequenceNo: 2, role: 'user', content: '第二条', citations: [] }, { messageId: 3, sequenceNo: 3, role: 'assistant', content: '第三条', citations: [] }, { messageId: 4, sequenceNo: 4, role: 'assistant', content: '第四条', citations: [] }],
      nextBeforeSequence: null, hasMore: false,
    })
    const wrapper = mount(AssistantView)
    await flushPromises()

    await wrapper.get('.load-older').trigger('click')
    await flushPromises()
    expect(api.getMessages).toHaveBeenCalledWith(101, { beforeSequence: 4, limit: 50 })
    expect(wrapper.text().match(/第四条/g)).toHaveLength(1)
    expect(wrapper.text().indexOf('第二条')).toBeLessThan(wrapper.text().indexOf('第五条'))
    wrapper.unmount()
  })

  it('does not merge an older page into a newly selected session', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const sessionA = session({ sessionId: 101, title: '研究 A' })
    const sessionB = session({ sessionId: 102, title: '研究 B' })
    api.getHistory.mockResolvedValue({ items: [sessionA, sessionB], nextCursor: null, hasMore: false })
    api.getSession
      .mockResolvedValueOnce(detail({ session: sessionA, messages: [], nextBeforeSequence: 5, hasMoreMessages: true }))
      .mockResolvedValueOnce(detail({
        session: sessionB,
        messages: [{ messageId: 220, sequenceNo: 1, role: 'user', content: '会话 B 的问题', citations: [] }],
      }))
    const older = deferred()
    api.getMessages.mockReturnValue(older.promise)

    const wrapper = mount(AssistantView)
    await flushPromises()
    await wrapper.get('.load-older').trigger('click')
    const sessionBRow = wrapper.findAll('.history-row-main').find((row) => row.text().includes('研究 B'))
    await sessionBRow.trigger('click')
    await flushPromises()
    older.resolve({
      items: [{ messageId: 205, sequenceNo: 4, role: 'user', content: '会话 A 的更早问题', citations: [] }],
      nextBeforeSequence: null,
      hasMore: false,
    })
    await flushPromises()

    expect(latestEventValue(wrapper, 'workspace-title')).toBe('研究 B')
    expect(wrapper.text()).toContain('会话 B 的问题')
    expect(wrapper.text()).not.toContain('会话 A 的更早问题')
    wrapper.unmount()
  })

  it('preserves the last known run during a temporary polling failure', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({ activeRun }))
    api.getRun.mockRejectedValueOnce(new Error('network down'))
    const wrapper = mount(AssistantView)
    await flushPromises()

    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(wrapper.text()).toContain('正在检索政策')
    expect(wrapper.text()).toContain('连接中断，正在恢复最后已知状态')
    expect(wrapper.text()).not.toContain('研究运行未完成')
    wrapper.unmount()
  })

  it('performs one final server refresh at the run deadline without inventing a failed state', async () => {
    vi.setSystemTime(new Date('2026-08-02T10:00:00.000Z'))
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const deadlineAt = new Date(Date.now() + 500).toISOString()
    const runningAtDeadline = { ...activeRun, deadlineAt }
    api.getSession.mockResolvedValue(detail({ activeRun: runningAtDeadline }))
    api.getRun.mockResolvedValue(runningAtDeadline)

    const wrapper = mount(AssistantView)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(200)
    await flushPromises()

    expect(api.getRun).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('服务器正在完成结算')
    expect(wrapper.text()).toContain('重新获取状态')
    expect(wrapper.text()).not.toContain('研究运行未完成')

    await vi.advanceTimersByTimeAsync(30000)
    await flushPromises()
    expect(api.getRun).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('lets a user re-fetch a deadline-limited run and accepts the next terminal server status', async () => {
    vi.setSystemTime(new Date('2026-08-02T10:00:00.000Z'))
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const deadlineAt = new Date(Date.now() + 500).toISOString()
    const runningAtDeadline = { ...activeRun, deadlineAt }
    api.getSession.mockResolvedValue(detail({ activeRun: runningAtDeadline }))
    api.getRun.mockResolvedValue(runningAtDeadline)

    const wrapper = mount(AssistantView)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(wrapper.get('button[aria-label="重新获取当前研究状态"]').exists()).toBe(true)

    api.getRun.mockResolvedValue({ ...runningAtDeadline, status: 'completed', currentStage: 'completed', completedAt: '2026-08-02T10:00:01' })
    await wrapper.get('button[aria-label="重新获取当前研究状态"]').trigger('click')
    await vi.advanceTimersByTimeAsync(0)
    await flushPromises()

    expect(api.getRun).toHaveBeenCalledTimes(3)
    expect(wrapper.text()).toContain('研究已完成')
    expect(wrapper.find('button[aria-label="重新获取当前研究状态"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('uses the bounded network backoff and allows a paused run to recover manually', async () => {
    vi.setSystemTime(new Date('2026-08-02T10:00:00.000Z'))
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const running = { ...activeRun, deadlineAt: new Date(Date.now() + 60000).toISOString() }
    api.getSession.mockResolvedValue(detail({ activeRun: running }))
    api.getRun.mockRejectedValue(new Error('network down'))
    const wrapper = mount(AssistantView)
    await flushPromises()

    await vi.advanceTimersByTimeAsync(300)
    for (const delay of [1000, 2000, 4000, 8000, 10000]) await vi.advanceTimersByTimeAsync(delay)
    await flushPromises()

    expect(api.getRun).toHaveBeenCalledTimes(6)
    expect(wrapper.text()).toContain('自动恢复已暂停')
    expect(wrapper.get('button[aria-label="重新获取当前研究状态"]').text()).toContain('重新获取状态')

    api.getRun.mockResolvedValue(running)
    await wrapper.get('button[aria-label="重新获取当前研究状态"]').trigger('click')
    await vi.advanceTimersByTimeAsync(0)
    await flushPromises()

    expect(api.getRun).toHaveBeenCalledTimes(7)
    expect(wrapper.text()).not.toContain('自动恢复已暂停')
    wrapper.unmount()
  })

  it.each(['completed', 'evidence_insufficient', 'failed', 'cancelled', 'expired', 'clarification_needed'])('stops polling after the server returns %s', async (status) => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const terminalRun = { ...activeRun, status, currentStage: status }
    api.getSession.mockResolvedValue(detail({ activeRun }))
    api.getRun.mockResolvedValue(terminalRun)
    const wrapper = mount(AssistantView)
    await flushPromises()

    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(10000)
    await flushPromises()

    expect(api.getRun).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('keeps a completed run visible and synchronizes its session details only when the user requests recovery', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const completedRun = { ...activeRun, status: 'completed', currentStage: 'completed', completedAt: '2026-08-02T10:00:01' }
    const completedMessage = { messageId: 210, sequenceNo: 2, role: 'assistant', runId: 301, content: '已同步的研究结果', citations: [] }
    api.getSession
      .mockResolvedValueOnce(detail({ activeRun }))
      .mockRejectedValue(new Error('会话详情网络中断'))
    api.getRun.mockResolvedValue(completedRun)

    const wrapper = mount(AssistantView, { attachTo: document.body })
    try {
      await flushPromises()
      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      expect(wrapper.text()).toContain('研究已完成')
      expect(wrapper.text()).toContain('会话内容仍在同步')
      expect(wrapper.text()).not.toContain('安全重试')
      expect(wrapper.get('textarea[aria-label="研究问题"]').attributes('disabled')).toBeDefined()
      const sync = wrapper.get('button[aria-label="同步研究结果"]')
      sync.element.focus()
      expect(document.activeElement).toBe(sync.element)

      await vi.advanceTimersByTimeAsync(30000)
      await flushPromises()
      expect(api.getRun).toHaveBeenCalledTimes(1)

      api.getSession.mockResolvedValueOnce(detail({ messages: [completedMessage], latestRun: completedRun }))
      await sync.trigger('click')
      await flushPromises()

      expect(api.getRun).toHaveBeenCalledTimes(2)
      expect(wrapper.text()).toContain('已同步的研究结果')
      expect(wrapper.find('button[aria-label="同步研究结果"]').exists()).toBe(false)
      expect(wrapper.get('textarea[aria-label="研究问题"]').attributes('disabled')).toBeUndefined()
      expect(api.send).not.toHaveBeenCalled()
      expect(api.start).not.toHaveBeenCalled()
    } finally {
      wrapper.unmount()
    }
  })

  it('uses result synchronization instead of safe retry until failed terminal details are available', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const failedRun = { ...activeRun, status: 'failed', currentStage: 'failed', retryContent: '只重试关联问题' }
    api.getSession
      .mockResolvedValueOnce(detail({ activeRun }))
      .mockRejectedValueOnce(new Error('会话详情网络中断'))
      .mockResolvedValueOnce(detail({ latestRun: failedRun, messages: [] }))
    api.getRun.mockResolvedValue(failedRun)

    const wrapper = mount(AssistantView)
    try {
      await flushPromises()
      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      expect(wrapper.text()).toContain('研究运行未完成')
      expect(wrapper.text()).toContain('会话内容仍在同步')
      expect(wrapper.text()).not.toContain('安全重试')
      expect(wrapper.get('button[aria-label="同步研究结果"]').text()).toContain('同步结果')

      await wrapper.get('button[aria-label="同步研究结果"]').trigger('click')
      await flushPromises()

      expect(wrapper.find('button[aria-label="同步研究结果"]').exists()).toBe(false)
      expect(wrapper.text()).toContain('安全重试')
    } finally {
      wrapper.unmount()
    }
  })

  it('ignores a delayed terminal detail response after the user switches sessions', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const sessionA = session({ sessionId: 101, title: '研究 A' })
    const sessionB = session({ sessionId: 102, title: '研究 B' })
    const completedRun = { ...activeRun, sessionId: 101, status: 'completed', currentStage: 'completed' }
    const delayedDetail = deferred()
    api.getHistory.mockResolvedValue({ items: [sessionA, sessionB], nextCursor: null, hasMore: false })
    api.getSession
      .mockResolvedValueOnce(detail({ session: sessionA, activeRun }))
      .mockReturnValueOnce(delayedDetail.promise)
      .mockResolvedValueOnce(detail({ session: sessionB, messages: [{ messageId: 220, sequenceNo: 1, role: 'user', content: '会话 B 的问题', citations: [] }] }))
    api.getRun.mockResolvedValue(completedRun)

    const wrapper = mount(AssistantView)
    try {
      await flushPromises()
      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      const sessionBRow = wrapper.findAll('.history-row-main').find((row) => row.text().includes('研究 B'))
      await sessionBRow.trigger('click')
      await flushPromises()
      delayedDetail.resolve(detail({ session: sessionA, latestRun: completedRun, messages: [{ messageId: 221, sequenceNo: 2, role: 'assistant', content: '研究 A 的迟到结果', citations: [] }] }))
      await flushPromises()

      expect(latestEventValue(wrapper, 'workspace-title')).toBe('研究 B')
      expect(wrapper.text()).toContain('会话 B 的问题')
      expect(wrapper.text()).not.toContain('研究 A 的迟到结果')
      expect(wrapper.find('button[aria-label="同步研究结果"]').exists()).toBe(false)
    } finally {
      wrapper.unmount()
    }
  })

  it('does not apply a delayed terminal detail response after the workspace unmounts', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const completedRun = { ...activeRun, status: 'completed', currentStage: 'completed' }
    const delayedDetail = deferred()
    api.getSession
      .mockResolvedValueOnce(detail({ activeRun }))
      .mockReturnValueOnce(delayedDetail.promise)
    api.getRun.mockResolvedValue(completedRun)

    const wrapper = mount(AssistantView)
    try {
      await flushPromises()
      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()
      const usageCallsBeforeUnmount = api.getUsage.mock.calls.length
      wrapper.unmount()

      delayedDetail.resolve(detail({ latestRun: completedRun, messages: [{ messageId: 222, sequenceNo: 2, role: 'assistant', content: '卸载后的迟到结果', citations: [] }] }))
      await flushPromises()

      expect(api.getUsage).toHaveBeenCalledTimes(usageCallsBeforeUnmount)
    } finally {
      if (wrapper.exists()) wrapper.unmount()
    }
  })

  it('does not apply a late response from a previous run after switching sessions', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const sessionA = session({ sessionId: 101, title: '研究 A' })
    const sessionB = session({ sessionId: 102, title: '研究 B' })
    const oldRun = { ...activeRun, sessionId: 101 }
    const delayedRun = deferred()
    api.getHistory.mockResolvedValue({ items: [sessionA, sessionB], nextCursor: null, hasMore: false })
    api.getSession
      .mockResolvedValueOnce(detail({ session: sessionA, activeRun: oldRun }))
      .mockResolvedValueOnce(detail({ session: sessionB, messages: [{ messageId: 220, sequenceNo: 1, role: 'user', content: '会话 B 的问题', citations: [] }] }))
    api.getRun.mockReturnValue(delayedRun.promise)
    const wrapper = mount(AssistantView)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(300)

    const sessionBRow = wrapper.findAll('.history-row-main').find((row) => row.text().includes('研究 B'))
    await sessionBRow.trigger('click')
    await flushPromises()
    delayedRun.resolve({ ...oldRun, visibleProgress: '旧运行的迟到状态' })
    await flushPromises()

    expect(latestEventValue(wrapper, 'workspace-title')).toBe('研究 B')
    expect(wrapper.text()).toContain('会话 B 的问题')
    expect(wrapper.text()).not.toContain('旧运行的迟到状态')
    wrapper.unmount()
  })

  it('uses a finite local polling guard when the server has not returned a deadline', async () => {
    vi.setSystemTime(new Date('2026-08-02T10:00:00.000Z'))
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({ activeRun }))
    api.getRun.mockResolvedValue(activeRun)
    const wrapper = mount(AssistantView)
    await flushPromises()

    await vi.advanceTimersByTimeAsync(120000)
    await flushPromises()
    const requestCount = api.getRun.mock.calls.length

    expect(wrapper.text()).toContain('暂时无法确认服务器最终状态')
    await vi.advanceTimersByTimeAsync(30000)
    await flushPromises()
    expect(api.getRun).toHaveBeenCalledTimes(requestCount)
    wrapper.unmount()
  })

  it('clears the pending polling timer when the workspace unmounts', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({ activeRun }))
    const wrapper = mount(AssistantView)
    await flushPromises()
    wrapper.unmount()

    await vi.advanceTimersByTimeAsync(5000)
    await flushPromises()
    expect(api.getRun).not.toHaveBeenCalled()
  })

  it('resumes polling when cancellation fails for the current session', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({ activeRun }))
    api.cancel.mockRejectedValue(new Error('取消请求超时'))
    const wrapper = mount(AssistantView)
    await flushPromises()

    await wrapper.get('.run-progress button[aria-label="停止当前研究"]').trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(0)
    await flushPromises()

    expect(wrapper.text()).toContain('取消请求超时')
    expect(api.getRun).toHaveBeenCalledWith(301)
    wrapper.unmount()
  })

  it('retries a restored terminal run with only its server-linked retry content', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    const failedRun = { ...activeRun, runId: 304, status: 'failed', currentStage: 'failed', visibleProgress: '模型服务超时', retryContent: '请重试这条关联问题' }
    api.getSession.mockResolvedValue(detail({
      messages: [{ messageId: 206, sequenceNo: 1, role: 'user', content: '另一条较新的用户消息', citations: [] }],
      latestRun: failedRun,
    }))
    api.send.mockResolvedValue({ sessionId: 101, messageId: 207, runId: 305, status: 'received' })

    const wrapper = mount(AssistantView)
    await flushPromises()
    const retry = wrapper.findAll('.run-progress button').find((button) => button.text().includes('安全重试'))
    expect(retry).toBeTruthy()
    await retry.trigger('click')
    await flushPromises()

    expect(api.send).toHaveBeenCalledWith(101, expect.objectContaining({ content: '请重试这条关联问题' }))
    wrapper.unmount()
  })

  it('does not offer retry when a terminal run has no retained request content', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({
      messages: [],
      latestRun: { ...activeRun, runId: 304, status: 'expired', currentStage: 'expired', retryContent: '' },
    }))

    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(wrapper.text()).toContain('研究运行已过期')
    expect(wrapper.text()).not.toContain('安全重试')
    wrapper.unmount()
  })

  it('opens verified citations in a hardened detail drawer', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({ messages: [{
      messageId: 205, sequenceNo: 1, role: 'assistant', content: '湖北省有一项支持政策。', status: 'completed', runId: 301,
      citations: [{ sourceId: 8, title: '湖北省政策原文', publisher: '湖北省人民政府', url: 'https://example.gov.cn/policy', claim: '该政策提供创业支持。' }],
    }] }))
    const wrapper = mount(AssistantView, { attachTo: document.body })
    await flushPromises()

    await wrapper.get('.citation-trigger').trigger('click')
    await flushPromises()
    const drawer = wrapper.get('.assistant-inspector').element
    expect(drawer.textContent).toContain('湖北省政策原文')
    const link = drawer.querySelector('a')
    expect(link.getAttribute('target')).toBe('_blank')
    expect(link.getAttribute('rel')).toBe('noopener noreferrer')
    wrapper.unmount()
  })

  it('resolves historical citations from the message run instead of the latest run', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({
      messages: [{
        messageId: 205, sequenceNo: 1, role: 'assistant', content: '历史回答', status: 'completed', runId: 300,
        citations: [{ sourceId: 8, claim: '历史结论' }],
      }],
      latestRun: { ...activeRun, runId: 301, status: 'completed', currentStage: 'completed' },
    }))
    api.getEvidence.mockImplementation(async (runId) => ({
      runId,
      status: 'completed',
      items: runId === 300
        ? [{ itemType: 'policy', itemId: 18, sourceId: 8, title: '历史政策证据', publisher: '湖北省人民政府', originalUrl: 'https://example.gov.cn/history', available: true }]
        : [{ itemType: 'case', itemId: 19, sourceId: 9, title: '最新运行证据', available: true }],
    }))

    const wrapper = mount(AssistantView, { attachTo: document.body })
    await flushPromises()
    api.getEvidence.mockClear()

    await wrapper.get('.citation-trigger').trigger('click')
    await flushPromises()

    expect(api.getEvidence).toHaveBeenCalledTimes(1)
    expect(api.getEvidence).toHaveBeenCalledWith(300)
    expect(document.querySelector('.assistant-inspector').textContent).toContain('历史政策证据')
    expect(document.querySelector('.assistant-inspector').textContent).not.toContain('最新运行证据')
    wrapper.unmount()
  })

  it('loads sanitized research materials beside a restored terminal answer', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({
      messages: [{ messageId: 205, sequenceNo: 1, role: 'assistant', content: '研究回答', status: 'completed', runId: 301, citations: [{ sourceId: 2, claim: 'case evidence' }] }],
      latestRun: { ...activeRun, status: 'completed', currentStage: 'completed' },
    }))
    api.getEvidence.mockResolvedValue({
      runId: 301,
      status: 'completed',
      items: [{
        itemType: 'case', itemId: 11, sourceId: 2, title: '武汉 AI 工作室', brief: '案例摘要',
        regionName: '武汉市', geographicLevel: 'city', industry: 'software', matchReason: '匹配地区与行业',
        evidenceStatus: 'verified', publisher: '武汉市政府', sourceTitle: '案例原文',
        originalUrl: 'https://example.gov.cn/case/11', detailUrl: '/cases/11', available: true,
      }],
    })

    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(api.getEvidence).toHaveBeenCalledWith(301)
    expect(wrapper.findAll('.evidence-summary-command')).toHaveLength(1)
    expect(wrapper.find('.evidence-item').exists()).toBe(false)
    await wrapper.get('.evidence-summary-command').trigger('click')
    await nextTick()
    const drawer = wrapper.get('.assistant-inspector').element
    expect(drawer.textContent).toContain('研究资料')
    expect(drawer.textContent).toContain('武汉 AI 工作室')
    expect(drawer.querySelector('a[href="/cases/11"]')).not.toBeNull()
    expect(drawer.querySelector('.evidence-item').dataset.runId).toBe('301')
    expect(drawer.querySelector('.evidence-item').dataset.sourceId).toBe('2')
    expect(drawer.querySelector('.evidence-item').dataset.citationId).toBe('301:2:1')
    wrapper.unmount()
  })

  it('opens and closes the mobile history drawer with Escape', async () => {
    const historyRequest = ref(0)
    const restoreFocus = vi.fn()
    const wrapper = mount(AssistantView, {
      global: { provide: { 'assistant-history-control': { request: historyRequest, restoreFocus } } },
    })
    await flushPromises()

    historyRequest.value += 1
    await nextTick()
    expect(wrapper.get('.history-sidebar').classes()).toContain('is-mobile-open')
    await wrapper.get('.history-sidebar').trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(wrapper.get('.history-sidebar').classes()).not.toContain('is-mobile-open')
    expect(restoreFocus).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('opens research materials from the layout request and closes the history drawer first', async () => {
    const evidenceRequest = ref(0)
    const historyRequest = ref(0)
    const wrapper = mount(AssistantView, {
      attachTo: document.body,
      global: { provide: {
        'assistant-evidence-request': evidenceRequest,
        'assistant-history-control': { request: historyRequest, restoreFocus: vi.fn() },
      } },
    })
    await flushPromises()

    historyRequest.value += 1
    await nextTick()
    expect(wrapper.get('.history-sidebar').classes()).toContain('is-mobile-open')
    evidenceRequest.value += 1
    await nextTick()

    expect(wrapper.get('.history-sidebar').classes()).not.toContain('is-mobile-open')
    expect(document.querySelector('.assistant-inspector').textContent).toContain('研究资料')
    document.querySelector('.assistant-inspector').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(document.querySelector('.assistant-inspector')).toBeNull()
    wrapper.unmount()
  })

  it('shows a clear unauthenticated load error', async () => {
    api.getUsage.mockRejectedValue(new Error('请先登录'))
    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('请先登录')
    wrapper.unmount()
  })

  it('keeps an analytics dashboard handoff local until the user explicitly sends it', async () => {
    sessionStorage.setItem('opc_analytics_research_draft:user:42', JSON.stringify({
      version: 1,
      metricId: 'overview.verified_cases',
      metricLabel: '已核验案例',
      dataVersion: 'analytics-v1:9d18c2',
      filters: {},
      selectedBucketIds: [],
      userQuestion: '请基于“已核验案例”这一已核验数据指标，说明它对当前创业研究范围意味着什么；区分可确认事实、推断、风险和下一步行动。',
    }))
    route.query = { handoff: 'analytics' }
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.getSession.mockResolvedValue(detail({ session: session({ sessionId: 501 }) }))
    api.startAnalytics.mockResolvedValue({
      session: session({ sessionId: 501 }), messageId: 701, runId: 801, status: 'received',
      analyticsSnapshotId: 91, metricId: 'overview.verified_cases', dataVersion: 'analytics-v1:9d18c2',
    })

    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(api.start).not.toHaveBeenCalled()
    expect(api.startAnalytics).not.toHaveBeenCalled()
    expect(wrapper.get('textarea[aria-label="研究问题"]').element.value).toContain('已核验案例')
    await openConditions(wrapper)
    expect(wrapper.text()).toContain('数据看板条件')
    await vi.advanceTimersByTimeAsync(2201)
    await flushPromises()
    expect(wrapper.text()).toContain('analytics-v1:9d18c2')

    await starterForm(wrapper).trigger('submit')
    await flushPromises()

    expect(api.start).not.toHaveBeenCalled()
    expect(api.startAnalytics).toHaveBeenCalledWith(expect.objectContaining({
      metricId: 'overview.verified_cases', dataVersion: 'analytics-v1:9d18c2', filters: {}, selectedBucketIds: [],
      userQuestion: expect.stringContaining('已核验案例'),
      idempotencyKey: expect.any(String),
    }))
    expect(sessionStorage.getItem('opc_analytics_research_draft:user:42')).toBeNull()
    wrapper.unmount()
  })

  it('preserves an industry bucket and its data version through the explicit analytics start', async () => {
    sessionStorage.setItem('opc_analytics_research_draft:user:42', JSON.stringify({
      version: 1,
      metricId: 'industry.case_count',
      metricLabel: '行业案例数量：人工智能服务',
      dataVersion: 'analytics-v1:industry',
      filters: { industryTagId: 7 },
      selectedBucketIds: ['industry:7'],
      userQuestion: '请基于人工智能服务行业的已核验案例统计继续研究。',
    }))
    route.query = { handoff: 'analytics' }
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.getSession.mockResolvedValue(detail({ session: session({ sessionId: 502 }) }))
    api.startAnalytics.mockResolvedValue({
      session: session({ sessionId: 502 }), messageId: 702, runId: 802, status: 'received',
      analyticsSnapshotId: 92, metricId: 'industry.case_count', dataVersion: 'analytics-v1:industry',
    })

    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(api.startAnalytics).not.toHaveBeenCalled()
    expect(wrapper.get('textarea[aria-label="研究问题"]').element.value).toContain('人工智能服务')
    await starterForm(wrapper).trigger('submit')
    await flushPromises()

    expect(api.startAnalytics).toHaveBeenCalledWith(expect.objectContaining({
      metricId: 'industry.case_count',
      dataVersion: 'analytics-v1:industry',
      filters: { industryTagId: 7 },
      selectedBucketIds: ['industry:7'],
    }))
    expect(sessionStorage.getItem('opc_analytics_research_draft:user:42')).toBeNull()
    wrapper.unmount()
  })

  it('preserves the handoff and asks for a refreshed dashboard version when it becomes stale', async () => {
    sessionStorage.setItem('opc_analytics_research_draft:user:42', JSON.stringify({
      version: 1,
      metricId: 'overview.verified_cases',
      metricLabel: '已核验案例',
      dataVersion: 'analytics-v1:stale',
      filters: {},
      selectedBucketIds: [],
      userQuestion: '请基于“已核验案例”这一已核验数据指标，说明它对当前创业研究范围意味着什么；区分可确认事实、推断、风险和下一步行动。',
    }))
    route.query = { handoff: 'analytics' }
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.startAnalytics.mockRejectedValue(Object.assign(new Error('数据版本不再可用'), {
      businessCode: 409,
      diagnosticCode: 'ANALYTICS_DATA_VERSION_STALE',
    }))

    const wrapper = mount(AssistantView)
    await flushPromises()
    await starterForm(wrapper).trigger('submit')
    await flushPromises()

    expect(api.start).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('数据版本已更新，请返回数据看板刷新后重新带入研究。')
    expect(sessionStorage.getItem('opc_analytics_research_draft:user:42')).not.toBeNull()
    wrapper.unmount()
  })

  it('keeps new research separate from the continuing composer and reveals conditions only on demand', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(wrapper.find('.research-starter').exists()).toBe(true)
    expect(wrapper.find('form.composer').exists()).toBe(false)
    expect(wrapper.find('.assistant-inspector').exists()).toBe(false)

    await wrapper.get('[data-testid="open-research-conditions"]').trigger('click')
    await nextTick()

    expect(wrapper.get('.assistant-inspector').text()).toContain('确定本次研究边界')
    expect(wrapper.get('.assistant-inspector').text()).toContain('研究任务')
    wrapper.unmount()
  })

  it('uses the bottom composer only after a research session exists', async () => {
    localStorage.setItem(SELECTED_SESSION_KEY, '101')
    api.getSession.mockResolvedValue(detail({ session: session(), messages: [] }))
    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(wrapper.find('.research-starter').exists()).toBe(false)
    expect(wrapper.get('form.composer').text()).toContain('继续研究')
    wrapper.unmount()
  })
})
