<template>
  <div class="admin-stack">
    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>{{ editingId ? '编辑案例' : '新增案例' }}</h2>
          <p>案例记录用于沉淀 AI + OPC / 一人公司相关应用场景和典型实践。</p>
        </div>
      </div>

      <form class="admin-form" @submit.prevent="submitForm">
        <label class="span-2">
          <span>案例标题 *</span>
          <input v-model.trim="form.title" required />
        </label>
        <label>
          <span>地区 *</span>
          <select v-model.number="form.regionId" required>
            <option value="">请选择地区</option>
            <option v-for="region in regions" :key="region.id" :value="region.id">{{ region.name }}</option>
          </select>
        </label>
        <label>
          <span>领域 *</span>
          <input v-model.trim="form.category" required placeholder="例如：AI 电商 / 内容创业" />
        </label>
        <label>
          <span>主体</span>
          <input v-model.trim="form.actorName" placeholder="企业、团队或个人" />
        </label>
        <div class="admin-source-field">
          <label>
            <span>来源 *</span>
            <input
              v-model.trim="form.sourceTitle"
              required
              autocomplete="off"
              placeholder="输入来源名称"
              :aria-invalid="Boolean(sourceValidationError)"
              @input="handleSourceTitleInput"
            />
          </label>
          <div class="admin-source-guidance" :class="{ 'has-error': sourceValidationError }">
            <small role="status">
              {{ sourceValidationError || '新名称将创建待补充来源，保存后请到来源管理完善资料' }}
            </small>
            <RouterLink class="admin-source-management-link" to="/admin/sources" target="_blank" rel="noopener">
              <span>打开来源管理</span>
              <ArrowUpRight :size="15" aria-hidden="true" />
            </RouterLink>
          </div>
        </div>
        <label>
          <span>访问日期 *</span>
          <input v-model="form.accessedAt" required type="date" />
        </label>
        <label>
          <span>状态 *</span>
          <select v-model="form.status" required>
            <option value="published">已发布</option>
            <option value="draft">草稿</option>
            <option value="pending">待校对</option>
            <option value="archived">归档</option>
          </select>
        </label>
        <label>
          <span>校对人</span>
          <input v-model.trim="form.reviewer" />
        </label>
        <label class="span-3">
          <span>摘要 *</span>
          <textarea v-model.trim="form.summary" required rows="4"></textarea>
        </label>
        <label class="span-3">
          <span>商业模式</span>
          <textarea v-model.trim="form.businessModel" rows="4"></textarea>
        </label>
        <label class="span-3">
          <span>AI 工具</span>
          <textarea v-model.trim="form.aiTools" rows="3"></textarea>
        </label>
        <label class="span-3">
          <span>成果</span>
          <textarea v-model.trim="form.outcome" rows="3"></textarea>
        </label>
        <label class="span-3">
          <span>标签</span>
          <input v-model.trim="form.tags" placeholder="多个标签用中文逗号隔开" />
        </label>
        <label class="span-2">
          <span>原文链接</span>
          <input v-model.trim="form.originalUrl" placeholder="https://..." />
        </label>
        <label>
          <span>本地文件</span>
          <input v-model.trim="form.localFile" />
        </label>
        <div class="admin-actions span-3">
          <button class="button" type="submit">{{ editingId ? '保存案例' : '新增案例' }}</button>
          <button v-if="editingId" class="button button-ghost" type="button" @click="resetForm">取消编辑</button>
        </div>
      </form>
    </section>

    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>案例列表</h2>
          <p>点击编辑会读取完整详情，再回填到上方表单。</p>
        </div>
        <button class="button button-ghost" type="button" @click="loadCases">刷新</button>
      </div>

      <AdminBulkStatusToolbar
        v-if="cases.length"
        v-model="bulkStatus"
        :busy="bulkUpdating"
        :options="caseStatusOptions"
        :selected-count="selectedCaseCount"
        @apply="applyBulkStatus"
        @clear="clearCaseSelection"
      />
      <p v-if="bulkMessage" class="success admin-bulk-notice" role="status">{{ bulkMessage }}</p>
      <p v-if="bulkError" class="error admin-bulk-notice" role="alert">{{ bulkError }}</p>

      <div v-if="loading" class="muted">正在加载案例...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="table-wrap">
        <table class="admin-resizable-table">
          <colgroup>
            <col
              v-for="column in caseTableColumns"
              :key="column.key"
              :style="{ width: `${caseColumnPercentages[column.key]}%` }"
            />
          </colgroup>
          <thead>
            <tr>
              <th class="admin-select-column">
                <input
                  class="admin-table-checkbox"
                  type="checkbox"
                  :checked="allCasesSelected"
                  :indeterminate.prop="someCasesSelected"
                  aria-label="选择全部案例"
                  @change="toggleAllCases($event.target.checked)"
                />
              </th>
              <SortableTableHeader
                v-for="column in caseSortableColumns"
                :key="column.key"
                :label="column.label"
                :column="column.key"
                :active-column="caseSortColumn"
                :direction="caseSortDirection"
                @toggle="toggleCaseSort"
                @resize-start="startCaseColumnResize"
                @resize-by="resizeCaseColumn"
              />
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in sortedCases" :key="item.id" :class="{ 'is-selected': selectedCaseIds.has(item.id) }">
              <td class="admin-select-column">
                <input
                  class="admin-table-checkbox"
                  type="checkbox"
                  :checked="selectedCaseIds.has(item.id)"
                  :aria-label="`选择案例 ${item.title}`"
                  @change="toggleCaseRow(item.id, $event.target.checked)"
                />
              </td>
              <td>{{ item.id }}</td>
              <td>{{ item.title }}</td>
              <td>{{ item.regionName || '-' }}</td>
              <td>{{ item.category || '-' }}</td>
              <td>{{ item.actorName || '-' }}</td>
              <td><span class="status-pill">{{ item.status || '-' }}</span></td>
              <td>
                <div class="row-actions">
                  <button type="button" @click="startEdit(item)">编辑</button>
                  <button type="button" class="danger" @click="removeCase(item)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ArrowUpRight } from 'lucide-vue-next'
