import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const policyApi = await readFile(new URL('../src/api/policy.js', import.meta.url), 'utf8')
const policyView = await readFile(new URL('../src/views/admin/PolicyAdminView.vue', import.meta.url), 'utf8')
const assistantView = await readFile(new URL('../src/views/AssistantView.vue', import.meta.url), 'utf8')

assert.match(policyApi, /request\.put\('\/admin\/policies\/applicability\/batch', data\)/)
assert.match(policyView, /v-model="form\.applicabilityMode"/)
assert.match(policyView, /v-model="form\.industryTagIds"/)
assert.match(policyView, /支持措施标签/)
assert.match(policyView, /批量设为通用/)
assert.match(policyView, /批量关联行业/)
assert.match(policyView, /expectedEvidenceRevision:\s*Number\(policy\.evidenceRevision/)
assert.match(policyView, /expectedUpdatedAt:\s*policy\.updatedAt/)
assert.match(assistantView, /选入政策/)
assert.match(assistantView, /直接行业政策/)
assert.match(assistantView, /通用政策/)

console.log('policy applicability frontend contract tests passed')
