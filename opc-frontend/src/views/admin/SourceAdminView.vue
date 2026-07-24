<template>
  <div class="admin-stack">
    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>{{ editingId ? '编辑来源' : '新增来源' }}</h2>
          <p>来源用于追溯政策、案例或文献的原始出处。</p>
        </div>
      </div>

      <form class="admin-form" @submit.prevent="submitForm">
        <div v-if="formError" class="error span-3" role="alert">{{ formError }}</div>
        <label class="span-2">
          <span>来源标题 *</span>
          <input
            v-model.trim="form.title"
            required
            placeholder="例如：北京市人工智能产业政策原文"
            @input="formError = ''"
          />
        </label>
        <label>
          <span>来源类型 *</span>
          <select v-model="form.sourceType" required>
            <option value="web">网页</option>
            <option value="file">文件</option>
            <option value="paper">文献</option>
            <option value="news">新闻</option>
            <option value="other">其他</option>
          </select>
        </label>
        <label>
          <span>发布机构</span>
          <input v-model.trim="form.publisher" placeholder="例如：北京市政府" />
        </label>
        <label class="span-2">
          <span>来源链接</span>
          <input v-model.trim="form.url" placeholder="https://..." />
        </label>
        <label>
          <span>本地文件名</span>
          <input v-model.trim="form.localFile" placeholder="文件名或路径" />
        </label>
        <label>
          <span>访问日期 *</span>
          <input v-model="form.accessedAt" required type="date" />
        </label>
        <label>
          <span>状态 *</span>
          <select v-model="form.status" required>
            <option value="published">已发布</option>
            <option value="draft">草稿</option>
            <option value="pending">待补充</option>
            <option value="archived">归档</option>
          </select>
        </label>
        <label class="span-3">
          <span>备注</span>
          <textarea v-model.trim="form.notes" rows="3" placeholder="补充说明"></textarea>
        </label>
        <div class="admin-actions span-3">
          <button class="button" type="submit">{{ editingId ? '保存修改' : '新增来源' }}</button>
          <button v-if="editingId" class="button button-ghost" type="button" @click="resetForm">取消编辑</button>
        </div>
      </form>
    </section>

    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>来源列表</h2>
          <p>政策和案例详情页会引用这里的来源记录。</p>
        </div>
      </div>

      <AdminBulkStatusToolbar
        v-if="sources.length"
        v-model="bulkStatus"
        :busy="bulkUpdating"
        :options="sourceStatusOptions"
        :selected-count="selectedSourceCount"
        @apply="applyBulkStatus"
        @clear="clearSourceSelection"
      />
      <p v-if="bulkMessage" class="success admin-bulk-notice" role="status">{{ bulkMessage }}</p>
      <p v-if="bulkError" class="error admin-bulk-notice" role="alert">{{ bulkError }}</p>

      <div v-if="loading" class="muted">正在加载来源...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="table-wrap">
        <table class="admin-resizable-table">
          <colgroup>
            <col
              v-for="column in sourceTableColumns"
              :key="column.key"
              :style="{ width: `${sourceColumnPercentages[column.key]}%` }"
            />
          </colgroup>
          <thead>
            <tr>
              <th class="admin-select-column">
                <input
                  class="admin-table-checkbox"
                  type="checkbox"
                  :checked="allSourcesSelected"
                  :indeterminate.prop="someSourcesSelected"
                  aria-label="选择全部来源"
                  @change="toggleAllSources($event.target.checked)"
                />
              </th>
              <SortableTableHeader
                v-for="column in sourceSortableColumns"
                :key="column.key"
                :label="column.label"
                :column="column.key"
                :active-column="sourceSortColumn"
                :direction="sourceSortDirection"
                @toggle="toggleSourceSort"
                @resize-start="startSourceColumnResize"
                @resize-by="resizeSourceColumn"
              />
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="source in sortedSources" :key="source.id" :class="{ 'is-selected': selectedSourceIds.has(source.id) }">
              <td class="admin-select-column">
                <input
                  class="admin-table-checkbox"
                  type="checkbox"
                  :checked="selectedSourceIds.has(source.id)"
                  :aria-label="`选择来源 ${source.title}`"
                  @change="toggleSourceRow(source.id, $event.target.checked)"
                />
              </td>
              <td>{{ source.id }}</td>
              <td>{{ source.title }}</td>
              <td><span class="chip" :title="source.sourceType">{{ sourceTypeLabel(source.sourceType) }}</span></td>
              <td>{{ source.publisher || '-' }}</td>
              <td>{{ source.accessedAt || '-' }}</td>
              <td><span class="status-pill">{{ sourceStatusLabel(source.status) }}</span></td>
              <td>
                <div class="row-actions">
                  <button type="button" @click="startEdit(source)">编辑</button>
                  <button type="button" class="danger" @click="removeSource(source)">删除</button>
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
import AdminBulkStatusToolbar from '@/components/AdminBulkStatusToolbar.vue'
import SortableTableHeader from '@/components/SortableTableHeader.vue'
import { createSource, deleteSource, getAdminSources, updateSource } from '@/api/source'
import { useAdminTableControls } from '@/composables/useAdminTableControls'
import { useResizableColumns } from '@/composables/useResizableColumns'

