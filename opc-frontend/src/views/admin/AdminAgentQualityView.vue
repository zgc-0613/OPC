<template>
  <div class="admin-stack quality-page">
    <section class="quality-intro" aria-labelledby="agent-quality-title">
      <div>
        <span class="caption">AGENT OBSERVABILITY</span>
        <h2 id="agent-quality-title">Agent quality monitor</h2>
        <p>Aggregate outcomes, resource use, evidence gaps, and feedback signals for administrator review.</p>
      </div>
      <p class="quality-intro__scope">
        Aggregate-only view. User messages, prompts, response content, session identifiers, and credentials are excluded.
      </p>
    </section>

    <form class="quality-filters" aria-label="Quality report filters" @submit.prevent="loadQuality">
      <label>
        <span>From date</span>
        <input v-model="filters.dateFrom" name="date-from" type="date" :max="filters.dateTo || undefined" />
      </label>
      <label>
        <span>To date</span>
        <input v-model="filters.dateTo" name="date-to" type="date" :min="filters.dateFrom || undefined" />
      </label>
      <label>
        <span>Task type</span>
        <select v-model="filters.taskType" name="task-type">
          <option value="">All task types</option>
          <option v-for="option in taskTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </label>
      <label>
        <span>Model</span>
        <input v-model.trim="filters.model" name="model" type="search" placeholder="For example, deepseek-v4" />
      </label>
      <label>
        <span>Grouping</span>
        <select v-model="filters.granularity" name="granularity">
          <option value="day">Daily</option>
          <option value="week">Weekly</option>
          <option value="month">Monthly</option>
        </select>
      </label>
      <div class="quality-filters__actions">
        <button class="button" type="submit" :disabled="loading">{{ loading ? 'Updating...' : 'Apply filters' }}</button>
        <button class="button button-ghost" type="button" :disabled="loading" @click="resetFilters">Reset</button>
      </div>
    </form>

    <p v-if="error" class="quality-state quality-state--error" role="alert">
      {{ error }}
      <button type="button" @click="loadQuality">Try again</button>
    </p>
    <p v-else-if="loading && !quality" class="quality-state" role="status" aria-live="polite">Loading aggregate quality data...</p>

    <template v-else-if="quality && !sampleSize">
      <section class="quality-empty" aria-labelledby="quality-empty-title">
        <span class="caption">NO MATCHING RUNS</span>
        <h2 id="quality-empty-title">No aggregate runs match these filters.</h2>
        <p>Change the reporting period, task type, or model and run the report again.</p>
      </section>
    </template>

    <template v-else-if="quality">
      <p v-if="loading" class="quality-refreshing" role="status" aria-live="polite">Updating aggregate quality data...</p>

      <section class="quality-panel" aria-labelledby="quality-overview-title">
        <div class="quality-panel__head">
          <div>
            <span class="caption">RUN OUTCOMES</span>
            <h2 id="quality-overview-title">Aggregate quality overview</h2>
          </div>
          <p>{{ formatNumber(sampleSize) }} sampled runs</p>
        </div>

        <dl class="quality-metrics quality-metrics--outcomes">
          <div>
            <dt>Completed rate</dt>
            <dd>{{ formatRate(quality.completedCount) }}</dd>
            <small>{{ formatNumber(quality.completedCount) }} completed</small>
          </div>
          <div>
            <dt>Failed rate</dt>
            <dd>{{ formatRate(quality.failedCount) }}</dd>
            <small>{{ formatNumber(quality.failedCount) }} failed</small>
          </div>
          <div>
            <dt>Cancelled rate</dt>
            <dd>{{ formatRate(quality.cancelledCount) }}</dd>
            <small>{{ formatNumber(quality.cancelledCount) }} cancelled</small>
          </div>
          <div>
            <dt>Timeout rate</dt>
            <dd>{{ formatRate(quality.timeoutCount) }}</dd>
            <small>{{ formatNumber(quality.timeoutCount) }} timed out</small>
          </div>
          <div>
            <dt>Evidence-insufficient rate</dt>
            <dd>{{ formatRate(quality.evidenceInsufficientCount) }}</dd>
            <small>{{ formatNumber(quality.evidenceInsufficientCount) }} flagged</small>
          </div>
        </dl>
      </section>

      <section class="quality-panel" aria-labelledby="quality-resource-title">
        <div class="quality-panel__head">
          <div>
            <span class="caption">RESOURCE USE</span>
            <h2 id="quality-resource-title">Duration, token, and tool telemetry</h2>
          </div>
        </div>

        <dl class="quality-metrics quality-metrics--resources">
          <div>
            <dt>Average duration</dt>
            <dd>{{ formatMilliseconds(quality.latencySummary?.average) }}</dd>
            <small>{{ formatMilliseconds(quality.latencySummary?.total) }} total</small>
          </div>
          <div>
            <dt>Average tokens</dt>
            <dd>{{ formatNumber(quality.tokenSummary?.average) }}</dd>
            <small>{{ formatNumber(quality.tokenSummary?.total) }} total tokens</small>
          </div>
          <div>
            <dt>Average tool calls</dt>
            <dd>{{ formatNumber(quality.toolCallSummary?.average) }}</dd>
            <small>{{ formatNumber(quality.toolCallSummary?.total) }} total tool calls</small>
          </div>
        </dl>
      </section>

      <section class="quality-breakdowns" aria-label="Quality breakdowns">
        <article class="quality-panel">
          <div class="quality-panel__head">
            <div>
              <span class="caption">TASK BREAKDOWN</span>
              <h2>Outcomes by task type</h2>
            </div>
          </div>
          <QualityBreakdownTable label="Task type" :items="quality.taskBreakdown" />
        </article>

        <article class="quality-panel">
          <div class="quality-panel__head">
            <div>
              <span class="caption">MODEL BREAKDOWN</span>
              <h2>Outcomes by model</h2>
            </div>
          </div>
          <QualityBreakdownTable label="Model" :items="quality.modelBreakdown" />
        </article>
      </section>

      <section class="quality-breakdowns" aria-label="Failure, evidence, and feedback signals">
        <article class="quality-panel" aria-labelledby="quality-failure-title">
          <div class="quality-panel__head">
            <div>
              <span class="caption">FAILED RUNS</span>
              <h2 id="quality-failure-title">Failure reasons</h2>
            </div>
          </div>
          <ul v-if="failureReasonEntries.length" class="quality-signal-list">
            <li v-for="item in failureReasonEntries" :key="item.key">
              <code>{{ item.key }}</code>
              <strong>{{ formatNumber(item.count) }}</strong>
            </li>
          </ul>
          <p v-else class="quality-panel__empty">No failed-run reasons were returned for this selection.</p>
        </article>

        <article class="quality-panel">
          <div class="quality-panel__head">
            <div>
              <span class="caption">EVIDENCE GAPS</span>
              <h2>Evidence-insufficient reasons</h2>
            </div>
          </div>
          <ul v-if="evidenceReasonEntries.length" class="quality-signal-list">
            <li v-for="item in evidenceReasonEntries" :key="item.key">
              <code>{{ item.key }}</code>
              <strong>{{ formatNumber(item.count) }}</strong>
            </li>
          </ul>
          <p v-else class="quality-panel__empty">No evidence-insufficient reasons were returned for this selection.</p>
        </article>

        <article class="quality-panel">
          <div class="quality-panel__head">
            <div>
              <span class="caption">FEEDBACK TREND</span>
              <h2>Aggregate feedback signal</h2>
            </div>
            <p>{{ helpfulRateLabel }}</p>
          </div>
          <dl class="quality-feedback-summary">
            <div><dt>Helpful</dt><dd>{{ formatNumber(quality.helpfulCount) }}</dd></div>
            <div><dt>Not helpful</dt><dd>{{ formatNumber(quality.notHelpfulCount) }}</dd></div>
          </dl>
          <ul v-if="feedbackReasonEntries.length" class="quality-signal-list">
            <li v-for="item in feedbackReasonEntries" :key="item.key">
              <span>{{ formatKey(item.key) }}</span>
              <strong>{{ formatNumber(item.count) }}</strong>
            </li>
          </ul>
          <p v-else class="quality-panel__empty">No feedback reasons were returned for this selection.</p>
          <p class="quality-panel__note">The report contains counts only; individual feedback comments are not included.</p>
        </article>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { getAdminResearchQuality } from '@/api/ai'

