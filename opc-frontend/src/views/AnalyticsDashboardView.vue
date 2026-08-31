<template>
  <section class="analytics-dashboard" aria-labelledby="analytics-dashboard-title">
    <div v-if="loading" class="analytics-state" role="status" aria-live="polite">
      正在读取已核验数据看板。
    </div>

    <div v-else-if="error" class="analytics-state analytics-state--error" role="alert">
      <div>
        <strong>数据看板暂时无法读取</strong>
        <p>{{ error }}</p>
      </div>
      <button type="button" class="analytics-command" @click="loadOverview">
        <RefreshCw :size="17" aria-hidden="true" />重新读取
      </button>
    </div>

    <template v-else>
      <header class="analytics-heading">
        <div>
          <p class="analytics-eyebrow">已发布且已核验的数据范围</p>
          <h2 id="analytics-dashboard-title">研究数据看板</h2>
        </div>
        <dl class="analytics-provenance" aria-label="数据版本信息">
          <div><dt>数据版本</dt><dd>{{ overview.dataVersion || '-' }}</dd></div>
          <div><dt>可用时间</dt><dd>{{ formatDate(overview.generatedAt) }}</dd></div>
        </dl>
      </header>

      <p v-if="handoffMessage" class="analytics-handoff-status" role="status">{{ handoffMessage }}</p>

      <div v-if="availableCards.length" class="analytics-grid" aria-label="可研究的已核验指标">
        <article v-for="card in availableCards" :key="card.metricId" class="analytics-metric">
          <div class="analytics-metric-copy">
            <p>{{ card.label }}</p>
            <strong>{{ formatValue(card.value) }}<small>{{ card.unit || '' }}</small></strong>
            <span>当前已核验样本</span>
          </div>
          <div class="analytics-metric-action">
            <span class="analytics-status"><CheckCircle2 :size="15" aria-hidden="true" />已核验</span>
            <button
              type="button"
              class="analytics-command"
              :data-testid="`research-from-${card.metricId}`"
              :aria-label="`将${card.label}带入研究`"
              @click="handoffToResearch(card)"
            >
              <ArrowUpRight :size="17" aria-hidden="true" />带入研究
            </button>
          </div>
        </article>
      </div>

      <p v-else-if="!unavailableCards.length && !industryBuckets.length" class="analytics-empty" role="status">
        当前没有可用于研究的已核验统计。
      </p>

      <section v-if="materialCounts.length" class="analytics-material-distribution" data-testid="policy-material-distribution" aria-labelledby="policy-material-title">
        <header class="analytics-section-heading">
          <div>
            <p class="analytics-eyebrow">政策资料性质</p>
            <h3 id="policy-material-title">政策资料构成</h3>
            <p>按人工核验的资料性质展示核心数量；其他资料不纳入 80 条正式政策的七类统计。</p>
          </div>
          <strong class="analytics-resource-total">{{ formatValue(materialTotal) }} 条</strong>
        </header>
        <div class="analytics-material-grid">
          <article v-for="item in materialCounts" :key="item.code" class="analytics-material-item">
            <span>{{ item.label }}</span>
            <strong>{{ formatValue(item.value) }}<small>条</small></strong>
          </article>
        </div>
      </section>

      <section v-if="industryMetric" class="analytics-industry" aria-labelledby="analytics-industry-title">
        <header class="analytics-section-heading">
          <div>
            <p class="analytics-eyebrow">正式行业标签 · 多标签口径</p>
            <h3 id="analytics-industry-title">{{ industryMetric.name || '行业案例数量' }}</h3>
            <p>{{ industryMetric.definition }}</p>
          </div>
          <dl class="analytics-sample" aria-label="行业统计样本信息">
            <div><dt>样本</dt><dd>{{ industries.sampleSize ?? 0 }}</dd></div>
            <div><dt>缺失</dt><dd>{{ industries.missingCount ?? 0 }}</dd></div>
            <div><dt>合格总数</dt><dd>{{ industries.totalEligible ?? 0 }}</dd></div>
          </dl>
        </header>

        <label v-if="industryBuckets.length" class="analytics-filter">
          <span><ListFilter :size="16" aria-hidden="true" />行业筛选</span>
          <select v-model="industryFilter">
            <option value="">全部已核验行业</option>
            <option v-for="bucket in industryBuckets" :key="bucket.bucketId" :value="bucket.bucketId">
              {{ bucket.label }}
            </option>
          </select>
        </label>

        <ul v-if="industries.caveats?.length" class="analytics-caveats" aria-label="行业统计限制">
          <li v-for="caveat in industries.caveats" :key="caveat.code">{{ caveat.message }}</li>
        </ul>

        <p v-if="industries.status === 'unavailable'" class="analytics-empty" role="status">
          当前行业统计尚未达到正式使用条件。
        </p>
        <p v-else-if="!visibleIndustryBuckets.length" class="analytics-empty" role="status">
          当前筛选没有合格行业案例。
        </p>
        <template v-else>
          <div
            class="analytics-bars"
            role="img"
            data-testid="industry-chart"
            :aria-label="industryChartLabel"
          >
            <div v-for="bucket in visibleIndustryBuckets" :key="bucket.bucketId" class="analytics-bar-row">
              <span class="analytics-bar-label">{{ bucket.label }}</span>
              <span class="analytics-bar-track" aria-hidden="true">
                <span class="analytics-bar-fill" :style="{ '--bar-size': `${barSize(bucket.value)}%` }" />
              </span>
              <strong>{{ formatValue(bucket.value) }} 条</strong>
            </div>
          </div>

          <div class="analytics-table-wrap">
            <table data-testid="industry-table">
              <caption>行业案例数量文本表</caption>
              <thead><tr><th scope="col">行业</th><th scope="col">案例</th><th scope="col">占比</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
              <tbody>
                <tr
                  v-for="bucket in visibleIndustryBuckets"
                  :key="bucket.bucketId"
                  :data-testid="`industry-row-${bucket.bucketId}`"
                >
                  <th scope="row">{{ bucket.label }}</th>
                  <td>{{ formatValue(bucket.value) }}</td>
                  <td>{{ formatRatio(bucket.ratio) }}</td>
                  <td>{{ bucket.sampleSize < 3 ? '低样本' : '可比较' }}</td>
                  <td class="analytics-table-actions">
                    <button
                      type="button"
                      class="analytics-icon-command"
                      :data-testid="`cases-from-${bucket.bucketId}`"
                      :aria-label="`查看${bucket.label}案例`"
                      title="查看案例"
                      @click="openIndustryCases(bucket)"
                    >
                      <Search :size="17" aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      class="analytics-command"
                      :data-testid="`research-from-${bucket.bucketId}`"
                      :aria-label="`将${bucket.label}行业统计带入研究`"
                      @click="handoffIndustry(bucket)"
                    >
                      <ArrowUpRight :size="17" aria-hidden="true" />带入研究
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </section>

      <section class="analytics-regional-distribution" data-testid="regional-distribution" aria-labelledby="regional-distribution-title">
        <header class="analytics-section-heading">
          <div>
            <p class="analytics-eyebrow">全国空间分布 · 已发布且已核验</p>
            <h3 id="regional-distribution-title">全国 OPC 区域分布</h3>
            <p>按省份或七大区、港澳台及其他地区比较政策与案例数量，点击省份可进入对应明细。</p>
          </div>
          <div class="analytics-toggle-group" role="group" aria-label="区域统计范围">
            <button v-for="option in regionalLevelOptions" :key="option.value" type="button" :class="{ active: regionalLevel === option.value }" @click="regionalLevel = option.value">{{ option.label }}</button>
          </div>
        </header>
        <div class="analytics-toggle-group analytics-toggle-group--metric" role="group" aria-label="区域统计指标">
          <button v-for="option in regionalMetricOptions" :key="option.value" type="button" :class="{ active: regionalMetric === option.value }" @click="regionalMetric = option.value">{{ option.label }}</button>
        </div>
        <div v-if="regionalRows.length" class="regional-column-chart" role="img" :aria-label="regionalChartLabel">
          <button v-for="row in regionalRows" :key="row.label" type="button" class="regional-column-group" @click="selectRegionalRow(row)">
            <span class="regional-column-values">
              <span v-if="regionalMetric !== 'case'" class="regional-column-value regional-column-value--policy">
                <strong>{{ formatValue(row.policy) }}</strong><small>{{ formatRatio(row.policyRatio) }}</small>
              </span>
              <span v-if="regionalMetric !== 'policy'" class="regional-column-value regional-column-value--case">
                <strong>{{ formatValue(row.case) }}</strong><small>{{ formatRatio(row.caseRatio) }}</small>
              </span>
            </span>
            <span class="regional-column-bars" aria-hidden="true">
              <span v-if="regionalMetric !== 'case'" class="regional-column-fill regional-column-fill--policy" :style="{ height: `${barHeight(row.policy, regionalRows, 'policy')}%` }" />
              <span v-if="regionalMetric !== 'policy'" class="regional-column-fill regional-column-fill--case" :style="{ height: `${barHeight(row.case, regionalRows, 'case')}%` }" />
            </span>
            <span class="regional-column-label">{{ row.label }}</span>
          </button>
        </div>
        <p v-else class="analytics-resource-empty">当前没有可用于区域比较的已核验数据。</p>
        <div class="regional-legend" aria-label="图例">
          <span><i class="regional-legend-dot regional-legend-dot--policy" />政策</span>
          <span><i class="regional-legend-dot regional-legend-dot--case" />案例</span>
        </div>
        <section v-if="regionalLevel === 'macro' && regionalProvinceRows.length" class="regional-province-breakdown" data-testid="regional-province-breakdown" aria-labelledby="regional-province-title">
          <div class="regional-breakdown-heading">
            <div>
              <p class="analytics-eyebrow">大区内省份明细</p>
              <h4 id="regional-province-title">{{ selectedMacroRegion }}地区分布</h4>
            </div>
            <select v-model="selectedMacroRegion" aria-label="选择大区查看省份明细">
              <option v-for="option in macroRegionOptions" :key="option" :value="option">{{ option }}</option>
            </select>
          </div>
          <div class="regional-breakdown-table-wrap">
            <table class="regional-breakdown-table">
              <caption>{{ selectedMacroRegion }}各省政策与案例数量及区域内占比</caption>
              <thead><tr><th scope="col">省份</th><th scope="col">政策</th><th scope="col">案例</th></tr></thead>
              <tbody>
                <tr v-for="row in regionalProvinceRows" :key="row.label">
                  <th scope="row">{{ row.label }}</th>
                  <td><strong>{{ formatValue(row.policy) }}</strong><small>{{ formatRatio(row.policyRatio) }}</small></td>
                  <td><strong>{{ formatValue(row.case) }}</strong><small>{{ formatRatio(row.caseRatio) }}</small></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </section>

      <section class="analytics-resource-grid" aria-label="政策与案例省级统计">
        <article class="analytics-resource analytics-resource--wide" data-testid="policy-trend">
          <header class="analytics-section-heading">
            <div>
              <p class="analytics-eyebrow">Verified policy series</p>
              <h3>Policy publish trend</h3>
            </div>
            <strong v-if="policyTrend.available" class="analytics-resource-total">
              {{ formatValue(policyTrendTotal) }}
            </strong>
          </header>

          <div v-if="policyTrend.available && policyTrendSeries.length" class="analytics-resource-series">
            <div v-for="point in policyTrendSeries" :key="point.bucketId" class="analytics-series-row">
              <span>{{ formatPolicyPeriod(point) }}</span>
              <span class="analytics-series-track" aria-hidden="true">
                <span
                  class="analytics-series-fill"
                  :style="{ width: `${Math.max(2, Math.round((Number(point.value || 0) / Math.max(1, ...policyTrendSeries.map((item) => Number(item.value || 0)))) * 100))}%` }"
                />
              </span>
              <strong>{{ formatValue(point.value) }}</strong>
            </div>
          </div>
          <p v-else class="analytics-resource-empty" role="status">
            {{ resourceMessages(policyTrend)[0] }}
          </p>
        </article>

        <article class="analytics-resource analytics-resource--ranking" data-testid="case-regions">
          <header class="analytics-section-heading">
            <div>
              <p class="analytics-eyebrow">已发布 · 来源已核验</p>
              <h3>各省案例数量排名</h3>
            </div>
          </header>

          <p v-if="caseRegions.available" class="analytics-ranking-scope">
            省级归属 {{ formatValue(caseRegions.coverage?.covered || 0) }} 条，未归属
            {{ formatValue(caseRegions.coverage?.missing || 0) }} 条
          </p>
          <ol v-if="caseRegions.available && caseRegionRows.length" class="analytics-ranking-list">
            <li v-for="(region, index) in caseRegionRows" :key="region.bucketId" class="analytics-ranking-row">
              <span class="analytics-rank" :aria-label="`第 ${index + 1} 名`">{{ index + 1 }}</span>
              <div class="analytics-ranking-main">
                <div class="analytics-ranking-label">
                  <strong>{{ region.label || region.bucketId }}</strong>
                  <span>{{ formatValue(region.value) }} 条</span>
                </div>
                <span class="analytics-series-track" aria-hidden="true">
                  <span class="analytics-series-fill" :style="{ width: rankingBarWidth(region, caseRegionRows) }" />
                </span>
              </div>
            </li>
          </ol>
          <p v-else class="analytics-resource-empty" role="status">
            {{ resourceMessages(caseRegions)[0] }}
          </p>
          <p v-for="message in resourceCaveatMessages(caseRegions)" :key="message" class="analytics-ranking-note">
            {{ message }}
          </p>
        </article>

        <article class="analytics-resource analytics-resource--ranking" data-testid="policy-regions">
          <header class="analytics-section-heading">
            <div>
              <p class="analytics-eyebrow">已发布 · 来源已核验</p>
              <h3>各省政策数量排名</h3>
            </div>
          </header>

          <p v-if="policyRegions.available" class="analytics-ranking-scope">
            省级归属 {{ formatValue(policyRegions.coverage?.covered || 0) }} 条，未归属
            {{ formatValue(policyRegions.coverage?.missing || 0) }} 条
          </p>
          <ol v-if="policyRegions.available && policyRegionRows.length" class="analytics-ranking-list">
            <li v-for="(region, index) in policyRegionRows" :key="region.bucketId" class="analytics-ranking-row">
              <span class="analytics-rank" :aria-label="`第 ${index + 1} 名`">{{ index + 1 }}</span>
              <div class="analytics-ranking-main">
                <div class="analytics-ranking-label">
                  <strong>{{ region.label || region.bucketId }}</strong>
                  <span>{{ formatValue(region.value) }} 条</span>
                </div>
                <span class="analytics-series-track" aria-hidden="true">
                  <span class="analytics-series-fill" :style="{ width: rankingBarWidth(region, policyRegionRows) }" />
                </span>
              </div>
              <button
                type="button"
                class="analytics-icon-command"
                :data-testid="`drilldown-${region.bucketId}`"
                :aria-label="`查看${region.label || region.bucketId}政策明细`"
                title="查看政策明细"
                @click="openPolicyRegion(region)"
              >
                <Search :size="17" aria-hidden="true" />
              </button>
            </li>
          </ol>
          <p v-else class="analytics-resource-empty" role="status">
            {{ resourceMessages(policyRegions)[0] }}
          </p>
          <p v-for="message in resourceCaveatMessages(policyRegions)" :key="message" class="analytics-ranking-note">
            {{ message }}
          </p>
        </article>

        <article class="analytics-resource analytics-resource--limited" data-testid="technology-analytics">
          <header class="analytics-section-heading">
            <div>
              <p class="analytics-eyebrow">Technology taxonomy</p>
              <h3>Technology coverage</h3>
            </div>
          </header>
          <ul v-if="technologies.available && technologyRows.length" class="analytics-resource-list analytics-resource-list--plain">
            <li v-for="row in technologyRows" :key="row.bucketId || row.id || row.label">
              <span>{{ row.label || row.bucketId }}</span><strong>{{ formatValue(row.value) }}</strong>
            </li>
          </ul>
          <p v-else class="analytics-resource-empty" role="status">
            {{ resourceMessages(technologies)[0] }}
          </p>
        </article>

        <article class="analytics-resource analytics-resource--limited" data-testid="revenue-analytics">
          <header class="analytics-section-heading">
            <div>
              <p class="analytics-eyebrow">Annual CNY revenue</p>
              <h3>Revenue distribution</h3>
            </div>
          </header>
          <ul v-if="revenue.available && revenueRows.length" class="analytics-resource-list analytics-resource-list--plain">
            <li v-for="row in revenueRows" :key="row.bucketId || row.id || row.label">
              <span>{{ row.label || row.bucketId }}</span><strong>{{ formatValue(row.value) }}</strong>
            </li>
          </ul>
          <p v-else class="analytics-resource-empty" role="status">
            {{ resourceMessages(revenue)[0] }}
          </p>
        </article>

        <article v-if="policyDrilldown" class="analytics-resource analytics-resource--wide" data-testid="policy-drilldown">
          <header class="analytics-section-heading">
            <div>
              <p class="analytics-eyebrow">Version-bound policy rows</p>
              <h3>Policy details</h3>
            </div>
            <span v-if="drilldownLoading" class="analytics-status">Loading</span>
          </header>
          <p v-if="drilldownError" class="analytics-resource-empty analytics-resource-empty--error" role="alert">
            {{ drilldownError }}
          </p>
          <p v-else-if="!policyDrilldownRows.length" class="analytics-resource-empty" role="status">
            {{ resourceMessages(policyDrilldown)[0] }}
          </p>
          <ul v-else class="analytics-resource-list analytics-resource-list--plain">
            <li v-for="row in policyDrilldownRows" :key="row.id">
              <a v-if="row.detailHref" :href="row.detailHref">{{ row.title || row.id }}</a>
              <span v-else>{{ row.title || row.id }}</span>
              <small>{{ row.evidenceStatus || '-' }}</small>
              <small>{{ row.publishDate || '-' }} · {{ row.regionName || '-' }}</small>
            </li>
          </ul>
        </article>
      </section>

      <section v-if="unavailableCards.length" class="analytics-limitations" aria-labelledby="analytics-limitations-title">
        <h3 id="analytics-limitations-title">暂不可用的统计维度</h3>
        <ul>
          <li v-for="card in unavailableCards" :key="card.metricId">
            <strong>{{ card.label }}</strong><span>{{ card.caveat || '当前数据未达到可研究条件。' }}</span>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowUpRight, CheckCircle2, ListFilter, RefreshCw, Search } from 'lucide-vue-next'
