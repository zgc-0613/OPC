import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  archive: vi.fn(), cancel: vi.fn(), checkReadiness: vi.fn(), create: vi.fn(), getCapabilities: vi.fn(),
  getEvidence: vi.fn(), getHistory: vi.fn(), getMessages: vi.fn(), getRun: vi.fn(), getSession: vi.fn(), getUsage: vi.fn(),
  purge: vi.fn(), resolveIndustry: vi.fn(), restore: vi.fn(), send: vi.fn(), start: vi.fn(), trash: vi.fn(), unarchive: vi.fn(), update: vi.fn(),
  getRegions: vi.fn(), getIndustryTags: vi.fn(),
}))
const auth = vi.hoisted(() => ({ profile: { userId: 42, username: 'researcher' } }))

vi.mock('@/api/ai', () => ({
  archiveResearchSessionExplicit: api.archive, cancelResearchRun: api.cancel,
  checkEntrepreneurshipReadiness: api.checkReadiness, createResearchSession: api.create,
  getAiCapabilities: api.getCapabilities, getResearchHistory: api.getHistory,
  getResearchMessages: api.getMessages, getResearchRun: api.getRun, getResearchSession: api.getSession,
  getResearchRunEvidence: api.getEvidence,
  getResearchUsage: api.getUsage, permanentlyDeleteResearchSession: api.purge,
  restoreResearchSession: api.restore, sendResearchMessage: api.send,
  resolveIndustryWithAi: api.resolveIndustry,
  startResearchSession: api.start,
  trashResearchSession: api.trash, unarchiveResearchSession: api.unarchive,
  updateResearchSession: api.update,
}))
vi.mock('@/api/region', () => ({ getRegions: api.getRegions }))
vi.mock('@/api/tag', () => ({ getIndustryTags: api.getIndustryTags }))
vi.mock('@/api/auth', () => ({ getUserProfile: () => auth.profile }))

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

