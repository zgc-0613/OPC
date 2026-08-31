<template>
  <div class="admin-stack">
    <section class="admin-panel admin-hero admin-analytics-hero">
      <div>
        <span class="caption">DATA OPERATIONS</span>
        <h2>录入、校对、维护政策与案例资料</h2>
        <p>后台首页汇总公开页面访问情况，辅助判断哪些政策、案例和页面更受关注。</p>
      </div>
      <div class="admin-quick">
        <RouterLink class="button" to="/admin/policies">维护政策</RouterLink>
        <RouterLink class="button button-ghost" to="/admin/sources">维护来源</RouterLink>
        <button class="button button-ghost" type="button" :disabled="exportingDataset" @click="downloadPaperDataset">
          {{ exportingDataset ? '正在生成...' : '导出论文数据快照' }}
        </button>
      </div>
      <p v-if="exportNotice" class="muted" role="status">{{ exportNotice }}</p>
      <p v-if="exportError" class="error" role="alert">{{ exportError }}</p>
    </section>

    <section class="admin-grid">
      <RouterLink class="admin-card" to="/admin/policies">
        <span>01</span>
        <strong>政策管理</strong>
        <p>新增、编辑、删除政策记录，维护摘要、标签、来源和实施字段。</p>
      </RouterLink>
      <RouterLink class="admin-card" to="/admin/cases">
        <span>02</span>
        <strong>案例管理</strong>
        <p>维护 AI + OPC / 一人公司案例，沉淀应用场景和成果。</p>
      </RouterLink>
      <RouterLink class="admin-card" to="/admin/sources">
        <span>03</span>
        <strong>来源管理</strong>
        <p>维护原文链接、文件名、访问日期和来源说明。</p>
      </RouterLink>
      <RouterLink class="admin-card" to="/admin/tags">
        <span>04</span>
        <strong>标签管理</strong>
        <p>维护政策、案例通用标签，为筛选和摘要分析服务。</p>
      </RouterLink>
    </section>

    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>访问统计</h2>
          <p>统计来自公开展示页访问，不包含登录页和后台管理页。</p>
        </div>
        <button class="button button-ghost" type="button" @click="loadVisitAnalytics">刷新数据</button>
      </div>

      <div v-if="loading" class="muted">正在加载访问统计...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <template v-else>
        <div class="admin-visit-stats">
          <div class="admin-visit-card">
            <span>总访问量 PV</span>
            <strong>{{ formatNumber(summary.totalPv) }}</strong>
            <small>公开页面访问次数</small>
          </div>
          <div class="admin-visit-card">
            <span>独立访客 UV</span>
            <strong>{{ formatNumber(summary.totalUv) }}</strong>
            <small>按访问者标识去重</small>
          </div>
          <div class="admin-visit-card">
            <span>今日 PV</span>
            <strong>{{ formatNumber(summary.todayPv) }}</strong>
            <small>今日新增访问</small>
          </div>
          <div class="admin-visit-card">
            <span>今日 UV</span>
            <strong>{{ formatNumber(summary.todayUv) }}</strong>
            <small>今日独立访客</small>
          </div>
        </div>

        <div class="admin-analytics-grid">
          <article class="admin-analytics-panel admin-trend-panel">
            <div class="admin-mini-head">
              <div>
                <span class="caption">{{ trendCaption }}</span>
                <h3>{{ trendHeading }}</h3>
              </div>
              <div class="admin-trend-meta">
                <strong>
                  <span>区间 PV</span>
                  {{ formatNumber(trendTotalPv) }}
                </strong>
                <div class="admin-trend-range" role="group" aria-label="访问趋势时间范围">
                  <button
                    v-for="option in trendRangeOptions"
                    :key="option.days"
                    type="button"
                    :class="{ 'is-active': selectedTrendDays === option.days }"
                    :aria-pressed="selectedTrendDays === option.days"
                    @click="changeTrendRange(option.days)"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>
            </div>
            <p v-if="trendError" class="admin-trend-error" role="alert">{{ trendError }}</p>
            <div v-if="trend.length" class="admin-trend-chart" :aria-busy="trendLoading">
              <div class="admin-trend-plot" @mouseleave="clearActiveTrendPoint()">
                <svg viewBox="0 0 640 220" role="group" :aria-label="`${trendHeading}，可聚焦数据点查看日期、PV 和 UV`">
                  <title>{{ trendHeading }}，区间访问量 {{ formatNumber(trendTotalPv) }} PV</title>
                  <defs>
                    <linearGradient id="adminTrendFill" x1="0" x2="0" y1="0" y2="1">
                      <stop offset="0%" stop-color="#4F6F58" stop-opacity="0.2" />
                      <stop offset="100%" stop-color="#4F6F58" stop-opacity="0" />
                    </linearGradient>
                  </defs>
                  <path class="admin-trend-area" :d="trendAreaPath" />
                  <polyline class="admin-trend-line" :points="trendPoints" fill="none" />
                  <g v-for="point in trendPointList" :key="point.date">
                    <circle
                      class="admin-trend-hit-area"
                      :cx="point.x"
                      :cy="point.y"
                      r="13"
                      tabindex="0"
                      role="button"
                      :aria-label="trendPointAriaLabel(point)"
                      @mouseenter="setActiveTrendPoint(point)"
                      @focus="setActiveTrendPoint(point)"
                      @blur="clearActiveTrendPoint(point)"
                      @click="setActiveTrendPoint(point)"
                      @keyup.enter.space.prevent="setActiveTrendPoint(point)"
                    />
                    <circle
                      class="admin-trend-dot"
                      :class="{ 'is-active': activeTrendPoint?.date === point.date }"
                      :cx="point.x"
                      :cy="point.y"
                      r="4"
                      aria-hidden="true"
                    />
                  </g>
                </svg>
                <div
                  v-if="activeTrendPoint"
                  class="admin-trend-tooltip"
                  :class="{ 'is-below': activeTrendPoint.y < 82 }"
                  :style="trendTooltipStyle"
                  role="status"
                >
                  <time :datetime="activeTrendPoint.date">{{ formatFullDate(activeTrendPoint.date) }}</time>
                  <span>PV <strong>{{ formatNumber(activeTrendPoint.pv) }}</strong></span>
                  <span>UV <strong>{{ formatNumber(activeTrendPoint.uv) }}</strong></span>
                </div>
                <span v-if="trendLoading" class="admin-trend-loading" role="status">正在更新趋势...</span>
              </div>
              <div class="admin-trend-labels" aria-hidden="true">
                <span
                  v-for="item in trendLabels"
                  :key="item.date"
                  :class="item.alignment"
                  :style="{ left: `${(item.x / 640) * 100}%` }"
                >{{ formatDateLabel(item.date) }}</span>
              </div>
            </div>
            <div v-else class="empty-state">
              <strong>暂无趋势数据</strong>
              <p>公开页面产生访问后，这里会自动形成趋势。</p>
            </div>
          </article>

          <article class="admin-analytics-panel">
            <div class="admin-mini-head">
              <div>
                <span class="caption">CONTENT SHARE</span>
                <h3>政策 / 案例访问占比</h3>
              </div>
            </div>
            <div class="admin-share-bars">
              <div class="admin-share-item">
                <span>政策访问</span>
                <strong>{{ formatNumber(summary.policyPv) }}</strong>
                <i><b :style="{ width: `${policyShare}%` }"></b></i>
              </div>
              <div class="admin-share-item">
                <span>案例访问</span>
                <strong>{{ formatNumber(summary.casePv) }}</strong>
                <i><b :style="{ width: `${caseShare}%` }"></b></i>
              </div>
            </div>
          </article>

          <article class="admin-analytics-panel">
            <div class="admin-mini-head">
              <div>
                <span class="caption">POLICY TOP 5</span>
                <h3>热门政策排行</h3>
              </div>
            </div>
            <ol v-if="policyRankings.length" class="admin-ranking-list">
              <li v-for="(item, index) in policyRankings" :key="`policy-${item.targetId}`">
                <span>{{ index + 1 }}</span>
                <p>{{ item.title || `政策 #${item.targetId}` }}</p>
                <strong>{{ formatNumber(item.pv) }}</strong>
              </li>
            </ol>
            <div v-else class="empty-state">
              <strong>暂无政策访问</strong>
              <p>用户访问政策详情页后会出现在这里。</p>
            </div>
          </article>

          <article class="admin-analytics-panel">
            <div class="admin-mini-head">
              <div>
                <span class="caption">CASE TOP 5</span>
                <h3>热门案例排行</h3>
              </div>
            </div>
            <ol v-if="caseRankings.length" class="admin-ranking-list">
              <li v-for="(item, index) in caseRankings" :key="`case-${item.targetId}`">
                <span>{{ index + 1 }}</span>
                <p>{{ item.title || `案例 #${item.targetId}` }}</p>
                <strong>{{ formatNumber(item.pv) }}</strong>
              </li>
            </ol>
            <div v-else class="empty-state">
              <strong>暂无案例访问</strong>
              <p>用户访问案例详情页后会出现在这里。</p>
            </div>
          </article>

          <article class="admin-analytics-panel">
            <div class="admin-mini-head">
              <div>
                <span class="caption">SEARCH TOP 10</span>
                <h3>热门搜索词</h3>
              </div>
            </div>
            <ol v-if="hotKeywords.length" class="admin-ranking-list admin-keyword-list">
              <li v-for="(item, index) in hotKeywords" :key="`${item.searchScope}-${item.keyword}`">
                <span>{{ index + 1 }}</span>
                <p>
                  {{ item.keyword }}
                  <small>{{ scopeLabel(item.searchScope) }}</small>
                </p>
                <strong>{{ formatNumber(item.searchCount) }}</strong>
              </li>
            </ol>
            <div v-else class="empty-state">
              <strong>暂无搜索记录</strong>
              <p>用户在政策或案例页输入关键词后会出现在这里。</p>
            </div>
          </article>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getVisitRankings, getVisitSummary, getVisitTrend } from '@/api/visit'
