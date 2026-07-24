<template>
  <div class="admin-stack evidence-workbench-page">
    <header class="evidence-workbench-intro">
      <div>
        <span class="caption">EVIDENCE GOVERNANCE</span>
        <h2>证据审核工作台</h2>
        <p>在同一处完成资料核对、来源链检查、原地修正和审核决策。所有状态变化都会记录管理员、原因与操作批次。</p>
      </div>
      <div class="evidence-workbench-key">
        <span><i class="is-ready"></i>可批准</span>
        <span><i class="is-pending"></i>待补充</span>
        <span><i class="is-blocked"></i>有阻止项</span>
      </div>
    </header>

    <form class="evidence-workbench-filters" @submit.prevent="applyFilters">
      <label class="is-search">
        <span>搜索资料</span>
        <input v-model="query.keyword" type="search" placeholder="标题、摘要、标签或发布机构" />
      </label>
      <label>
        <span>资料类型</span>
        <select v-model="query.itemType">
          <option value="">全部资料</option><option value="source">来源</option><option value="policy">政策</option><option value="case">案例</option>
        </select>
      </label>
      <label>
        <span>核验状态</span>
        <select v-model="query.evidenceStatus">
          <option value="legacy_unverified">待审核</option><option value="verified">已核验</option><option value="excluded">已排除</option><option value="">全部状态</option>
        </select>
      </label>
      <label>
        <span>可审核性</span>
        <select v-model="query.reviewability">
          <option value="all">全部</option><option value="reviewable">可批准</option><option value="blocked">有阻止项</option>
        </select>
      </label>
      <label>
        <span>关联来源</span>
        <select v-model="query.sourceId">
          <option value="">全部来源</option>
          <option v-for="source in sources" :key="source.id" :value="source.id">{{ source.title }}</option>
        </select>
      </label>
      <label>
        <span>排序</span>
        <select v-model="query.sort">
          <option value="updated_desc">最近更新</option><option value="updated_asc">最早更新</option><option value="title_asc">标题升序</option><option value="title_desc">标题降序</option>
        </select>
      </label>
      <div class="evidence-workbench-filters__actions">
        <button class="button" type="submit" :disabled="queueLoading">应用筛选</button>
        <button class="button button-ghost" type="button" :disabled="queueLoading" @click="resetFilters">重置</button>
      </div>
    </form>

    <EvidenceReviewBatchBar
      :selected-count="selectedKeys.size"
      :busy="batch.busy"
      @action="openBatch"
      @clear="clearSelection"
    />
    <p v-if="notice" class="success evidence-workbench-notice" role="status">{{ notice }}</p>
    <p v-if="pageError" class="error evidence-workbench-notice" role="alert">{{ pageError }}</p>

    <section class="evidence-workbench" :class="{ 'show-detail': compactPane === 'detail' }">
      <EvidenceReviewQueuePanel
        class="evidence-workbench__queue"
        :groups="groups"
        :loading="queueLoading"
        :selected-key="selectedKey"
        :selected-keys="selectedKeys"
        :selected-count="selectedKeys.size"
        :total="total"
        :page="query.page"
        :total-pages="totalPages"
        @select="selectItem"
        @toggle="toggleSelection"
        @page="changePage"
      />

      <div class="evidence-workbench__detail">
        <EvidenceReviewEditor
          v-if="editing && detail"
          :detail="detail"
          :regions="regions"
          :sources="sources"
          :saving="editSaving"
          :error="editError"
          @save="saveEdit"
          @cancel="editing = false"
          @dirty="editDirty = $event"
        />
        <EvidenceReviewDetailPanel
          v-else
          :detail="detail"
          :loading="detailLoading"
          :error="detailError"
          :busy="decisionBusy"
          @retry="loadDetail"
          @back="returnToQueue"
          @edit="editing = true"
          @decision="applyDecision"
          @select-related="selectItem"
        />
      </div>
    </section>

    <div v-if="batch.open" class="evidence-modal-backdrop" @click.self="closeBatch">
      <section class="evidence-modal" role="dialog" aria-modal="true" aria-labelledby="batch-title">
        <header>
          <div><span class="caption">BATCH PREFLIGHT</span><h3 id="batch-title">{{ batchActionLabel(batch.status) }}</h3></div>
          <button type="button" aria-label="关闭" @click="closeBatch"><X :size="19" /></button>
        </header>
        <div class="evidence-modal__body">
          <p>已选择 {{ selectedKeys.size }} 项。预检不会修改数据，只有全部项目可处理时才能提交。</p>
          <label>
            <span>操作原因 <b v-if="batch.status !== 'verified'">*</b></span>
            <input v-model="batch.reason" type="text" placeholder="说明本次批量处理原因" />
          </label>
          <label>
            <span>审核意见</span>
            <textarea v-model="batch.notes" rows="3" placeholder="可选，记录统一核验依据"></textarea>
          </label>
          <label class="evidence-modal__cascade">
            <input v-model="batch.cascade" type="checkbox" @change="runBatchPreflight" />
            <span>确认级联处理来源的已核验依赖项</span>
          </label>
          <div v-if="batch.loading" class="evidence-modal__state" role="status">正在检查阻止项和依赖影响...</div>
          <div v-else-if="batch.error" class="evidence-modal__state is-error" role="alert">{{ batch.error }}</div>
          <div v-else-if="batch.preflight" class="evidence-preflight">
            <div class="evidence-preflight__summary">
              <span><strong>{{ batch.preflight.actionableCount }}</strong> 可处理</span>
              <span><strong>{{ batch.preflight.blockedCount }}</strong> 被阻止</span>
              <span><strong>{{ batch.preflight.affectedCaseCount + batch.preflight.affectedPolicyCount }}</strong> 依赖影响</span>
            </div>
            <ul v-if="batch.preflight.blockedCount">
              <li v-for="item in blockedBatchItems" :key="`${item.itemType}:${item.itemId}`">
                <strong>{{ item.title || `${item.itemType} #${item.itemId}` }}</strong>
                <span>{{ item.blockingReasons.join('；') }}</span>
              </li>
            </ul>
          </div>
        </div>
        <footer>
          <button type="button" class="button button-secondary" :disabled="batch.busy" @click="closeBatch">取消</button>
          <button type="button" class="button button-secondary" :disabled="batch.loading || batch.busy" @click="runBatchPreflight">重新预检</button>
          <button type="button" class="button" :disabled="!canSubmitBatch" @click="submitBatch">{{ batch.busy ? '正在处理...' : '确认提交' }}</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { X } from 'lucide-vue-next'
