<template>
  <div class="page-stack analysis-overview-page">
    <header class="home-section-heading prisma-features-heading analysis-overview-heading scroll-reveal">
      <WordsPullUpMultiStyle :segments="analysisHeadingSegments" tag="h2" />
    </header>

    <div v-if="loading" class="analysis-state analysis-state--loading" role="status" aria-live="polite">
      正在同步资料分析数据...
    </div>
    <div v-else-if="error" class="analysis-state analysis-state--error" role="alert">
      {{ error }}。分析结构与真实接口请求已保留，请确认后端服务和数据库状态。
    </div>

    <section class="analysis-grid archive-analysis-grid" @pointermove="handlePanelSpotlight">
      <article class="panel analysis-panel wide-panel public-visit-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>平台访问热度</h2>
            <p>基于公开页面访问记录动态统计，反映近期内容关注情况。</p>
          </div>
          <span class="analysis-badge">LIVE</span>
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
            <div v-if="visitTrend.length" class="public-visit-bars">
              <i
                v-for="item in visitTrendBars"
                :key="item.date"
                :style="{ height: `${item.height}%` }"
                :title="`${item.date}: ${item.pv}`"
              ></i>
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

      <article class="panel analysis-panel wide-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>地区资料排行</h2>
            <p>合并政策和案例的地区字段统计。</p>
          </div>
          <span class="analysis-badge">TOP {{ topRegions.length }}</span>
        </div>

        <div v-if="!topRegions.length" class="muted">暂无地区统计数据。</div>
        <div v-else class="rank-chart">
          <div v-for="item in topRegions" :key="item.name" class="rank-row">
            <span class="rank-name">{{ item.name }}</span>
            <div class="rank-track">
              <span :style="{ width: `${item.percent}%` }"></span>
            </div>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>来源追溯概览</h2>
            <p>统计来源链接、访问日期与出处信息的记录情况。</p>
          </div>
        </div>

        <div class="trace-list source-trace-list">
          <div v-for="item in sourceTraceStats" :key="item.name" class="trace-item">
            <div>
              <strong>{{ item.rate }}%</strong>
              <span>{{ item.name }}</span>
              <small>{{ item.count }}/{{ item.total }}</small>
            </div>
            <div class="trace-ring" :style="{ '--value': `${item.rate}%`, '--color': item.color }">
              <span>{{ item.rate }}%</span>
            </div>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>高频标签</h2>
            <p>从政策和案例标签字段提取高频主题。</p>
          </div>
        </div>

        <div v-if="!tagStats.length" class="muted">暂无标签统计数据。</div>
        <div v-else class="tag-cloud">
          <span v-for="tag in tagStats" :key="tag.name" :style="{ '--weight': tag.weight }">
            {{ tag.name }} <b>{{ tag.count }}</b>
          </span>
        </div>
      </article>

      <article class="panel analysis-panel wide-panel scroll-reveal">
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
                  <stop offset="0%" stop-color="#4F6F58" stop-opacity="0.2" />
                  <stop offset="100%" stop-color="#4F6F58" stop-opacity="0" />
                </linearGradient>
              </defs>
              <polygon class="trend-area" :points="trendAreaPoints" fill="url(#trendFill)" />
              <polyline
                class="trend-line"
                :points="trendLinePoints"
                fill="none"
                stroke="#222522"
                stroke-width="4"
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
                  fill="#4F6F58"
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

      <article class="panel analysis-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>案例领域分布</h2>
            <p>按案例类型字段统计应用方向。</p>
          </div>
        </div>

        <div v-if="!caseCategoryStats.length" class="muted">暂无案例领域数据。</div>
        <div v-else class="category-list">
          <div v-for="item in caseCategoryStats" :key="item.name" class="category-row">
            <div>
              <span>{{ item.name }}</span>
              <strong>{{ item.count }} 条</strong>
            </div>
            <p>{{ item.percent }}%</p>
            <i :style="{ width: `${item.rate}%` }"></i>
          </div>
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
import { computed, onMounted, onUnmounted, ref } from 'vue'
import WordsPullUpMultiStyle from '@/components/WordsPullUpMultiStyle.vue'
import { getDashboardSummary } from '@/api/dashboard'
import { getCases } from '@/api/case'
import { getPolicies } from '@/api/policy'
import { getSources } from '@/api/source'
import { getVisitRankings, getVisitSummary, getVisitTrend } from '@/api/visit'

