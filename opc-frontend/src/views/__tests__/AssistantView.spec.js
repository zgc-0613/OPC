import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  archiveSession: vi.fn(),
  cancelRun: vi.fn(),
  checkReadiness: vi.fn(),
  createSession: vi.fn(),
  getCapabilities: vi.fn(),
  getRun: vi.fn(),
  getSession: vi.fn(),
  getSessions: vi.fn(),
  resolveIndustry: vi.fn(),
  sendMessage: vi.fn(),
  getRegions: vi.fn(),
  getIndustryTags: vi.fn(),
}))

vi.mock('@/api/ai', () => ({
  archiveResearchSession: api.archiveSession,
  cancelResearchRun: api.cancelRun,
  checkEntrepreneurshipReadiness: api.checkReadiness,
  createResearchSession: api.createSession,
  getAiCapabilities: api.getCapabilities,
  getResearchRun: api.getRun,
  getResearchSession: api.getSession,
  getResearchSessions: api.getSessions,
  resolveIndustryWithAi: api.resolveIndustry,
  sendResearchMessage: api.sendMessage,
}))
vi.mock('@/api/region', () => ({ getRegions: api.getRegions }))
vi.mock('@/api/tag', () => ({ getIndustryTags: api.getIndustryTags }))

import AssistantView from '@/views/AssistantView.vue'

const activeRun = {
  runId: 301,
  sessionId: 101,
  status: 'running',
  currentStage: 'tool_running',
  visibleProgress: '正在检索政策',
  stepCount: 1,
  toolCallCount: 1,
  tools: [],
  tokenUsage: { promptTokens: 12, completionTokens: 3, totalTokens: 15 },
}

