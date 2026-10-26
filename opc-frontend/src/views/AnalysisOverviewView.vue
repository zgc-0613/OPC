<template>
  <div class="page-stack analysis-overview-page">
    <div v-if="loading" class="analysis-state analysis-state--loading" role="status" aria-live="polite">
      正在同步资料分析数据...
    </div>
    <div v-else-if="error" class="analysis-state analysis-state--error" role="alert">
      {{ error }}。分析结构与真实接口请求已保留，请确认后端服务和数据库状态。
    </div>

    <section v-if="!loading && !error" class="analysis-overview-intro" aria-labelledby="analysis-overview-title">
      <div class="analysis-overview-intro-copy">
        <span class="analysis-overview-kicker">RESEARCH ATLAS / 资料概览</span>
        <h2 id="analysis-overview-title">从资料出发，<br /><em>看见更多关联</em></h2>
        <p>沿着地区、时间、主题与来源追踪 OPC 生态的公开资料，从一条记录走向更完整的研究线索。</p>
        <div class="analysis-overview-intro-links">
          <RouterLink to="/analytics">深入研究指标 <ArrowUpRight :size="17" /></RouterLink>
          <RouterLink to="/regions">探索地区分布 <ArrowUpRight :size="17" /></RouterLink>
        </div>
      </div>
      <dl class="analysis-overview-snapshot" aria-label="当前资料规模">
        <div v-for="item in snapshotItems" :key="item.key">
          <dt>{{ item.label }}</dt>
          <dd :aria-label="`${item.label} ${item.target === null ? '暂不可用' : `${formatNumber(item.target)} ${item.unit}`}`">{{ item.value === null ? '—' : formatNumber(item.value) }}<small v-if="item.value !== null">{{ item.unit }}</small></dd>
        </div>
      </dl>
    </section>

    <section class="analysis-grid archive-analysis-grid" @pointermove="handlePanelSpotlight">
      <article class="panel analysis-panel wide-panel public-visit-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>平台访问热度</h2>
            <p>基于公开页面访问记录统计，反映近期内容关注趋势。</p>
          </div>
        </div>

        <div class="public-visit-layout">
          <div class="public-visit-summary">
            <div>
              <span>总访问量</span>
              <strong>{{ formatNumber(visitSummary.totalPv) }}</strong>
              <small>PV</small>
            </div>
            <div>
              <span>独立访客</span>
              <strong>{{ formatNumber(visitSummary.totalUv) }}</strong>
              <small>UV</small>
            </div>
            <div>
              <span>今日访问</span>
              <strong>{{ formatNumber(visitSummary.todayPv) }}</strong>
              <small>今日 PV</small>
            </div>
          </div>

          <div class="public-visit-trend">
            <span>最近七天趋势</span>
            <div v-if="visitTrend.length" class="public-visit-bars" role="img" :aria-label="`最近七天访问趋势：${visitTrend.map((item) => `${item.date} ${item.pv} 次`).join('；')}`">
              <span
                v-for="item in visitTrendBars"
                :key="item.date"
                :title="`${item.date}: ${item.pv}`"
              ><i :style="{ height: `${item.height}%` }"></i><small>{{ String(item.date).slice(5) }}</small></span>
            </div>
            <p v-else class="muted">暂无访问趋势。</p>
          </div>

          <div class="public-visit-hot">
            <div>
              <span>热门政策</span>
              <strong>{{ topPolicy?.title || '暂无数据' }}</strong>
            </div>
            <div>
              <span>热门案例</span>
              <strong>{{ topCase?.title || '暂无数据' }}</strong>
            </div>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel wide-panel region-ranking-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>地区资料排行</h2>
            <p>按地区维度汇总政策与案例数量。</p>
          </div>
        </div>

        <div v-if="!topRegions.length" class="muted">暂无地区统计数据。</div>
        <div v-else class="rank-chart">
          <div v-for="(item, index) in topRegions" :key="item.name" class="rank-row">
            <span class="rank-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="rank-name">{{ item.name }}</span>
            <div class="rank-track">
              <span :style="{ width: `${item.percent}%` }"></span>
            </div>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel wide-panel policy-trend-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>政策发布趋势</h2>
            <p>按月份统计政策发布日期。</p>
          </div>
          <div class="policy-trend-range" role="group" aria-label="政策发布趋势时间范围">
            <button
              v-for="option in publishTrendRangeOptions"
              :key="option.value"
              type="button"
              :class="{ 'is-active': publishTrendRange === option.value }"
              :aria-pressed="publishTrendRange === option.value"
              @click="publishTrendRange = option.value"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div v-if="!publishTrend.length" class="muted">暂无发布日期数据。</div>
        <div v-else class="trend-chart">
          <div class="policy-trend-plot" @mouseleave="clearActivePublishTrendPoint()">
            <svg viewBox="0 0 640 210" role="group" aria-label="政策发布时间趋势，可聚焦数据点查看当月政策发布数量">
              <title>政策发布趋势，共 {{ publishTrendTotal }} 条政策</title>
              <defs>
                <linearGradient id="trendFill" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stop-color="#16b9ad" stop-opacity="0.28" />
                  <stop offset="100%" stop-color="#16b9ad" stop-opacity="0" />
                </linearGradient>
              </defs>
              <g class="analysis-trend-grid" aria-hidden="true"><line x1="28" x2="612" y1="52" y2="52" /><line x1="28" x2="612" y1="100" y2="100" /><line x1="28" x2="612" y1="148" y2="148" /><line x1="28" x2="612" y1="195" y2="195" /></g>
              <polygon class="trend-area" :points="trendAreaPoints" fill="url(#trendFill)" />
              <polyline
                class="trend-line"
                :points="trendLinePoints"
                fill="none"
                stroke="#1bc7bb"
                stroke-width="3"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <g v-for="(point, index) in trendDots" :key="point.label">
                <circle
                  class="policy-trend-hit-area"
                  :cx="point.x"
                  :cy="point.y"
                  r="14"
                  tabindex="0"
                  role="button"
                  :aria-label="`${formatMonthLabel(point.label)}发布 ${point.count} 条政策`"
                  @mouseenter="setActivePublishTrendPoint(point)"
                  @focus="setActivePublishTrendPoint(point)"
                  @blur="clearActivePublishTrendPoint(point)"
                  @click="setActivePublishTrendPoint(point)"
                  @keyup.enter.space.prevent="setActivePublishTrendPoint(point)"
                />
                <circle
                  class="trend-dot"
                  :class="{ 'is-active': activePublishTrendPoint?.label === point.label }"
                  :style="{ animationDelay: `${320 + index * 60}ms` }"
                  :cx="point.x"
                  :cy="point.y"
                  r="5"
                  fill="#62f2df"
                  aria-hidden="true"
                />
              </g>
            </svg>
            <div
              v-if="activePublishTrendPoint"
              class="policy-trend-tooltip"
              :class="{ 'is-below': activePublishTrendPoint.y < 74 }"
              :style="publishTrendTooltipStyle"
              role="status"
            >
              <strong>{{ formatMonthLabel(activePublishTrendPoint.label) }}</strong>
              <span>发布 <b>{{ activePublishTrendPoint.count }}</b> 条政策</span>
            </div>
          </div>
          <div class="policy-trend-labels" aria-hidden="true">
            <span
              v-for="(item, index) in publishTrendLabels"
              :key="item.label"
              :class="{ 'is-first': index === 0, 'is-last': index === publishTrendLabels.length - 1 }"
              :style="{ left: `${(item.x / 640) * 100}%` }"
            >{{ item.label }}</span>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel insight-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>资料观察</h2>
            <p>基于当前记录生成的概览性线索。</p>
          </div>
        </div>

        <div class="insight-list">
          <p v-for="insight in insights" :key="insight">{{ insight }}</p>
        </div>
      </article>

      <article class="panel analysis-panel archive-recent-panel compact-recent-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>最近更新</h2>
            <p>最新 5 条资料记录。</p>
          </div>
        </div>

        <div v-if="!compactRecentUpdates.length" class="muted">暂无最近更新。</div>
        <div v-else class="timeline-list archive-timeline compact-recent-list">
          <div v-for="item in compactRecentUpdates" :key="`${item.itemType}-${item.itemId}`" class="list-row">
            <div>
              <span class="caption">{{ item.itemType }}</span>
              <strong>{{ item.title }}</strong>
            </div>
            <span class="muted">{{ item.updatedDate || '-' }}</span>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ArrowUpRight } from 'lucide-vue-next'
