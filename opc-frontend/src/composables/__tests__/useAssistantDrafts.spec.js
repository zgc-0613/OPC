import { beforeEach, describe, expect, it } from 'vitest'

import { createAssistantDraftStore } from '@/composables/useAssistantDrafts'

describe('assistant drafts', () => {
  beforeEach(() => localStorage.clear())

  it('keeps an independent unsent draft for each session and the new research page', () => {
    const drafts = createAssistantDraftStore(localStorage)
    drafts.save(101, '会话一草稿')
    drafts.save(102, '会话二草稿')
    drafts.save(null, '新研究草稿')

    expect(drafts.load(101)).toBe('会话一草稿')
    expect(drafts.load(102)).toBe('会话二草稿')
    expect(drafts.load(null)).toBe('新研究草稿')
    drafts.clear(101)
    expect(drafts.load(101)).toBe('')
  })

  it('does not store server history or oversized content', () => {
    const drafts = createAssistantDraftStore(localStorage)
    drafts.save(101, 'a'.repeat(9000))

    expect(drafts.load(101).length).toBe(8000)
    expect([...Array(localStorage.length)].map((_, index) => localStorage.key(index)))
      .toEqual(['opc_assistant:anonymous:draft:101'])
  })

  it('isolates drafts for different authenticated users sharing one browser', () => {
    const userA = createAssistantDraftStore(localStorage, 'user:42')
    const userB = createAssistantDraftStore(localStorage, 'user:84')

    userA.save(101, '用户 A 的未发送研究')
    userB.save(101, '用户 B 的未发送研究')

    expect(userA.load(101)).toBe('用户 A 的未发送研究')
    expect(userB.load(101)).toBe('用户 B 的未发送研究')
  })
})