describe('AssistantView research workspace', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    document.body.innerHTML = ''
    Element.prototype.scrollTo = vi.fn()
    window.matchMedia = vi.fn(() => ({ matches: false }))
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: vi.fn().mockResolvedValue() } })

    api.getRegions.mockResolvedValue([{ id: 42, name: '湖北省' }])
    api.getIndustryTags.mockResolvedValue([{ tagId: 7, name: '人工智能应用' }])
    api.getCapabilities.mockResolvedValue({ provider: { available: true, provider: 'deepseek', model: 'deepseek-v4-flash' }, capabilities: [{ id: 'agent-runtime', available: true }] })
    api.getUsage.mockResolvedValue({ usedTokens: 120, limitTokens: 10000, remainingTokens: 9880, unlimited: false })
    api.getHistory.mockResolvedValue({ items: [session()], nextCursor: null, hasMore: false })
    api.getSession.mockResolvedValue(detail())
    api.getRun.mockResolvedValue(activeRun)
    api.getEvidence.mockResolvedValue({ runId: 301, status: 'completed', items: [], groups: {} })
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

    expect(wrapper.get('details.profile-editor').attributes('open')).toBeUndefined()
    expect(wrapper.get('textarea[aria-label="研究问题"]').exists()).toBe(true)
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
    const selects = wrapper.findAll('.profile-fields select')
    await selects[1].setValue('42')
    await wrapper.get('[role="combobox"]').setValue('人工智能应用行业')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()

    expect(api.resolveIndustry).toHaveBeenCalledWith('人工智能应用行业')
    expect(api.checkReadiness).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('建议匹配“人工智能应用”')
    expect(wrapper.get('textarea[aria-label="研究问题"]').attributes('disabled')).toBeDefined()

    const accept = wrapper.findAll('.industry-resolution-actions button').find((button) => button.text().includes('采用'))
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
    const region = wrapper.findAll('.profile-fields select')[1]
    const industry = wrapper.get('[role="combobox"]')

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
    await wrapper.findAll('.profile-fields select')[1].setValue('42')
    await wrapper.get('[role="combobox"]').setValue('AI 咨询服务')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    const reject = wrapper.findAll('.industry-resolution-actions button').find((button) => button.text().includes('保留原始输入'))
    await reject.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('未采用建议匹配')
    await wrapper.get('.profile-fields input[maxlength="200"]').setValue('验证首批客户')
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
    await wrapper.findAll('.profile-fields select')[1].setValue('42')
    const combobox = wrapper.get('[role="combobox"]')
    await combobox.setValue('旧行业')
    await vi.advanceTimersByTimeAsync(320)
    await combobox.setValue('新行业')
    await vi.advanceTimersByTimeAsync(320)

    second.resolve({ tagId: 8, name: '智能制造', method: 'alias', confidence: 1, requiresConfirmation: false })
    await flushPromises()
    first.resolve({ tagId: 7, name: '人工智能应用', method: 'fuzzy', confidence: 0.8, requiresConfirmation: true })
    await flushPromises()

    expect(wrapper.get('[role="combobox"]').element.value).toBe('智能制造')
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
    const region = wrapper.findAll('.profile-fields select')[1]
    await region.setValue('42')
    await wrapper.get('[role="combobox"]').setValue('人工智能应用')
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

    const selects = wrapper.findAll('.profile-fields select')
    await selects[1].setValue('42')
    await wrapper.get('[role="combobox"]').setValue('人工智能应用')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()

    expect(api.checkReadiness).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('证据充分')
    api.checkReadiness.mockClear()

    await selects[0].setValue('small_team')
    await selects[2].setValue('growth')
    await selects[3].setValue('100k_500k')
    await wrapper.get('.field-goal input').setValue('验证首批付费客户')
    await wrapper.get('.field-resources textarea').setValue('已有产品原型')
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
    expect(wrapper.findAll('form.composer')).toHaveLength(1)
    expect(wrapper.get('button.send-command').attributes('aria-label')).toBe('开始本次研究')

    const selects = wrapper.findAll('.profile-fields select')
    await selects[1].setValue('42')
    await wrapper.get('[role="combobox"]').setValue('人工智能应用')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    await wrapper.get('textarea[aria-label="研究问题"]').setValue('请研究湖北人工智能创业机会')
    await wrapper.get('form.composer').trigger('submit')
    await flushPromises()

    expect(api.start).toHaveBeenCalledWith(expect.objectContaining({
      profile: expect.objectContaining({ regionId: 42, industryTagId: 7, industry: '人工智能应用' }),
      content: '请研究湖北人工智能创业机会',
      idempotencyKey: expect.stringMatching(/^[A-Za-z0-9_-]{8,64}$/),
    }))
    expect(api.create).not.toHaveBeenCalled()
    expect(api.send).not.toHaveBeenCalled()
    expect(wrapper.findAll('form.composer')).toHaveLength(1)
    expect(wrapper.get('button.send-command').attributes('aria-label')).toBe('发送研究问题')
    wrapper.unmount()
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
    await first.findAll('.profile-fields select')[1].setValue('42')
    await first.get('[role="combobox"]').setValue('人工智能应用')
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    await first.get('textarea[aria-label="研究问题"]').setValue('请研究湖北人工智能创业机会')
    await first.get('form.composer').trigger('submit')
    await flushPromises()
    const firstKey = api.start.mock.calls[0][0].idempotencyKey
    expect(first.get('[role="alert"]').text()).toContain('timeout')
    first.unmount()

    const second = mount(AssistantView)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(420)
    await flushPromises()
    expect(second.get('textarea[aria-label="研究问题"]').element.value).toBe('请研究湖北人工智能创业机会')
    await second.get('form.composer').trigger('submit')
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

    expect(wrapper.get('#assistant-title').text()).toBe('研究 B')
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
    expect(document.querySelector('.citation-drawer')).not.toBeNull()

    const sessionBRow = wrapper.findAll('.history-row-main').find((row) => row.text().includes('研究 B'))
    await sessionBRow.trigger('click')
    await flushPromises()
    expect(document.querySelector('.citation-drawer')).toBeNull()

    processA.resolve({ ...activeRun, visibleProgress: '会话 A 的研究过程' })
    await flushPromises()
    expect(document.querySelector('.citation-drawer')).toBeNull()
    expect(wrapper.get('#assistant-title').text()).toBe('研究 B')
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

    expect(wrapper.get('#assistant-title').text()).toBe('研究 B')
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

    expect(wrapper.get('#assistant-title').text()).toBe('研究 B')
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
    const drawer = document.querySelector('.citation-drawer')
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
    expect(document.querySelector('.citation-drawer').textContent).toContain('历史政策证据')
    expect(document.querySelector('.citation-drawer').textContent).not.toContain('最新运行证据')
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
    expect(wrapper.text()).toContain('研究资料')
    expect(wrapper.text()).toContain('武汉 AI 工作室')
    expect(wrapper.get('a[href="/cases/11"]').exists()).toBe(true)
    expect(wrapper.get('.evidence-item').attributes('data-run-id')).toBe('301')
    expect(wrapper.get('.evidence-item').attributes('data-source-id')).toBe('2')
    expect(wrapper.get('.evidence-item').attributes('data-citation-id')).toBe('301:2:1')
    wrapper.unmount()
  })

  it('opens and closes the mobile history drawer with Escape', async () => {
    const wrapper = mount(AssistantView)
    await flushPromises()

    await wrapper.get('.mobile-history-command').trigger('click')
    expect(wrapper.get('.history-sidebar').classes()).toContain('is-mobile-open')
    await wrapper.get('.history-sidebar').trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(wrapper.get('.history-sidebar').classes()).not.toContain('is-mobile-open')
    wrapper.unmount()
  })

  it('shows a clear unauthenticated load error', async () => {
    api.getUsage.mockRejectedValue(new Error('请先登录'))
    const wrapper = mount(AssistantView)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('请先登录')
    wrapper.unmount()
  })
})
