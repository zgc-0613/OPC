import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const files = {
  caseApi: await readFile(new URL('../src/api/case.js', import.meta.url), 'utf8'),
  policyApi: await readFile(new URL('../src/api/policy.js', import.meta.url), 'utf8'),
  sourceApi: await readFile(new URL('../src/api/source.js', import.meta.url), 'utf8'),
  caseView: await readFile(new URL('../src/views/admin/CaseAdminView.vue', import.meta.url), 'utf8'),
  policyView: await readFile(new URL('../src/views/admin/PolicyAdminView.vue', import.meta.url), 'utf8'),
  sourceView: await readFile(new URL('../src/views/admin/SourceAdminView.vue', import.meta.url), 'utf8'),
}

for (const api of [files.caseApi, files.policyApi, files.sourceApi]) {
  assert.match(api, /request\.delete\([^\n]+\{ params: snapshot \}\)/)
}
for (const view of [files.caseView, files.policyView, files.sourceView]) {
  assert.match(view, /expectedEvidenceRevision:\s*Number\([^\n]+evidenceRevision/)
  assert.match(view, /expectedUpdatedAt:\s*[^,\n]+updatedAt/)
}
assert.match(files.sourceView, /evidenceRevision:\s*Number\(source\.evidenceRevision \?\? 0\)/)
assert.match(files.sourceView, /updatedAt:\s*source\.updatedAt \|\| null/)

console.log('admin concurrency snapshot tests passed')