import {
  getAnalyticsDrilldown,
  getAnalyticsIndustries,
  getAnalyticsOverview,
  getAnalyticsRegions,
  getAnalyticsRevenue,
  getAnalyticsTechnologies,
  getAnalyticsTrends,
} from '@/api/analytics'
import { getUserProfile } from '@/api/auth'
import { createAnalyticsResearchDraft, saveAnalyticsResearchDraft } from '@/composables/useAnalyticsResearchHandoff'

const router = useRouter()
const route = useRoute()
const loading = ref(true)
const error = ref('')
const overview = ref({ cards: [] })
const industries = ref({ buckets: [], caveats: [] })
const technologies = ref({ available: false, rows: [], series: [], caveats: [] })
const revenue = ref({ available: false, rows: [], series: [], caveats: [] })
const caseRegions = ref({ available: false, rows: [], caveats: [] })
const policyRegions = ref({ available: false, rows: [], caveats: [] })
const policyTrend = ref({ available: false, rows: [], series: [], caveats: [] })
const policyDrilldown = ref(null)
const drilldownLoading = ref(false)
const drilldownError = ref('')
const industryFilter = ref('')
const regionalLevel = ref('macro')
const regionalMetric = ref('combined')
const selectedMacroRegion = ref('华东')
const handoffMessage = ref('')
const userId = getUserProfile()?.userId || 'anonymous'