import { getHotSearchKeywords } from '@/api/searchLog'
import { exportPaperDataset } from '@/api/export'

const loading = ref(false)
const error = ref('')
const summary = ref({})
const policyRankings = ref([])
const caseRankings = ref([])
const trend = ref([])
const hotKeywords = ref([])
const selectedTrendDays = ref(7)
const loadedTrendDays = ref(7)
const activeTrendPoint = ref(null)
const trendLoading = ref(false)
const trendError = ref('')
const exportingDataset = ref(false)
const exportNotice = ref('')
const exportError = ref('')
let trendRequestId = 0

async function downloadPaperDataset() {
  exportingDataset.value = true
  exportNotice.value = ''
  exportError.value = ''
  try {
    await exportPaperDataset()
    exportNotice.value = '论文数据快照已导出；请保留原文件，并在副本中进行字段优化。'
  } catch (error) {
    exportError.value = error.message || '论文数据快照导出失败。'
  } finally {
    exportingDataset.value = false
  }
}

const trendRangeOptions = [
  { days: 7, label: '7 天' },
  { days: 30, label: '30 天' },
  { days: 180, label: '半年' },
]

const trendTotalPv = computed(() => trend.value.reduce((total, item) => total + Number(item.pv || 0), 0))
const contentTotalPv = computed(() => Number(summary.value.policyPv || 0) + Number(summary.value.casePv || 0))
const policyShare = computed(() => getShare(summary.value.policyPv, contentTotalPv.value))
const caseShare = computed(() => getShare(summary.value.casePv, contentTotalPv.value))
const trendCaption = computed(() => {
  const captions = { 7: '7 DAY TREND', 30: '30 DAY TREND', 180: '6 MONTH TREND' }
  return captions[selectedTrendDays.value]
})
const trendHeading = computed(() => {
  const headings = { 7: '最近七天访问趋势', 30: '最近三十天访问趋势', 180: '最近半年访问趋势' }
  return headings[selectedTrendDays.value]
})

