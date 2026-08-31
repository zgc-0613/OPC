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
            <option value="comprehensive">综合发展政策</option>
            <option value="computing_support">算力与技术基础设施</option>
            <option value="funding_subsidy">资金补贴与财政激励</option>
            <option value="scenario_demand">场景与应用推广</option>
            <option value="talent_service">人才与高校支持</option>
            <option value="investment">投融资与金融服务</option>
            <option value="governance_market">制度治理与市场环境</option>
          </select>
        </label>
        <label>
          <span>政策适用范围 *</span>
          <select v-model="form.applicabilityMode" required @change="handleApplicabilityModeChange">
            <option value="unclassified">未分类</option>
            <option value="general">通用创业政策</option>
            <option value="specific">指定行业</option>
          </select>
        </label>
        <fieldset v-if="form.applicabilityMode === 'specific'" class="policy-industry-field span-2">
          <legend>适用行业 *</legend>
          <div class="policy-industry-options">
            <label v-for="industry in industries" :key="industry.tagId">
              <input v-model="form.industryTagIds" type="checkbox" :value="industry.tagId" />
              <span>{{ industry.name }}</span>
            </label>
          </div>
          <small>仅表示政策经审核后明确适用的行业，不等同于下方支持措施标签。</small>
        </fieldset>
        <p v-if="applicabilityValidationError" class="error span-3" role="alert">
          {{ applicabilityValidationError }}
        </p>
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
            <option value="consultation">征求意见稿（不纳入分类）</option>
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
          <span>支持措施标签</span>
          <input v-model.trim="form.tags" placeholder="多个标签用中文逗号隔开，仅限七大主分类" />
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
          <button class="button" type="submit" :disabled="formSubmitting">
            {{ formSubmitting ? '正在保存...' : editingId ? '保存政策' : '新增政策' }}
          </button>
          <button v-if="editingId" class="button button-ghost" type="button" :disabled="formSubmitting" @click="resetForm">取消编辑</button>
        </div>
        <p v-if="formError" class="error span-3" role="alert">{{ formError }}</p>
      </form>
    </section>

    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>政策列表</h2>
          <p>点击标题或查看详情可打开完整政策及来源链接；编辑会回填到上方表单。</p>
        </div>
        <button class="button button-ghost" type="button" @click="loadPolicies">刷新</button>
      </div>

      <div class="admin-list-filter" aria-label="政策地区筛选">
        <label>
          <span>按省筛选</span>
          <select v-model="selectedProvinceId">
            <option value="">全部省份</option>
            <option v-for="province in provinceOptions" :key="province.id" :value="province.id">{{ province.name }}</option>
          </select>
        </label>
        <span>当前显示 {{ filteredPolicies.length }} / {{ policies.length }} 条政策</span>
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
      <div v-if="policies.length && selectedPolicyCount" class="policy-applicability-bulk">
        <div class="policy-applicability-bulk__summary">
          <strong>批量设置适用范围</strong>
          <span>已选择 {{ selectedPolicyCount }} 条政策；变更后，已核验政策会自动移回待审。</span>
        </div>
        <button class="button button-ghost" type="button" :disabled="applicabilityUpdating" @click="applyBulkApplicability('general')">
          批量设为通用
        </button>
        <div class="policy-applicability-bulk__specific">
          <div class="policy-industry-options is-compact">
            <label v-for="industry in industries" :key="`bulk-${industry.tagId}`">
              <input v-model="batchIndustryTagIds" type="checkbox" :value="industry.tagId" />
              <span>{{ industry.name }}</span>
            </label>
          </div>
          <button class="button" type="button" :disabled="applicabilityUpdating || !batchIndustryTagIds.length" @click="applyBulkApplicability('specific')">
            批量关联行业
          </button>
        </div>
        <button class="button button-ghost" type="button" :disabled="applicabilityUpdating" @click="applyBulkApplicability('unclassified')">
          移回未分类
        </button>
      </div>
      <p v-if="bulkMessage" class="success admin-bulk-notice" role="status">{{ bulkMessage }}</p>
      <p v-if="bulkError" class="error admin-bulk-notice" role="alert">{{ bulkError }}</p>

      <div v-if="loading" class="muted">正在加载政策...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="!filteredPolicies.length" class="admin-list-empty muted">当前省份没有政策记录。</div>
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
              <td>
                <RouterLink
                  class="admin-record-link"
                  :to="{ name: 'policy-detail', params: { id: policy.id } }"
                  target="_blank"
                  rel="noopener"
                  :aria-label="`查看政策详情：${policy.title}`"
                >
                  <span>{{ policy.title }}</span><ArrowUpRight :size="14" aria-hidden="true" />
                </RouterLink>
              </td>
              <td>{{ policy.regionName || '-' }}</td>
              <td>{{ policy.issuingBody || '-' }}</td>
              <td>{{ policy.publishDate || '-' }}</td>
              <td>
                <span class="status-pill" :class="`is-${policy.applicabilityMode || 'unclassified'}`">
                  {{ applicabilityLabel(policy) }}
                </span>
                <small v-if="policy.applicabilityMode === 'specific'" class="policy-industry-names">
                  {{ policy.industryTagNames?.join('、') || '未关联行业' }}
                </small>
              </td>
              <td><span class="status-pill">{{ materialNatureText(policy) }}</span></td>
              <td>
                <div class="row-actions">
                  <RouterLink :to="{ name: 'policy-detail', params: { id: policy.id } }" target="_blank" rel="noopener">查看详情</RouterLink>
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
import {
  createPolicy,
  deletePolicy,
  getAdminPolicies,
  getAdminPolicyDetail,
  updatePolicy,
  updatePolicyApplicabilityBatch,
} from '@/api/policy'
import { getRegions } from '@/api/region'
import { getAdminSources, resolveSourcePlaceholder } from '@/api/source'
import { getIndustryTags } from '@/api/tag'
import { useAdminTableControls } from '@/composables/useAdminTableControls'
import { useProvinceFilter } from '@/composables/useProvinceFilter'
import { useResizableColumns } from '@/composables/useResizableColumns'
import { deletionConfirmation, prepareEvidenceItemDeletion } from '@/utils/adminEvidenceDeletion'

