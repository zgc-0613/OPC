import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ get: vi.fn(), update: vi.fn() }))
vi.mock('@/api/ai', () => ({
  getResearchRunFeedback: api.get,
  updateResearchRunFeedback: api.update,
}))

import AssistantRunFeedback from '@/components/assistant/AssistantRunFeedback.vue'

describe('AssistantRunFeedback', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.get.mockResolvedValue(null)
    api.update.mockResolvedValue({ runId: 30, rating: 'helpful', reason: 'good_evidence', comment: '', revision: 1 })
  })

  it('only reads and submits feedback after an eligible user explicitly opens it', async () => {
    const wrapper = mount(AssistantRunFeedback, {
      props: { run: { runId: 30, feedbackEligible: true } },
    })

    expect(api.get).not.toHaveBeenCalled()
    await wrapper.get('[data-testid="open-run-feedback"]').trigger('click')
    await flushPromises()
    expect(api.get).toHaveBeenCalledWith(30)
    expect(wrapper.findAll('input[type="radio"]').map((input) => input.attributes('name'))).toEqual([
      'research-run-feedback-30',
      'research-run-feedback-30',
    ])

    await wrapper.get('input[value="helpful"]').setValue()
    await wrapper.get('select').setValue('good_evidence')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.update).toHaveBeenCalledWith(30, expect.objectContaining({
      rating: 'helpful', reason: 'good_evidence', expectedRevision: 0,
    }))
  })

  it('does not expose feedback affordance for a run the server marked ineligible', () => {
    const wrapper = mount(AssistantRunFeedback, { props: { run: { runId: 30, feedbackEligible: false } } })
    expect(wrapper.find('[data-testid="open-run-feedback"]').exists()).toBe(false)
  })

  it('reloads and resets local feedback state when the run changes', async () => {
    api.get
      .mockResolvedValueOnce({ rating: 'not_helpful', reason: 'missing_evidence', comment: 'old run', revision: 4 })
      .mockResolvedValueOnce(null)
    const wrapper = mount(AssistantRunFeedback, { props: { run: { runId: 30, feedbackEligible: true } } })

    await wrapper.get('[data-testid="open-run-feedback"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('textarea').element.value).toBe('old run')

    await wrapper.setProps({ run: { runId: 31, feedbackEligible: true } })
    await flushPromises()

    expect(api.get).toHaveBeenLastCalledWith(31)
    expect(wrapper.get('textarea').element.value).toBe('')
    expect(wrapper.get('input[value="helpful"]').element.checked).toBe(true)
  })
})
