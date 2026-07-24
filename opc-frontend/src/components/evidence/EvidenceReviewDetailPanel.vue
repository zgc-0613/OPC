<template>
  <main class="review-detail">
    <div v-if="loading" class="review-detail__state" role="status">正在加载完整资料与来源链...</div>
    <div v-else-if="error" class="review-detail__state is-error">
      <strong>详情加载失败</strong>
      <span>{{ error }}</span>
      <button type="button" class="button button-secondary" @click="$emit('retry')">重新加载</button>
    </div>
    <div v-else-if="!detail" class="review-detail__state">
      <strong>选择一项资料开始审核</strong>
      <span>左侧队列按来源组织，选择后将在此处加载完整内容。</span>
    </div>
    <template v-else>
      <header class="review-detail__header">
        <button class="review-detail__back" type="button" aria-label="返回队列" @click="$emit('back')"><ArrowLeft :size="19" /></button>
        <div class="review-detail__heading">
          <div class="review-detail__eyebrow">
            <span>{{ typeLabel(detail.itemType) }}</span>
            <span class="status-pill" :class="`evidence-status--${detail.evidenceStatus}`">{{ statusLabel(detail.evidenceStatus) }}</span>
            <span class="status-pill" :class="detail.publicationStatus === 'published' ? 'status-pill--active' : 'status-pill--pending'">{{ publicationLabel(detail.publicationStatus) }}</span>
          </div>
          <h2>{{ detail.title }}</h2>
          <p>证据版本 v{{ detail.version }} · 更新于 {{ formatDate(detail.updatedAt) }}</p>
        </div>
        <a v-if="safeOriginalUrl" class="button button-secondary review-detail__original" :href="safeOriginalUrl" target="_blank" rel="noopener noreferrer">
          打开原文 <ExternalLink :size="15" />
        </a>
      </header>

      <section class="review-detail__section">
        <div class="review-detail__section-heading">
          <span class="caption">REVIEW CHECKS</span>
          <h3>审核检查清单</h3>
        </div>
        <div class="review-checks">
          <div v-for="check in detail.checks || []" :key="check.key" class="review-check" :class="check.passed ? 'is-passed' : 'is-failed'">
            <Check v-if="check.passed" :size="17" />
            <AlertTriangle v-else :size="17" />
            <span><strong>{{ check.label }}</strong><small>{{ check.message }}</small></span>
          </div>
        </div>
        <div v-if="detail.blockingReasons?.length" class="review-blockers" role="alert">
          <strong>当前不能批准</strong>
          <span v-for="reason in detail.blockingReasons" :key="reason">{{ reason }}</span>
        </div>
        <p v-else class="review-ready"><Check :size="16" /> 资料完整，审核条件已满足，可纳入智能体证据。</p>
      </section>

      <section class="review-detail__section">
        <div class="review-detail__section-heading">
          <span class="caption">FULL RECORD</span>
          <h3>资料完整内容</h3>
        </div>
        <dl class="review-metadata">
          <template v-for="row in metadataRows" :key="row.key">
            <dt>{{ row.label }}</dt><dd>{{ row.value }}</dd>
          </template>
        </dl>
        <article v-for="row in narrativeRows" :key="row.key" class="review-narrative">
          <h4>{{ row.label }}</h4>
          <p>{{ row.value }}</p>
        </article>
      </section>

      <section class="review-detail__section">
        <div class="review-detail__section-heading">
          <span class="caption">SOURCE CHAIN</span>
          <h3>关联来源与资料</h3>
        </div>
        <div v-if="detail.source" class="review-source-card">
          <div>
            <small>{{ detail.source.publisher || '发布机构待补充' }}</small>
            <strong>{{ detail.source.title }}</strong>
            <span>{{ publicationLabel(detail.source.status) }} · {{ statusLabel(detail.source.aiEvidenceStatus) }}</span>
          </div>
          <a v-if="safeSourceUrl" :href="safeSourceUrl" target="_blank" rel="noopener noreferrer" aria-label="打开来源原文"><ExternalLink :size="18" /></a>
        </div>
        <p v-else class="review-missing">该资料尚未关联来源。</p>
        <div v-if="detail.relatedItems?.length" class="review-related">
          <button v-for="item in detail.relatedItems" :key="`${item.itemType}:${item.itemId}`" type="button" @click="$emit('select-related', item)">
            <FileText :size="16" />
            <span><small>{{ typeLabel(item.itemType) }}</small><strong>{{ item.title }}</strong></span>
            <em>{{ statusLabel(item.evidenceStatus) }}</em>
          </button>
        </div>
      </section>

      <section class="review-detail__section">
        <div class="review-detail__section-heading">
          <span class="caption">AUDIT TRAIL</span>
          <h3>审核历史</h3>
        </div>
        <ol v-if="detail.history?.length" class="review-history">
          <li v-for="entry in detail.history" :key="entry.id">
            <span class="review-history__marker"></span>
            <div>
              <strong>{{ actionLabel(entry.actionType) }}</strong>
              <p>{{ statusLabel(entry.previousStatus) }} → {{ statusLabel(entry.newStatus) }}</p>
              <small>{{ entry.adminUsername }} · {{ formatDate(entry.createdAt) }} · {{ entry.operationId || '历史记录' }}</small>
              <span v-if="entry.reason">原因：{{ entry.reason }}</span>
              <span v-if="entry.notes">备注：{{ entry.notes }}</span>
            </div>
          </li>
        </ol>
        <p v-else class="review-missing">暂无审核历史。</p>
      </section>

      <section class="review-decision">
        <div class="review-decision__fields">
          <label>
            <span>操作原因 <b v-if="requiresReason">*</b></span>
            <input v-model="reason" type="text" placeholder="排除或移回待审时必须填写" />
          </label>
          <label>
            <span>审核意见</span>
            <input v-model="notes" type="text" placeholder="可选，记录核验依据或补充说明" />
          </label>
          <label v-if="detail.itemType === 'source'" class="review-decision__cascade">
            <input v-model="cascade" type="checkbox" />
            <span>确认级联处理已核验的关联案例与政策</span>
          </label>
        </div>
        <div class="review-decision__actions">
          <button type="button" class="button button-secondary" :disabled="busy" @click="$emit('edit')"><Edit3 :size="16" /> 修正资料</button>
          <button type="button" class="button button-secondary" :disabled="busy || !reason.trim()" @click="submit('legacy_unverified')">移回待审</button>
          <button type="button" class="button button-danger" :disabled="busy || !reason.trim()" @click="submit('excluded')"><X :size="16" /> 排除</button>
          <button type="button" class="button" :disabled="busy || !detail.reviewable" :title="detail.blockingReasons?.join('；')" @click="submit('verified')"><Check :size="16" /> 批准并下一条</button>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { AlertTriangle, ArrowLeft, Check, Edit3, ExternalLink, FileText, X } from 'lucide-vue-next'

