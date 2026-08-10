<template>
  <section class="research-reports" aria-labelledby="research-reports-title">
    <button
      v-if="!autoOpen"
      class="reports-trigger"
      data-testid="open-research-reports"
      type="button"
      :aria-expanded="open"
      aria-controls="research-reports-panel"
      @click="openPanel"
    >
      <BookMarked :size="16" aria-hidden="true" />
      <span id="research-reports-title">已保存报告</span>
      <span class="reports-trigger-note">固化研究结果、引用与版本</span>
      <ChevronUp v-if="open" :size="16" aria-hidden="true" />
      <ChevronDown v-else :size="16" aria-hidden="true" />
    </button>

    <div v-if="open || autoOpen" id="research-reports-panel" class="reports-panel" :aria-busy="loading">
      <p v-if="error" class="reports-error" role="alert">{{ error }}</p>
      <p v-if="notice" class="reports-notice" role="status">{{ notice }}</p>

      <div class="report-commands">
        <div class="report-scope" role="group" aria-label="报告范围">
          <button type="button" :class="{ selected: scope === 'active' }" :aria-pressed="scope === 'active'" @click="changeScope('active')">当前报告</button>
          <button type="button" :class="{ selected: scope === 'trash' }" :aria-pressed="scope === 'trash'" @click="changeScope('trash')">回收站</button>
        </div>
        <button v-if="canSave" class="save-current-report" data-testid="save-current-report" type="button" @click="showCreate = !showCreate">
          <Save :size="15" aria-hidden="true" />保存当前结果
        </button>
      </div>

      <form v-if="showCreate" class="report-create-form" @submit.prevent="saveCurrent">
        <label><span>报告标题</span><input v-model="createForm.title" required maxlength="120" /></label>
        <label><span>备注</span><textarea v-model="createForm.notes" rows="2" maxlength="1000" /></label>
        <footer><button type="submit" :disabled="saving">{{ saving ? '正在保存' : '保存报告' }}</button><button type="button" :disabled="saving" @click="showCreate = false">取消</button></footer>
      </form>

      <p v-if="loading" class="reports-empty" role="status">正在读取报告</p>
      <template v-else>
        <p v-if="!reports.length" class="reports-empty">{{ scope === 'trash' ? '回收站中没有报告。' : '尚未保存研究报告。' }}</p>
        <ol v-else class="report-list">
          <li v-for="report in reports" :key="report.reportId">
            <template v-if="editingId === report.reportId">
              <form class="report-edit-form" @submit.prevent="update(report)">
                <label><span>报告标题</span><input v-model="editForm.title" required maxlength="120" /></label>
                <label><span>备注</span><textarea v-model="editForm.notes" rows="2" maxlength="1000" /></label>
                <footer><button type="submit" :disabled="saving">保存修改</button><button type="button" :disabled="saving" @click="editingId = null">取消</button></footer>
              </form>
            </template>
            <template v-else>
              <div class="report-summary">
                <strong>{{ report.title }}</strong>
                <small>证据 {{ report.evidenceVersion || '待核验' }}<template v-if="report.dataVersion"> · 数据 {{ report.dataVersion }}</template></small>
                <span class="report-evidence-state" :data-state="report.evidenceState || 'unknown'">{{ evidenceStateLabel(report.evidenceState) }}</span>
                <p v-if="report.notes">{{ report.notes }}</p>
              </div>
              <div class="report-actions">
                <button v-if="report.status === 'active'" type="button" :aria-label="`编辑报告 ${report.title}`" @click="beginEdit(report)"><Pencil :size="15" aria-hidden="true" /></button>
                <div v-if="report.status === 'active'" class="report-export" role="group" :aria-label="`导出报告 ${report.title}`">
                  <select
                    v-model="exportFormats[report.reportId]"
                    class="report-export-format"
                    :data-testid="`export-format-${report.reportId}`"
                    :aria-label="`${report.title}的导出格式`"
                    :disabled="saving"
                  >
                    <option value="markdown">Markdown (.md)</option>
                    <option value="html">HTML (.html)</option>
                    <option value="pdf">PDF (.pdf)</option>
                  </select>
                  <button :data-testid="`export-report-${report.reportId}`" type="button" :aria-label="`导出报告 ${report.title}`" :disabled="saving" @click="exportReport(report)"><Download :size="15" aria-hidden="true" /></button>
                </div>
                <button v-if="report.status === 'active' && shouldRerun(report)" :data-testid="`re-research-report-${report.reportId}`" type="button" :aria-label="`重新研究 ${report.title}`" @click="emit('re-research', report)"><RefreshCw :size="15" aria-hidden="true" /></button>
                <button v-if="report.status === 'active'" type="button" :aria-label="`移入回收站 ${report.title}`" @click="trash(report)"><Trash2 :size="15" aria-hidden="true" /></button>
                <button v-if="report.status === 'trash'" type="button" :aria-label="`恢复报告 ${report.title}`" @click="restore(report)"><RotateCcw :size="15" aria-hidden="true" /></button>
                <button v-if="report.status === 'trash'" type="button" :aria-label="`永久删除报告 ${report.title}`" @click="confirmPurge = report.reportId"><X :size="15" aria-hidden="true" /></button>
              </div>
              <div v-if="confirmPurge === report.reportId" class="purge-confirmation" role="alert">
                <span>永久删除会清除报告正文和导出内容。</span>
                <button type="button" :disabled="saving" @click="purge(report)">确认删除</button>
                <button type="button" :disabled="saving" @click="confirmPurge = null">取消</button>
              </div>
            </template>
          </li>
        </ol>
        <button v-if="hasMore" class="load-more-reports" data-testid="load-more-reports" type="button" :disabled="loading" @click="loadMore">
          {{ loading ? '正在读取' : '加载更多报告' }}
        </button>
      </template>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { BookMarked, ChevronDown, ChevronUp, Download, Pencil, RefreshCw, RotateCcw, Save, Trash2, X } from 'lucide-vue-next'
