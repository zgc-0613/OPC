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
      </div>
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
                <span class="caption">7 DAY TREND</span>
                <h3>最近七天访问趋势</h3>
              </div>
              <strong>{{ formatNumber(trendTotalPv) }}</strong>
            </div>
            <div v-if="trend.length" class="admin-trend-chart">
              <svg viewBox="0 0 640 220" role="img" aria-label="最近七天访问趋势">
                <defs>
                  <linearGradient id="adminTrendFill" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="0%" stop-color="#315cdb" stop-opacity="0.18" />
                    <stop offset="100%" stop-color="#315cdb" stop-opacity="0" />
                  </linearGradient>
                </defs>
                <path class="admin-trend-area" :d="trendAreaPath" />
                <polyline class="admin-trend-line" :points="trendPoints" fill="none" />
                <circle
                  v-for="point in trendPointList"
                  :key="point.date"
                  class="admin-trend-dot"
                  :cx="point.x"
                  :cy="point.y"
                  r="4"
                />
              </svg>
              <div class="admin-trend-labels">
                <span v-for="item in trend" :key="item.date">{{ formatDateLabel(item.date) }}</span>
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
              <div>
                <span>政策访问</span>
                <strong>{{ formatNumber(summary.policyPv) }}</strong>
                <i><b :style="{ width: `${policyShare}%` }"></b></i>
              </div>
              <div>
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

const loading = ref(false)
const error = ref('')
const summary = ref({})
const policyRankings = ref([])
const caseRankings = ref([])
const trend = ref([])
const hotKeywords = ref([])

const trendTotalPv = computed(() => trend.value.reduce((total, item) => total + Number(item.pv || 0), 0))
const contentTotalPv = computed(() => Number(summary.value.policyPv || 0) + Number(summary.value.casePv || 0))
const policyShare = computed(() => getShare(summary.value.policyPv, contentTotalPv.value))
const caseShare = computed(() => getShare(summary.value.casePv, contentTotalPv.value))

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
    x: startX + step * index,
    y: startY + height - (Number(item.pv || 0) / maxPv) * height,
  }))
})

const trendPoints = computed(() => trendPointList.value.map((point) => `${point.x},${point.y}`).join(' '))
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
  try {
    const [summaryData, policyData, caseData, trendData, keywordData] = await Promise.all([
      getVisitSummary(),
      getVisitRankings({ targetType: 'policy', limit: 5 }),
      getVisitRankings({ targetType: 'case', limit: 5 }),
      getVisitTrend({ days: 7 }),
      getHotSearchKeywords({ limit: 10 }),
    ])

    summary.value = summaryData || {}
    policyRankings.value = policyData || []
    caseRankings.value = caseData || []
    trend.value = trendData || []
    hotKeywords.value = keywordData || []
  } catch (err) {
    error.value = err.message || '访问统计加载失败'
  } finally {
    loading.value = false
  }
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
