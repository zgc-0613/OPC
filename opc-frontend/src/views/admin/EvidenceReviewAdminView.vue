<template>
  <div class="admin-stack evidence-review-page">
    <section class="admin-panel evidence-review-intro">
      <div>
        <span class="caption">EVIDENCE GOVERNANCE</span>
        <h2>证据审核队列</h2>
        <p>仅已发布且来源链完整的案例与政策可进入智能体证据集。每次批准、排除或撤销核验都会记录操作管理员和时间。</p>
      </div>
      <div class="evidence-review-key">
        <span><i class="is-verified"></i>已核验</span>
        <span><i class="is-pending"></i>待审核</span>
        <span><i class="is-excluded"></i>已排除</span>
      </div>
    </section>

    <section class="admin-panel evidence-review-panel">
      <form class="evidence-review-filters" @submit.prevent="loadQueue(1)">
        <label>
          <span>资料类型</span>
          <select v-model="query.itemType">
            <option value="">全部资料</option>
            <option value="case">案例</option>
            <option value="policy">政策</option>
            <option value="source">来源</option>
          </select>
        </label>
        <label>
          <span>核验状态</span>
          <select v-model="query.evidenceStatus">
            <option value="">全部状态</option>
            <option value="legacy_unverified">待审核</option>
            <option value="verified">已核验</option>
            <option value="excluded">已排除</option>
          </select>
        </label>
        <button class="button" type="submit" :disabled="loading">筛选</button>
        <button class="button button-ghost" type="button" :disabled="loading" @click="resetFilters">重置</button>
      </form>

      <p v-if="notice" class="success evidence-review-notice" role="status">{{ notice }}</p>
      <p v-if="error" class="error evidence-review-notice" role="alert">{{ error }}</p>
      <div v-if="loading" class="settings-state muted" role="status">正在读取证据审核队列...</div>
      <div v-else-if="!items.length" class="empty-state">
        <strong>当前筛选下暂无资料</strong>
        <p>可切换资料类型或核验状态查看其他待处理记录。</p>
      </div>
      <div v-else class="table-wrap evidence-review-table-wrap">
        <table class="evidence-review-table">
          <thead>
            <tr>
              <th>资料</th>
              <th>发布状态</th>
              <th>来源链</th>
              <th>核验状态</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="`${item.itemType}-${item.itemId}`">
              <td>
                <span class="evidence-type">{{ typeLabel(item.itemType) }}</span>
                <strong>{{ item.title }}</strong>
              </td>
              <td><span class="status-pill" :class="item.publicationStatus === 'published' ? 'status-pill--active' : 'status-pill--pending'">{{ item.publicationStatus === 'published' ? '已发布' : item.publicationStatus }}</span></td>
              <td>
                <template v-if="item.itemType === 'source'">
                  <span :class="item.sourceEligible ? 'evidence-ready' : 'evidence-blocked'">{{ item.sourceEligible ? '来源可用' : '需补全链接或发布状态' }}</span>
                </template>
                <template v-else>
                  <strong v-if="item.sourceTitle">{{ item.sourceTitle }}</strong>
                  <span v-else class="evidence-blocked">未关联来源</span>
                  <small v-if="item.sourceTitle" :class="item.sourceEligible ? 'evidence-ready' : 'evidence-blocked'">{{ item.sourceEligible ? '已发布并核验' : '未发布或未核验' }}</small>
                </template>
              </td>
              <td><span class="status-pill" :class="`evidence-status--${item.evidenceStatus}`">{{ statusLabel(item.evidenceStatus) }}</span></td>
              <td>{{ formatDate(item.updatedAt) }}</td>
              <td>
                <div class="row-actions evidence-review-actions">
                  <button type="button" :disabled="actionKey" @click="review(item, 'verified')">批准核验</button>
                  <button type="button" :disabled="actionKey" @click="review(item, 'excluded')">排除</button>
                  <button type="button" :disabled="actionKey" @click="review(item, 'legacy_unverified')">撤销核验</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <nav v-if="total > query.size" class="pagination evidence-review-pagination" aria-label="证据审核分页">
        <button type="button" :disabled="loading || query.page <= 1" @click="loadQueue(query.page - 1)">上一页</button>
        <span>第 {{ query.page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
        <button type="button" :disabled="loading || query.page >= totalPages" @click="loadQueue(query.page + 1)">下一页</button>
      </nav>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getEvidenceReviewQueue, updateEvidenceReview } from '@/api/evidenceReview'

const query = reactive({ itemType: '', evidenceStatus: '', page: 1, size: 20 })
const items = ref([])
const total = ref(0)
const loading = ref(false)
const actionKey = ref('')
const notice = ref('')
const error = ref('')
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.size)))

