<template>
  <div class="university-opc-page">
    <section class="university-opc-intro">
      <p class="eyebrow">UNIVERSITY OPC / 高校资料</p>
      <h2>高校 OPC 支撑网络</h2>
      <p class="intro-copy">按社区、支持措施、竞赛活动和创业案例查阅高校 OPC 资料，并查看每条记录的来源与核验状态。</p>
    </section>

    <nav class="university-opc-stats" role="tablist" aria-label="高校 OPC 资料分类">
      <button v-for="(stat, index) in stats" :id="`university-tab-${stat.type}`" :key="stat.label" class="stat-item" :class="{ active: activeTab === stat.type }" type="button" role="tab" :aria-selected="activeTab === stat.type" :tabindex="activeTab === stat.type ? 0 : -1" aria-controls="university-opc-results" @click="activeTab = stat.type" @keydown.left.prevent="focusTab(index - 1)" @keydown.right.prevent="focusTab(index + 1)" @keydown.home.prevent="focusTab(0)" @keydown.end.prevent="focusTab(stats.length - 1)">
        <span class="stat-index">{{ String(index + 1).padStart(2, '0') }} / {{ stat.type.toUpperCase() }}</span>
        <span class="stat-label">{{ stat.label }}</span>
        <strong>{{ records.filter((record) => record.type === stat.type).length }}</strong>
        <small>{{ stat.subtitle }}</small>
      </button>
    </nav>

    <section id="university-opc-results" class="university-opc-workspace" role="tabpanel" :aria-labelledby="`university-tab-${activeTab}`">
      <header class="workspace-heading">
        <div>
          <p class="eyebrow">{{ activeTabMeta.subtitle }}</p>
          <h3 :id="`${activeTab}-title`">{{ activeTabMeta.label }}</h3>
        </div>
        <span class="verification-note">以各条记录的核验状态为准</span>
      </header>

      <div class="university-opc-content">
        <div class="university-opc-main">
        <div class="university-opc-filter-panel" aria-label="高校 OPC 筛选条件">
          <div class="filter-heading"><span>筛选资料</span><small>缩小当前分类的查阅范围</small></div>
          <div class="filter-row">
            <label><span>省份</span><select v-model="filters.province" data-testid="university-province-filter"><option value="">全部省份</option><option v-for="province in provinces" :key="province" :value="province">{{ province }}</option></select></label>
            <label><span>证据等级</span><select v-model="filters.evidence" data-testid="university-evidence-filter"><option value="">全部等级</option><option value="A">A · 官方确认</option><option value="B">B · 官方支持</option><option value="C">C · 官方报道</option></select></label>
            <label><span>关键词</span><input v-model.trim="filters.keyword" data-testid="university-keyword-filter" placeholder="搜索高校、社区或活动" /></label>
          </div>
        </div>

        <div v-if="loading" class="university-opc-empty" role="status"><strong>正在加载高校 OPC 资料</strong></div>
        <div v-else-if="loadError" class="university-opc-empty" role="alert"><strong>资料加载失败</strong><p>{{ loadError }}</p></div>
        <div v-else class="university-opc-records" aria-live="polite">
          <div class="records-heading"><strong>资料列表</strong><span>显示 {{ displayRangeText }}</span></div>
        <article v-for="record in paginatedRecords" :key="record.id" class="university-opc-record" :class="{ 'is-selected': selectedRecord?.id === record.id }">
          <button class="university-opc-record-select" type="button" :aria-pressed="selectedRecord?.id === record.id" @click="selectedRecordId = record.id">
            <div class="record-heading"><span class="record-id">{{ record.id }}</span><h4>{{ record.name }}</h4><span class="record-status" :class="`status-${record.status}`">{{ statusLabel(record.status) }}</span></div>
            <p class="record-meta">{{ record.institution || '高校未明确' }} · {{ record.province }}{{ record.city ? ` · ${record.city}` : '' }} · 证据 {{ record.grade || '未标注' }}</p>
          </button>
          <div v-if="selectedRecord?.id === record.id" class="university-opc-inline-preview">
            <h5>摘要</h5>
            <p>{{ record.summary || '当前记录暂无摘要。' }}</p>
            <a v-if="record.sourceUrl" :href="firstUrl(record.sourceUrl)" target="_blank" rel="noopener noreferrer">查看来源：{{ record.sourceTitle || '原始链接' }} <ArrowUpRight :size="16" /></a>
          </div>
        </article>
        <div v-if="!filteredRecords.length" class="university-opc-empty"><strong>没有符合条件的记录</strong><p>请调整省份、证据等级或关键词。</p></div>
        <nav v-else class="university-opc-pagination" aria-label="高校 OPC 分页">
          <label class="university-opc-page-size">
            <span>每页显示</span>
            <select v-model.number="pageSize" data-testid="university-page-size" aria-label="每页显示条数">
              <option :value="10">10</option>
              <option :value="20">20</option>
              <option :value="50">50</option>
            </select>
            <span>条</span>
          </label>
          <div class="university-opc-page-controls">
            <button
              type="button"
              data-testid="university-prev-page"
              :disabled="currentPage === 1"
              aria-label="上一页"
              @click="goToPage(currentPage - 1)"
            >
              上一页
            </button>
            <button
              v-for="page in paginationPages"
              :key="page"
              type="button"
              class="university-opc-numbered-page"
              :data-testid="`university-page-${page}`"
              :class="{ active: page === currentPage }"
              :aria-current="page === currentPage ? 'page' : undefined"
              :aria-label="`第 ${page} 页`"
              @click="goToPage(page)"
            >
              {{ page }}
            </button>
            <span class="university-opc-mobile-page-label" aria-hidden="true">第 {{ currentPage }} / {{ totalPages }} 页</span>
            <button
              type="button"
              data-testid="university-next-page"
              :disabled="currentPage === totalPages"
              aria-label="下一页"
              @click="goToPage(currentPage + 1)"
            >
              下一页
            </button>
          </div>
          <span data-testid="university-pagination-summary" class="university-pagination-summary">
            {{ currentPage }} / {{ totalPages }} · 共 {{ filteredRecords.length }} 条
          </span>
        </nav>
        </div>
        </div>

        <aside class="university-opc-preview" aria-label="高校 OPC 资料预览" aria-live="polite">
          <template v-if="selectedRecord && !loading && !loadError">
            <div class="university-preview-top"><span>{{ activeTabMeta.label }}</span><span>资料预览</span></div>
            <h4>{{ selectedRecord.name }}</h4>
            <span class="record-status" :class="`status-${selectedRecord.status}`">{{ statusLabel(selectedRecord.status) }}</span>
            <dl class="university-preview-meta">
              <div><dt>高校 / 机构</dt><dd>{{ selectedRecord.institution || '未标注' }}</dd></div>
              <div><dt>地区</dt><dd>{{ selectedRecord.province || '未标注' }}{{ selectedRecord.city ? ` · ${selectedRecord.city}` : '' }}</dd></div>
              <div><dt>证据等级</dt><dd>{{ selectedRecord.grade || '未标注' }}</dd></div>
            </dl>
            <section class="university-preview-summary"><h5>摘要</h5><p>{{ selectedRecord.summary || '当前记录暂无摘要。' }}</p></section>
            <a v-if="selectedRecord.sourceUrl" class="university-preview-source" :href="firstUrl(selectedRecord.sourceUrl)" target="_blank" rel="noopener noreferrer">查看来源：{{ selectedRecord.sourceTitle || '原始链接' }} <ArrowUpRight :size="17" /></a>
          </template>
          <div v-else class="university-preview-placeholder">选择中间的资料查看摘要</div>
        </aside>
      </div>
    </section>

    <section class="university-opc-rules">
      <h3>统计边界</h3>
      <ul>
        <li>高校 OPC 社区、支持措施、竞赛活动和高校创业案例分别统计。</li>
        <li>只有官方来源或可追溯报道才能标记为已核验。</li>
        <li>高校社区不计入普通创业案例总量，关联案例通过关系字段连接。</li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowUpRight } from 'lucide-vue-next'

