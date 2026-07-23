<template>
  <div class="admin-stack">
    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>{{ editingId ? '编辑政策' : '新增政策' }}</h2>
          <p>政策记录会展示在前台政策库，并参与首页统计与 Excel 导出。</p>
        </div>
      </div>

      <form class="admin-form" @submit.prevent="submitForm">
        <label class="span-2">
          <span>政策标题 *</span>
          <input v-model.trim="form.title" required placeholder="政策文件标题" />
        </label>
        <label>
          <span>地区 *</span>
          <select v-model.number="form.regionId" required>
            <option value="">请选择地区</option>
            <option v-for="region in regions" :key="region.id" :value="region.id">{{ region.name }}</option>
          </select>
        </label>
        <label>
          <span>发文单位 *</span>
          <input v-model.trim="form.issuingBody" required />
        </label>
        <label>
          <span>文号</span>
          <input v-model.trim="form.documentNo" />
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
          <span>政策层级 *</span>
          <select v-model="form.policyLevel" required>
            <option value="national">国家级</option>
            <option value="provincial">省级</option>
            <option value="city">市级</option>
            <option value="district">区县级</option>
            <option value="other">其他</option>
          </select>
        </label>
        <label>
          <span>政策类型 *</span>
          <select v-model="form.policyType" required>
            <option value="comprehensive">综合政策</option>
            <option value="computing_support">算力支持</option>
            <option value="funding_subsidy">资金补贴</option>
            <option value="scenario_demand">场景需求</option>
            <option value="talent_service">人才服务</option>
            <option value="investment">投资融资</option>
            <option value="other">其他</option>
          </select>
        </label>
        <label>
          <span>发布日期</span>
          <input v-model="form.publishDate" type="date" />
        </label>
        <label>
          <span>开始实施时间</span>
          <input v-model="form.effectiveDate" type="date" />
        </label>
        <label>
          <span>有效时长</span>
          <input v-model.trim="form.validPeriod" placeholder="例如：长期有效 / 三年" />
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
            <option value="pending">待校对</option>
            <option value="archived">归档</option>
          </select>
        </label>
        <label>
          <span>校对人</span>
          <input v-model.trim="form.reviewer" />
        </label>
        <label>
          <span>AI 证据资格</span>
          <select v-model="form.aiEvidenceStatus">
            <option value="legacy_unverified">待 AI 证据核验</option>
            <option value="verified">已核验，可用于智能体</option>
            <option value="excluded">排除，不用于智能体</option>
          </select>
        </label>
        <label class="span-3">
          <span>摘要 *</span>
          <textarea v-model.trim="form.summary" required rows="4" placeholder="100-300 字摘要"></textarea>
        </label>
        <label class="span-3">
          <span>政策要点</span>
          <textarea v-model.trim="form.keyPoints" rows="4"></textarea>
        </label>
        <label class="span-3">
          <span>支持措施</span>
          <textarea v-model.trim="form.supportMeasures" rows="4"></textarea>
        </label>
        <label class="span-3">
          <span>标签</span>
          <input v-model.trim="form.tags" placeholder="多个标签用中文逗号隔开，例如：资金补贴，算力支持" />
        </label>
        <label class="span-2">
          <span>原文链接</span>
          <input v-model.trim="form.originalUrl" placeholder="https://..." />
        </label>
        <label>
          <span>本地文件</span>
          <input v-model.trim="form.localFile" />
        </label>
        <label class="span-3">
          <span>辅证链接</span>
          <input v-model.trim="form.evidenceUrl" placeholder="https://..." />
        </label>
        <div class="admin-actions span-3">
          <button class="button" type="submit">{{ editingId ? '保存政策' : '新增政策' }}</button>
          <button v-if="editingId" class="button button-ghost" type="button" @click="resetForm">取消编辑</button>
        </div>
      </form>
    </section>

    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>政策列表</h2>
          <p>点击编辑会读取完整详情，再回填到上方表单。</p>
        </div>
        <button class="button button-ghost" type="button" @click="loadPolicies">刷新</button>
      </div>

      <AdminBulkStatusToolbar
        v-if="policies.length"
        v-model="bulkStatus"
        :busy="bulkUpdating"
        :options="policyStatusOptions"
        :selected-count="selectedPolicyCount"
        @apply="applyBulkStatus"
        @clear="clearPolicySelection"
      />
      <p v-if="bulkMessage" class="success admin-bulk-notice" role="status">{{ bulkMessage }}</p>
      <p v-if="bulkError" class="error admin-bulk-notice" role="alert">{{ bulkError }}</p>

      <div v-if="loading" class="muted">正在加载政策...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="table-wrap">
        <table class="admin-resizable-table">
          <colgroup>
            <col
              v-for="column in policyTableColumns"
              :key="column.key"
              :style="{ width: `${policyColumnPercentages[column.key]}%` }"
            />
          </colgroup>
          <thead>
            <tr>
              <th class="admin-select-column">
                <input
                  class="admin-table-checkbox"
                  type="checkbox"
                  :checked="allPoliciesSelected"
                  :indeterminate.prop="somePoliciesSelected"
                  aria-label="选择全部政策"
                  @change="toggleAllPolicies($event.target.checked)"
                />
              </th>
              <SortableTableHeader
                v-for="column in policySortableColumns"
                :key="column.key"
                :label="column.label"
                :column="column.key"
                :active-column="policySortColumn"
                :direction="policySortDirection"
                @toggle="togglePolicySort"
                @resize-start="startPolicyColumnResize"
                @resize-by="resizePolicyColumn"
              />
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="policy in sortedPolicies" :key="policy.id" :class="{ 'is-selected': selectedPolicyIds.has(policy.id) }">
              <td class="admin-select-column">
                <input
                  class="admin-table-checkbox"
                  type="checkbox"
                  :checked="selectedPolicyIds.has(policy.id)"
                  :aria-label="`选择政策 ${policy.title}`"
                  @change="togglePolicyRow(policy.id, $event.target.checked)"
                />
              </td>
              <td>{{ policy.id }}</td>
              <td>{{ policy.title }}</td>
              <td>{{ policy.regionName || '-' }}</td>
              <td>{{ policy.issuingBody || '-' }}</td>
              <td>{{ policy.publishDate || '-' }}</td>
              <td><span class="status-pill">{{ policy.status || '-' }}</span></td>
              <td>
                <div class="row-actions">
                  <button type="button" @click="startEdit(policy)">编辑</button>
                  <button type="button" class="danger" @click="removePolicy(policy)">删除</button>
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
import { createPolicy, deletePolicy, getAdminPolicies, getAdminPolicyDetail, updatePolicy } from '@/api/policy'
import { getRegions } from '@/api/region'
import { getAdminSources, resolveSourcePlaceholder } from '@/api/source'
import { useAdminTableControls } from '@/composables/useAdminTableControls'
import { useResizableColumns } from '@/composables/useResizableColumns'

