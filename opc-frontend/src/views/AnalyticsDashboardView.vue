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
import { getAnalyticsIndustries, getAnalyticsOverview } from '@/api/analytics'
import { getUserProfile } from '@/api/auth'
import { createAnalyticsResearchDraft, saveAnalyticsResearchDraft } from '@/composables/useAnalyticsResearchHandoff'

const router = useRouter()
const route = useRoute()
const loading = ref(true)
const error = ref('')
const overview = ref({ cards: [] })
const industries = ref({ buckets: [], caveats: [] })
const industryFilter = ref('')
const handoffMessage = ref('')
const userId = getUserProfile()?.userId || 'anonymous'

const availableCards = computed(() => (overview.value.cards || []).filter((card) => (
  card?.readiness === 'green' && card.value !== null && card.value !== undefined && Number.isFinite(Number(card.value))
)))
const unavailableCards = computed(() => (overview.value.cards || []).filter((card) => !availableCards.value.includes(card)))
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
    const [overviewResult, industryResult] = await Promise.all([
      getAnalyticsOverview(),
      getAnalyticsIndustries(linkedIndustryTagId ? [linkedIndustryTagId] : []),
    ])
    overview.value = overviewResult || { cards: [] }
    industries.value = industryResult || { buckets: [], caveats: [] }
    industryFilter.value = linkedIndustryTagId ? `industry:${linkedIndustryTagId}` : ''
  } catch (requestError) {
    error.value = requestError.message || '请稍后重试。'
  } finally {
    loading.value = false
  }
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
</style>