import AdminBulkStatusToolbar from '@/components/AdminBulkStatusToolbar.vue'
import SortableTableHeader from '@/components/SortableTableHeader.vue'
import { createCase, deleteCase, getAdminCaseDetail, getAdminCases, updateCase } from '@/api/case'
import { getRegions } from '@/api/region'
import { getAdminSources, resolveSourcePlaceholder } from '@/api/source'
import { useAdminTableControls } from '@/composables/useAdminTableControls'
import { useResizableColumns } from '@/composables/useResizableColumns'

const today = new Date().toISOString().slice(0, 10)
const loading = ref(false)
const error = ref('')
const cases = ref([])
const regions = ref([])
const sources = ref([])
const editingId = ref(null)
const sourceValidationError = ref('')
const bulkStatus = ref('')
const bulkUpdating = ref(false)
const bulkMessage = ref('')
const bulkError = ref('')

const caseStatusOptions = [
  { value: 'published', label: '已发布' },
  { value: 'draft', label: '草稿' },
  { value: 'pending', label: '待校对' },
  { value: 'archived', label: '归档' },
]
const caseSortableColumns = [
  { key: 'id', label: 'ID', width: 72, minWidth: 58, maxWidth: 150 },
  { key: 'title', label: '标题', width: 340, minWidth: 180, maxWidth: 620 },
  { key: 'regionName', label: '地区', width: 120, minWidth: 90, maxWidth: 260 },
  { key: 'category', label: '领域', width: 190, minWidth: 120, maxWidth: 360 },
  { key: 'actorName', label: '主体', width: 240, minWidth: 140, maxWidth: 460 },
  { key: 'status', label: '状态', width: 112, minWidth: 90, maxWidth: 220 },
]
const caseTableColumns = [
  { key: 'selection', width: 46, minWidth: 46, maxWidth: 46, resizable: false },
  ...caseSortableColumns,
  { key: 'actions', width: 112, minWidth: 90, maxWidth: 180, resizable: false },
]

const defaultForm = () => ({
  title: '',
  regionId: '',
  category: '',
  actorName: '',
  sourceId: '',
  sourceTitle: '',
  summary: '',
  businessModel: '',
  aiTools: '',
  outcome: '',
  tags: '',
  originalUrl: '',
  localFile: '',
  accessedAt: today,
  status: 'published',
  reviewer: '',
})

const form = reactive(defaultForm())
const {
  allSelected: allCasesSelected,
  clearSelection: clearCaseSelection,
  replaceSelection: replaceCaseSelection,
  selectedCount: selectedCaseCount,
  selectedIds: selectedCaseIds,
  someSelected: someCasesSelected,
  sortColumn: caseSortColumn,
  sortDirection: caseSortDirection,
  sortedItems: sortedCases,
  toggleAll: toggleAllCases,
  toggleRow: toggleCaseRow,
  toggleSort: toggleCaseSort,
} = useAdminTableControls(cases)
const {
  columnPercentages: caseColumnPercentages,
  resizeBy: resizeCaseColumn,
  startResize: startCaseColumnResize,
} = useResizableColumns('opc-admin-case-column-widths-v2', caseTableColumns)