import {
  exportResearchReport, getResearchReports, permanentlyDeleteResearchReport,
  restoreResearchReport, saveResearchReport, trashResearchReport, updateResearchReport,
} from '@/api/ai'

const EXPORT_FORMATS = {
  markdown: { extension: 'md', mime: 'text/markdown' },
  html: { extension: 'html', mime: 'text/html' },
  pdf: { extension: 'pdf', mime: 'application/pdf' },
}

const props = defineProps({
  sessionId: { type: [Number, String], default: null },
  run: { type: Object, default: null },
  autoOpen: Boolean,
})
const emit = defineEmits(['saved', 're-research'])
const open = ref(props.autoOpen)
const loaded = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const scope = ref('active')
const reports = ref([])
const nextCursor = ref(null)
const hasMore = ref(false)
const showCreate = ref(false)
const editingId = ref(null)
const confirmPurge = ref(null)
const exportFormats = ref({})
const createForm = ref({ title: '', notes: '' })
const editForm = ref({ title: '', notes: '' })

onMounted(() => {
  if (props.autoOpen) void load()
})
const canSave = computed(() => Number(props.sessionId) > 0 && props.run?.status === 'completed'
  && Number(props.run?.finalMessage?.messageId) > 0)

async function openPanel() {
  open.value = !open.value
  if (open.value) await load()
}

async function load(force = false, append = false) {
  if (loading.value || (loaded.value && !force && !append)) return
  if (force) {
    reports.value = []
    nextCursor.value = null
    hasMore.value = false
  }
  loading.value = true
  error.value = ''
  try {
    const page = await getResearchReports({
      scope: scope.value,
      q: '',
      cursor: append ? nextCursor.value || undefined : undefined,
      limit: 30,
    }) || {}
    const items = Array.isArray(page.items) ? page.items : []
    reports.value = append
      ? [...reports.value, ...items.filter((item) => !reports.value.some((current) => current.reportId === item.reportId))]
      : items
    nextCursor.value = page.nextCursor || null
    hasMore.value = Boolean(page.hasMore && nextCursor.value)
    loaded.value = true
  } catch (requestError) {
    error.value = requestError.message || '报告暂时无法读取。'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!hasMore.value || !nextCursor.value) return
  await load(false, true)
}

async function changeScope(nextScope) {
  if (scope.value === nextScope) return
  scope.value = nextScope
  loaded.value = false
  editingId.value = null
  confirmPurge.value = null
  await load(true)
}