const today = new Date().toISOString().slice(0, 10)
const loading = ref(false)
const error = ref('')
const policies = ref([])
const regions = ref([])
const sources = ref([])
const editingId = ref(null)
const sourceValidationError = ref('')
const bulkStatus = ref('')
const bulkUpdating = ref(false)
const bulkMessage = ref('')
const bulkError = ref('')

const policyStatusOptions = [
  { value: 'published', label: '已发布' },
  { value: 'draft', label: '草稿' },
  { value: 'pending', label: '待校对' },
  { value: 'archived', label: '归档' },
]
const policySortableColumns = [
  { key: 'id', label: 'ID', width: 72, minWidth: 58, maxWidth: 150 },
  { key: 'title', label: '标题', width: 340, minWidth: 180, maxWidth: 620 },
  { key: 'regionName', label: '地区', width: 120, minWidth: 90, maxWidth: 260 },
  { key: 'issuingBody', label: '发文单位', width: 280, minWidth: 160, maxWidth: 520 },
  { key: 'publishDate', label: '发布日期', width: 132, minWidth: 110, maxWidth: 220 },
  { key: 'status', label: '状态', width: 112, minWidth: 90, maxWidth: 220 },
]
const policyTableColumns = [
  { key: 'selection', width: 46, minWidth: 46, maxWidth: 46, resizable: false },
  ...policySortableColumns,
  { key: 'actions', width: 112, minWidth: 90, maxWidth: 180, resizable: false },
]

const defaultForm = () => ({
  title: '',
  regionId: '',
  issuingBody: '',
  documentNo: '',
  publishDate: '',
  effectiveDate: '',
  validPeriod: '',
  sourceId: '',
  sourceTitle: '',
  policyLevel: 'provincial',
  policyType: 'comprehensive',
  summary: '',
  keyPoints: '',
  supportMeasures: '',
  tags: '',
  originalUrl: '',
  evidenceUrl: '',
  localFile: '',
  accessedAt: today,
  status: 'published',
  reviewer: '',
  aiEvidenceStatus: 'legacy_unverified',
})