const trendPointList = computed(() => {
  if (!trend.value.length) {
    return []
  }

  const maxPv = Math.max(...trend.value.map((item) => Number(item.pv || 0)), 1)
  const width = 600
  const height = 170
  const startX = 20
  const startY = 24
  const step = trend.value.length > 1 ? width / (trend.value.length - 1) : 0

  return trend.value.map((item, index) => ({
    date: item.date,
    pv: Number(item.pv || 0),
    uv: Number(item.uv || 0),
    x: startX + step * index,
    y: startY + height - (Number(item.pv || 0) / maxPv) * height,
  }))
})

const trendLabels = computed(() => {
  const points = trendPointList.value
  if (!points.length) {
    return []
  }

  const targetCount = selectedTrendDays.value === 7 ? points.length : selectedTrendDays.value === 30 ? 6 : 7
  const labelCount = Math.min(points.length, targetCount)
  const indexes = new Set()
  for (let index = 0; index < labelCount; index += 1) {
    indexes.add(labelCount === 1 ? 0 : Math.round((index * (points.length - 1)) / (labelCount - 1)))
  }

  return [...indexes].map((pointIndex, labelIndex, allIndexes) => ({
    ...points[pointIndex],
    alignment: labelIndex === 0 ? 'is-first' : labelIndex === allIndexes.length - 1 ? 'is-last' : '',
  }))
})