async function saveCurrent() {
  if (!canSave.value) return
  saving.value = true
  error.value = ''
  try {
    const report = await saveResearchReport(Number(props.sessionId), {
      finalMessageId: Number(props.run.finalMessage.messageId),
      title: createForm.value.title.trim(),
      notes: createForm.value.notes.trim(),
      idempotencyKey: createIdempotencyKey(),
    })
    if (scope.value === 'active') reports.value = [report, ...reports.value.filter((item) => item.reportId !== report.reportId)]
    showCreate.value = false
    createForm.value = { title: '', notes: '' }
    notice.value = '报告已保存，内容与证据版本已固化。'
    emit('saved', report)
  } catch (requestError) {
    error.value = requestError.message || '报告暂时无法保存。'
  } finally {
    saving.value = false
  }
}

function beginEdit(report) {
  editingId.value = report.reportId
  editForm.value = { title: report.title || '', notes: report.notes || '' }
}

async function update(report) {
  await mutate(report, () => updateResearchReport(report.reportId, {
    expectedRevision: report.revision,
    title: editForm.value.title.trim(),
    notes: editForm.value.notes.trim(),
  }), '报告已更新。')
  editingId.value = null
}

async function trash(report) {
  const updated = await mutate(report, () => trashResearchReport(report.reportId, { expectedRevision: report.revision }), '报告已移入回收站。')
  if (updated && scope.value === 'active') reports.value = reports.value.filter((item) => item.reportId !== report.reportId)
}

async function restore(report) {
  const updated = await mutate(report, () => restoreResearchReport(report.reportId, { expectedRevision: report.revision }), '报告已恢复。')
  if (updated && scope.value === 'trash') reports.value = reports.value.filter((item) => item.reportId !== report.reportId)
}

async function purge(report) {
  const updated = await mutate(report, () => permanentlyDeleteResearchReport(report.reportId, { expectedRevision: report.revision }), '报告已永久删除。')
  if (updated) reports.value = reports.value.filter((item) => item.reportId !== report.reportId)
  confirmPurge.value = null
}

async function mutate(report, operation, successMessage) {
  saving.value = true
  error.value = ''
  try {
    const updated = await operation()
    reports.value = reports.value.map((item) => item.reportId === report.reportId ? updated : item)
    notice.value = successMessage
    return updated
  } catch (requestError) {
    error.value = requestError.message || '报告操作未完成，请重新读取后再试。'
    return null
  } finally {
    saving.value = false
  }
}

