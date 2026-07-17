<template>
  <div class="page-stack home-archive-page">
    <section class="archive-hero" @pointermove="handleHeroPointerMove" @pointerleave="clearCursorTrail">
      <header class="home-landing-nav">
        <RouterLink class="home-landing-brand" to="/" aria-label="SoloFirm 首页">
          <span class="brand-mark brand-logo-mark archive-brand-mark" aria-hidden="true">
            <svg viewBox="0 0 72 72" focusable="false">
              <rect x="4" y="4" width="64" height="64" rx="20" />
              <path class="logo-path-main" d="M24 45V25h12c7 0 12 4 12 10s-5 10-12 10H24Z" />
              <path class="logo-path-accent" d="M18 23C28 14 44 14 54 23" />
              <path class="logo-path-accent" d="M18 49C28 58 44 58 54 49" />
            </svg>
          </span>
          <strong>SoloFirm</strong>
        </RouterLink>

        <nav class="home-landing-links" aria-label="首页导航">
          <RouterLink to="/">首页</RouterLink>
          <RouterLink to="/policies">政策库</RouterLink>
          <RouterLink to="/cases">案例库</RouterLink>
          <a href="#home-data-view">数据分析</a>
        </nav>

        <div class="home-landing-actions">
          <RouterLink class="home-landing-action" to="/login">登录</RouterLink>
          <RouterLink class="home-landing-action primary" to="/policies">进入平台</RouterLink>
        </div>
      </header>

      <div class="hero-network" aria-hidden="true">
        <span class="hero-node"></span>
        <span class="hero-node"></span>
        <span class="hero-node"></span>
        <span class="hero-node"></span>
        <span class="hero-beam hero-beam-one"></span>
        <span class="hero-beam hero-beam-two"></span>
        <span class="hero-beam hero-beam-three"></span>
        <span class="hero-flow hero-flow-one"></span>
        <span class="hero-flow hero-flow-two"></span>
        <span class="hero-flow hero-flow-three"></span>
      </div>
      <div class="cursor-trail" aria-hidden="true">
        <span
          v-for="dot in cursorTrail"
          :key="dot.id"
          :style="{ left: `${dot.x}px`, top: `${dot.y}px`, '--trail-size': `${dot.size}px` }"
        ></span>
      </div>

      <div class="archive-hero-copy">
        <span class="caption">AI + OPC POLICY AND CASE INDEX</span>
        <h1>一人公司的<br /><span>智能创业索引</span></h1>
        <p>
          汇聚 AI + OPC 相关政策、案例与来源资料，帮助创业者快速定位地区机会、政策支持与可参考案例。
        </p>
        <div class="archive-hero-actions">
          <RouterLink class="button" to="/policies">进入政策库</RouterLink>
          <RouterLink class="button button-ghost" to="/cases">查看案例库</RouterLink>
        </div>
        <div class="archive-hero-proof" aria-label="平台核心能力">
          <span>地区分类检索</span>
          <span>来源出处追溯</span>
          <span>摘要标签分析</span>
        </div>
      </div>

      <a class="home-scroll-cue" href="#home-data-view" aria-label="跳转到资料分析概览"></a>
    </section>

    <div id="home-data-view" class="home-section-heading scroll-reveal">
      <span>DATA VIEW</span>
      <h2>资料分析概览</h2>
      <p>以下图表根据当前数据库记录动态生成，新增政策或案例后会随接口数据同步变化。</p>
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
          <span
            v-for="tag in tagStats"
            :key="tag.name"
            :style="{ '--weight': tag.weight }"
          >
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
          <span class="analysis-badge">最近 {{ publishTrend.length }} 期</span>
        </div>

        <div v-if="!publishTrend.length" class="muted">暂无发布日期数据。</div>
        <div v-else class="trend-chart">
          <svg viewBox="0 0 640 210" role="img" aria-label="政策发布时间趋势">
            <defs>
              <linearGradient id="trendFill" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stop-color="#2563eb" stop-opacity="0.18" />
                <stop offset="100%" stop-color="#2563eb" stop-opacity="0" />
              </linearGradient>
            </defs>
            <polygon class="trend-area" :points="trendAreaPoints" fill="url(#trendFill)" />
            <polyline class="trend-line" :points="trendLinePoints" fill="none" stroke="#334155" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
            <circle
              v-for="(point, index) in trendDots"
              :key="point.label"
              class="trend-dot"
              :style="{ animationDelay: `${320 + index * 90}ms` }"
              :cx="point.x"
              :cy="point.y"
              r="5"
              fill="#2563eb"
            />
          </svg>
          <div class="trend-labels">
            <span v-for="item in publishTrend" :key="item.name">{{ item.name }}</span>
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

    <footer class="home-contact-footer scroll-reveal">
      <section class="home-contact-card" aria-labelledby="home-contact-title">
        <div>
          <span class="home-contact-kicker">CONTACT</span>
          <h2 id="home-contact-title">联系我们，<span>共建 OPC 智能创业索引</span></h2>
          <p>
            如果你关注一人公司、AI 创业、政策资料整理或案例共建，欢迎通过以下方式与我们联系。
          </p>

          <div class="home-contact-methods" aria-label="联系方式">
            <div class="home-contact-method">
              <span class="home-contact-icon" aria-hidden="true">LOC</span>
              <div>
                <strong>所属单位</strong>
                <p>西北工业大学软件学院</p>
              </div>
            </div>

            <div class="home-contact-method">
              <span class="home-contact-icon" aria-hidden="true">@</span>
              <div>
                <strong>联系人</strong>
                <p>
                  王兵书 <a href="mailto:wangbingshu@nwpu.edu.cn">wangbingshu@nwpu.edu.cn</a>
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="home-site-footer" aria-label="网站页脚">
        <div>
          <div class="home-footer-brand">
            <span class="brand-mark brand-logo-mark archive-brand-mark" aria-hidden="true">
              <svg viewBox="0 0 72 72" focusable="false">
                <rect x="4" y="4" width="64" height="64" rx="20" />
                <path class="logo-path-main" d="M24 45V25h12c7 0 12 4 12 10s-5 10-12 10H24Z" />
                <path class="logo-path-accent" d="M18 23C28 14 44 14 54 23" />
                <path class="logo-path-accent" d="M18 49C28 58 44 58 54 49" />
              </svg>
            </span>
            <strong>SoloFirm</strong>
          </div>
          <p>聚合 AI + OPC 相关政策、案例与来源资料，帮助创业者和研究者更快理解一人公司的机会结构。</p>
        </div>

        <div class="home-footer-contact">
          <strong>联系我们</strong>
          <span>西北工业大学软件学院</span>
          <a href="mailto:wangbingshu@nwpu.edu.cn">王兵书 wangbingshu@nwpu.edu.cn</a>
        </div>
      </section>
    </footer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
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
const cursorTrail = ref([])
const trailTimers = []
let trailId = 0
let lastTrailAt = 0
let revealObserver