import request from '@/api/request'
import { getDashboardSummary } from '@/api/dashboard'
import { getCases } from '@/api/case'
import { getPlatformSites } from '@/api/platformSite'
import { getPolicies } from '@/api/policy'
import { getSources } from '@/api/source'
import { getVisitRankings, getVisitSummary, getVisitTrend } from '@/api/visit'

const loading = ref(false)
const error = ref('')
const summary = ref({})
const policies = ref([])
const cases = ref([])
const sources = ref([])
const universityRecords = ref(null)
const platformSites = ref(null)
const snapshotDisplay = ref({ policies: 0, cases: 0, sources: 0, university: 0, platforms: 0 })
const snapshotItems = computed(() => [
  { key: 'policies', label: '政策资料', unit: '条', target: policies.value.length, value: snapshotDisplay.value.policies },
  { key: 'cases', label: '案例资料', unit: '条', target: cases.value.length, value: snapshotDisplay.value.cases },
  { key: 'sources', label: '来源记录', unit: '条', target: sources.value.length, value: snapshotDisplay.value.sources },
  { key: 'university', label: '高校资料', unit: '条', target: universityRecords.value?.length ?? null, value: universityRecords.value === null ? null : snapshotDisplay.value.university },
  { key: 'platforms', label: '平台网站', unit: '个', target: platformSites.value?.length ?? null, value: platformSites.value === null ? null : snapshotDisplay.value.platforms },
])
let snapshotObserver
let snapshotFrame
const visitSummary = ref({})
const visitTrend = ref([])
const policyVisitRankings = ref([])
const caseVisitRankings = ref([])
const publishTrendRange = ref('quarter')
const activePublishTrendPoint = ref(null)
let revealObserver