const QualityBreakdownTable = defineComponent({
  name: 'QualityBreakdownTable',
  props: {
    label: { type: String, required: true },
    items: { type: Array, default: () => [] },
  },
  setup(props) {
    const formatNumber = (value) => new Intl.NumberFormat('en-US').format(Number(value || 0))
    const formatKey = (value) => String(value || 'Not reported').replaceAll('_', ' ')

    return () => {
      if (!props.items.length) {
        return h('p', { class: 'quality-panel__empty' }, `No ${props.label.toLowerCase()} breakdown was returned for this selection.`)
      }

      return h('div', { class: 'quality-table-wrap' }, [
        h('table', [
          h('caption', `${props.label} quality breakdown`),
          h('thead', [h('tr', [
            h('th', { scope: 'col' }, props.label),
            h('th', { scope: 'col' }, 'Sample'),
            h('th', { scope: 'col' }, 'Completed'),
            h('th', { scope: 'col' }, 'Failed'),
            h('th', { scope: 'col' }, 'Evidence insufficient'),
          ])]),
          h('tbody', props.items.map((item) => h('tr', { key: item.key }, [
            h('th', { scope: 'row' }, formatKey(item.key)),
            h('td', formatNumber(item.sampleSize)),
            h('td', formatNumber(item.completedCount)),
            h('td', formatNumber(item.failedCount)),
            h('td', formatNumber(item.evidenceInsufficientCount)),
          ]))),
        ]),
      ])
    }
  },
})