const props = defineProps({
  detail: { type: Object, default: null },
  loading: Boolean,
  error: { type: String, default: '' },
  busy: Boolean,
})
const emit = defineEmits(['retry', 'back', 'edit', 'decision', 'select-related'])
const reason = ref('')
const notes = ref('')
const cascade = ref(false)
const pendingStatus = ref('')
const requiresReason = computed(() => ['excluded', 'legacy_unverified'].includes(pendingStatus.value))

watch(() => props.detail?.itemId, () => {
  reason.value = ''
  notes.value = ''
  cascade.value = false
  pendingStatus.value = ''
})

const labelMap = {
  regionId: '地区 ID', category: '行业领域', actorName: '主体名称', sourceId: '来源 ID', tags: '标签',
  issuingBody: '发文机关', documentNo: '文号', publishDate: '发布日期', effectiveDate: '生效日期',
  validPeriod: '有效期限', policyLevel: '政策层级', policyType: '政策类型', accessedAt: '访问日期',
  status: '发布状态', reviewer: '资料复核人', publisher: '发布机构', sourceType: '来源类型', localFile: '本地文件',
}
const narrativeMap = {
  summary: '摘要', businessModel: '商业模式', aiTools: '技术与工具', outcome: '实施结果',
  keyPoints: '关键要点', supportMeasures: '支持措施', notes: '资料备注',
}
const hiddenKeys = new Set(['id', 'title', 'createdAt', 'updatedAt', 'aiEvidenceStatus', 'originalUrl', 'evidenceUrl', 'url'])
const metadataRows = computed(() => Object.entries(props.detail?.content || {})
  .filter(([key, value]) => !hiddenKeys.has(key) && !narrativeMap[key] && value !== null && value !== '')
  .map(([key, value]) => ({ key, label: labelMap[key] || key, value: String(value) })))
