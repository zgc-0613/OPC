const DAY_MS = 24 * 60 * 60 * 1000

export function groupHistorySessions(items, now = new Date()) {
  const buckets = [
    { label: '置顶', items: [] },
    { label: '今天', items: [] },
    { label: '最近 7 天', items: [] },
    { label: '最近 30 天', items: [] },
    { label: '更早', items: [] },
  ]
  const today = startOfDay(now).getTime()

  for (const item of items || []) {
    if (item.pinned) {
      buckets[0].items.push(item)
      continue
    }
    const activity = new Date(item.lastMessageAt || item.updatedAt || item.createdAt || 0)
    const days = Number.isNaN(activity.getTime())
      ? Number.POSITIVE_INFINITY
      : Math.max(0, Math.floor((today - startOfDay(activity).getTime()) / DAY_MS))
    if (days === 0) buckets[1].items.push(item)
    else if (days < 7) buckets[2].items.push(item)
    else if (days < 30) buckets[3].items.push(item)
    else buckets[4].items.push(item)
  }

  return buckets.filter((bucket) => bucket.items.length)
}

export function mergeMessagePages(current, older) {
  const byKey = new Map()
  for (const message of [...(older || []), ...(current || [])]) {
    const key = message.messageId != null ? `id:${message.messageId}` : `seq:${message.sequenceNo}`
    byKey.set(key, message)
  }
  return [...byKey.values()].sort((a, b) => {
    const sequence = Number(a.sequenceNo || 0) - Number(b.sequenceNo || 0)
    if (sequence !== 0) return sequence
    return Number(a.messageId || 0) - Number(b.messageId || 0)
  })
}

function startOfDay(value) {
  const date = new Date(value)
  date.setHours(0, 0, 0, 0)
  return date
}