const trendPoints = computed(() => trendPointList.value.map((point) => `${point.x},${point.y}`).join(' '))
const trendTooltipStyle = computed(() => {
  if (!activeTrendPoint.value) {
    return {}
  }
  return {
    '--trend-tooltip-x': `${(activeTrendPoint.value.x / 640) * 100}%`,
    '--trend-tooltip-y': `${(activeTrendPoint.value.y / 220) * 100}%`,
  }
})
const trendAreaPath = computed(() => {
  const points = trendPointList.value
  if (!points.length) {
    return ''
  }
  const first = points[0]
  const last = points[points.length - 1]
  return `M ${first.x} 204 L ${points.map((point) => `${point.x} ${point.y}`).join(' L ')} L ${last.x} 204 Z`
})

async function loadVisitAnalytics() {
  loading.value = true
  error.value = ''
  trendError.value = ''
  activeTrendPoint.value = null
  try {
    const [summaryData, policyData, caseData, trendData, keywordData] = await Promise.all([
      getVisitSummary(),
      getVisitRankings({ targetType: 'policy', limit: 5 }),
      getVisitRankings({ targetType: 'case', limit: 5 }),
      getVisitTrend({ days: selectedTrendDays.value }),
      getHotSearchKeywords({ limit: 10 }),
    ])

    summary.value = summaryData || {}
    policyRankings.value = policyData || []
    caseRankings.value = caseData || []
    trend.value = trendData || []
    loadedTrendDays.value = selectedTrendDays.value
    hotKeywords.value = keywordData || []
  } catch (err) {
    error.value = err.message || '访问统计加载失败'
  } finally {
    loading.value = false
  }
}

async function changeTrendRange(days) {
  if (days === selectedTrendDays.value && trend.value.length) {
    return
  }

  const requestId = ++trendRequestId
  selectedTrendDays.value = days
  activeTrendPoint.value = null
  trendLoading.value = true
  trendError.value = ''

  try {
    const trendData = await getVisitTrend({ days })
    if (requestId !== trendRequestId) {
      return
    }
    trend.value = trendData || []
    loadedTrendDays.value = days
  } catch (err) {
    if (requestId !== trendRequestId) {
      return
    }
    selectedTrendDays.value = loadedTrendDays.value
    trendError.value = err.message || '趋势数据更新失败，请稍后重试。'
  } finally {
    if (requestId === trendRequestId) {
      trendLoading.value = false
    }
  }
}

function setActiveTrendPoint(point) {
  activeTrendPoint.value = point
}

function clearActiveTrendPoint(point) {
  if (!point || activeTrendPoint.value?.date === point.date) {
    activeTrendPoint.value = null
  }
}