const availableCards = computed(() => (overview.value.cards || []).filter((card) => (
  card?.readiness === 'Green' && card.value !== null && card.value !== undefined && Number.isFinite(Number(card.value))
)))
const unavailableCards = computed(() => (overview.value.cards || []).filter((card) => !availableCards.value.includes(card)))
const materialCounts = computed(() => Array.isArray(overview.value?.materialCounts) ? overview.value.materialCounts : [])
const materialTotal = computed(() => materialCounts.value.reduce((sum, item) => sum + Number(item?.value || 0), 0))
const industryMetric = computed(() => industries.value?.metric || null)
const industryBuckets = computed(() => Array.isArray(industries.value?.buckets) ? industries.value.buckets : [])
const visibleIndustryBuckets = computed(() => industryFilter.value
  ? industryBuckets.value.filter((bucket) => bucket.bucketId === industryFilter.value)
  : industryBuckets.value)
const industryChartLabel = computed(() => visibleIndustryBuckets.value
  .map((bucket) => `${bucket.label} ${formatValue(bucket.value)} 条`)
  .join('；'))

async function loadOverview() {
  loading.value = true
  error.value = ''
  try {
    const linkedIndustryTagId = industryTagIdFromRoute(route.query)
    const [overviewResult, industryResult, technologyResult, revenueResult, caseRegionResult, policyRegionResult, trendResult] = await Promise.all([
      getAnalyticsOverview(),
      getAnalyticsIndustries(linkedIndustryTagId ? [linkedIndustryTagId] : []),
      getAnalyticsTechnologies(),
      getAnalyticsRevenue({ currency: 'CNY', revenuePeriod: 'annual', revenueType: 'revenue' }),
      getAnalyticsRegions({ metricId: 'region.case_count', regionRole: 'operation' }),
      getAnalyticsRegions({ metricId: 'region.policy_count', regionRole: 'policy_applicability' }),
      getAnalyticsTrends({ metricId: 'trend.policy_publish_time' }),
    ])
    overview.value = overviewResult || { cards: [] }
    industries.value = industryResult || { buckets: [], caveats: [] }
    technologies.value = technologyResult || { available: false, rows: [], series: [], caveats: [] }
    revenue.value = revenueResult || { available: false, rows: [], series: [], caveats: [] }
    caseRegions.value = caseRegionResult || { available: false, rows: [], caveats: [] }
    policyRegions.value = policyRegionResult || { available: false, rows: [], caveats: [] }
    policyTrend.value = trendResult || { available: false, rows: [], series: [], caveats: [] }
    policyDrilldown.value = null
    drilldownError.value = ''
    industryFilter.value = linkedIndustryTagId ? `industry:${linkedIndustryTagId}` : ''
  } catch (requestError) {
    error.value = requestError.message || '请稍后重试。'
  } finally {
    loading.value = false
  }
}