const defaultFilters = () => ({
  dateFrom: '',
  dateTo: '',
  taskType: '',
  model: '',
  granularity: 'day',
})

const filters = reactive(defaultFilters())
const quality = ref(null)
const loading = ref(false)
const error = ref('')
let requestId = 0

const taskTypeOptions = [
  { value: 'auto', label: 'Auto' },
  { value: 'case_analysis', label: 'Case analysis' },
  { value: 'case_comparison', label: 'Case comparison' },
  { value: 'technology_assessment', label: 'Technology assessment' },
  { value: 'policy_lookup', label: 'Policy lookup' },
  { value: 'source_verification', label: 'Source verification' },
  { value: 'general_research', label: 'General research' },
]

const sampleSize = computed(() => count(quality.value?.sampleSize))
const failureReasonEntries = computed(() => toEntries(quality.value?.failureReasons))
const evidenceReasonEntries = computed(() => toEntries(quality.value?.evidenceInsufficientReasons))
const feedbackReasonEntries = computed(() => toEntries(quality.value?.reasonCounts))
const helpfulRateLabel = computed(() => {
  const rate = quality.value?.helpfulRate
  if (rate === null || rate === undefined || rate === '') return 'No rated feedback'
  return Number.isFinite(Number(rate)) ? `${(Number(rate) * 100).toFixed(1)}% helpful` : 'No rated feedback'
})

function buildRequestParams() {
  const params = { granularity: filters.granularity }
  if (filters.dateFrom) params.dateFrom = filters.dateFrom
  if (filters.dateTo) params.dateTo = filters.dateTo
  if (filters.taskType) params.taskType = filters.taskType
  if (filters.model) params.model = filters.model
  return params
}

async function loadQuality() {
  if (filters.dateFrom && filters.dateTo && filters.dateFrom > filters.dateTo) {
    error.value = 'The end date must be on or after the start date.'
    return
  }

  const currentRequestId = ++requestId
  loading.value = true
  error.value = ''
  try {
    const result = await getAdminResearchQuality(buildRequestParams())
    if (currentRequestId === requestId) quality.value = result || emptyAggregate()
  } catch (reason) {
    if (currentRequestId === requestId) error.value = reason.message || 'Aggregate quality data could not be loaded.'
  } finally {
    if (currentRequestId === requestId) loading.value = false
  }
}

async function resetFilters() {
  Object.assign(filters, defaultFilters())
  await loadQuality()
}

function emptyAggregate() {
  return {
    sampleSize: 0,
    completedCount: 0,
    failedCount: 0,
    cancelledCount: 0,
    timeoutCount: 0,
    evidenceInsufficientCount: 0,
    helpfulCount: 0,
    notHelpfulCount: 0,
    helpfulRate: null,
    reasonCounts: {},
    evidenceInsufficientReasons: {},
    failureReasons: {},
    taskBreakdown: [],
    modelBreakdown: [],
    latencySummary: { total: 0, average: 0 },
    tokenSummary: { total: 0, average: 0 },
    toolCallSummary: { total: 0, average: 0 },
  }
}