const publishTrendRangeOptions = [
  { value: 'quarter', label: '近一季度', months: 3 },
  { value: 'halfYear', label: '近半年', months: 6 },
  { value: 'year', label: '近一年', months: 12 },
  { value: 'all', label: '有史以来', months: null },
]

const typeLabels = {
  comprehensive: '综合政策',
  computing_support: '算力支持',
  funding_subsidy: '资金补贴',
  scenario_demand: '场景需求',
  talent_service: '人才服务',
  investment: '投资融资',
  other: '其他',
}

const recentUpdates = computed(() => summary.value.recentUpdates || [])
const dedupedRecentUpdates = computed(() => {
  const seen = new Set()
  return recentUpdates.value.filter((item) => {
    const key = `${item.itemType || ''}::${item.title || ''}`
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
})
const compactRecentUpdates = computed(() => dedupedRecentUpdates.value.slice(0, 5))
const topPolicy = computed(() => policyVisitRankings.value[0] || null)
const topCase = computed(() => caseVisitRankings.value[0] || null)
const visitTrendBars = computed(() => {
  const max = Math.max(...visitTrend.value.map((item) => Number(item.pv || 0)), 1)
  return visitTrend.value.map((item) => ({
    ...item,
    height: Math.max(12, Math.round((Number(item.pv || 0) / max) * 100)),
  }))
})

const topRegions = computed(() => {
  const rows = countBy([...policies.value, ...cases.value], (item) => item.regionName || '未标注地区').slice(0, 8)
  const max = rows[0]?.count || 1
  return rows.map((item) => ({
    ...item,
    percent: Math.max(8, Math.round((item.count / max) * 100)),
  }))
})

const policyTypeStats = computed(() => {
  const rows = countBy(policies.value, (item) => item.policyType || 'other')
  const total = rows.reduce((sum, item) => sum + item.count, 0) || 1
  return rows.map((item) => ({
    ...item,
    label: typeLabels[item.name] || item.name,
    percent: Math.round((item.count / total) * 100),
  }))
})

const allPublishTrend = computed(() => {
  const counts = new Map()
  policies.value
    .map((item) => monthKey(item.publishDate))
    .filter(Boolean)
    .forEach((month) => counts.set(month, (counts.get(month) || 0) + 1))

  const months = [...counts.keys()].sort((left, right) => left.localeCompare(right))
  if (!months.length) {
    return []
  }

  return enumerateMonths(months[0], months[months.length - 1]).map((name) => ({
    name,
    count: counts.get(name) || 0,
  }))
})

const publishTrend = computed(() => {
  const option = publishTrendRangeOptions.find((item) => item.value === publishTrendRange.value)
  if (!option?.months) {
    return allPublishTrend.value
  }
  return allPublishTrend.value.slice(-option.months)
})

const publishTrendTotal = computed(() => publishTrend.value.reduce((total, item) => total + item.count, 0))

const trendDots = computed(() => {
  if (!publishTrend.value.length) {
    return []
  }

  const width = 640
  const height = 170
  const top = 20
  const left = 28
  const right = 28
  const max = Math.max(...publishTrend.value.map((item) => item.count), 1)
  const step = publishTrend.value.length > 1 ? (width - left - right) / (publishTrend.value.length - 1) : 0

  return publishTrend.value.map((item, index) => ({
    label: item.name,
    count: item.count,
    x: left + index * step,
    y: top + height - (item.count / max) * height,
  }))
})

const publishTrendLabels = computed(() => {
  const points = trendDots.value
  if (!points.length) {
    return []
  }
  const labelCount = Math.min(points.length, 8)
  const indexes = new Set()
  for (let index = 0; index < labelCount; index += 1) {
    indexes.add(labelCount === 1 ? 0 : Math.round((index * (points.length - 1)) / (labelCount - 1)))
  }
  return [...indexes].map((pointIndex) => ({
    ...points[pointIndex],
    label: points[pointIndex].label,
  }))
})

const publishTrendTooltipStyle = computed(() => {
  if (!activePublishTrendPoint.value) {
    return {}
  }
  return {
    '--policy-tooltip-x': `${(activePublishTrendPoint.value.x / 640) * 100}%`,
    '--policy-tooltip-y': `${(activePublishTrendPoint.value.y / 210) * 100}%`,
  }
})

const trendLinePoints = computed(() => trendDots.value.map((point) => `${point.x},${point.y}`).join(' '))

const trendAreaPoints = computed(() => {
  if (!trendDots.value.length) {
    return ''
  }
  const baseline = 195
  const first = trendDots.value[0]
  const last = trendDots.value[trendDots.value.length - 1]
  return `${first.x},${baseline} ${trendLinePoints.value} ${last.x},${baseline}`
})

const insights = computed(() => {
  const region = topRegions.value[0]
  const type = policyTypeStats.value[0]
  const trendLast = publishTrend.value[publishTrend.value.length - 1]

  return [
    region ? `当前资料收录最多的地区为「${region.name}」，政策与案例合计 ${region.count} 条。` : '当前还缺少可用于地区分析的资料数据。',
    type ? `政策类型中「${type.label}」占比最高。` : '当前还缺少可用于类型分析的政策数据。',
    trendLast ? `最近一期新增 ${trendLast.count} 条政策记录。` : '建议补齐政策发布日期，用于形成时间趋势分析。',
  ]
})

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function startSnapshotCountUp() {
  const targets = Object.fromEntries(snapshotItems.value.map((item) => [item.key, item.target]))
  const finish = () => { snapshotDisplay.value = targets }
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches) {
    finish()
    return
  }
  const startedAt = performance.now()
  const duration = 1100
  const tick = (now) => {
    const progress = Math.min(1, (now - startedAt) / duration)
    const eased = 1 - Math.pow(1 - progress, 3)
    snapshotDisplay.value = Object.fromEntries(Object.entries(targets).map(([key, value]) => [
      key, value === null ? null : Math.round(value * eased),
    ]))
    if (progress < 1) snapshotFrame = window.requestAnimationFrame(tick)
    else finish()
  }
  snapshotFrame = window.requestAnimationFrame(tick)
}

function observeSnapshot() {
  const snapshot = document.querySelector('.route-analysis-overview .analysis-overview-snapshot')
  if (!snapshot || !('IntersectionObserver' in window)) {
    startSnapshotCountUp()
    return
  }
  snapshotObserver = new IntersectionObserver((entries) => {
    if (!entries[0]?.isIntersecting) return
    snapshotObserver.disconnect()
    startSnapshotCountUp()
  }, { threshold: 0.2 })
  snapshotObserver.observe(snapshot)
}

function handlePanelSpotlight(event) {
  const panel = event.target.closest('.analysis-panel')
  if (!panel) {
    return
  }
  const rect = panel.getBoundingClientRect()
  panel.style.setProperty('--spotlight-x', `${event.clientX - rect.left}px`)
  panel.style.setProperty('--spotlight-y', `${event.clientY - rect.top}px`)
}

function countBy(list, picker) {
  return countValues(list.map(picker).filter(Boolean))
}

function countValues(values) {
  const map = new Map()
  values.forEach((value) => {
    map.set(value, (map.get(value) || 0) + 1)
  })
  return Array.from(map.entries())
    .map(([name, count]) => ({ name, count }))
    .sort((left, right) => right.count - left.count)
}

function monthKey(date) {
  if (!date || String(date).length < 7) {
    return ''
  }
  return String(date).slice(0, 7)
}

function enumerateMonths(start, end) {
  const [startYear, startMonth] = start.split('-').map(Number)
  const [endYear, endMonth] = end.split('-').map(Number)
  const startIndex = startYear * 12 + startMonth - 1
  const endIndex = endYear * 12 + endMonth - 1
  const months = []
  for (let index = startIndex; index <= endIndex; index += 1) {
    const year = Math.floor(index / 12)
    const month = (index % 12) + 1
    months.push(`${year}-${String(month).padStart(2, '0')}`)
  }
  return months
}

function formatMonthLabel(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})$/)
  return match ? `${match[1]}年${Number(match[2])}月` : value || '-'
}