const today = new Date().toISOString().slice(0, 10)
const loading = ref(false)
const error = ref('')
const policies = ref([])
const regions = ref([])
const sources = ref([])
const industries = ref([])
const editingId = ref(null)
const sourceValidationError = ref('')
const applicabilityValidationError = ref('')
const formError = ref('')
const formSubmitting = ref(false)
const bulkStatus = ref('')
const bulkUpdating = ref(false)
const bulkMessage = ref('')
const bulkError = ref('')
const applicabilityUpdating = ref(false)
const batchIndustryTagIds = ref([])

const policyStatusOptions = [
  { value: 'published', label: '已发布' },
  { value: 'consultation', label: '征求意见稿（不纳入分类）' },
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
  { key: 'applicabilityMode', label: '适用范围', width: 170, minWidth: 140, maxWidth: 300 },
  { key: 'status', label: '状态', width: 112, minWidth: 90, maxWidth: 220 },
]
const policyTableColumns = [
  { key: 'selection', width: 46, minWidth: 46, maxWidth: 46, resizable: false },
  ...policySortableColumns,
  { key: 'actions', width: 210, minWidth: 190, maxWidth: 260, resizable: false },
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
  applicabilityMode: 'unclassified',
  industryTagIds: [],
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
})

const form = reactive(defaultForm())
const {
  filteredItems: filteredPolicies,
  provinceOptions,
  selectedProvinceId,
} = useProvinceFilter(policies, regions)
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
} = useAdminTableControls(filteredPolicies)
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
  applicabilityValidationError.value = ''
  formError.value = ''
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
    applicabilityMode: detail.applicabilityMode || 'unclassified',
    industryTagIds: (detail.industryTagIds || []).map(Number),
  })
}

function handleApplicabilityModeChange() {
  if (form.applicabilityMode !== 'specific') form.industryTagIds = []
  applicabilityValidationError.value = ''
}

function toPayload(sourceId) {
  const { sourceTitle, ...policyFields } = form
  const payload = {
    ...policyFields,
    regionId: Number(form.regionId),
    sourceId,
    publishDate: form.publishDate || null,
    effectiveDate: form.effectiveDate || null,
    applicabilityMode: form.applicabilityMode,
    industryTagIds: form.applicabilityMode === 'specific' ? form.industryTagIds.map(Number) : [],
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
  if (formSubmitting.value) return
  if (form.applicabilityMode === 'specific' && !form.industryTagIds.length) {
    applicabilityValidationError.value = '指定行业政策必须至少选择一个适用行业。'
    return
  }
  applicabilityValidationError.value = ''
  formError.value = ''
  formSubmitting.value = true
  try {
    const sourceResolution = await resolveSourceId()
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
  } catch (err) {
    if (err?.businessCode === 409) {
      formError.value = '政策已被其他管理员修改。列表已刷新，请重新打开该政策后再保存。'
      await loadPolicies()
    } else {
      formError.value = err.message || '政策保存失败'
    }
  } finally {
    formSubmitting.value = false
  }
}

async function removePolicy(policy) {
  if (!window.confirm(deletionConfirmation('policy', policy))) {
    return
  }
  error.value = ''
  try {
    const snapshot = await prepareEvidenceItemDeletion('policy', policy)
    await deletePolicy(policy.id, snapshot)
  } catch (err) {
    error.value = err?.businessCode === 409
      ? '政策已被其他操作修改，列表已重新加载，请确认后重试。'
      : (err.message || '政策删除失败')
    window.alert(error.value)
    await loadPolicies()
    return
  }
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
    applicabilityMode: detail.applicabilityMode || 'unclassified',
    industryTagIds: (detail.industryTagIds || []).map(Number),
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
    expectedEvidenceRevision: Number(detail.evidenceRevision ?? 0),
    expectedUpdatedAt: detail.updatedAt || null,
  }
}