const loading = ref(false)
const error = ref('')
const summary = ref({})
const policies = ref([])
const cases = ref([])
const sources = ref([])
const visitSummary = ref({})
const visitTrend = ref([])
const policyVisitRankings = ref([])
const caseVisitRankings = ref([])
const publishTrendRange = ref('quarter')
const activePublishTrendPoint = ref(null)
let revealObserver

const analysisHeadingSegments = [
  { text: '资料分析概览，随当前数据实时更新。', className: 'prisma-feature-heading-primary' },
  { text: '新增政策或案例后，统计、趋势与观察同步变化。', className: 'prisma-feature-heading-muted' },
]

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

const tagStats = computed(() => {
  const tags = [...policies.value, ...cases.value].flatMap((item) => splitTags(item.tags))
  const rows = countValues(tags).slice(0, 12)
  const max = rows[0]?.count || 1
  return rows.map((item) => ({
    ...item,
    weight: (0.72 + item.count / max).toFixed(2),
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

const sourceTraceStats = computed(() => {
  const totalSources = sources.value.length
  const totalItems = policies.value.length + cases.value.length
  return [
    makeTraceItem('来源关联', [...policies.value, ...cases.value].filter((item) => item.sourceId).length, totalItems, '#181A18'),
    makeTraceItem('来源链接', sources.value.filter((item) => item.url).length, totalSources, '#555B56'),
    makeTraceItem('访问日期', sources.value.filter((item) => item.accessedAt).length, totalSources, '#4F6F58'),
  ]
})

const caseCategoryStats = computed(() => {
  const rows = countBy(cases.value, (item) => item.category || '未标注领域').slice(0, 6)
  const total = rows.reduce((sum, item) => sum + item.count, 0) || 1
  const max = rows[0]?.count || 1
  return rows.map((item) => ({
    ...item,
    percent: Math.round((item.count / total) * 100),
    rate: Math.max(8, Math.round((item.count / max) * 100)),
  }))
})

const insights = computed(() => {
  const region = topRegions.value[0]
  const type = policyTypeStats.value[0]
  const tag = tagStats.value[0]
  const trendLast = publishTrend.value[publishTrend.value.length - 1]

  return [
    region ? `当前资料收录最多的地区是「${region.name}」，政策与案例合计 ${region.count} 条。` : '当前还缺少可用于地区分析的资料数据。',
    type ? `政策类型中「${type.label}」占比最高，可作为汇报中的重点方向。` : '当前还缺少可用于类型分析的政策数据。',
    tag ? `高频标签是「${tag.name}」，说明该方向在资料库中出现较多。` : '当前标签还不够完整，建议录入时补齐 3-8 个标签。',
    trendLast ? `最近一期有 ${trendLast.count} 条政策记录，可结合发布时间判断政策热度。` : '建议补齐政策发布日期，用于形成时间趋势分析。',
  ]
})

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
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

function splitTags(tags) {
  if (!tags) {
    return []
  }
  return String(tags)
    .split(/[,，、;；]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
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

function makeTraceItem(name, count, total, color) {
  return {
    name,
    count,
    total,
    color,
    rate: total > 0 ? Math.round((count / total) * 100) : 0,
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
    const [summaryData, policyData, caseData, sourceData] = await Promise.all([
      getDashboardSummary().catch(() => ({})),
      getPolicies(),
      getCases(),
      getSources().catch(() => []),
    ])
    const [visitSummaryData, policyRankData, caseRankData, visitTrendData] = await Promise.all([
      getVisitSummary().catch(() => ({})),
      getVisitRankings({ targetType: 'policy', limit: 5 }).catch(() => []),
      getVisitRankings({ targetType: 'case', limit: 5 }).catch(() => []),
      getVisitTrend({ days: 7 }).catch(() => []),
    ])
    summary.value = summaryData
    policies.value = policyData
    cases.value = caseData
    sources.value = sourceData
    visitSummary.value = visitSummaryData || {}
    policyVisitRankings.value = policyRankData || []
    caseVisitRankings.value = caseRankData || []
    visitTrend.value = visitTrendData || []
  } catch {
    error.value = '资料分析数据暂时无法读取'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  revealObserver?.disconnect()
})
</script>