import EvidenceReviewBatchBar from '@/components/evidence/EvidenceReviewBatchBar.vue'
import EvidenceReviewDetailPanel from '@/components/evidence/EvidenceReviewDetailPanel.vue'
import EvidenceReviewEditor from '@/components/evidence/EvidenceReviewEditor.vue'
import EvidenceReviewQueuePanel from '@/components/evidence/EvidenceReviewQueuePanel.vue'
import {
  getEvidenceReviewDetail,
  getEvidenceReviewQueue,
  preflightEvidenceReviews,
  updateEvidenceReview,
  updateEvidenceReviews,
} from '@/api/evidenceReview'
import { getRegions } from '@/api/region'
import { getAdminSources, updateSource } from '@/api/source'
import { updateCase } from '@/api/case'
import { updatePolicy } from '@/api/policy'
import {
  buildEvidenceBatchPayload,
  buildEvidenceDecisionPayload,
  evidenceItemKey,
  groupEvidenceBySource,
  isLatestEvidenceRequest,
  nextEvidenceItem,
  reconcileEvidenceSelection,
  clampEvidencePage,
  readEvidenceQuery,
  resolveEvidencePane,
  writeEvidenceQuery,
} from '@/utils/evidenceWorkbench'

const route = useRoute()
const router = useRouter()
const query = reactive(readEvidenceQuery(route.query))
const items = ref([])
const total = ref(0)
const sources = ref([])
const regions = ref([])
const selectedKey = ref(String(route.query.selected || ''))
const selectedKeys = ref(new Set())
const detail = ref(null)
const queueLoading = ref(false)
const detailLoading = ref(false)
const decisionBusy = ref(false)
const detailError = ref('')
const pageError = ref('')
const notice = ref('')
const editing = ref(false)
const editDirty = ref(false)
const editSaving = ref(false)
const editError = ref('')
const isCompact = ref(false)
let queueRequestId = 0
let detailRequestId = 0
let routeSyncing = false

const groups = computed(() => groupEvidenceBySource(items.value))
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))
const compactPane = computed(() => resolveEvidencePane(isCompact.value, selectedKey.value))
const blockedBatchItems = computed(() => batch.preflight?.items?.filter((item) => !item.allowed) || [])
const canSubmitBatch = computed(() => Boolean(
  batch.preflight
  && batch.preflight.blockedCount === 0
  && !batch.loading
  && !batch.busy
  && (batch.status === 'verified' || batch.reason.trim()),
))

