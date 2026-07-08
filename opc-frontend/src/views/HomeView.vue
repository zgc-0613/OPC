<template>
  <div class="page-stack">
    <section class="hero-panel analytics-hero">
      <div class="hero-copy">
        <span class="caption">AI + OPC policy and case index</span>
        <h2>一人公司的政策经纬</h2>
        <p>
          把分散在各地的 AI + OPC 政策、案例和来源整理成可筛选、可引用、可复核的资料索引。
        </p>
        <div class="hero-actions">
          <RouterLink class="button" to="/policies">进入政策索引</RouterLink>
          <RouterLink class="button button-ghost" to="/regions">查看地区目录</RouterLink>
        </div>
      </div>

      <div class="signal-board analytics-board" aria-label="平台数据状态">
        <div class="signal-board-head">
          <span>INDEX RECORDS</span>
          <strong>{{ totalRecordCount }}</strong>
        </div>
        <div class="radar-rings">
          <span></span>
          <span></span>
          <span></span>
        </div>
      </div>
    </section>

    <section class="panel panel-raised">
      <div class="section-header compact-header">
        <div>
          <h2>资料概览</h2>
          <p>统计来自后端实时接口，后台新增或修改数据后，刷新页面即可更新。</p>
        </div>
      </div>

      <div v-if="loading" class="muted">正在加载统计数据...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="stats-grid">
        <div class="stat-card stat-card-hot">
          <span>政策记录</span>
          <strong>{{ policyCount }}</strong>
          <small>Policy</small>
        </div>
        <div class="stat-card">
          <span>案例记录</span>
          <strong>{{ caseCount }}</strong>
          <small>Case</small>
        </div>
        <div class="stat-card">
          <span>来源记录</span>
          <strong>{{ sourceCount }}</strong>
          <small>Source</small>
        </div>
        <div class="stat-card">
          <span>覆盖地区</span>
          <strong>{{ coveredRegionCount }}</strong>
          <small>Region</small>
        </div>
      </div>
    </section>

    <section class="analysis-grid">
      <article class="panel analysis-panel wide-panel">
        <div class="section-header">
          <div>
            <h2>地区资料排行</h2>
            <p>合并政策和案例的地区字段统计，展示当前资料库重点覆盖地区。</p>
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

      <article class="panel analysis-panel">
        <div class="section-header">
          <div>
            <h2>政策类型占比</h2>
            <p>判断政策重点更偏资金、算力、场景还是综合支持。</p>
          </div>
        </div>

        <div v-if="!policyTypeStats.length" class="muted">暂无类型统计数据。</div>
        <div v-else class="type-share-chart">
          <div class="type-share-total">
            <span>政策总量</span>
            <strong>{{ policies.length }}</strong>
            <small>按 policyType 字段统计</small>
          </div>
          <div class="type-share-list">
            <div v-for="item in policyTypeStats" :key="item.name" class="type-share-row">
              <div>
                <strong>{{ item.label }}</strong>
                <small>{{ item.count }} 条 / {{ item.percent }}%</small>
              </div>
              <span class="type-share-track">
                <i :style="{ width: `${item.percent}%`, background: item.color }"></i>
              </span>
            </div>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel">
        <div class="section-header">
          <div>
            <h2>高频标签</h2>
            <p>从政策和案例标签字段提取高频支持方向与应用场景。</p>
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

      <article class="panel analysis-panel wide-panel">
        <div class="section-header">
          <div>
            <h2>政策发布趋势</h2>
            <p>按月份统计政策发布日期，观察资料库中政策发布的时间分布。</p>
          </div>
          <span class="analysis-badge">最近 {{ publishTrend.length }} 期</span>
        </div>

        <div v-if="!publishTrend.length" class="muted">暂无发布日期数据。</div>
        <div v-else class="trend-chart">
          <svg viewBox="0 0 640 210" role="img" aria-label="政策发布时间趋势">
            <defs>
              <linearGradient id="trendFill" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stop-color="#252724" stop-opacity="0.20" />
                <stop offset="100%" stop-color="#252724" stop-opacity="0" />
              </linearGradient>
            </defs>
            <polygon class="trend-area" :points="trendAreaPoints" fill="url(#trendFill)" />
            <polyline class="trend-line" :points="trendLinePoints" fill="none" stroke="#252724" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
            <circle
              v-for="(point, index) in trendDots"
              :key="point.label"
              class="trend-dot"
              :style="{ animationDelay: `${320 + index * 90}ms` }"
              :cx="point.x"
              :cy="point.y"
              r="5"
              fill="#8a640f"
            />
          </svg>
          <div class="trend-labels">
            <span v-for="item in publishTrend" :key="item.name">{{ item.name }}</span>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel">
        <div class="section-header">
          <div>
            <h2>来源追溯完整度</h2>
            <p>检查来源链接、文件、访问日期和政策辅证链接是否完整。</p>
          </div>
        </div>

        <div class="trace-list">
          <div v-for="item in sourceTraceStats" :key="item.name" class="trace-item">
            <div>
              <strong>{{ item.rate }}%</strong>
              <span>{{ item.name }}</span>
            </div>
            <div class="trace-ring" :style="{ '--value': `${item.rate}%`, '--color': item.color }">
              <span>{{ item.count }}/{{ item.total }}</span>
            </div>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel insight-panel">
        <div class="section-header">
          <div>
            <h2>快速解读</h2>
            <p>基于当前真实数据生成的汇报线索。</p>
          </div>
        </div>

        <div class="insight-list">
          <p v-for="insight in insights" :key="insight">{{ insight }}</p>
        </div>
      </article>

      <article class="panel analysis-panel">
        <div class="section-header">
          <div>
            <h2>案例领域分布</h2>
            <p>按案例类型字段统计当前资料库中的一人公司应用方向。</p>
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
    </section>

    <section class="panel">
      <div class="section-header">
        <div>
          <h2>最近更新</h2>
          <p>来自政策、案例和来源记录的最新数据。</p>
        </div>
      </div>

      <div v-if="!recentUpdates.length" class="muted">暂无最近更新。</div>
      <div v-else class="timeline-list">
        <div v-for="item in recentUpdates" :key="`${item.itemType}-${item.itemId}`" class="list-row">
          <div>
            <span class="caption">{{ item.itemType }}</span>
            <strong>{{ item.title }}</strong>
          </div>
          <span class="muted">{{ item.updatedDate || '-' }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDashboardSummary } from '@/api/dashboard'