function count(value) {
  return Math.max(0, Number(value || 0))
}

function formatNumber(value) {
  return new Intl.NumberFormat('en-US').format(count(value))
}

function formatMilliseconds(value) {
  return `${formatNumber(value)} ms`
}

function formatRate(value) {
  if (!sampleSize.value) return '0.0%'
  return `${((count(value) / sampleSize.value) * 100).toFixed(1)}%`
}

function formatKey(value) {
  return String(value || 'Not reported').replaceAll('_', ' ')
}

function toEntries(values) {
  return Object.entries(values || {}).map(([key, value]) => ({ key, count: count(value) }))
}

onMounted(loadQuality)
</script>

<style scoped>
.quality-page { gap: 22px; }
.quality-intro { display: flex; align-items: end; justify-content: space-between; gap: 24px; padding: 4px 2px; }
.quality-intro h2, .quality-panel h2, .quality-empty h2 { margin: 7px 0 0; color: #202420; font-family: 'Noto Serif SC', STSong, SimSun, serif; font-weight: 500; letter-spacing: 0; }
.quality-intro h2 { font-size: clamp(1.7rem, 2.8vw, 2.45rem); }
.quality-intro p { max-width: 720px; margin: 10px 0 0; color: #59605a; line-height: 1.65; }
.quality-intro__scope { max-width: 360px !important; margin: 0 !important; padding: 12px 0 12px 14px; border-left: 3px solid #7b857b; color: #555e55 !important; font-size: .78rem; }
.quality-filters { display: grid; grid-template-columns: repeat(5, minmax(120px, 1fr)) auto; align-items: end; gap: 12px; padding: 18px; border: 1px solid #cfd4ce; background: #f7f8f4; }
.quality-filters label { display: grid; gap: 7px; min-width: 0; }
.quality-filters label > span { color: #4b514c; font-size: .74rem; font-weight: 700; }
.quality-filters :is(input, select) { width: 100%; min-width: 0; min-height: 44px; }
.quality-filters__actions { display: flex; gap: 8px; }
.quality-filters__actions .button { min-height: 44px; white-space: nowrap; }
.quality-state, .quality-empty { margin: 0; padding: 22px; border: 1px solid #cbd0ca; background: #f6f7f3; color: #59605a; }
.quality-state--error { display: flex; align-items: center; justify-content: space-between; gap: 18px; border-color: #d3aaa3; background: #fbf0ed; color: #732f27; }
.quality-state--error button { min-height: 44px; padding: 0 12px; border: 1px solid currentColor; background: transparent; color: inherit; font: inherit; font-weight: 700; cursor: pointer; }
.quality-state--error button:is(:hover, :focus-visible) { background: #f2e3df; }
.quality-state--error button:focus-visible { outline: 2px solid #732f27; outline-offset: 2px; }
.quality-empty h2 { font-size: 1.25rem; }
.quality-empty p { margin: 8px 0 0; }
.quality-refreshing { margin: -10px 0 0; color: #5a625b; font-size: .78rem; }
.quality-panel { min-width: 0; padding: 20px; border: 1px solid #cbd0ca; background: #fbfbf8; }
.quality-panel__head { display: flex; align-items: end; justify-content: space-between; gap: 16px; padding-bottom: 16px; border-bottom: 1px solid #d9ddd7; }
.quality-panel__head h2 { font-size: 1.16rem; }
.quality-panel__head > p { margin: 0; color: #5d655e; font-size: .77rem; }
.quality-metrics { display: grid; gap: 0; margin: 0; }
.quality-metrics--outcomes { grid-template-columns: repeat(5, minmax(0, 1fr)); }
.quality-metrics--resources { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.quality-metrics > div { display: grid; align-content: start; gap: 5px; min-width: 0; min-height: 112px; padding: 17px; border-right: 1px solid #d9ddd7; }
.quality-metrics > div:first-child { padding-left: 0; }
.quality-metrics > div:last-child { padding-right: 0; border-right: 0; }
.quality-metrics dt, .quality-feedback-summary dt { color: #616862; font-size: .72rem; font-weight: 700; }
.quality-metrics dd, .quality-feedback-summary dd { margin: 0; color: #222722; font-family: 'Bookman Old Style', Georgia, serif; font-size: 1.55rem; line-height: 1.1; }
.quality-metrics small { color: #697069; font-size: .72rem; line-height: 1.5; }
.quality-breakdowns { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr)); gap: 18px; }
.quality-table-wrap { overflow-x: auto; }
.quality-table-wrap table { width: 100%; min-width: 500px; border-collapse: collapse; text-align: left; }
.quality-table-wrap caption { padding: 12px 0 8px; color: #59605a; text-align: left; font-size: .75rem; }
.quality-table-wrap :is(th, td) { padding: 10px 8px; border-top: 1px solid #dfe2dc; color: #505850; font-size: .74rem; white-space: nowrap; }
.quality-table-wrap thead th { color: #606860; font-size: .68rem; }
.quality-table-wrap tbody th { color: #282d28; font-weight: 700; }
.quality-table-wrap :is(th, td):first-child { padding-left: 0; }
.quality-table-wrap :is(th, td):last-child { padding-right: 0; }
.quality-signal-list { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }
.quality-signal-list li { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 11px 0; border-bottom: 1px solid #dfe2dc; color: #555d56; font-size: .78rem; }
.quality-signal-list li:last-child { border-bottom: 0; }
.quality-signal-list code { color: #343b35; font: 600 .74rem/1.3 'Courier New', monospace; overflow-wrap: anywhere; }
.quality-signal-list strong { color: #252a25; font-family: 'Bookman Old Style', Georgia, serif; }
.quality-feedback-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 16px 0 8px; border-top: 1px solid #dfe2dc; border-left: 1px solid #dfe2dc; }
.quality-feedback-summary > div { display: grid; gap: 5px; padding: 13px; border-right: 1px solid #dfe2dc; border-bottom: 1px solid #dfe2dc; }
.quality-panel__empty, .quality-panel__note { margin: 14px 0 0; color: #697069; font-size: .77rem; line-height: 1.55; }
.quality-panel__note { padding-top: 12px; border-top: 1px solid #dfe2dc; }
@media (max-width: 1200px) { .quality-filters { grid-template-columns: repeat(3, minmax(0, 1fr)); }.quality-filters__actions { justify-content: flex-end; }.quality-metrics--outcomes { grid-template-columns: repeat(3, minmax(0, 1fr)); }.quality-metrics--outcomes > div:nth-child(3) { border-right: 0; }.quality-metrics--outcomes > div:nth-child(4) { padding-left: 0; }.quality-metrics--outcomes > div:nth-child(5) { border-right: 0; } }
@media (max-width: 800px) { .quality-intro { display: grid; }.quality-intro__scope { max-width: none !important; }.quality-breakdowns { grid-template-columns: 1fr; }.quality-metrics--outcomes { grid-template-columns: repeat(2, minmax(0, 1fr)); }.quality-metrics--outcomes > div { padding: 14px; border-right: 1px solid #d9ddd7; border-bottom: 1px solid #d9ddd7; }.quality-metrics--outcomes > div:nth-child(odd) { padding-left: 0; }.quality-metrics--outcomes > div:nth-child(even) { border-right: 0; }.quality-metrics--outcomes > div:last-child { border-bottom: 0; }.quality-metrics--resources { grid-template-columns: 1fr; }.quality-metrics--resources > div { min-height: 0; padding: 14px 0; border-right: 0; border-bottom: 1px solid #d9ddd7; }.quality-metrics--resources > div:last-child { border-bottom: 0; } }
@media (max-width: 620px) { .quality-filters { grid-template-columns: 1fr; }.quality-filters__actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }.quality-panel { padding: 16px; }.quality-panel__head { align-items: start; flex-direction: column; }.quality-metrics--outcomes { grid-template-columns: 1fr; }.quality-metrics--outcomes > div { padding: 13px 0; border-right: 0; border-bottom: 1px solid #d9ddd7; }.quality-metrics--outcomes > div:last-child { border-bottom: 0; }.quality-state--error { align-items: start; flex-direction: column; } }
</style>