async function exportReport(report) {
  const format = exportFormats.value[report.reportId] || 'markdown'
  const exportFormat = EXPORT_FORMATS[format]
  if (!exportFormat) return
  saving.value = true
  error.value = ''
  try {
    const responseBlob = await exportResearchReport(report.reportId, format)
    const blob = responseBlob.type === exportFormat.mime
      ? responseBlob
      : responseBlob.slice(0, responseBlob.size, exportFormat.mime)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${safeFileName(report.title)}.${exportFormat.extension}`
    link.rel = 'noopener'
    try {
      document.body.appendChild(link)
      link.click()
    } finally {
      link.remove()
      URL.revokeObjectURL(url)
    }
    notice.value = '报告已导出。'
  } catch (requestError) {
    error.value = requestError.message || '报告暂时无法导出。'
  } finally {
    saving.value = false
  }
}

function createIdempotencyKey() {
  const random = globalThis.crypto?.randomUUID?.().replaceAll('-', '')
    || Math.random().toString(36).slice(2)
  return `report_${Date.now().toString(36)}_${random}`.slice(0, 64)
}

function safeFileName(title) {
  const value = String(title || 'research-report').replace(/[\\/:*?"<>|\u0000-\u001F]/g, '_').trim()
  return value || 'research-report'
}

function evidenceStateLabel(state) {
  return {
    current: '已核验，来源未发现变化',
    evidence_changed: '证据已更新，建议重新研究',
    source_unavailable: '来源已失效，需要重新核验',
    evidence_insufficient: '引用证据有限，建议补充核验',
  }[state] || '来源状态待核验'
}

function shouldRerun(report) {
  return ['evidence_changed', 'source_unavailable', 'evidence_insufficient'].includes(report.evidenceState)
}
</script>

<style scoped>
.research-reports{flex:0 0 auto;border-bottom:1px solid #d0d3ce;background:#f9f9f5;color:#282d28}.reports-trigger{display:grid;grid-template-columns:auto auto minmax(0,1fr) auto;align-items:center;width:100%;min-height:44px;gap:8px;padding:10px 24px;border:0;background:transparent;color:#303630;text-align:left}.reports-trigger span:first-of-type{font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:.78rem;font-weight:700}.reports-trigger-note{min-width:0;overflow:hidden;color:#5f665f;font-size:.65rem;text-overflow:ellipsis;white-space:nowrap}.reports-trigger:focus-visible{background:#eef0eb}.reports-trigger:focus-visible,.reports-panel button:focus-visible,.reports-panel :is(input,textarea,select):focus-visible{outline:2px solid #4f6f58;outline-offset:2px}.reports-panel{display:grid;gap:12px;max-height:min(48dvh,480px);overflow:auto;padding:0 24px 16px;scrollbar-gutter:stable}.reports-error,.reports-notice,.reports-empty{margin:0;font-size:.7rem;line-height:1.6}.reports-error{color:#7a3731}.reports-notice{color:#3f684a}.reports-empty{color:#5f665f}.report-commands{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px}.report-scope{display:flex;gap:6px}.report-scope button,.save-current-report,.report-actions button,.report-create-form footer button,.report-edit-form footer button,.purge-confirmation button{display:inline-flex;align-items:center;justify-content:center;gap:5px;min-height:38px;padding:0 10px;border:1px solid #b9bfb8;border-radius:3px;background:#fbfbf7;color:#303630;font:inherit;font-size:.69rem;font-weight:700}.report-scope button.selected,.save-current-report,.report-create-form footer button[type="submit"],.report-edit-form footer button[type="submit"]{border-color:#303630;background:#303630;color:#fff}.reports-panel button:focus-visible{border-color:#5f665f;background:#eceee8}.reports-panel .save-current-report:focus-visible,.report-create-form footer button[type="submit"]:focus-visible,.report-edit-form footer button[type="submit"]:focus-visible{background:#3b413b;color:#fff}.reports-panel button:disabled{opacity:.55;cursor:not-allowed}.report-create-form,.report-edit-form{display:grid;gap:9px;padding:11px 12px;border:1px solid #c5cac3;border-radius:3px;background:#f3f4f0}.report-create-form label,.report-edit-form label{display:grid;gap:5px;min-width:0}.report-create-form label>span,.report-edit-form label>span{color:#555b55;font-size:.67rem;font-weight:700}.report-create-form :is(input,textarea),.report-edit-form :is(input,textarea){width:100%;min-width:0;border:1px solid #bfc5bd;border-radius:3px;background:#fff;color:#252a25;font:inherit;font-size:.75rem;line-height:1.5}.report-create-form input,.report-edit-form input{height:40px;padding:0 9px}.report-create-form textarea,.report-edit-form textarea{padding:8px 9px;resize:vertical}.report-create-form footer,.report-edit-form footer{display:flex;gap:8px;flex-wrap:wrap}.report-list{display:grid;gap:8px;margin:0;padding:0;list-style:none}.report-list>li{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px 12px;padding:11px 0;border-top:1px solid #d9ddd7}.report-list>li:first-child{border-top:0}.report-summary{display:grid;gap:3px;min-width:0}.report-summary strong,.report-summary small,.report-summary p,.report-evidence-state{overflow-wrap:anywhere}.report-summary strong{font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:.78rem}.report-summary small{color:#5f665f;font-family:'Bookman Old Style',Georgia,serif;font-size:.61rem}.report-evidence-state{color:#4f574f;font-size:.63rem;line-height:1.45}.report-evidence-state[data-state='current']{color:#3f684a}.report-evidence-state[data-state='evidence_changed'],.report-evidence-state[data-state='source_unavailable']{color:#7a4b31}.report-summary p{margin:0;color:#585f58;font-size:.69rem;line-height:1.55}.report-actions{display:flex;align-items:flex-start;flex-wrap:wrap;gap:5px}.report-actions button{width:38px;padding:0}.report-export{display:flex;align-items:flex-start;gap:5px}.report-export-format{width:138px;min-height:38px;padding:0 8px;border:1px solid #b9bfb8;border-radius:3px;background:#fbfbf7;color:#303630;font:inherit;font-size:.69rem;font-weight:700}.report-export-format:disabled{opacity:.55;cursor:not-allowed}.purge-confirmation{grid-column:1/-1;display:flex;align-items:center;flex-wrap:wrap;gap:8px;padding:9px 10px;border:1px solid #d2bdb7;background:#f8efec;color:#6f3b35;font-size:.68rem}.purge-confirmation span{flex:1 1 220px}@media(max-width:720px){.reports-trigger{padding:10px 16px}.reports-panel{padding:0 16px 14px}.reports-trigger-note{display:none}.report-scope button,.save-current-report,.report-actions button,.report-export-format,.report-create-form footer button,.report-edit-form footer button,.purge-confirmation button{min-height:44px}.report-actions button{width:44px}.report-list>li{grid-template-columns:1fr}.report-actions{order:2}.report-create-form,.report-edit-form{padding:10px}}@media(max-height:680px){.reports-panel{max-height:min(34dvh,300px)}}@media(prefers-reduced-motion:reduce){.reports-trigger{transition:none}}
.load-more-reports{justify-self:center;min-height:38px;padding:0 12px;border:1px solid #b9bfb8;border-radius:3px;background:#fbfbf7;color:#303630;font:inherit;font-size:.69rem;font-weight:700}.load-more-reports:focus-visible{border-color:#5f665f;background:#eceee8}@media(max-width:720px){.load-more-reports{min-height:44px}}
@media (hover: hover) and (pointer: fine){.reports-trigger:hover{background:#eef0eb}.reports-panel button:hover{border-color:#747b74;background:#eceee8}.reports-panel .save-current-report:hover,.report-create-form footer button[type="submit"]:hover,.report-edit-form footer button[type="submit"]:hover{background:#3b413b;color:#fff}.load-more-reports:hover{border-color:#747b74;background:#eceee8}}</style>
<style scoped>
.reports-panel { padding: 16px 24px 16px; }
@media (max-width: 720px) { .reports-panel { padding: 14px 16px 14px; } }
.report-commands { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; }
.report-scope { min-width: 0; }
.report-scope button,
.save-current-report {
  min-height: 44px;
  white-space: nowrap;
  border-color: rgba(99, 107, 98, .38);
  background: rgba(251, 251, 247, .78);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .82), inset 0 -1px 0 rgba(41, 47, 40, .08), 0 1px 1px rgba(32, 37, 31, .06);
  backdrop-filter: blur(12px) saturate(1.08);
  -webkit-backdrop-filter: blur(12px) saturate(1.08);
  transition: transform var(--duration-fast) var(--ease-out), background-color var(--duration-fast) ease, border-color var(--duration-fast) ease, box-shadow var(--duration-fast) ease;
}
.report-scope button.selected,
.save-current-report {
  border-color: rgba(37, 42, 37, .88);
  background: rgba(37, 42, 37, .94);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .15), inset 0 -1px 0 rgba(0, 0, 0, .22), 0 1px 1px rgba(32, 37, 31, .08);
}
@media (hover: hover) and (pointer: fine) {
  .report-scope button:hover,
  .save-current-report:hover { transform: translateY(-1px); border-color: rgba(71, 80, 70, .7); box-shadow: inset 0 1px 0 rgba(255, 255, 255, .9), inset 0 -1px 0 rgba(41, 47, 40, .08), 0 4px 12px rgba(32, 37, 31, .1); }
  .report-scope button.selected:hover,
  .save-current-report:hover { background: rgba(47, 53, 47, .96); }
}
.report-scope button:active,
.save-current-report:active { transform: scale(.975); }
@media (max-width: 520px) { .report-commands { grid-template-columns: 1fr; } .save-current-report { justify-self: stretch; } }
@media (prefers-reduced-motion: reduce) { .report-scope button,.save-current-report { transition: none; transform: none; } }
</style>