const route = useRoute()
const tabKeys = ['communities', 'support', 'activities', 'cases']
const normalizeTab = (tab) => (tabKeys.includes(tab) ? tab : 'communities')
const activeTab = ref(normalizeTab(route.query.tab))
const records = ref([])
const loading = ref(true)
const loadError = ref('')
const filters = reactive({ province: '', evidence: '', keyword: '' })
const pageSize = ref(10)
const currentPage = ref(1)
const selectedRecordId = ref(null)
const tabs = [
  { key: 'communities', label: 'OPC 社区', subtitle: '高校载体与空间' },
  { key: 'support', label: '支持措施', subtitle: '课程、算力与孵化' },
  { key: 'activities', label: '竞赛活动', subtitle: '赛事、实训与路演' },
  { key: 'cases', label: '高校创业案例', subtitle: '学生与高校项目' },
]
const stats = [
  { label: '高校 OPC 社区', type: 'communities', subtitle: '空间与载体' },
  { label: '支持措施', type: 'support', subtitle: '课程与孵化' },
  { label: '竞赛与活动', type: 'activities', subtitle: '赛事与实训' },
  { label: '高校创业案例', type: 'cases', subtitle: '实践项目' },
]
const activeTabMeta = computed(() => tabs.find((tab) => tab.key === activeTab.value) || tabs[0])
const activeRecords = computed(() => records.value.filter((record) => record.type === activeTab.value))
const provinces = computed(() => [...new Set(activeRecords.value.map((record) => record.province).filter(Boolean))].sort())
const filteredRecords = computed(() => activeRecords.value.filter((record) => {
  const haystack = `${record.name} ${record.institution} ${record.summary}`.toLowerCase()
  return (!filters.province || record.province === filters.province)
    && (!filters.evidence || record.grade === filters.evidence)
    && (!filters.keyword || haystack.includes(filters.keyword.toLowerCase()))
}))
const totalPages = computed(() => Math.max(1, Math.ceil(filteredRecords.value.length / pageSize.value)))
const paginatedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})
const selectedRecord = computed(() => paginatedRecords.value.find((record) => record.id === selectedRecordId.value) || paginatedRecords.value[0] || null)
const paginationPages = computed(() => {
  const total = totalPages.value
  const start = Math.max(1, Math.min(currentPage.value - 2, total - 4))
  const end = Math.min(total, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})
const displayRangeText = computed(() => {
  if (!filteredRecords.value.length) {
    return '0 / 0 条'
  }
  const start = (currentPage.value - 1) * pageSize.value + 1
  const end = Math.min(currentPage.value * pageSize.value, filteredRecords.value.length)
  return `${start}-${end} / ${filteredRecords.value.length} 条`
})

function statusLabel(status) {
  return { verified: '已核验', partially_verified: '部分核验', pending: '待核验' }[status] || status || '未标注'
}

function firstUrl(value) {
  return String(value).split(';')[0].trim()
}

function goToPage(page) {
  currentPage.value = Math.min(Math.max(Number(page) || 1, 1), totalPages.value)
}

function focusTab(index) {
  const nextTab = stats[(index + stats.length) % stats.length]
  activeTab.value = nextTab.type
  document.getElementById(`university-tab-${nextTab.type}`)?.focus()
}

onMounted(async () => {
  try {
    const response = await fetch('/api/public/university-opc', { headers: { Accept: 'application/json' } })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const payload = await response.json()
    records.value = Array.isArray(payload?.data) ? payload.data : []
  } catch (error) {
    loadError.value = '请稍后刷新页面重试。'
  } finally {
    loading.value = false
  }
})

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = normalizeTab(tab)
  },
)