import { getCases } from '@/api/case'
import { getPolicies } from '@/api/policy'
import { getSources } from '@/api/source'

const loading = ref(false)
const error = ref('')
const summary = ref({})
const policies = ref([])
const cases = ref([])
const sources = ref([])

const typeLabels = {
  comprehensive: '综合政策',
  computing_support: '算力支持',
  funding_subsidy: '资金补贴',
  scenario_demand: '场景需求',
  talent_service: '人才服务',
  investment: '投资融资',
  other: '其他',
}

const chartColors = ['#252724', '#8a640f', '#777b74', '#a5a9a1', '#d1d3cd', '#4d514b', '#ededeb']

const recentUpdates = computed(() => summary.value.recentUpdates || [])
const policyCount = computed(() => Number(summary.value.policyCount ?? policies.value.length))
const caseCount = computed(() => Number(summary.value.caseCount ?? cases.value.length))
const sourceCount = computed(() => Number(summary.value.sourceCount ?? sources.value.length))
const totalRecordCount = computed(() => policyCount.value + caseCount.value + sourceCount.value)
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
    makeTraceItem('来源关联', [...policies.value, ...cases.value].filter((item) => item.sourceId).length, totalItems, '#252724'),
    makeTraceItem('来源链接', sources.value.filter((item) => item.url).length, totalSources, '#8a640f'),
    makeTraceItem('本地文件', sources.value.filter((item) => item.localFile).length, totalSources, '#777b74'),
    makeTraceItem('访问日期', sources.value.filter((item) => item.accessedAt).length, totalSources, '#4d514b'),
    makeTraceItem('辅证链接', policies.value.filter((item) => item.evidenceUrl).length, totalPolicies, '#a5a9a1'),
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

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const [summaryData, policyData, caseData, sourceData] = await Promise.all([
      getDashboardSummary(),
      getPolicies(),
      getCases(),
      getSources(),
    ])
    summary.value = summaryData
    policies.value = policyData
    cases.value = caseData
    sources.value = sourceData
  } catch (err) {
    error.value = err.message || '统计数据加载失败'
  } finally {
    loading.value = false
  }
})
</script>