const narrativeRows = computed(() => Object.entries(props.detail?.content || {})
  .filter(([key, value]) => narrativeMap[key] && value)
  .map(([key, value]) => ({ key, label: narrativeMap[key], value: String(value) })))
const safeOriginalUrl = computed(() => safeUrl(props.detail?.originalUrl))
const safeSourceUrl = computed(() => safeUrl(props.detail?.source?.url))

function safeUrl(value) {
  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : ''
  } catch {
    return ''
  }
}

function submit(status) {
  pendingStatus.value = status
  if (['excluded', 'legacy_unverified'].includes(status) && !reason.value.trim()) return
  emit('decision', status, { reason: reason.value, notes: notes.value, cascade: cascade.value })
}

const typeLabel = (type) => ({ case: '案例', policy: '政策', source: '来源' }[type] || type)
const statusLabel = (status) => ({ legacy_unverified: '待审核', verified: '已核验', excluded: '已排除' }[status] || status || '-')
const publicationLabel = (status) => ({ published: '已发布', pending: '待发布', draft: '草稿', archived: '已归档' }[status] || status || '-')
const actionLabel = (action) => ({ single_review: '单条审核', batch_review: '批量审核', content_invalidated: '内容修改自动失效', dependency_invalidated: '来源依赖自动失效', legacy_review: '历史审核' }[action] || action || '审核操作')
const formatDate = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
</script>