function applicabilityLabel(policy) {
  const labels = {
    general: '通用政策',
    specific: '指定行业',
    unclassified: '未分类',
  }
  return labels[policy.applicabilityMode] || labels.unclassified
}

function materialNatureText(policy) {
  if (policy?.materialNatureLabel) return policy.materialNatureLabel
  if (policy?.materialNature === 'consultation_draft' || policy?.status === 'consultation') return '征求意见稿'
  if (policy?.materialNature === 'standard_reference') return '标准规范文件'
  if (policy?.materialNature === 'official_platform_service') return '官方平台/服务信息'
  if (policy?.materialNature === 'formal_policy') return policy.status === 'expired' ? '正式文件（失效）' : '正式文件'
  if (policy?.materialNature) return '其他资料'
  return policy?.status === 'published' ? '正式文件' : (policy?.status || '-')
}

async function applyBulkApplicability(mode) {
  if (!selectedPolicyCount.value || applicabilityUpdating.value) return
  if (mode === 'specific' && !batchIndustryTagIds.value.length) {
    bulkError.value = '请先选择至少一个适用行业。'
    return
  }
  const selected = policies.value.filter((policy) => selectedPolicyIds.value.has(policy.id))
  applicabilityUpdating.value = true
  bulkMessage.value = ''
  bulkError.value = ''
  try {
    await updatePolicyApplicabilityBatch({
      applicabilityMode: mode,
      industryTagIds: mode === 'specific' ? batchIndustryTagIds.value.map(Number) : [],
      items: selected.map((policy) => ({
        policyId: Number(policy.id),
        expectedEvidenceRevision: Number(policy.evidenceRevision ?? 0),
        expectedUpdatedAt: policy.updatedAt,
      })),
    })
    bulkMessage.value = `已更新 ${selected.length} 条政策的适用范围；需要复核的政策已移回待审。`
    batchIndustryTagIds.value = []
    clearPolicySelection()
    await loadPolicies()
  } catch (err) {
    bulkError.value = err?.businessCode === 409
      ? '部分政策已被其他管理员修改，列表已重新加载；原选择仍保留，请核对后重试。'
      : (err.message || '批量设置政策适用范围失败')
    await loadPolicies()
  } finally {
    applicabilityUpdating.value = false
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
    const [regionList, sourceList, policyList, industryList] = await Promise.all([
      getRegions(), getAdminSources(), getAdminPolicies(), getIndustryTags(),
    ])
    regions.value = regionList
    sources.value = sourceList
    policies.value = policyList
    industries.value = industryList
  } catch (err) {
    error.value = err.message || '政策加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.policy-industry-field {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.policy-industry-field legend {
  margin-bottom: 8px;
  color: #4b514c;
  font-size: 0.76rem;
  font-weight: 700;
}

.policy-industry-field > small {
  display: block;
  margin-top: 8px;
  color: #626862;
}

.policy-industry-options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 8px 12px;
  max-height: 176px;
  overflow: auto;
  padding: 10px 0;
  border-top: 1px solid #d0d4cf;
  border-bottom: 1px solid #d0d4cf;
}

.policy-industry-options label {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: #303431;
  font-size: 0.78rem;
}

.policy-industry-options input {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
}

.policy-applicability-bulk {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto minmax(320px, 1.4fr) auto;
  align-items: end;
  gap: 12px;
  margin: -8px 0 18px;
  padding: 14px 0;
  border-bottom: 1px solid #d0d4cf;
}

.policy-applicability-bulk__summary {
  display: grid;
  gap: 4px;
}

.policy-applicability-bulk__summary span,
.policy-industry-names {
  color: #626862;
  font-size: 0.72rem;
}

.policy-applicability-bulk__specific {
  display: grid;
  grid-template-columns: minmax(200px, 1fr) auto;
  align-items: end;
  gap: 10px;
}

.policy-industry-options.is-compact {
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  max-height: 108px;
  padding: 8px 0;
}

.policy-industry-names {
  display: block;
  margin-top: 5px;
  line-height: 1.4;
}

.status-pill.is-unclassified {
  border-color: #c8cbc7;
  color: #626862;
}

@media (max-width: 1100px) {
  .policy-applicability-bulk {
    grid-template-columns: 1fr 1fr;
  }

  .policy-applicability-bulk__summary,
  .policy-applicability-bulk__specific {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .policy-applicability-bulk,
  .policy-applicability-bulk__specific {
    grid-template-columns: 1fr;
  }

  .policy-applicability-bulk .button {
    width: 100%;
  }
}
</style>
