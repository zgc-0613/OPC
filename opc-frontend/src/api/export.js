import request from './request'

export function exportPolicies() {
  window.open('/api/public/export/policies.xlsx', '_blank')
}

export function exportCases() {
  return downloadAdminWorkbook('/admin/export/cases.xlsx', 'cases.xlsx')
}

export function exportSources() {
  return downloadAdminWorkbook('/admin/export/sources.xlsx', 'sources.xlsx')
}

export function exportPaperDataset() {
  const date = new Date().toISOString().slice(0, 10)
  return downloadAdminWorkbook('/admin/export/paper-dataset.xlsx', `findopc-paper-dataset-${date}.xlsx`)
}

async function downloadAdminWorkbook(url, filename) {
  const blob = await request.get(url, {
    responseType: 'blob',
    timeout: 60000,
  })
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(objectUrl)
}