<style scoped>
.review-detail { min-width: 0; background: #fbfbf8; }.review-detail__state { display: grid; align-content: center; justify-items: start; gap: 10px; min-height: 640px; padding: 44px; color: #687069; }.review-detail__state strong { color: #252925; font-family: 'Noto Serif SC', STSong, SimSun, serif; font-size: 1.2rem; }.review-detail__state.is-error strong { color: #7b342e; }
.review-detail__header { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: start; gap: 18px; padding: 30px 34px; border-bottom: 1px solid #d5d9d3; }.review-detail__back { display: none; width: 38px; height: 38px; border: 1px solid #cdd2cc; background: #fff; color: #202320; }.review-detail__heading { min-width: 0; }.review-detail__eyebrow { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; color: #626962; font-family: 'Bookman Old Style', Georgia, serif; font-size: .72rem; }.review-detail__heading h2 { margin: 12px 0 7px; color: #181a18; font-family: 'Noto Serif SC', STSong, SimSun, serif; font-size: clamp(1.45rem, 2.5vw, 2.15rem); font-weight: 500; line-height: 1.25; }.review-detail__heading p { margin: 0; color: #767d77; font-size: .72rem; }.review-detail__original { display: inline-flex !important; align-items: center; justify-content: center; gap: 7px; width: max-content !important; min-width: 112px; min-height: 42px; padding: 0 16px !important; border-radius: 2px !important; box-sizing: border-box; overflow: visible; white-space: nowrap; flex-shrink: 0; justify-self: end; }
.review-detail__section { display: grid; gap: 20px; padding: 30px 34px; border-bottom: 1px solid #e0e3de; }.review-detail__section-heading { display: grid; gap: 7px; }.review-detail__section-heading h3 { margin: 0; color: #252925; font-family: 'Noto Serif SC', STSong, SimSun, serif; font-size: 1.12rem; font-weight: 600; }
.review-checks { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border-top: 1px solid #d7dad5; border-left: 1px solid #d7dad5; }.review-check { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 10px; padding: 14px; border-right: 1px solid #d7dad5; border-bottom: 1px solid #d7dad5; }.review-check.is-passed { color: #365d40; }.review-check.is-failed { color: #7a342e; }.review-check span { display: grid; gap: 4px; }.review-check strong { color: #282c28; font-size: .82rem; }.review-check small { color: #6d746e; font-size: .72rem; line-height: 1.45; }.review-blockers { display: grid; gap: 6px; padding: 14px 16px; border-left: 3px solid #8b4038; background: #f7efed; color: #74342e; font-size: .78rem; }.review-ready { display: flex; align-items: center; gap: 8px; margin: 0; color: #31583b; font-size: .8rem; }
.review-metadata { display: grid; grid-template-columns: minmax(120px, .28fr) minmax(0, 1fr); margin: 0; border-top: 1px solid #d9ddd7; }.review-metadata dt, .review-metadata dd { margin: 0; padding: 11px 13px; border-right: 1px solid #d9ddd7; border-bottom: 1px solid #d9ddd7; border-left: 1px solid #d9ddd7; }.review-metadata dt { color: #676e68; font-size: .74rem; }.review-metadata dd { border-left: 0; color: #292d29; font-size: .82rem; overflow-wrap: anywhere; }.review-narrative { display: grid; gap: 8px; }.review-narrative h4 { margin: 0; color: #3e443f; font-size: .8rem; }.review-narrative p { margin: 0; color: #4d544e; line-height: 1.75; white-space: pre-wrap; }
.review-source-card { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 17px 18px; border: 1px solid #cfd4ce; background: #f4f5f1; }.review-source-card div { display: grid; gap: 5px; }.review-source-card small, .review-source-card span { color: #687069; font-size: .72rem; }.review-source-card strong { color: #232723; font-family: 'Noto Serif SC', STSong, SimSun, serif; }.review-source-card a { display: grid; place-items: center; width: 38px; height: 38px; color: #242824; }.review-related { display: grid; border-top: 1px solid #d9ddd7; }.review-related button { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 11px; padding: 12px 4px; border: 0; border-bottom: 1px solid #d9ddd7; border-radius: 0; background: transparent; color: #303530; text-align: left; }.review-related button span { display: grid; gap: 3px; }.review-related button small { color: #747b75; font-size: .67rem; }.review-related button strong { font-size: .82rem; }.review-related button em { color: #626963; font-size: .72rem; font-style: normal; }.review-missing { margin: 0; color: #78504a; }
.review-history { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }.review-history li { display: grid; grid-template-columns: 14px minmax(0, 1fr); gap: 12px; padding-bottom: 22px; }.review-history__marker { width: 9px; height: 9px; margin-top: 5px; border-radius: 50%; background: #4e6052; box-shadow: 0 0 0 4px #e4e8e2; }.review-history li > div { display: grid; gap: 4px; }.review-history p { margin: 0; color: #444b45; }.review-history small, .review-history li > div > span { color: #747b75; font-size: .72rem; overflow-wrap: anywhere; }
.review-decision { position: sticky; bottom: 0; z-index: 4; display: grid; gap: 14px; padding: 17px 34px; border-top: 1px solid #c7ccc6; background: rgba(247, 248, 244, .96); backdrop-filter: blur(9px); }.review-decision__fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: end; gap: 12px; }.review-decision label { display: grid; gap: 6px; }.review-decision label > span { color: #555d56; font-size: .72rem; font-weight: 700; }.review-decision label b { color: #80372f; }.review-decision__cascade { grid-column: 1 / -1; grid-template-columns: auto minmax(0, 1fr) !important; align-items: center; }.review-decision__cascade input { width: 16px; height: 16px; accent-color: #202320; }.review-decision__actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 9px; }.review-decision__actions .button { display: inline-flex; align-items: center; gap: 7px; }.button-danger { border: 1px solid #71332e !important; background: #71332e !important; color: #fff !important; }
.evidence-status--verified { border-color: #aac0af !important; background: #edf3ee !important; color: #31553a !important; }.evidence-status--excluded { border-color: #d7b8b3 !important; background: #f8eeeb !important; color: #742e26 !important; }.evidence-status--legacy_unverified { border-color: #d5c9af !important; background: #f4f0e7 !important; color: #665b3d !important; }
@media (max-width: 980px) { .review-detail__back { display: grid; place-items: center; }.review-detail__header { grid-template-columns: auto minmax(0, 1fr); padding: 24px 20px; }.review-detail__original { grid-column: 2; justify-self: start; }.review-detail__section { padding: 26px 20px; }.review-decision { padding: 15px 20px; }.review-checks { grid-template-columns: 1fr; } }
@media (max-width: 620px) { .review-decision__fields { grid-template-columns: 1fr; }.review-decision__cascade { grid-column: auto; }.review-decision__actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }.review-decision__actions .button { justify-content: center; }.review-metadata { grid-template-columns: 1fr; }.review-metadata dd { border-left: 1px solid #d9ddd7; border-top: 0; }.review-metadata dt { border-bottom: 0; } }
</style>