const typeLabels = {
  comprehensive: '综合政策',
  computing_support: '算力支持',
  funding_subsidy: '资金补贴',
  scenario_demand: '场景需求',
  talent_service: '人才服务',
  investment: '投资融资',
  other: '其他',
}

const chartColors = ['#334155', '#2563eb', '#64748b', '#94a3b8', '#1e293b', '#475569', '#cbd5e1']

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
const policyCount = computed(() => Number(summary.value.policyCount ?? policies.value.length))
const caseCount = computed(() => Number(summary.value.caseCount ?? cases.value.length))
const sourceCount = computed(() => Number(summary.value.sourceCount ?? sources.value.length))
const totalRecordCount = computed(() => policyCount.value + caseCount.value + sourceCount.value)
const todayPv = computed(() => Number(visitSummary.value.todayPv || 0))
const coveredRegionCount = computed(() => {
  const fromSummary = summary.value.coveredRegionCount
  if (fromSummary !== undefined && fromSummary !== null) {
    return Number(fromSummary)
  }
  return new Set(
    [...policies.value, ...cases.value]
      .map((item) => item.regionId || item.regionName)
    .filter(Boolean),
  ).size
})
const animatedPolicyCount = useAnimatedNumber(policyCount)
const animatedCaseCount = useAnimatedNumber(caseCount)
const animatedSourceCount = useAnimatedNumber(sourceCount)
const animatedCoveredRegionCount = useAnimatedNumber(coveredRegionCount)
const animatedTotalRecordCount = useAnimatedNumber(totalRecordCount)
const animatedTodayPv = useAnimatedNumber(todayPv)