describe('AssistantView Agent Runtime', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    Element.prototype.scrollTo = vi.fn()
    window.matchMedia = vi.fn(() => ({ matches: false }))

    api.getRegions.mockResolvedValue([{ id: 42, name: '湖北省' }])
    api.getIndustryTags.mockResolvedValue([{ tagId: 7, name: '人工智能应用' }])
    api.getCapabilities.mockResolvedValue({
      provider: { available: true, provider: 'deepseek', model: 'deepseek-chat' },
      capabilities: [{ id: 'agent-runtime', available: true }],
    })
    api.getSessions.mockResolvedValue([
      { sessionId: 101, title: '湖北省 · 人工智能应用', status: 'active' },
    ])
    api.getSession.mockResolvedValue({
      session: { sessionId: 101, title: '湖北省 · 人工智能应用', status: 'active' },
      messages: [{
        messageId: 201,
        role: 'user',
        content: '研究湖北人工智能创业机会',
        status: 'completed',
        citations: [],
        createdAt: '2026-07-25T10:00:00',
      }],
      activeRun,
    })
    api.getRun.mockResolvedValue({ ...activeRun, visibleProgress: '正在核验来源' })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('restores the active session after refresh and resumes run polling', async () => {
    localStorage.setItem('opc_agent_session_id', '101')
    const wrapper = mount(AssistantView, {
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })

    await flushPromises()

    expect(api.getSession).toHaveBeenCalledWith(101)
    expect(wrapper.text()).toContain('研究湖北人工智能创业机会')
    expect(wrapper.text()).toContain('正在检索政策')

    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(api.getRun).toHaveBeenCalledWith(301)
    expect(wrapper.text()).toContain('正在核验来源')
    wrapper.unmount()
  })

  it('prevents composing a new message in an archived session', async () => {
    api.getSessions.mockResolvedValue([
      { sessionId: 101, title: '已归档研究', status: 'archived' },
    ])
    api.getSession.mockResolvedValue({
      session: { sessionId: 101, title: '已归档研究', status: 'archived' },
      messages: [],
      activeRun: null,
    })
    localStorage.setItem('opc_agent_session_id', '101')

    const wrapper = mount(AssistantView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()

    expect(wrapper.get('#assistant-message').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.assistant-composer').text()).toContain('已归档')
    wrapper.unmount()
  })

  it('keeps the cancelled terminal state visible after refreshing the session', async () => {
    api.cancelRun.mockResolvedValue({
      ...activeRun,
      status: 'cancelled',
      currentStage: 'cancelled',
      visibleProgress: '已取消运行',
    })
    api.getSession
      .mockResolvedValueOnce({
        session: { sessionId: 101, title: '湖北研究', status: 'active' },
        messages: [],
        activeRun,
      })
      .mockResolvedValue({
        session: { sessionId: 101, title: '湖北研究', status: 'active' },
        messages: [],
        activeRun: null,
      })

    const wrapper = mount(AssistantView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()
    await wrapper.get('.assistant-run-status button').trigger('click')
    await flushPromises()

    expect(api.cancelRun).toHaveBeenCalledWith(301)
    expect(wrapper.get('.assistant-run-error').text()).toContain('研究运行已取消')
    wrapper.unmount()
  })

  it('submits a message asynchronously and polls the returned run', async () => {
    api.getSession.mockResolvedValue({
      session: { sessionId: 101, title: '湖北研究', status: 'active' },
      messages: [],
      activeRun: null,
    })
    api.sendMessage.mockResolvedValue({ sessionId: 101, messageId: 202, runId: 302, status: 'received' })
    api.getRun.mockResolvedValue({
      ...activeRun,
      runId: 302,
      currentStage: 'tool_running',
      visibleProgress: '正在检索案例',
      tools: [{ toolCallId: 1, toolName: 'search_cases', status: 'running', evidenceCount: 0 }],
    })
    const wrapper = mount(AssistantView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()

    await wrapper.get('#assistant-message').setValue('研究湖北人工智能创业机会')
    await wrapper.get('.assistant-composer').trigger('submit')
    await flushPromises()

    expect(api.sendMessage).toHaveBeenCalledOnce()
    const payload = api.sendMessage.mock.calls[0][1]
    expect(api.sendMessage.mock.calls[0][0]).toBe(101)
    expect(payload.content).toBe('研究湖北人工智能创业机会')
    expect(payload.idempotencyKey).toMatch(/^[A-Za-z0-9_-]{8,64}$/)

    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(api.getRun).toHaveBeenCalledWith(302)
    expect(wrapper.text()).toContain('正在检索案例')
    expect(wrapper.text()).toContain('检索案例')
    wrapper.unmount()
  })

  it('renders one clarification question returned by the run', async () => {
    const baseDetail = {
      session: { sessionId: 101, title: '待补充研究', status: 'active' },
      messages: [],
      activeRun: null,
    }
    const clarifiedDetail = {
      ...baseDetail,
      messages: [{
        messageId: 203,
        role: 'assistant',
        content: '您希望这次研究聚焦哪个地区？',
        status: 'completed',
        citations: [],
      }],
    }
    api.getSession
      .mockResolvedValueOnce(baseDetail)
      .mockResolvedValueOnce(baseDetail)
      .mockResolvedValue(clarifiedDetail)
    api.sendMessage.mockResolvedValue({ sessionId: 101, messageId: 202, runId: 303, status: 'received' })
    api.getRun.mockResolvedValue({
      runId: 303,
      sessionId: 101,
      status: 'clarification_needed',
      currentStage: 'clarification_needed',
      visibleProgress: '需要补充一项信息',
      tools: [],
      citations: [],
    })
    const wrapper = mount(AssistantView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()
    await wrapper.get('#assistant-message').setValue('请帮我研究创业机会')
    await wrapper.get('.assistant-composer').trigger('submit')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(wrapper.text()).toContain('您希望这次研究聚焦哪个地区？')
    expect(api.getRun).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('uses a fresh idempotency key when retrying a failed run', async () => {
    api.getSession.mockResolvedValue({
      session: { sessionId: 101, title: '失败重试', status: 'active' },
      messages: [],
      activeRun: null,
    })
    api.sendMessage
      .mockResolvedValueOnce({ sessionId: 101, messageId: 202, runId: 304, status: 'received' })
      .mockResolvedValueOnce({ sessionId: 101, messageId: 204, runId: 305, status: 'received' })
    api.getRun.mockResolvedValue({
      runId: 304,
      sessionId: 101,
      status: 'failed',
      currentStage: 'failed',
      diagnosticCode: 'PROVIDER_TIMEOUT',
      visibleProgress: '研究运行未完成',
      tools: [],
    })
    const wrapper = mount(AssistantView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()
    await wrapper.get('#assistant-message').setValue('研究湖北人工智能创业机会')
    await wrapper.get('.assistant-composer').trigger('submit')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    await wrapper.get('.assistant-run-error button').trigger('click')
    await flushPromises()

    expect(api.sendMessage).toHaveBeenCalledTimes(2)
    expect(api.sendMessage.mock.calls[1][1].idempotencyKey)
      .not.toBe(api.sendMessage.mock.calls[0][1].idempotencyKey)
    wrapper.unmount()
  })

  it('opens numbered verified citations without exposing raw tool JSON', async () => {
    api.getSession.mockResolvedValue({
      session: { sessionId: 101, title: '引用研究', status: 'active' },
      messages: [{
        messageId: 205,
        role: 'assistant',
        content: '湖北省有一项已核验政策支持。',
        status: 'completed',
        citations: [{
          sourceId: 8,
          title: '湖北省政策原文',
          publisher: '湖北省人民政府',
          url: 'https://example.gov.cn/policy',
          claim: '该政策提供创业支持。',
        }],
      }],
      activeRun: null,
    })
    const wrapper = mount(AssistantView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()
    await wrapper.get('.assistant-citation-trigger').trigger('click')

    expect(wrapper.get('.assistant-evidence-drawer').text()).toContain('01')
    expect(wrapper.get('.assistant-evidence-drawer').text()).toContain('湖北省政策原文')
    expect(wrapper.get('.assistant-evidence-drawer a').attributes('href')).toBe('https://example.gov.cn/policy')
    expect(wrapper.text()).not.toContain('{"items"')
    wrapper.unmount()
  })

  it('creates a new session from the current bounded research profile', async () => {
    api.getSessions.mockResolvedValue([])
    api.createSession.mockResolvedValue({ sessionId: 106, title: '湖北省 · 人工智能应用', status: 'active' })
    api.getSession.mockResolvedValue({
      session: { sessionId: 106, title: '湖北省 · 人工智能应用', status: 'active' },
      messages: [],
      activeRun: null,
    })
    const wrapper = mount(AssistantView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()
    const selects = wrapper.findAll('.assistant-profile-form select')
    await selects[1].setValue('42')
    await wrapper.get('.assistant-industry-input input').setValue('人工智能应用')
    await wrapper.get('.assistant-profile-form').trigger('submit')
    await flushPromises()

    expect(api.createSession).toHaveBeenCalledWith(expect.objectContaining({
      title: '湖北省 · 人工智能应用',
      profile: expect.objectContaining({ regionId: 42, industry: '人工智能应用' }),
    }))
    expect(localStorage.getItem('opc_agent_session_id')).toBe('106')
    wrapper.unmount()
  })

  it('shows a clear unauthenticated load error', async () => {
    api.getSessions.mockRejectedValue(new Error('请先登录'))
    const wrapper = mount(AssistantView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('请先登录')
    wrapper.unmount()
  })

  it.each([
    ['synthesizing', '正在整理回答'],
    ['tool_running', '正在检索并核验证据'],
  ])('renders the %s visible progress state', async (stage, progress) => {
    api.getSession.mockResolvedValue({
      session: { sessionId: 101, title: '阶段状态', status: 'active' },
      messages: [],
      activeRun: { ...activeRun, currentStage: stage, visibleProgress: progress },
    })
    const wrapper = mount(AssistantView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()

    expect(wrapper.get('.assistant-run-status').text()).toContain(progress)
    wrapper.unmount()
  })

  it('uses reduced motion scrolling when the user requests it', async () => {
    window.matchMedia = vi.fn(() => ({ matches: true }))
    api.getSession.mockResolvedValue({
      session: { sessionId: 101, title: '低动效', status: 'active' },
      messages: [{ messageId: 1, role: 'user', content: '问题', status: 'completed', citations: [] }],
      activeRun: null,
    })
    const wrapper = mount(AssistantView, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()

    expect(Element.prototype.scrollTo).toHaveBeenCalledWith(expect.objectContaining({ behavior: 'auto' }))
    wrapper.unmount()
  })
})
