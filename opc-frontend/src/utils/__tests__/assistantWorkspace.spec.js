import { describe, expect, it } from 'vitest'

import { groupHistorySessions, mergeMessagePages } from '@/utils/assistantWorkspace'

describe('assistant workspace history', () => {
  it('groups pinned and recent sessions without duplicating pinned rows', () => {
    const now = new Date('2026-07-25T12:00:00+08:00')
    const groups = groupHistorySessions([
      { sessionId: 1, title: '置顶研究', pinned: true, lastMessageAt: '2026-07-01T10:00:00+08:00' },
      { sessionId: 2, title: '今天', pinned: false, lastMessageAt: '2026-07-25T09:00:00+08:00' },
      { sessionId: 3, title: '本周', pinned: false, lastMessageAt: '2026-07-21T09:00:00+08:00' },
      { sessionId: 4, title: '本月', pinned: false, lastMessageAt: '2026-07-10T09:00:00+08:00' },
      { sessionId: 5, title: '更早', pinned: false, lastMessageAt: '2026-05-10T09:00:00+08:00' },
    ], now)

    expect(groups.map((group) => group.label)).toEqual(['置顶', '今天', '最近 7 天', '最近 30 天', '更早'])
    expect(groups.flatMap((group) => group.items).map((item) => item.sessionId))
      .toEqual([1, 2, 3, 4, 5])
  })

  it('merges older message pages in stable sequence order without duplicates', () => {
    const merged = mergeMessagePages(
      [{ messageId: 4, sequenceNo: 4 }, { messageId: 5, sequenceNo: 5 }],
      [{ messageId: 2, sequenceNo: 2 }, { messageId: 3, sequenceNo: 3 }, { messageId: 4, sequenceNo: 4 }],
    )

    expect(merged.map((message) => message.sequenceNo)).toEqual([2, 3, 4, 5])
  })
})