const overviewBars = computed(() => {
  const trendRows = publishTrend.value.slice(-6).map((item) => ({
    name: item.name,
    label: formatTrendLabel(item.name),
    count: item.count,
  }))
  const rows = trendRows.length
    ? trendRows
    : [
        { name: 'policy', label: '政策', count: policyCount.value },
        { name: 'case', label: '案例', count: caseCount.value },
        { name: 'source', label: '来源', count: sourceCount.value },
        { name: 'region', label: '地区', count: coveredRegionCount.value },
      ]
  const max = Math.max(...rows.map((item) => item.count), 1)
  return rows.map((item) => ({
    ...item,
    height: Math.max(16, Math.round((item.count / max) * 100)),
  }))
})
const overviewChartTitle = computed(() => {
  if (!publishTrend.value.length) {
    return '资料结构速览'
  }
  const year = getTrendYear(publishTrend.value)
  return year ? `近月政策发布（${year}）` : '近月政策发布'
})
const overviewChartCaption = computed(() => (publishTrend.value.length ? '按发布日期统计' : '按记录类型统计'))

function formatTrendLabel(value) {
  const text = String(value || '')
  const match = text.match(/^(\d{4})[-/.年](\d{1,2})/)
  if (!match) {
    return text
  }
  return `${Number(match[2])}月`
}

function getTrendYear(rows) {
  const years = rows
    .map((item) => String(item.name || '').match(/^(\d{4})/)?.[1])
    .filter(Boolean)
  return [...new Set(years)].length === 1 ? years[0] : ''
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function useAnimatedNumber(target) {
  const value = ref(0)
  let frameId = 0

  watch(
    target,
    (next, previous = 0) => {
      window.cancelAnimationFrame(frameId)
      const to = Number(next) || 0
      const from = Number(value.value || previous) || 0

      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || from === to) {
        value.value = to
        return
      }

      const start = window.performance.now()
      const duration = 900
      const tick = (now) => {
        const progress = Math.min((now - start) / duration, 1)
        const eased = 1 - Math.pow(1 - progress, 4)
        value.value = Math.round(from + (to - from) * eased)
        if (progress < 1) {
          frameId = window.requestAnimationFrame(tick)
        }
      }
      frameId = window.requestAnimationFrame(tick)
    },
    { immediate: true },
  )

  return value
}

function handleHeroPointerMove(event) {
  if (event.pointerType !== 'mouse') {
    return
  }
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return
  }
  const now = window.performance.now()
  if (now - lastTrailAt < 30) {
    return
  }
  lastTrailAt = now
  const rect = event.currentTarget.getBoundingClientRect()
  const dot = {
    id: trailId++,
    x: event.clientX - rect.left,
    y: event.clientY - rect.top,
    size: 8 + Math.round(Math.random() * 8),
  }
  cursorTrail.value = [...cursorTrail.value.slice(-10), dot]
  const timer = window.setTimeout(() => {
    cursorTrail.value = cursorTrail.value.filter((item) => item.id !== dot.id)
  }, 560)
  trailTimers.push(timer)
}

function clearCursorTrail() {
  cursorTrail.value = []
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
  return rows.map((item, index) => ({
    ...item,
    label: typeLabels[item.name] || item.name,
    color: chartColors[index % chartColors.length],
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

const publishTrend = computed(() => {
  const values = policies.value
    .map((item) => monthKey(item.publishDate))
    .filter(Boolean)
  return countValues(values)
    .sort((a, b) => a.name.localeCompare(b.name))
    .slice(-8)
})

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
    x: left + index * step,
    y: top + height - (item.count / max) * height,
  }))
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
  const totalPolicies = policies.value.length
  const totalItems = policies.value.length + cases.value.length
  return [
    makeTraceItem('来源关联', [...policies.value, ...cases.value].filter((item) => item.sourceId).length, totalItems, '#315cdb'),
    makeTraceItem('来源链接', sources.value.filter((item) => item.url).length, totalSources, '#334155'),
    makeTraceItem('访问日期', sources.value.filter((item) => item.accessedAt).length, totalSources, '#0f766e'),
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
    .sort((a, b) => b.count - a.count)
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
  const items = document.querySelectorAll('.scroll-reveal')
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
  } catch (err) {
    error.value = err.message || '统计数据加载失败'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  trailTimers.forEach((timer) => window.clearTimeout(timer))
  revealObserver?.disconnect()
})
</script>