const policyTrendSeries = computed(() => listResourceItems(policyTrend.value, 'series'))
const caseRegionRows = computed(() => listResourceItems(caseRegions.value, 'rows'))
const policyRegionRows = computed(() => listResourceItems(policyRegions.value, 'rows'))
const technologyRows = computed(() => listResourceItems(technologies.value, 'rows'))
const revenueRows = computed(() => listResourceItems(revenue.value, 'rows'))
const policyDrilldownRows = computed(() => listResourceItems(policyDrilldown.value, 'rows'))
const regionalLevelOptions = [
  { label: '七大区 + 港澳台', value: 'macro' },
  { label: '省份', value: 'province' },
]
const regionalMetricOptions = [
  { label: '政策 + 案例', value: 'combined' },
  { label: '政策', value: 'policy' },
  { label: '案例', value: 'case' },
]
const macroRegionMap = {
  华北: ['北京市', '天津市', '河北省', '山西省', '内蒙古自治区'],
  东北: ['辽宁省', '吉林省', '黑龙江省'],
  华东: ['上海市', '江苏省', '浙江省', '安徽省', '福建省', '江西省', '山东省'],
  华中: ['河南省', '湖北省', '湖南省'],
  华南: ['广东省', '广西壮族自治区', '海南省'],
  西南: ['重庆市', '四川省', '贵州省', '云南省', '西藏自治区'],
  西北: ['陕西省', '甘肃省', '青海省', '宁夏回族自治区', '新疆维吾尔自治区'],
  '港澳台及其他': ['香港特别行政区', '澳门特别行政区', '台湾特别行政区', '台湾省', '台湾地区'],
}
const regionalRows = computed(() => {
  const policy = new Map(policyRegionRows.value.map((row) => [row.label, Number(row.value || 0)]))
  const cases = new Map(caseRegionRows.value.map((row) => [row.label, Number(row.value || 0)]))
  const labels = regionalLevel.value === 'macro' ? Object.keys(macroRegionMap) : [...new Set([...policy.keys(), ...cases.keys()])]
  const policyTotal = [...policy.values()].reduce((sum, value) => sum + value, 0)
  const caseTotal = [...cases.values()].reduce((sum, value) => sum + value, 0)
  return labels.map((label) => {
    const provinces = regionalLevel.value === 'macro' ? macroRegionMap[label] : [label]
    const policyValue = provinces.reduce((sum, province) => sum + (policy.get(province) || 0), 0)
    const caseValue = provinces.reduce((sum, province) => sum + (cases.get(province) || 0), 0)
    const sourceRow = regionalLevel.value === 'province'
      ? (policyRegionRows.value.find((row) => row.label === label) || caseRegionRows.value.find((row) => row.label === label))
      : null
    return {
      label,
      regionId: sourceRow?.regionId,
      policy: policyValue,
      case: caseValue,
      policyRatio: policyTotal ? policyValue / policyTotal : 0,
      caseRatio: caseTotal ? caseValue / caseTotal : 0,
      value: regionalMetric.value === 'policy' ? policyValue : regionalMetric.value === 'case' ? caseValue : policyValue + caseValue,
    }
  }).filter((row) => row.value > 0).sort((a, b) => b.value - a.value)
})
const macroRegionOptions = Object.keys(macroRegionMap)
const regionalProvinceRows = computed(() => {
  const provinces = macroRegionMap[selectedMacroRegion.value] || []
  const policy = new Map(policyRegionRows.value.map((row) => [row.label, Number(row.value || 0)]))
  const cases = new Map(caseRegionRows.value.map((row) => [row.label, Number(row.value || 0)]))
  const policyTotal = provinces.reduce((sum, province) => sum + (policy.get(province) || 0), 0)
  const caseTotal = provinces.reduce((sum, province) => sum + (cases.get(province) || 0), 0)
  return provinces.map((label) => {
    const policyValue = policy.get(label) || 0
    const caseValue = cases.get(label) || 0
    return { label, policy: policyValue, case: caseValue, policyRatio: policyTotal ? policyValue / policyTotal : 0, caseRatio: caseTotal ? caseValue / caseTotal : 0 }
  }).filter((row) => row.policy || row.case).sort((a, b) => (b.policy + b.case) - (a.policy + a.case))
})
const regionalChartLabel = computed(() => regionalRows.value.map((row) => `${row.label} 政策 ${row.policy} 条，占政策 ${formatRatio(row.policyRatio)}；案例 ${row.case} 条，占案例 ${formatRatio(row.caseRatio)}`).join('；'))
const policyTrendTotal = computed(() => policyTrendSeries.value.reduce((total, point) => total + Number(point?.value || 0), 0))