const batch = reactive({
  open: false,
  status: '',
  reason: '',
  notes: '',
  cascade: false,
  loading: false,
  busy: false,
  error: '',
  preflight: null,
})

let mediaQuery
function updateCompact() { isCompact.value = mediaQuery?.matches || false }

onMounted(async () => {
  mediaQuery = window.matchMedia('(max-width: 980px)')
  mediaQuery.addEventListener('change', updateCompact)
  updateCompact()
  try {
    ;[regions.value, sources.value] = await Promise.all([getRegions(), getAdminSources()])
  } catch (error) {
    pageError.value = error.message || '审核辅助数据加载失败'
  }
  await loadQueue()
  if (selectedKey.value) await loadDetail()
})
onBeforeUnmount(() => mediaQuery?.removeEventListener('change', updateCompact))

function confirmUnsavedNavigation() {
  return !editing.value || !editDirty.value || window.confirm('当前修改尚未保存，确认离开吗？')
}
onBeforeRouteUpdate(() => confirmUnsavedNavigation())
onBeforeRouteLeave(() => confirmUnsavedNavigation())

watch(() => route.fullPath, async () => {
  if (routeSyncing) {
    routeSyncing = false
    return
  }
  Object.assign(query, readEvidenceQuery(route.query))
  selectedKey.value = String(route.query.selected || '')
  editing.value = false
  await loadQueue()
  if (selectedKey.value) await loadDetail()
  else detail.value = null
})

async function syncUrl() {
  routeSyncing = true
  try {
    await router.replace({ query: writeEvidenceQuery(query, selectedKey.value) })
  } finally {
    routeSyncing = false
  }
}

async function loadQueue() {
  const requestId = ++queueRequestId
  queueLoading.value = true
  pageError.value = ''
  try {
    const result = await getEvidenceReviewQueue(query)
    if (!isLatestEvidenceRequest(requestId, queueRequestId)) return
    items.value = result.items || []
    total.value = Number(result.total || 0)
    selectedKeys.value = reconcileEvidenceSelection(items.value, selectedKeys.value)
    const validPage = clampEvidencePage(query.page, total.value, query.size)
    if (validPage !== query.page) {
      query.page = validPage
      await syncUrl()
      await loadQueue()
    }
  } catch (error) {
    if (isLatestEvidenceRequest(requestId, queueRequestId)) pageError.value = error.message || '审核队列暂时无法读取'
  } finally {
    if (isLatestEvidenceRequest(requestId, queueRequestId)) queueLoading.value = false
  }
}

async function loadDetail() {
  if (!selectedKey.value) return
  const [itemType, itemId] = selectedKey.value.split(':')
  const requestId = ++detailRequestId
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await getEvidenceReviewDetail(itemType, itemId)
    if (isLatestEvidenceRequest(requestId, detailRequestId)) detail.value = result
  } catch (error) {
    if (isLatestEvidenceRequest(requestId, detailRequestId)) detailError.value = error.message || '完整资料加载失败'
  } finally {
    if (isLatestEvidenceRequest(requestId, detailRequestId)) detailLoading.value = false
  }
}

async function selectItem(item) {
  if (editing.value && editDirty.value && !window.confirm('当前修改尚未保存，确认切换资料吗？')) return
  editing.value = false
  editDirty.value = false
  selectedKey.value = evidenceItemKey(item)
  await syncUrl()
  await loadDetail()
}

async function returnToQueue() {
  if (editing.value && editDirty.value && !window.confirm('当前修改尚未保存，确认返回队列吗？')) return
  selectedKey.value = ''
  detail.value = null
  editing.value = false
  await syncUrl()
}

function toggleSelection(item, checked) {
  const next = new Set(selectedKeys.value)
  if (checked) next.add(evidenceItemKey(item)); else next.delete(evidenceItemKey(item))
  selectedKeys.value = next
}
function clearSelection() { selectedKeys.value = new Set() }

async function applyFilters() {
  query.page = 1
  clearSelection()
  await syncUrl()
  await loadQueue()
}
async function resetFilters() {
  Object.assign(query, readEvidenceQuery({}))
  selectedKey.value = ''
  detail.value = null
  clearSelection()
  await syncUrl()
  await loadQueue()
}
async function changePage(page) {
  query.page = page
  clearSelection()
  await syncUrl()
  await loadQueue()
}