function trendPointAriaLabel(point) {
  return `${formatFullDate(point.date)}，PV ${formatNumber(point.pv)}，UV ${formatNumber(point.uv)}`
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function getShare(value, total) {
  if (!total) {
    return 0
  }
  return Math.round((Number(value || 0) / total) * 100)
}

function formatDateLabel(date) {
  if (!date) {
    return '-'
  }
  return String(date).slice(5)
}

function formatFullDate(date) {
  const match = String(date || '').match(/^(\d{4})-(\d{2})-(\d{2})$/)
  return match ? `${match[1]}年${match[2]}月${match[3]}日` : date || '-'
}

function scopeLabel(scope) {
  const labels = {
    policy: '政策',
    case: '案例',
    source: '来源',
    region: '地区',
    all: '全站',
  }
  return labels[scope] || scope || '全站'
}

onMounted(loadVisitAnalytics)
</script>

<style scoped>
.admin-trend-meta {
  display: grid;
  justify-items: end;
  gap: 10px;
}

.admin-trend-meta > strong {
  display: grid;
  justify-items: end;
  gap: 3px;
  color: #181a18;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 26px;
  line-height: 1;
}

.admin-trend-meta > strong span {
  color: #626863;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: 11px;
  font-weight: 600;
}

.admin-trend-range {
  display: grid;
  grid-template-columns: repeat(3, minmax(52px, 1fr));
  min-width: 184px;
  padding: 3px;
  border: 1px solid #c8ccc7;
  border-radius: 6px;
  background: #f5f5f1;
}

.admin-trend-range button {
  min-height: 30px;
  padding: 4px 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #535954;
  font: 600 12px/1 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  cursor: pointer;
}

.admin-trend-range button:hover {
  background: #e5e7e4;
  color: #181a18;
}

.admin-trend-range button.is-active {
  background: #181a18;
  color: #fbfbf8;
}

.admin-trend-range button:focus-visible {
  outline: 2px solid #181a18;
  outline-offset: 2px;
}

.admin-trend-error {
  margin: 0 0 10px;
  padding: 8px 10px;
  border: 1px solid #d4b4ad;
  border-radius: 6px;
  background: #f8eeeb;
  color: #742e26;
  font-size: 13px;
}

.admin-trend-plot {
  position: relative;
}

.admin-trend-hit-area {
  fill: transparent;
  stroke: transparent;
  cursor: crosshair;
}

.admin-trend-hit-area:focus {
  fill: rgba(24, 26, 24, 0.08);
  stroke: #181a18;
  stroke-width: 2;
  outline: none;
}

.admin-trend-dot {
  pointer-events: none;
  transition: r 160ms ease, fill 160ms ease;
}

.admin-trend-dot.is-active {
  fill: #181a18 !important;
  r: 6px;
}

.admin-trend-tooltip {
  position: absolute;
  z-index: 3;
  top: var(--trend-tooltip-y);
  left: clamp(76px, var(--trend-tooltip-x), calc(100% - 76px));
  display: grid;
  grid-template-columns: repeat(2, auto);
  gap: 5px 14px;
  min-width: 152px;
  padding: 9px 11px;
  border: 1px solid #181a18;
  border-radius: 6px;
  background: #fbfbf8;
  color: #4e544f;
  font-size: 12px;
  line-height: 1.25;
  box-shadow: 0 8px 22px rgba(24, 26, 24, 0.12);
  pointer-events: none;
  transform: translate(-50%, calc(-100% - 10px));
}

.admin-trend-tooltip.is-below {
  transform: translate(-50%, 14px);
}

.admin-trend-tooltip time {
  grid-column: 1 / -1;
  color: #181a18;
  font-weight: 700;
}

.admin-trend-tooltip strong {
  color: #181a18;
  font-family: 'Bookman Old Style', Georgia, serif;
}

.admin-trend-loading {
  position: absolute;
  inset: 1px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  background: rgba(251, 251, 248, 0.72);
  color: #333833;
  font-size: 13px;
  pointer-events: none;
}

.admin-trend-labels {
  position: relative;
  display: block;
  height: 18px;
}

.admin-trend-labels span {
  position: absolute;
  top: 0;
  white-space: nowrap;
  transform: translateX(-50%);
}

.admin-trend-labels span.is-first {
  transform: none;
}

.admin-trend-labels span.is-last {
  transform: translateX(-100%);
}

.admin-share-bars > .admin-share-item {
  min-height: 112px;
  padding: 16px;
  border: 1px solid #d0d4cf !important;
  border-radius: 6px;
  background: #fbfbf8 !important;
  color: #252925 !important;
}

@media (max-width: 640px) {
  .admin-mini-head {
    align-items: stretch;
    flex-direction: column;
  }

  .admin-trend-meta {
    grid-template-columns: auto minmax(0, 1fr);
    align-items: end;
    justify-items: stretch;
  }

  .admin-trend-meta > strong {
    justify-items: start;
  }

  .admin-trend-range {
    min-width: 0;
  }

  .admin-trend-chart svg {
    min-height: 184px;
  }
}
</style>