const form = reactive(defaultForm())
const {
  allSelected: allPoliciesSelected,
  clearSelection: clearPolicySelection,
  replaceSelection: replacePolicySelection,
  selectedCount: selectedPolicyCount,
  selectedIds: selectedPolicyIds,
  someSelected: somePoliciesSelected,
  sortColumn: policySortColumn,
  sortDirection: policySortDirection,
  sortedItems: sortedPolicies,
  toggleAll: toggleAllPolicies,
  toggleRow: togglePolicyRow,
  toggleSort: togglePolicySort,
} = useAdminTableControls(policies)
const {
  columnPercentages: policyColumnPercentages,
  resizeBy: resizePolicyColumn,
  startResize: startPolicyColumnResize,
} = useResizableColumns('opc-admin-policy-column-widths-v2', policyTableColumns)

async function loadPolicies() {
  loading.value = true
  error.value = ''
  try {
    policies.value = await getAdminPolicies()
  } catch (err) {
    error.value = err.message || '政策加载失败'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = null
  sourceValidationError.value = ''
  Object.assign(form, defaultForm())
}

async function startEdit(policy) {
  const detail = await getAdminPolicyDetail(policy.id)
  editingId.value = detail.id
  Object.assign(form, {
    ...defaultForm(),
    ...detail,
    sourceTitle: detail.sourceTitle || '',
    publishDate: detail.publishDate || '',
    effectiveDate: detail.effectiveDate || '',
    accessedAt: detail.accessedAt || today,
  })
}

function toPayload(sourceId) {
  const { sourceTitle, ...policyFields } = form
  return {
    ...policyFields,
    regionId: Number(form.regionId),
    sourceId,
    publishDate: form.publishDate || null,
    effectiveDate: form.effectiveDate || null,
  }
}

async function resolveSourceId() {
  sources.value = await getAdminSources()
  const resolution = await resolveSourcePlaceholder(sources.value, form.sourceTitle, {
    publisher: form.issuingBody,
    url: form.originalUrl,
    localFile: form.localFile,
    accessedAt: form.accessedAt || today,
    notes: `待补充：由政策“${form.title}”录入`,
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
    await updatePolicy(editingId.value, payload)
  } else {
    await createPolicy(payload)
  }
  resetForm()
  await loadPolicies()
  if (sourceResolution.created) {
    window.alert(
      `已创建待补充来源“${sourceResolution.source.title}”（ID: ${sourceResolution.source.id}），请到来源管理完善其余信息。`,
    )
  }
}

async function removePolicy(policy) {
  if (!window.confirm(`确认删除政策「${policy.title}」吗？`)) {
    return
  }
  await deletePolicy(policy.id)
  await loadPolicies()
}

function policyDetailPayload(detail, status) {
  return {
    title: detail.title,
    regionId: Number(detail.regionId),
    issuingBody: detail.issuingBody,
    documentNo: detail.documentNo || '',
    publishDate: detail.publishDate || null,
    effectiveDate: detail.effectiveDate || null,
    validPeriod: detail.validPeriod || '',
    sourceId: Number(detail.sourceId),
    policyLevel: detail.policyLevel,
    policyType: detail.policyType,
    summary: detail.summary,
    keyPoints: detail.keyPoints || '',
    supportMeasures: detail.supportMeasures || '',
    tags: detail.tags || '',
    originalUrl: detail.originalUrl || '',
    evidenceUrl: detail.evidenceUrl || '',
    localFile: detail.localFile || '',
    accessedAt: detail.accessedAt || today,
    status,
    reviewer: detail.reviewer || '',
    aiEvidenceStatus: detail.aiEvidenceStatus || 'legacy_unverified',
  }
}

async function applyBulkStatus() {
  const ids = [...selectedPolicyIds.value]
  if (!ids.length || !bulkStatus.value || bulkUpdating.value) {
    return
  }

  bulkUpdating.value = true
  bulkMessage.value = ''
  bulkError.value = ''
  const results = await Promise.allSettled(ids.map(async (id) => {
    const detail = await getAdminPolicyDetail(id)
    await updatePolicy(id, policyDetailPayload(detail, bulkStatus.value))
    return id
  }))
  const failedIds = results.flatMap((result, index) => (result.status === 'rejected' ? [ids[index]] : []))
  const updatedCount = ids.length - failedIds.length

  await loadPolicies()
  replacePolicySelection(failedIds)
  bulkStatus.value = ''
  bulkUpdating.value = false
  if (failedIds.length) {
    bulkError.value = `已更新 ${updatedCount} 项，另有 ${failedIds.length} 项失败；失败项已保留选择，可重试。`
  } else {
    bulkMessage.value = `已更新 ${updatedCount} 项政策状态。`
  }
}

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const [regionList, sourceList, policyList] = await Promise.all([getRegions(), getAdminSources(), getAdminPolicies()])
    regions.value = regionList
    sources.value = sourceList
    policies.value = policyList
  } catch (err) {
    error.value = err.message || '政策加载失败'
  } finally {
    loading.value = false
  }
})
</script>
