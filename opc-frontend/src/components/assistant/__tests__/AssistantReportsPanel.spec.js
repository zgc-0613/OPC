import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  export: vi.fn(), list: vi.fn(), permanent: vi.fn(), restore: vi.fn(), save: vi.fn(), trash: vi.fn(), update: vi.fn(),
}))

vi.mock('@/api/ai', () => ({
  exportResearchReport: api.export,
  getResearchReports: api.list,
  permanentlyDeleteResearchReport: api.permanent,
  restoreResearchReport: api.restore,
  saveResearchReport: api.save,
  trashResearchReport: api.trash,
  updateResearchReport: api.update,
}))

import AssistantReportsPanel from '@/components/assistant/AssistantReportsPanel.vue'

const report = {
  reportId: 71,
  title: '已保存的研究',
  notes: '团队讨论',
  status: 'active',
  revision: 1,
  evidenceVersion: 'sha256:evidence',
  dataVersion: null,
}

describe('AssistantReportsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.list.mockResolvedValue({ items: [report], nextCursor: null, hasMore: false })
    api.save.mockResolvedValue({ ...report, reportId: 72, title: '本次研究报告' })
    api.update.mockResolvedValue(report)
    api.trash.mockResolvedValue({ ...report, status: 'trash', revision: 2 })
    api.restore.mockResolvedValue({ ...report, status: 'active', revision: 3 })
    api.permanent.mockResolvedValue({ ...report, status: 'permanently_purged', revision: 3 })
    api.export.mockResolvedValue(new Blob(['report'], { type: 'text/markdown' }))
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:report')
    globalThis.URL.revokeObjectURL = vi.fn()
  })

  it('does not read reports or create a report until the user explicitly opens and saves one', async () => {
    const wrapper = mount(AssistantReportsPanel, {
      props: { sessionId: 10, run: { status: 'completed', finalMessage: { messageId: 501 } } },
    })

    expect(api.list).not.toHaveBeenCalled()
    expect(api.save).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="open-research-reports"]').trigger('click')
    await flushPromises()
    expect(api.list).toHaveBeenCalledWith({ scope: 'active', q: '', cursor: undefined, limit: 30 })

    await wrapper.get('[data-testid="save-current-report"]').trigger('click')
    await wrapper.get('form.report-create-form input').setValue('本次研究报告')
    await wrapper.get('form.report-create-form').trigger('submit')
    await flushPromises()

    expect(api.save).toHaveBeenCalledWith(10, expect.objectContaining({
      finalMessageId: 501,
      title: '本次研究报告',
    }))
  })

  it('exports a saved active report through the authenticated API rather than a direct browser URL', async () => {
    const wrapper = mount(AssistantReportsPanel, {
      props: { sessionId: 10, run: null },
    })
    await wrapper.get('[data-testid="open-research-reports"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="export-report-71"]').trigger('click')
    await flushPromises()

    expect(api.export).toHaveBeenCalledWith(71, 'markdown')
  })

  it('labels evidence drift and only emits a user-initiated re-research request', async () => {
    api.list.mockResolvedValue({ items: [{ ...report, evidenceState: 'evidence_changed' }], nextCursor: null, hasMore: false })
    const wrapper = mount(AssistantReportsPanel, { props: { sessionId: 10, run: null } })

    await wrapper.get('[data-testid="open-research-reports"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('证据已更新')
    await wrapper.get('[data-testid="re-research-report-71"]').trigger('click')
    expect(wrapper.emitted('re-research')?.[0]).toEqual([expect.objectContaining({ reportId: 71 })])
    expect(api.save).not.toHaveBeenCalled()
  })

  it('loads the next stable report page only after an explicit command', async () => {
    api.list
      .mockResolvedValueOnce({ items: [report], nextCursor: 'opaque-cursor', hasMore: true })
      .mockResolvedValueOnce({ items: [{ ...report, reportId: 70, title: 'Older report' }], nextCursor: null, hasMore: false })
    const wrapper = mount(AssistantReportsPanel, { props: { sessionId: 10, run: null } })

    await wrapper.get('[data-testid="open-research-reports"]').trigger('click')
    await flushPromises()
    expect(api.list).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="load-more-reports"]').trigger('click')
    await flushPromises()

    expect(api.list).toHaveBeenLastCalledWith({ scope: 'active', q: '', cursor: 'opaque-cursor', limit: 30 })
    expect(wrapper.text()).toContain('Older report')
  })
})