watch(activeTab, () => {
  if (filters.province && !provinces.value.includes(filters.province)) {
    filters.province = ''
  }
})

watch(
  () => [activeTab.value, filters.province, filters.evidence, filters.keyword, pageSize.value],
  () => {
    currentPage.value = 1
  },
)

watch(totalPages, (pages) => {
  if (currentPage.value > pages) {
    currentPage.value = pages
  }
})

</script>

<style scoped>
.university-opc-page{max-width:1120px;margin:0 auto;padding:8px 0 48px;color:#20251f}.university-opc-intro{padding:18px 0 24px;border-bottom:1px solid #cbd0c9}.eyebrow{margin:0 0 7px;color:#596159;font-size:.72rem;font-weight:700}.university-opc-intro h2{margin:0;font-family: var(--font-ui);font-size:2rem;line-height:1.1}.intro-copy{max-width:680px;margin:10px 0 0;color:#606760;font-size:.82rem;line-height:1.7}.university-opc-stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1px;margin-top:24px;border:1px solid #cbd0c9;background:#cbd0c9}.stat-item{display:grid;gap:7px;min-width:0;padding:18px;background:#eceeeb}.stat-item span{color:#596159;font-size:.73rem;font-weight:700}.stat-item strong{font-family: var(--font-ui);font-size:1.75rem}.stat-item small{color:#747b75;font-size:.68rem}.university-opc-tabs{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0;margin-top:30px;border:1px solid #8e958d;border-radius:6px;overflow:hidden}.university-opc-tabs button{display:grid;gap:4px;min-height:64px;padding:12px;border:0;border-right:1px solid #cbd0c9;background:#fbfbf8;color:#515752;text-align:left;font:inherit;cursor:pointer}.university-opc-tabs button:last-child{border-right:0}.university-opc-tabs button.active{background:#181a18;color:#fbfbf8}.university-opc-tabs button span{font-size:.78rem;font-weight:700}.university-opc-tabs button small{font-size:.67rem;opacity:.78}.university-opc-tabs button:focus-visible{outline:2px solid #4f6f58;outline-offset:-3px}.university-opc-workspace{margin-top:24px;padding:22px;border:1px solid #cbd0c9;background:#fbfbf8}.workspace-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}.workspace-heading h3{margin:0;font-size:1.12rem}.verification-note{padding:6px 9px;border:1px solid #cbd0c9;color:#596159;font-size:.67rem;white-space:nowrap}.filter-row{display:grid;grid-template-columns:1fr 1fr 1.5fr;gap:12px;margin-top:22px}.filter-row label{display:grid;gap:6px;color:#596159;font-size:.7rem;font-weight:700}.filter-row select,.filter-row input{min-height:42px;padding:0 11px;border:1px solid #8e958d;border-radius:5px;background:#f2f3ef;color:#20251f;font:inherit;font-size:.76rem}.filter-row :is(select,input):focus-visible{outline:2px solid #4f6f58;outline-offset:2px}.university-opc-empty{margin-top:24px;padding:38px 20px;border:1px dashed #bfc5bd;text-align:center}.university-opc-empty strong{font-size:.95rem}.university-opc-empty p{max-width:560px;margin:8px auto 0;color:#686f68;font-size:.76rem;line-height:1.7}.university-opc-rules{margin-top:30px;padding-top:20px;border-top:1px solid #cbd0c9}.university-opc-rules h3{margin:0;font-size:1rem}.university-opc-rules ul{display:grid;gap:7px;margin:12px 0 0;padding-left:18px;color:#606760;font-size:.74rem;line-height:1.6}@media(max-width:720px){.university-opc-stats,.university-opc-tabs{grid-template-columns:repeat(2,minmax(0,1fr))}.university-opc-tabs button:nth-child(2){border-right:0}.filter-row{grid-template-columns:1fr}.workspace-heading{flex-direction:column}.verification-note{white-space:normal}}@media(max-width:460px){.university-opc-page{padding-bottom:32px}.university-opc-workspace{padding:16px}.university-opc-stats{grid-template-columns:1fr 1fr}.university-opc-tabs button{min-height:58px;padding:10px 9px}}
.university-opc-records{display:grid;gap:10px;margin-top:22px}.preview-note{margin:0;color:#747b75;font-size:.7rem}.university-opc-record{padding:14px 16px;border:1px solid #d5d9d3;background:#f7f8f4}.record-heading{display:flex;align-items:center;gap:9px}.record-heading h4{flex:1;margin:0;font-size:.85rem;line-height:1.45}.record-id{color:#747b75;font-family: var(--font-code);font-size:.7rem}.record-status{padding:3px 7px;border-radius:3px;font-size:.65rem;white-space:nowrap}.status-verified{color:#35543d;background:#e1eee4}.status-partially_verified{color:#5c5131;background:#f3ecd1}.status-pending{color:#76542d;background:#f5e7d2}.record-meta,.record-summary{margin:6px 0 0;color:#697169;font-size:.72rem;line-height:1.6}.record-summary{color:#4d554e}.record-source{display:inline-block;margin-top:7px;color:#47624e;font-size:.7rem;text-decoration:underline;text-underline-offset:2px}
</style>