const today = new Date().toISOString().slice(0, 10)
const loading = ref(false)
const error = ref('')
const formError = ref('')
const sources = ref([])
const editingId = ref(null)
const bulkStatus = ref('')
const bulkUpdating = ref(false)
const bulkMessage = ref('')
const bulkError = ref('')
const sourceStatusOptions = [
  { value: 'published', label: '发布' },
  { value: 'pending', label: '待补充' },
  { value: 'archived', label: '归档' },
]
const sourceSortableColumns = [
  { key: 'id', label: 'ID', width: 72, minWidth: 58, maxWidth: 150 },
  { key: 'title', label: '标题', width: 340, minWidth: 180, maxWidth: 620 },
  { key: 'sourceType', label: '类型', width: 132, minWidth: 100, maxWidth: 260 },
  { key: 'publisher', label: '发布机构', width: 280, minWidth: 160, maxWidth: 520 },
  { key: 'accessedAt', label: '访问日期', width: 132, minWidth: 110, maxWidth: 220 },
  { key: 'status', label: '状态', width: 112, minWidth: 90, maxWidth: 220 },
]
const sourceTableColumns = [
  { key: 'selection', width: 46, minWidth: 46, maxWidth: 46, resizable: false },
  ...sourceSortableColumns,
  { key: 'actions', width: 112, minWidth: 90, maxWidth: 180, resizable: false },
]
const form = reactive({
  title: '',
  sourceType: 'web',
  publisher: '',
  url: '',
  localFile: '',
  accessedAt: today,
  notes: '',
  status: 'published',
})
const {
  allSelected: allSourcesSelected,
  clearSelection: clearSourceSelection,
  replaceSelection: replaceSourceSelection,
  selectedCount: selectedSourceCount,
  selectedIds: selectedSourceIds,
  someSelected: someSourcesSelected,
  sortColumn: sourceSortColumn,
  sortDirection: sourceSortDirection,
  sortedItems: sortedSources,
  toggleAll: toggleAllSources,
  toggleRow: toggleSourceRow,
  toggleSort: toggleSourceSort,
} = useAdminTableControls(sources)
const {
  columnPercentages: sourceColumnPercentages,
  resizeBy: resizeSourceColumn,
  startResize: startSourceColumnResize,
} = useResizableColumns('opc-admin-source-column-widths-v2', sourceTableColumns)

async function loadSources() {
  loading.value = true
  error.value = ''
  try {
    sources.value = await getAdminSources()
  } catch (err) {
    error.value = err.message || '来源加载失败'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = null
  formError.value = ''
  Object.assign(form, {
    title: '',
    sourceType: 'web',
    publisher: '',
    url: '',
    localFile: '',
    accessedAt: today,
    notes: '',
    status: 'published',
  })
}

function startEdit(source) {
  editingId.value = source.id
  formError.value = ''
  Object.assign(form, {
    title: source.title || '',
    sourceType: source.sourceType || 'web',
    publisher: source.publisher || '',
    url: source.url || '',
    localFile: source.localFile || '',
    accessedAt: source.accessedAt || today,
    notes: source.notes || '',
    status: source.status || 'published',
  })
}

async function submitForm() {
  const payload = {
    ...form,
    ...(editingId.value
      ? {
          expectedEvidenceRevision: Number(form.evidenceRevision ?? 0),
          expectedUpdatedAt: form.updatedAt || null,
        }
      : {}),
  }
  formError.value = ''
  try {
    if (editingId.value) {
      await updateSource(editingId.value, payload)
    } else {
      await createSource(payload)
    }
  } catch (err) {
    formError.value = err.message || '来源保存失败'
    return
  }
  resetForm()
  await loadSources()
}

function sourceStatusLabel(status) {
  return {
    published: '已发布',
    draft: '草稿',
    pending: '待补充',
    archived: '归档',
  }[status] || status || '-'
}

function sourceTypeLabel(sourceType) {
  return {
    government_site: '政府网站',
    cnki_journal: '知网期刊',
    cnki_newspaper: '知网报纸',
    news: '新闻',
    report: '报告',
    file: '文件',
    web: '网页',
    paper: '文献',
    other: '其他',
  }[sourceType] || sourceType || '-'
}

async function removeSource(source) {
  if (!window.confirm(`确认删除来源「${source.title}」吗？`)) {
    return
  }
  await deleteSource(source.id)
  await loadSources()
}

async function applyBulkStatus() {
  const ids = [...selectedSourceIds.value]
  if (!ids.length || !bulkStatus.value || bulkUpdating.value) {
    return
  }

  bulkUpdating.value = true
  bulkMessage.value = ''
  bulkError.value = ''
  const sourcesById = new Map(sources.value.map((source) => [source.id, source]))
  const results = await Promise.allSettled(ids.map(async (id) => {
    const source = sourcesById.get(id)
    if (!source) {
      throw new Error('来源不存在')
    }
    await updateSource(id, {
      title: source.title,
      sourceType: source.sourceType,
      publisher: source.publisher || '',
      url: source.url || '',
      localFile: source.localFile || '',
      accessedAt: source.accessedAt || today,
      notes: source.notes || '',
      status: bulkStatus.value,
      expectedEvidenceRevision: Number(source.evidenceRevision ?? 0),
      expectedUpdatedAt: source.updatedAt || null,
    })
    return id
  }))
  const failedIds = results.flatMap((result, index) => (result.status === 'rejected' ? [ids[index]] : []))
  const updatedCount = ids.length - failedIds.length

  await loadSources()
  replaceSourceSelection(failedIds)
  bulkStatus.value = ''
  bulkUpdating.value = false
  if (failedIds.length) {
    bulkError.value = `已更新 ${updatedCount} 项，另有 ${failedIds.length} 项失败；失败项已保留选择，可重试。`
  } else {
    bulkMessage.value = `已更新 ${updatedCount} 项来源状态。`
  }
}

onMounted(loadSources)
</script>