function listResourceItems(resource, key) {
  return Array.isArray(resource?.[key]) ? resource[key] : []
}

function resourceCaveats(resource) {
  return Array.isArray(resource?.caveats) ? resource.caveats : []
}

function resourceCaveatMessages(resource) {
  return resourceCaveats(resource)
    .map((caveat) => caveat?.message)
    .filter(Boolean)
}

function resourceUnavailableReason(resource) {
  return resource?.unavailableReason || resourceCaveats(resource)[0]?.code || 'ANALYTICS_METRIC_NOT_READY'
}

function resourceMessages(resource) {
  const messages = resourceCaveatMessages(resource)
  return messages.length ? messages : [resourceUnavailableReason(resource)]
}

function rankingBarWidth(region, rows) {
  const maximum = Math.max(1, ...rows.map((row) => Number(row?.value || 0)))
  return `${Math.max(2, Math.round((Number(region?.value || 0) / maximum) * 100))}%`
}

function barHeight(value, rows, key) {
  const maximum = Math.max(1, ...rows.map((row) => Number(row?.[key] || 0)))
  return Math.max(2, Math.round((Number(value || 0) / maximum) * 100))
}

function openRegionalDetail(row) {
  if (regionalLevel.value !== 'province') return
  const target = row?.label
  if (!target) return
  if (!row.regionId) return
  router.push({ name: regionalMetric.value === 'case' ? 'case-list' : 'policy-list', query: { regionId: String(row.regionId) } })
}

function selectRegionalRow(row) {
  if (regionalLevel.value === 'macro') {
    selectedMacroRegion.value = row.label
    return
  }
  openRegionalDetail(row)
}

async function openPolicyRegion(region) {
  if (!region?.bucketId || drilldownLoading.value) return
  drilldownLoading.value = true
  drilldownError.value = ''
  policyDrilldown.value = null
  try {
    const dataVersion = policyRegions.value?.dataVersion || overview.value?.dataVersion
    policyDrilldown.value = await getAnalyticsDrilldown({
      metricId: 'region.policy_count',
      dataVersion,
      entityType: 'policy',
      bucketId: region.bucketId,
    })
  } catch (requestError) {
    drilldownError.value = requestError?.message || 'Unable to load policy details'
  } finally {
    drilldownLoading.value = false
  }
}

function formatPolicyPeriod(value) {
  if (!value) return '-'
  if (typeof value === 'string') return value.slice(0, 10)
  return value.periodStart || value.bucketId || '-'
}

function handoffToResearch(card) {
  const draft = createAnalyticsResearchDraft(card, overview.value.dataVersion)
  if (!draft) {
    handoffMessage.value = '当前指标无法作为研究条件带入。'
    return
  }
  try {
    if (!saveAnalyticsResearchDraft(sessionStorage, userId, draft)) {
      handoffMessage.value = '当前指标无法作为研究条件带入。'
      return
    }
    handoffMessage.value = '研究条件已带入 Assistant，尚未发送。'
    Promise.resolve(router.push({ name: 'assistant', query: { handoff: 'analytics' } })).catch(() => {
      handoffMessage.value = '研究条件已保存，但无法打开 Assistant。'
    })
  } catch {
    handoffMessage.value = '浏览器未能保存研究条件，请允许本地存储后重试。'
  }
}

function handoffIndustry(bucket) {
  const card = {
    metricId: 'industry.case_count',
    metricLabel: `行业案例数量：${bucket.label}`,
    label: `行业案例数量：${bucket.label}`,
    bucketId: bucket.bucketId,
    industryTagId: bucket.industryTagId,
  }
  const draft = createAnalyticsResearchDraft(card, industries.value.dataVersion)
  if (!draft) {
    handoffMessage.value = '当前行业数据无法作为研究条件带入。'
    return
  }
  try {
    if (!saveAnalyticsResearchDraft(sessionStorage, userId, draft)) {
      handoffMessage.value = '当前行业数据无法作为研究条件带入。'
      return
    }
    handoffMessage.value = '行业研究条件已带入 Assistant，尚未发送。'
    Promise.resolve(router.push({ name: 'assistant', query: { handoff: 'analytics' } })).catch(() => {
      handoffMessage.value = '研究条件已保存，但无法打开 Assistant。'
    })
  } catch {
    handoffMessage.value = '浏览器未能保存研究条件，请允许本地存储后重试。'
  }
}

function openIndustryCases(bucket) {
  router.push({ name: 'case-list', query: { industryTagId: String(bucket.industryTagId) } })
}

function industryTagIdFromRoute(query) {
  const metricId = Array.isArray(query?.metricId) ? query.metricId[0] : query?.metricId
  const rawTagId = Array.isArray(query?.industryTagId) ? query.industryTagId[0] : query?.industryTagId
  if (metricId !== 'industry.case_count' || typeof rawTagId !== 'string' || !/^[1-9]\d*$/.test(rawTagId)) return null
  const tagId = Number(rawTagId)
  return Number.isSafeInteger(tagId) ? tagId : null
}

function barSize(value) {
  const maximum = Math.max(1, ...visibleIndustryBuckets.value.map((bucket) => Number(bucket.value) || 0))
  return Math.max(2, Math.round(((Number(value) || 0) / maximum) * 100))
}

function formatRatio(value) {
  const ratio = Number(value)
  return Number.isFinite(ratio) ? `${(ratio * 100).toFixed(1)}%` : '-'
}

function formatValue(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadOverview)
</script>

