import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  archive: vi.fn(), cancel: vi.fn(), checkReadiness: vi.fn(), create: vi.fn(), getCapabilities: vi.fn(),
  getHistory: vi.fn(), getMessages: vi.fn(), getRun: vi.fn(), getSession: vi.fn(), getUsage: vi.fn(),
  purge: vi.fn(), restore: vi.fn(), send: vi.fn(), trash: vi.fn(), unarchive: vi.fn(), update: vi.fn(),
  getRegions: vi.fn(), getIndustryTags: vi.fn(),
}))

vi.mock('@/api/ai', () => ({
  archiveResearchSessionExplicit: api.archive, cancelResearchRun: api.cancel,
  checkEntrepreneurshipReadiness: api.checkReadiness, createResearchSession: api.create,
  getAiCapabilities: api.getCapabilities, getResearchHistory: api.getHistory,
  getResearchMessages: api.getMessages, getResearchRun: api.getRun, getResearchSession: api.getSession,
  getResearchUsage: api.getUsage, permanentlyDeleteResearchSession: api.purge,
  restoreResearchSession: api.restore, sendResearchMessage: api.send,
  trashResearchSession: api.trash, unarchiveResearchSession: api.unarchive,
  updateResearchSession: api.update,
}))
vi.mock('@/api/region', () => ({ getRegions: api.getRegions }))
vi.mock('@/api/tag', () => ({ getIndustryTags: api.getIndustryTags }))

import AssistantView from '@/views/AssistantView.vue'
import assistantSource from '@/views/AssistantView.vue?raw'

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
const detail = (overrides = {}) => ({
  session: session(), messages: [], nextBeforeSequence: null, hasMoreMessages: false,
  activeRun: null, latestRun: null, ...overrides,
})

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

  it('restores the selected session and resumes its active run after refresh', async () => {
    localStorage.setItem('opc_agent_session_id', '101')
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
    localStorage.setItem('opc_agent_session_id', '101')
    api.getSession.mockResolvedValue(detail({ session: session({ status: 'archived', archivedAt: '2026-07-25T11:00:00' }) }))

    const wrapper = mount(AssistantView)
    await flushPromises()

    const textarea = wrapper.get('textarea[aria-label="研究问题"]')
    expect(textarea.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('归档会话仅供查阅')
    wrapper.unmount()
  })

  it('does not create an empty server session until the first question is sent', async () => {
    api.getHistory.mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
    api.create.mockResolvedValue(session({ sessionId: 106, title: '新研究', profile: { regionId: 42, industry: '人工智能应用' } }))
    api.send.mockResolvedValue({ sessionId: 106, messageId: 202, runId: 302, status: 'received' })
    api.getSession.mockResolvedValue(detail({ session: session({ sessionId: 106, title: '研究湖北人工智能机会' }) }))

    const wrapper = mount(AssistantView)
    await flushPromises()
    expect(api.create).not.toHaveBeenCalled()

    const selects = wrapper.findAll('.profile-fields select')
    await selects[1].setValue('42')
    await wrapper.get('input[list="assistant-industry-options"]').setValue('人工智能应用')
    await wrapper.get('textarea[aria-label="研究问题"]').setValue('请研究湖北人工智能创业机会')
    await wrapper.get('form.composer').trigger('submit')
    await flushPromises()

    expect(api.create).toHaveBeenCalledWith({ profile: expect.objectContaining({ regionId: 42, industryTagId: 7, industry: '人工智能应用' }) })
    expect(api.send).toHaveBeenCalledWith(106, expect.objectContaining({ content: '请研究湖北人工智能创业机会', idempotencyKey: expect.stringMatching(/^[A-Za-z0-9_-]{8,64}$/) }))
    expect(api.create.mock.invocationCallOrder[0]).toBeLessThan(api.send.mock.invocationCallOrder[0])
    wrapper.unmount()
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

  it('loads older messages with stable de-duplication', async () => {
    localStorage.setItem('opc_agent_session_id', '101')
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

  it('preserves the last known run during a temporary polling failure', async () => {
    localStorage.setItem('opc_agent_session_id', '101')
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

  it('opens verified citations in a hardened detail drawer', async () => {
    localStorage.setItem('opc_agent_session_id', '101')
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