onMounted(() => loadQueue())

async function loadQueue(page = query.page) {
  loading.value = true
  error.value = ''
  query.page = page
  try {
    const result = await getEvidenceReviewQueue(query)
    items.value = result.items || []
    total.value = Number(result.total || 0)
  } catch (err) {
    error.value = err.message || '证据审核队列暂时无法读取'
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.itemType = ''
  query.evidenceStatus = ''
  loadQueue(1)
}

async function review(item, evidenceStatus) {
  actionKey.value = `${item.itemType}-${item.itemId}`
  notice.value = ''
  error.value = ''
  try {
    await updateEvidenceReview(item.itemType, item.itemId, { evidenceStatus })
    notice.value = `${item.title} 已${statusLabel(evidenceStatus)}`
    await loadQueue(query.page)
  } catch (err) {
    error.value = err.message || '审核操作未完成'
  } finally {
    actionKey.value = ''
  }
}

function typeLabel(value) {
  return { case: '案例', policy: '政策', source: '来源' }[value] || value
}

function statusLabel(value) {
  return { legacy_unverified: '待审核', verified: '已核验', excluded: '已排除' }[value] || value
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}
</script>

<style scoped>
.evidence-review-page { display: grid; gap: 24px; }
.evidence-review-intro { display: flex; align-items: end; justify-content: space-between; gap: 28px; }
.evidence-review-intro h2 { margin: 7px 0 8px; color: #181a18; font-family: 'Noto Serif SC', STSong, SimSun, serif; font-size: clamp(1.55rem, 2.5vw, 2.3rem); font-weight: 500; }
.evidence-review-intro p { max-width: 680px; margin: 0; color: #59605a; line-height: 1.7; }
.evidence-review-key { display: flex; flex-wrap: wrap; gap: 13px; color: #555c56; font-size: .78rem; white-space: nowrap; }
.evidence-review-key span { display: inline-flex; align-items: center; gap: 6px; }
.evidence-review-key i { width: 8px; height: 8px; border-radius: 50%; background: #878d87; }
.evidence-review-key .is-verified { background: #426a4d; }.evidence-review-key .is-excluded { background: #7a4640; }
.evidence-review-panel { display: grid; gap: 20px; }
.evidence-review-filters { display: grid; grid-template-columns: minmax(170px, 1fr) minmax(170px, 1fr) max-content max-content; align-items: end; gap: 14px; padding-bottom: 20px; border-bottom: 1px solid #d0d4cf; }
.evidence-review-filters label { display: grid; gap: 7px; }.evidence-review-filters label > span { color: #4b514c; font-size: .78rem; font-weight: 700; }
.evidence-review-notice { margin: 0 !important; }.evidence-review-table { min-width: 1040px; }.evidence-review-table td { vertical-align: middle; }.evidence-review-table td:first-child { min-width: 260px; }.evidence-review-table td:first-child strong { display: block; max-width: 360px; margin-top: 5px; color: #202320; }.evidence-type { color: #757c76; font-family: 'Bookman Old Style', Georgia, serif; font-size: .71rem; text-transform: uppercase; }
.evidence-review-table td:nth-child(3) { min-width: 180px; }.evidence-review-table td:nth-child(3) strong, .evidence-review-table td:nth-child(3) small { display: block; }.evidence-review-table td:nth-child(3) small { margin-top: 4px; font-size: .75rem; }.evidence-ready { color: #31553a; }.evidence-blocked { color: #742e26; }
.evidence-status--verified { border-color: #aac0af !important; background: #edf3ee !important; color: #31553a !important; }.evidence-status--excluded { border-color: #d7b8b3 !important; background: #f8eeeb !important; color: #742e26 !important; }.evidence-status--legacy_unverified { border-color: #d5c9af !important; background: #f4f0e7 !important; color: #665b3d !important; }
.evidence-review-actions { display: grid !important; grid-template-columns: repeat(3, max-content); gap: 7px !important; }.evidence-review-actions button { min-height: 36px; padding: 7px 9px !important; white-space: nowrap; }.evidence-review-pagination { justify-content: center; }
@media (max-width: 820px) { .evidence-review-intro { display: grid; align-items: start; }.evidence-review-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); }.evidence-review-filters .button { width: 100%; justify-content: center; } }
@media (max-width: 480px) { .evidence-review-filters { grid-template-columns: minmax(0, 1fr); }.evidence-review-key { white-space: normal; } }
</style>