<style scoped>
.analytics-dashboard{min-width:0;max-width:1120px;margin:0 auto;padding:8px 0 40px;color:#20251f}.analytics-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;padding:12px 0 22px;border-bottom:1px solid #cbd0c9}.analytics-eyebrow{margin:0 0 6px;color:#596159;font-size:.72rem;font-weight:700}.analytics-heading h2{margin:0;font-family:"ZCOOL XiaoWei",STKaiti,KaiTi,serif;font-size:1.75rem;line-height:1.1}.analytics-provenance{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px 18px;margin:0;color:#5d645d;font-size:.72rem}.analytics-provenance div{display:grid;gap:3px}.analytics-provenance dt{font-weight:700}.analytics-provenance dd{margin:0;overflow-wrap:anywhere;font-family:"Bookman Old Style","URW Bookman",Georgia,serif;color:#262b26}.analytics-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:20px}.analytics-metric{display:flex;flex-direction:column;justify-content:space-between;gap:20px;min-width:0;padding:20px;border:1px solid #cbd0c9;border-radius:8px;background:#eceeeb}.analytics-metric-copy p{margin:0;color:#434a43;font-size:.8rem;font-weight:700}.analytics-metric-copy strong{display:block;margin-top:12px;font-family:"Bookman Old Style","URW Bookman",Georgia,serif;font-size:2.1rem;line-height:1;color:#181a18}.analytics-metric-copy small{margin-left:4px;font-family:"Noto Serif SC","Songti SC",STSong,SimSun,serif;font-size:.82rem;font-weight:400}.analytics-metric-copy>span{display:block;margin-top:8px;color:#596159;font-size:.69rem}.analytics-metric-action{display:flex;align-items:center;justify-content:space-between;gap:10px}.analytics-status{display:inline-flex;align-items:center;gap:5px;color:#3f6149;font-size:.71rem;font-weight:700}.analytics-command{display:inline-flex;align-items:center;justify-content:center;gap:6px;min-height:44px;padding:0 13px;border:1px solid #303630;border-radius:999px;background:#fbfbf8;color:#20251f;font:inherit;font-size:.74rem;font-weight:700;white-space:nowrap}.analytics-command:is(:hover,:focus-visible){background:#e2e5df}.analytics-command:active{background:#d7dad4}.analytics-command:focus-visible{outline:2px solid #4f6f58;outline-offset:2px}.analytics-state{display:flex;align-items:center;justify-content:space-between;gap:16px;min-height:170px;padding:24px;border:1px solid #cbd0c9;border-radius:8px;background:#eceeeb;color:#515752}.analytics-state--error{color:#743d37}.analytics-state p{margin:4px 0 0}.analytics-empty{margin:20px 0 0;padding:18px;border:1px dashed #bfc5bd;border-radius:6px;background:#fbfbf8;color:#596159;font-size:.82rem}.analytics-handoff-status{margin:16px 0 0;padding:10px 12px;border:1px solid #b7c2b9;border-radius:6px;background:#eff3ed;color:#36513d;font-size:.78rem}.analytics-limitations{margin-top:28px;padding-top:18px;border-top:1px solid #cbd0c9}.analytics-limitations h3{margin:0;color:#2d332d;font-size:1rem}.analytics-limitations ul{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin:14px 0 0;padding:0;list-style:none}.analytics-limitations li{display:grid;gap:4px;min-width:0;padding:13px 14px;border:1px solid #d2d5cf;border-radius:6px;background:#fbfbf8;color:#5e655e;font-size:.76rem}.analytics-limitations strong{color:#2c322c}.analytics-limitations span{overflow-wrap:anywhere}@media(max-width:900px){.analytics-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.analytics-dashboard{padding-bottom:28px}.analytics-heading{align-items:flex-start;flex-direction:column;gap:14px}.analytics-provenance{justify-content:flex-start}.analytics-grid,.analytics-limitations ul{grid-template-columns:1fr}.analytics-metric{padding:17px}.analytics-metric-copy strong{font-size:1.85rem}.analytics-state{align-items:flex-start;flex-direction:column;min-height:0}.analytics-command{min-height:44px}}@media(prefers-reduced-motion:reduce){.analytics-command{transition:none}}
.analytics-industry{min-width:0;margin-top:34px;padding-top:24px;border-top:1px solid #cbd0c9}.analytics-section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:24px}.analytics-section-heading h3{margin:0;font-size:1.08rem}.analytics-section-heading>div>p:last-child{max-width:680px;margin:7px 0 0;color:#606760;font-size:.76rem;line-height:1.65}.analytics-sample{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:10px 18px;margin:0}.analytics-sample div{display:grid;gap:3px}.analytics-sample dt{color:#687068;font-size:.68rem;font-weight:700}.analytics-sample dd{margin:0;font-family:"Bookman Old Style","URW Bookman",Georgia,serif;font-size:.9rem}.analytics-filter{display:flex;align-items:center;gap:12px;width:fit-content;max-width:100%;margin-top:18px}.analytics-filter>span{display:inline-flex;align-items:center;gap:6px;color:#3f463f;font-size:.74rem;font-weight:700;white-space:nowrap}.analytics-filter select{min-width:220px;max-width:100%;min-height:44px;padding:0 36px 0 12px;border:1px solid #8e958d;border-radius:6px;background:#fbfbf8;color:#20251f;font:inherit;font-size:.78rem}.analytics-filter select:focus-visible{outline:2px solid #4f6f58;outline-offset:2px}.analytics-caveats{display:grid;gap:4px;margin:14px 0 0;padding:0;list-style:none;color:#665b39;font-size:.72rem}.analytics-caveats li{overflow-wrap:anywhere}.analytics-bars{display:grid;gap:10px;margin-top:20px;padding:18px 0;border-block:1px solid #d5d9d2}.analytics-bar-row{display:grid;grid-template-columns:minmax(120px,200px) minmax(120px,1fr) 72px;align-items:center;gap:12px;min-width:0}.analytics-bar-label{overflow-wrap:anywhere;font-size:.76rem;font-weight:700}.analytics-bar-track{height:14px;overflow:hidden;border:1px solid #bec4bc;border-radius:3px;background:#eceeea}.analytics-bar-fill{display:block;width:var(--bar-size);height:100%;background:#587060}.analytics-bar-row>strong{text-align:right;font-family:"Bookman Old Style","URW Bookman",Georgia,serif;font-size:.76rem}.analytics-table-wrap{max-width:100%;margin-top:16px;overflow-x:auto}.analytics-table-wrap table{width:100%;min-width:660px;border-collapse:collapse;font-size:.76rem}.analytics-table-wrap caption{padding:0 0 8px;text-align:left;color:#626962;font-size:.68rem}.analytics-table-wrap :is(th,td){padding:10px 9px;border-bottom:1px solid #d4d8d1;text-align:left;overflow-wrap:anywhere}.analytics-table-wrap thead th{color:#5d645d;font-size:.68rem}.analytics-table-actions{display:flex;align-items:center;justify-content:flex-end;gap:7px}.analytics-icon-command{display:inline-flex;align-items:center;justify-content:center;width:44px;height:44px;padding:0;border:1px solid #303630;border-radius:50%;background:#fbfbf8;color:#20251f}.analytics-icon-command:is(:hover,:focus-visible){background:#e2e5df}.analytics-icon-command:focus-visible{outline:2px solid #4f6f58;outline-offset:2px}@media(max-width:640px){.analytics-section-heading{flex-direction:column;gap:14px}.analytics-sample{justify-content:flex-start}.analytics-filter{align-items:flex-start;flex-direction:column;width:100%;gap:7px}.analytics-filter select{width:100%}.analytics-bar-row{grid-template-columns:minmax(88px,1fr) minmax(80px,1.4fr) 62px;gap:8px}.analytics-table-wrap{padding-bottom:4px}}@media(prefers-reduced-motion:reduce){.analytics-icon-command{transition:none}}
.analytics-resource-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:34px;padding-top:24px;border-top:1px solid #cbd0c9}.analytics-resource{display:grid;gap:16px;min-width:0;padding:18px;border:1px solid #cbd0c9;border-radius:8px;background:#fbfbf8}.analytics-resource--wide{grid-column:1 / -1}.analytics-resource--limited{align-content:start}.analytics-resource-total{font-family:"Bookman Old Style","URW Bookman",Georgia,serif;font-size:1.1rem}.analytics-resource-series{display:grid;gap:10px}.analytics-series-row{display:grid;grid-template-columns:92px minmax(80px,1fr) 54px;align-items:center;gap:10px;min-width:0;font-size:.74rem}.analytics-series-row>span:first-child{overflow-wrap:anywhere}.analytics-series-row>strong{text-align:right;font-family:"Bookman Old Style","URW Bookman",Georgia,serif;font-size:.76rem}.analytics-series-track{height:12px;overflow:hidden;border:1px solid #c2c9c0;border-radius:3px;background:#eceeea}.analytics-series-fill{display:block;width:var(--bar-size,2%);height:100%;background:#587060}.analytics-resource-list{display:grid;gap:9px;margin:0;padding:0;list-style:none}.analytics-region-row{display:flex;align-items:center;justify-content:space-between;gap:12px;min-width:0;padding-bottom:9px;border-bottom:1px solid #e0e3dd}.analytics-region-row:last-child{padding-bottom:0;border-bottom:0}.analytics-region-row>div{display:grid;gap:3px;min-width:0}.analytics-region-row strong,.analytics-region-row span{overflow-wrap:anywhere}.analytics-region-row strong{font-size:.78rem}.analytics-region-row span{color:#646b64;font-size:.7rem}.analytics-resource-list--plain li{display:flex;align-items:flex-start;justify-content:space-between;gap:14px;min-width:0;padding-bottom:8px;border-bottom:1px solid #e0e3dd;font-size:.75rem}.analytics-resource-list--plain li:last-child{padding-bottom:0;border-bottom:0}.analytics-resource-list--plain li>span{overflow-wrap:anywhere}.analytics-resource-list--plain li>strong{font-family:"Bookman Old Style","URW Bookman",Georgia,serif}.analytics-resource-list--plain li>small{color:#646b64;text-align:right;white-space:nowrap}.analytics-resource-empty{margin:0;padding:12px;border:1px dashed #c6ccc4;border-radius:6px;color:#666e66;font-size:.74rem;overflow-wrap:anywhere}.analytics-resource-empty--error{border-color:#d9b7b2;color:#743d37}.analytics-resource .analytics-icon-command{flex:none}@media(max-width:640px){.analytics-resource-grid{grid-template-columns:1fr}.analytics-resource--wide{grid-column:auto}.analytics-series-row{grid-template-columns:78px minmax(70px,1fr) 48px}.analytics-resource{padding:16px}}
.analytics-resource--ranking{align-content:start}.analytics-ranking-scope{margin:-4px 0 2px;color:#646b64;font-size:.72rem}.analytics-ranking-list{display:grid;gap:0;max-height:520px;margin:0;padding:0;overflow:auto;list-style:none}.analytics-ranking-row{display:grid;grid-template-columns:28px minmax(0,1fr) auto;align-items:center;gap:10px;padding:10px 2px;border-bottom:1px solid #e0e3dd}.analytics-ranking-row:last-child{border-bottom:0}.analytics-rank{display:inline-flex;align-items:center;justify-content:center;width:24px;height:24px;border:1px solid #c4cac2;border-radius:50%;color:#4d574f;font-family:"Bookman Old Style","URW Bookman",Georgia,serif;font-size:.68rem}.analytics-ranking-row:nth-child(-n+3) .analytics-rank{border-color:#587060;background:#e5ebe5;color:#30463a}.analytics-ranking-main{display:grid;gap:7px;min-width:0}.analytics-ranking-label{display:flex;align-items:baseline;justify-content:space-between;gap:12px;font-size:.75rem}.analytics-ranking-label strong{overflow-wrap:anywhere}.analytics-ranking-label span{flex:none;color:#4e5650;font-family:"Bookman Old Style","URW Bookman",Georgia,serif}.analytics-ranking-note{margin:8px 0 0;color:#6a6250;font-size:.7rem;line-height:1.55}.analytics-ranking-row .analytics-series-track{height:8px}@media(max-width:640px){.analytics-ranking-list{max-height:none}.analytics-ranking-row{grid-template-columns:26px minmax(0,1fr) auto}}
.analytics-regional-distribution{margin-top:34px;padding-top:24px;border-top:1px solid #cbd0c9}.analytics-toggle-group{display:flex;flex-wrap:wrap;gap:0;margin-top:2px;border:1px solid #8e958d;border-radius:6px;overflow:hidden}.analytics-toggle-group button{min-height:38px;padding:0 13px;border:0;border-right:1px solid #cbd0c9;background:#fbfbf8;color:#596159;font:inherit;font-size:.72rem;font-weight:700}.analytics-toggle-group button:last-child{border-right:0}.analytics-toggle-group button.active{background:#181a18;color:#fbfbf8}.analytics-toggle-group button:focus-visible{position:relative;z-index:1;outline:2px solid #4f6f58;outline-offset:-3px}.analytics-toggle-group--metric{width:max-content;max-width:100%;margin-top:18px}.regional-bar-chart{display:grid;gap:8px;margin-top:20px;padding:16px 0;border-block:1px solid #d5d9d2}.regional-bar-row{display:grid;grid-template-columns:minmax(88px,150px) minmax(100px,1fr) 54px minmax(110px,160px);align-items:center;gap:10px;width:100%;padding:7px 0;border:0;background:transparent;color:#20251f;text-align:left;font:inherit;cursor:pointer}.regional-bar-row:hover{background:#f0f2ee}.regional-bar-row:focus-visible{outline:2px solid #4f6f58;outline-offset:2px}.regional-bar-label{font-size:.76rem;font-weight:700}.regional-bar-track{position:relative;display:block;height:18px;overflow:hidden;border:1px solid #bec4bc;border-radius:3px;background:#eceeea}.regional-bar-fill{position:absolute;left:0;display:block;height:100%;transition:width .45s cubic-bezier(.16,1,.3,1)}.regional-bar-fill--policy{top:0;background:#181a18}.regional-bar-fill--case{top:3px;height:12px;background:#718078}.regional-bar-row>strong{font-family:"Bookman Old Style","URW Bookman",Georgia,serif;font-size:.78rem;text-align:right}.regional-bar-row>small{color:#646b64;font-size:.68rem;text-align:right}.regional-legend{display:flex;gap:18px;margin-top:10px;color:#646b64;font-size:.7rem}.regional-legend span{display:inline-flex;align-items:center;gap:6px}.regional-legend-dot{display:inline-block;width:10px;height:10px;border-radius:2px}.regional-legend-dot--policy{background:#181a18}.regional-legend-dot--case{background:#718078}@media(max-width:640px){.analytics-toggle-group{width:100%}.analytics-toggle-group button{flex:1;padding:0 8px}.regional-bar-row{grid-template-columns:72px minmax(70px,1fr) 42px;gap:7px}.regional-bar-row>small{grid-column:2 / -1;text-align:left;font-size:.64rem}}
.regional-column-chart{display:flex;align-items:stretch;gap:10px;min-height:300px;margin-top:20px;padding:24px 8px 0;border-block:1px solid #d5d9d2;overflow-x:auto}.regional-column-group{position:relative;display:grid;grid-template-rows:48px 220px 30px;flex:1 0 78px;min-width:78px;padding:0 3px;border:0;background:transparent;color:#20251f;font:inherit;cursor:pointer}.regional-column-group:hover{background:#f0f2ee}.regional-column-group:focus-visible{z-index:1;outline:2px solid #4f6f58;outline-offset:2px}.regional-column-values{display:flex;justify-content:center;gap:4px;min-width:0;font-family:"Bookman Old Style","URW Bookman",Georgia,serif}.regional-column-value{display:grid;align-content:start;justify-items:center;min-width:28px}.regional-column-value strong{font-size:.75rem;line-height:1.1}.regional-column-value small{margin-top:3px;color:#697169;font-family:"Noto Serif SC","Songti SC",STSong,SimSun,serif;font-size:.62rem}.regional-column-bars{display:flex;align-items:end;justify-content:center;gap:5px;height:220px;padding:0 7px;border-bottom:1px solid #303630;background:repeating-linear-gradient(to top,transparent 0,transparent 43px,#e3e6e0 44px)}.regional-column-fill{display:block;width:22px;min-height:4px;transition:height .45s cubic-bezier(.16,1,.3,1)}.regional-column-fill--policy{background:#181a18}.regional-column-fill--case{background:#718078}.regional-column-label{display:block;overflow:hidden;padding-top:8px;white-space:nowrap;text-overflow:ellipsis;font-size:.7rem;font-weight:700;text-align:center}.regional-column-chart + .regional-legend{margin-top:10px}
.regional-province-breakdown{margin-top:22px;padding-top:18px;border-top:1px solid #d5d9d2}.regional-breakdown-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:16px}.regional-breakdown-heading h4{margin:0;font-size:.98rem}.regional-breakdown-heading select{min-height:40px;padding:0 30px 0 10px;border:1px solid #8e958d;border-radius:5px;background:#fbfbf8;color:#20251f;font:inherit;font-size:.72rem}.regional-breakdown-heading select:focus-visible{outline:2px solid #4f6f58;outline-offset:2px}.regional-breakdown-table-wrap{overflow-x:auto;margin-top:12px}.regional-breakdown-table{width:100%;border-collapse:collapse;min-width:430px;font-size:.74rem}.regional-breakdown-table caption{padding:0 0 8px;text-align:left;color:#646b64;font-size:.68rem}.regional-breakdown-table th,.regional-breakdown-table td{padding:9px 8px;border-bottom:1px solid #e0e3dd;text-align:left}.regional-breakdown-table thead th{color:#646b64;font-size:.68rem}.regional-breakdown-table tbody th{font-weight:700}.regional-breakdown-table td{font-family:"Bookman Old Style","URW Bookman",Georgia,serif}.regional-breakdown-table td small{display:inline-block;margin-left:7px;color:#697169;font-family:"Noto Serif SC","Songti SC",STSong,SimSun,serif;font-size:.64rem}.regional-breakdown-table tbody tr:hover{background:#f0f2ee}
.analytics-policy-themes{margin-top:34px;padding-top:24px;border-top:1px solid #cbd0c9}.policy-theme-chart{display:grid;gap:0;margin-top:20px;border-block:1px solid #d5d9d2}.policy-theme-row{display:grid;grid-template-columns:minmax(170px,1.2fr) minmax(230px,2fr) minmax(230px,2fr);align-items:center;gap:24px;padding:14px 0;border-bottom:1px solid #e0e3de}.policy-theme-row:last-child{border-bottom:0}.policy-theme-label{font-size:.78rem}.policy-theme-measure{display:grid;gap:7px}.policy-theme-measure>span{display:flex;justify-content:space-between;gap:12px;font-size:.68rem}.policy-theme-measure b{color:#303630}.policy-theme-measure em{color:#646b64;font-style:normal}.policy-theme-track{display:block;height:12px;overflow:hidden;border:1px solid #bec4bc;border-radius:2px;background:#eceeea}.policy-theme-fill{display:block;height:100%;transition:width .45s cubic-bezier(.16,1,.3,1)}.policy-theme-fill--primary{background:#181a18}.policy-theme-fill--involved{background:#718078}@media(max-width:780px){.policy-theme-row{grid-template-columns:1fr;gap:10px}.policy-theme-measure{grid-template-columns:minmax(76px,auto) 1fr;align-items:center}.policy-theme-measure>span{display:grid}.policy-theme-track{height:14px}}
.analytics-material-distribution{margin-top:34px;padding-top:24px;border-top:1px solid #cbd0c9}.analytics-material-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-top:18px}.analytics-material-item{display:flex;align-items:center;justify-content:space-between;gap:12px;min-width:0;padding:13px 14px;border:1px solid #d2d5cf;border-radius:6px;background:#fbfbf8}.analytics-material-item span{overflow-wrap:anywhere;color:#4f574f;font-size:.76rem}.analytics-material-item strong{flex:none;font-family:"Bookman Old Style","URW Bookman",Georgia,serif;font-size:1rem}.analytics-material-item small{margin-left:3px;font-family:"Noto Serif SC","Songti SC",STSong,SimSun,serif;font-size:.66rem;font-weight:400}@media(max-width:900px){.analytics-material-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.analytics-material-grid{grid-template-columns:1fr}.analytics-material-item{padding:12px}}
 </style>
