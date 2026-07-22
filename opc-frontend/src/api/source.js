import request from './request'

export function getSources() {
  return request.get('/public/sources')
}

export function createSource(data) {
  return request.post('/admin/sources', data)
}

export function updateSource(id, data) {
  return request.put(`/admin/sources/${id}`, data)
}

export function deleteSource(id) {
  return request.delete(`/admin/sources/${id}`)
}

function normalizeSourceTitle(value) {
  return String(value || '').trim().toLocaleLowerCase()
}

export function findSourceByTitle(sources, title) {
  const normalizedTitle = normalizeSourceTitle(title)
  return sources.find((source) => normalizeSourceTitle(source.title) === normalizedTitle) || null
}

export async function resolveSourcePlaceholder(sources, title, placeholder = {}) {
  const existing = findSourceByTitle(sources, title)
  if (existing) {
    return { source: existing, created: false }
  }

  const source = await createSource({
    sourceType: 'other',
    publisher: '',
    url: '',
    localFile: '',
    accessedAt: new Date().toISOString().slice(0, 10),
    notes: '待补充来源信息',
    status: 'pending',
    ...placeholder,
    title: String(title || '').trim(),
  })
  return { source, created: true }
}