async function applyDecision(status, options) {
  if (!detail.value || decisionBusy.value) return
  const currentKey = selectedKey.value
  const next = nextEvidenceItem(items.value, currentKey)
  decisionBusy.value = true
  pageError.value = ''
  notice.value = ''
  try {
    await updateEvidenceReview(detail.value.itemType, detail.value.itemId, buildEvidenceDecisionPayload(detail.value, status, options))
    notice.value = `${detail.value.title} 已完成${batchActionLabel(status)}`
    await loadQueue()
    const candidate = items.value.find((item) => next && evidenceItemKey(item) === evidenceItemKey(next)) || items.value[0]
    if (candidate) await selectItem(candidate)
    else await returnToQueue()
  } catch (error) {
    pageError.value = error.message || '审核操作未完成'
    if (error?.response?.status === 409) await loadDetail()
  } finally {
    decisionBusy.value = false
  }
}

async function saveEdit(payload) {
  if (!detail.value || editSaving.value) return
  editSaving.value = true
  editError.value = ''
  try {
    if (detail.value.itemType === 'case') await updateCase(detail.value.itemId, payload)
    else if (detail.value.itemType === 'policy') await updatePolicy(detail.value.itemId, payload)
    else await updateSource(detail.value.itemId, payload)
    editing.value = false
    editDirty.value = false
    notice.value = '资料已保存，审核条件已重新计算。'
    await Promise.all([loadQueue(), loadDetail()])
  } catch (error) {
    editError.value = error.message || '资料保存失败'
  } finally {
    editSaving.value = false
  }
}

async function openBatch(status) {
  batch.open = true
  batch.status = status
  batch.reason = ''
  batch.notes = ''
  batch.cascade = false
  batch.preflight = null
  batch.error = ''
  await runBatchPreflight()
}
function closeBatch() { if (!batch.busy) batch.open = false }
function currentBatchPayload() {
  return buildEvidenceBatchPayload(items.value, selectedKeys.value, batch.status, batch)
}
async function runBatchPreflight() {
  if (!selectedKeys.value.size) return
  batch.loading = true
  batch.error = ''
  batch.preflight = null
  try {
    batch.preflight = await preflightEvidenceReviews(currentBatchPayload())
  } catch (error) {
    batch.error = error.message || '批量预检失败'
  } finally {
    batch.loading = false
  }
}
async function submitBatch() {
  if (!canSubmitBatch.value) return
  batch.busy = true
  batch.error = ''
  try {
    const payload = currentBatchPayload()
    const result = await updateEvidenceReviews(payload)
    notice.value = `已完成 ${result.processedCount || payload.items.length} 项批量审核。`
    batch.open = false
    clearSelection()
    await loadQueue()
    if (selectedKey.value) await loadDetail()
  } catch (error) {
    batch.error = error.message || '批量审核未完成，未产生部分修改'
  } finally {
    batch.busy = false
  }
}

function batchActionLabel(status) {
  return { verified: '批准核验', excluded: '排除', legacy_unverified: '移回待审' }[status] || status
}
</script>