async function loadCases() {
  loading.value = true
  error.value = ''
  try {
    cases.value = await getAdminCases()
  } catch (err) {
    error.value = err.message || '案例加载失败'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = null
  sourceValidationError.value = ''
  Object.assign(form, defaultForm())
}

async function startEdit(item) {
  const detail = await getAdminCaseDetail(item.id)
  editingId.value = detail.id
  Object.assign(form, {
    ...defaultForm(),
    ...detail,
    sourceTitle: detail.sourceTitle || '',
    accessedAt: detail.accessedAt || today,
  })
}

function toPayload(sourceId) {
  const { sourceTitle, ...caseFields } = form
  const payload = {
    ...caseFields,
    regionId: Number(form.regionId),
    sourceId,
  }
  if (editingId.value) {
    payload.expectedEvidenceRevision = Number(form.evidenceRevision ?? 0)
    payload.expectedUpdatedAt = form.updatedAt || null
  }
  return payload
}

async function resolveSourceId() {
  sources.value = await getAdminSources()
  const resolution = await resolveSourcePlaceholder(sources.value, form.sourceTitle, {
    publisher: form.actorName,
    url: form.originalUrl,
    localFile: form.localFile,
    accessedAt: form.accessedAt || today,
    notes: `待补充：由案例“${form.title}”录入`,
  })
  const { source } = resolution
  if (resolution.created) {
    sources.value.push(source)
  }
  sourceValidationError.value = ''
  form.sourceId = Number(source.id)
  return { ...resolution, sourceId: Number(source.id) }
}

function handleSourceTitleInput() {
  form.sourceId = ''
  sourceValidationError.value = ''
}

async function submitForm() {
  let sourceResolution
  try {
    sourceResolution = await resolveSourceId()
  } catch (err) {
    sourceValidationError.value = err.message || '来源关联失败，请检查来源名称'
    return
  }
  const payload = toPayload(sourceResolution.sourceId)
  if (editingId.value) {
    await updateCase(editingId.value, payload)
  } else {
    await createCase(payload)
  }
  resetForm()
  await loadCases()
  if (sourceResolution.created) {
    window.alert(
      `已创建待补充来源“${sourceResolution.source.title}”（ID: ${sourceResolution.source.id}），请到来源管理完善其余信息。`,
    )
  }
}

async function removeCase(item) {
  if (!window.confirm(`确认删除案例「${item.title}」吗？`)) {
    return
  }
  await deleteCase(item.id)
  await loadCases()
}

function caseDetailPayload(detail, status) {
  return {
    title: detail.title,
    regionId: Number(detail.regionId),
    category: detail.category,
    actorName: detail.actorName || '',
    sourceId: Number(detail.sourceId),
    summary: detail.summary,
    businessModel: detail.businessModel || '',
    aiTools: detail.aiTools || '',
    outcome: detail.outcome || '',
    tags: detail.tags || '',
    originalUrl: detail.originalUrl || '',
    localFile: detail.localFile || '',
    accessedAt: detail.accessedAt || today,
    status,
    reviewer: detail.reviewer || '',
    expectedEvidenceRevision: Number(detail.evidenceRevision ?? 0),
    expectedUpdatedAt: detail.updatedAt || null,
  }
}

async function applyBulkStatus() {
  const ids = [...selectedCaseIds.value]
  if (!ids.length || !bulkStatus.value || bulkUpdating.value) {
    return
  }

  bulkUpdating.value = true
  bulkMessage.value = ''
  bulkError.value = ''
  const results = await Promise.allSettled(ids.map(async (id) => {
    const detail = await getAdminCaseDetail(id)
    await updateCase(id, caseDetailPayload(detail, bulkStatus.value))
    return id
  }))
  const failedIds = results.flatMap((result, index) => (result.status === 'rejected' ? [ids[index]] : []))
  const updatedCount = ids.length - failedIds.length

  await loadCases()
  replaceCaseSelection(failedIds)
  bulkStatus.value = ''
  bulkUpdating.value = false
  if (failedIds.length) {
    bulkError.value = `已更新 ${updatedCount} 项，另有 ${failedIds.length} 项失败；失败项已保留选择，可重试。`
  } else {
    bulkMessage.value = `已更新 ${updatedCount} 项案例状态。`
  }
}

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const [regionList, sourceList, caseList] = await Promise.all([getRegions(), getAdminSources(), getAdminCases()])
    regions.value = regionList
    sources.value = sourceList
    cases.value = caseList
  } catch (err) {
    error.value = err.message || '案例加载失败'
  } finally {
    loading.value = false
  }
})
</script>