function setActivePublishTrendPoint(point) {
  activePublishTrendPoint.value = point
}

function clearActivePublishTrendPoint(point) {
  if (!point || activePublishTrendPoint.value?.label === point.label) {
    activePublishTrendPoint.value = null
  }
}

function setupScrollReveal() {
  revealObserver?.disconnect()
  const items = document.querySelectorAll('.route-analysis-overview .scroll-reveal')
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    items.forEach((item) => item.classList.add('is-visible'))
    return
  }

  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          revealObserver.unobserve(entry.target)
        }
      })
    },
    {
      rootMargin: '0px 0px -12% 0px',
      threshold: 0.16,
    },
  )

  items.forEach((item) => revealObserver.observe(item))
}

onMounted(async () => {
  setupScrollReveal()
  loading.value = true
  error.value = ''
  try {
    const [summaryData, policyData, caseData, sourceData, universityData, platformData, visitSummaryData, policyRankData, caseRankData, visitTrendData] = await Promise.all([
      getDashboardSummary().catch(() => ({})),
      getPolicies(),
      getCases(),
      getSources().catch(() => []),
      request.get('/public/university-opc').catch(() => null),
      getPlatformSites().catch(() => null),
      getVisitSummary().catch(() => ({})),
      getVisitRankings({ targetType: 'policy', limit: 5 }).catch(() => []),
      getVisitRankings({ targetType: 'case', limit: 5 }).catch(() => []),
      getVisitTrend({ days: 7 }).catch(() => []),
    ])
    summary.value = summaryData
    policies.value = policyData
    cases.value = caseData
    sources.value = sourceData
    universityRecords.value = Array.isArray(universityData) ? universityData : null
    platformSites.value = Array.isArray(platformData) ? platformData : null
    visitSummary.value = visitSummaryData || {}
    policyVisitRankings.value = policyRankData || []
    caseVisitRankings.value = caseRankData || []
    visitTrend.value = visitTrendData || []
  } catch {
    error.value = '资料分析数据暂时无法读取'
  } finally {
    loading.value = false
  }
  if (!error.value) {
    await nextTick()
    observeSnapshot()
  }
})

onUnmounted(() => {
  revealObserver?.disconnect()
  snapshotObserver?.disconnect()
  if (snapshotFrame) window.cancelAnimationFrame(snapshotFrame)
})
</script>