<style scoped>
.evidence-workbench-page { display: grid; gap: 20px; }.evidence-workbench-intro { display: flex; align-items: end; justify-content: space-between; gap: 30px; padding: 4px 2px; }.evidence-workbench-intro h2 { margin: 8px 0; color: #181a18; font-family: 'Noto Serif SC', STSong, SimSun, serif; font-size: clamp(1.7rem, 2.8vw, 2.45rem); font-weight: 500; }.evidence-workbench-intro p { max-width: 760px; margin: 0; color: #59605a; line-height: 1.7; }.evidence-workbench-key { display: flex; flex-wrap: wrap; gap: 13px; color: #59605a; font-size: .76rem; white-space: nowrap; }.evidence-workbench-key span { display: inline-flex; align-items: center; gap: 6px; }.evidence-workbench-key i { width: 8px; height: 8px; border-radius: 50%; background: #a98042; }.evidence-workbench-key .is-ready { background: #3e6749; }.evidence-workbench-key .is-blocked { background: #8b4038; }
.evidence-workbench-filters { display: grid; grid-template-columns: minmax(210px, 1.4fr) repeat(5, minmax(126px, .72fr)) auto; align-items: end; gap: 12px; padding: 18px; border: 1px solid #cfd4ce; background: #f7f8f4; }.evidence-workbench-filters label { display: grid; gap: 7px; min-width: 0; }.evidence-workbench-filters label > span { color: #4b514c; font-size: .74rem; font-weight: 700; }.evidence-workbench-filters input, .evidence-workbench-filters select { min-width: 0; width: 100%; }.evidence-workbench-filters__actions { display: flex; gap: 7px; }.evidence-workbench-notice { margin: 0 !important; }
.evidence-workbench { display: grid; grid-template-columns: 350px minmax(0, 1fr); min-height: 680px; overflow: hidden; border: 1px solid #cbd0ca; background: #fbfbf8; }.evidence-workbench__detail { min-width: 0; max-height: calc(100vh - 130px); overflow: auto; }
.evidence-modal-backdrop { position: fixed; inset: 0; z-index: 120; display: grid; place-items: center; padding: 22px; background: rgba(22, 24, 22, .46); }.evidence-modal { display: grid; width: min(680px, 100%); max-height: min(760px, calc(100vh - 44px)); overflow: hidden; border: 1px solid #bfc5be; border-radius: 4px; background: #fbfbf8; }.evidence-modal header { display: flex; align-items: start; justify-content: space-between; gap: 20px; padding: 22px 24px; border-bottom: 1px solid #d4d8d2; }.evidence-modal h3 { margin: 7px 0 0; color: #202320; font-family: 'Noto Serif SC', STSong, SimSun, serif; font-size: 1.35rem; font-weight: 500; }.evidence-modal header > button { display: grid; place-items: center; width: 36px; height: 36px; border: 0; background: transparent; color: #2c302c; }.evidence-modal__body { display: grid; gap: 16px; padding: 22px 24px; overflow: auto; }.evidence-modal__body > p { margin: 0; color: #606761; line-height: 1.6; }.evidence-modal label { display: grid; gap: 7px; }.evidence-modal label > span { color: #464c47; font-size: .75rem; font-weight: 700; }.evidence-modal label b { color: #81382f; }.evidence-modal textarea { resize: vertical; }.evidence-modal__cascade { grid-template-columns: auto minmax(0, 1fr) !important; align-items: center; }.evidence-modal__cascade input { width: 16px; height: 16px; accent-color: #202320; }.evidence-modal__state { padding: 15px; border-left: 3px solid #7c827c; background: #f0f1ed; color: #5f665f; }.evidence-modal__state.is-error { border-color: #843a32; color: #7a342d; }.evidence-preflight { display: grid; gap: 14px; }.evidence-preflight__summary { display: grid; grid-template-columns: repeat(3, 1fr); border-top: 1px solid #d3d7d2; border-left: 1px solid #d3d7d2; }.evidence-preflight__summary span { display: grid; gap: 4px; padding: 13px; border-right: 1px solid #d3d7d2; border-bottom: 1px solid #d3d7d2; color: #687069; font-size: .72rem; }.evidence-preflight__summary strong { color: #242824; font-family: 'Bookman Old Style', Georgia, serif; font-size: 1.1rem; }.evidence-preflight ul { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }.evidence-preflight li { display: grid; gap: 4px; padding: 12px 14px; border-left: 3px solid #843a32; background: #f7efed; }.evidence-preflight li strong { color: #632e29; font-size: .8rem; }.evidence-preflight li span { color: #7a4a45; font-size: .73rem; }.evidence-modal footer { display: flex; justify-content: flex-end; gap: 8px; padding: 16px 24px; border-top: 1px solid #d4d8d2; background: #f4f5f1; }
@media (max-width: 1320px) { .evidence-workbench-filters { grid-template-columns: repeat(3, minmax(0, 1fr)); }.evidence-workbench-filters .is-search { grid-column: span 2; }.evidence-workbench-filters__actions { justify-content: flex-end; } }
@media (max-width: 980px) { .evidence-workbench-intro { display: grid; }.evidence-workbench-key { white-space: normal; }.evidence-workbench { display: block; }.evidence-workbench__queue, .evidence-workbench__detail { display: block; }.evidence-workbench__detail { max-height: none; }.evidence-workbench:not(.show-detail) .evidence-workbench__detail { display: none; }.evidence-workbench.show-detail .evidence-workbench__queue { display: none; } }
@media (max-width: 680px) { .evidence-workbench-filters { grid-template-columns: 1fr; }.evidence-workbench-filters .is-search { grid-column: auto; }.evidence-workbench-filters__actions { display: grid; grid-template-columns: repeat(2, 1fr); }.evidence-modal-backdrop { padding: 0; }.evidence-modal { width: 100%; height: 100%; max-height: none; border: 0; border-radius: 0; }.evidence-preflight__summary { grid-template-columns: 1fr; }.evidence-modal footer { display: grid; grid-template-columns: repeat(2, 1fr); }.evidence-modal footer .button:last-child { grid-column: 1 / -1; } }
</style>
