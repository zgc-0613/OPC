import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

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
import reportsPanelSource from '@/components/assistant/AssistantReportsPanel.vue?raw'

const report = {
  reportId: 71,
  title: '已保存的研究',
  notes: '团队讨论',
  status: 'active',
  revision: 1,
  evidenceVersion: 'sha256:evidence',
  dataVersion: null,
}
let downloadedLink

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
    downloadedLink = null
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function clickDownload() {
      downloadedLink = { download: this.download, href: this.href, rel: this.rel }
    })
  })

  afterEach(() => vi.restoreAllMocks())

  it('leaves a deliberate gutter between the inspector divider and report commands', () => {
    expect(reportsPanelSource).toMatch(/\.reports-panel\s*\{[^}]*padding:\s*16px\s+24px\s+16px/)
    expect(reportsPanelSource).toMatch(/@media\s*\(max-width:\s*720px\)[^{]*\{[^}]*\.reports-panel\s*\{[^}]*padding:\s*14px\s+16px\s+14px/)
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
    expect(wrapper.get('.report-scope button').attributes('aria-pressed')).toBe('true')
    expect(wrapper.findAll('.report-scope button')[1].attributes('aria-pressed')).toBe('false')

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
    api.export.mockResolvedValue(new Blob(['report']))
    const wrapper = mount(AssistantReportsPanel, {
      props: { sessionId: 10, run: null },
    })
    await wrapper.get('[data-testid="open-research-reports"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="export-report-71"]').trigger('click')
    await flushPromises()

    expect(api.export).toHaveBeenCalledWith(71, 'markdown')
    expect(globalThis.URL.createObjectURL).toHaveBeenCalledWith(expect.objectContaining({ type: 'text/markdown' }))
    expect(downloadedLink?.download).toBe('已保存的研究.md')
  })

  it('offers an accessible choice for every supported export format', async () => {
    const wrapper = mount(AssistantReportsPanel, { props: { sessionId: 10, run: null } })

    await wrapper.get('[data-testid="open-research-reports"]').trigger('click')
    await flushPromises()

    const format = wrapper.get('[data-testid="export-format-71"]')
    expect(format.attributes('aria-label')).toBe('已保存的研究的导出格式')
    expect(format.find('option[value="markdown"]').text()).toBe('Markdown (.md)')
    expect(format.find('option[value="html"]').text()).toBe('HTML (.html)')
    expect(format.find('option[value="pdf"]').text()).toBe('PDF (.pdf)')
    expect(format.find('option[value="pdf"]').attributes()).not.toHaveProperty('disabled')
  })

  it('downloads the selected HTML export with the expected MIME type and a safe filename', async () => {
    const htmlReport = { ...report, title: '市场/报告:2026?' }
    api.list.mockResolvedValue({ items: [htmlReport], nextCursor: null, hasMore: false })
    api.export.mockResolvedValue(new Blob(['<!doctype html>'], { type: 'text/html' }))
    const wrapper = mount(AssistantReportsPanel, { props: { sessionId: 10, run: null } })

    await wrapper.get('[data-testid="open-research-reports"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="export-format-71"]').setValue('html')
    await wrapper.get('[data-testid="export-report-71"]').trigger('click')
    await flushPromises()

    expect(api.export).toHaveBeenCalledWith(71, 'html')
    expect(globalThis.URL.createObjectURL).toHaveBeenCalledWith(expect.objectContaining({ type: 'text/html' }))
    expect(downloadedLink).toEqual({
      download: '市场_报告_2026_.html',
      href: 'blob:report',
      rel: 'noopener',
    })
  })

  it('downloads the selected PDF through the authenticated API with the expected MIME and filename', async () => {
    const pdfReport = { ...report, title: '市场/报告:2026?' }
    api.list.mockResolvedValue({ items: [pdfReport], nextCursor: null, hasMore: false })
    api.export.mockResolvedValue(new Blob(['%PDF-1.7'], { type: 'application/pdf' }))
    const wrapper = mount(AssistantReportsPanel, { props: { sessionId: 10, run: null } })

    await wrapper.get('[data-testid="open-research-reports"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="export-format-71"]').setValue('pdf')
    await wrapper.get('[data-testid="export-report-71"]').trigger('click')
    await flushPromises()

    expect(api.export).toHaveBeenCalledWith(71, 'pdf')
    expect(globalThis.URL.createObjectURL).toHaveBeenCalledWith(expect.objectContaining({ type: 'application/pdf' }))
    expect(downloadedLink).toEqual({
      download: '市场_报告_2026_.pdf',
      href: 'blob:report',
      rel: 'noopener',
    })
  })

  it('announces export failures without starting a browser download', async () => {
    api.export.mockRejectedValue(new Error('服务器拒绝导出'))
    const wrapper = mount(AssistantReportsPanel, { props: { sessionId: 10, run: null } })

    await wrapper.get('[data-testid="open-research-reports"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="export-report-71"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('服务器拒绝导出')
    expect(globalThis.URL.createObjectURL).not.toHaveBeenCalled()
    expect(downloadedLink).toBeNull()
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
