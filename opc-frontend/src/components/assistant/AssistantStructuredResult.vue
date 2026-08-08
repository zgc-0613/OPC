<template>
  <article v-if="supported" class="structured-result" data-testid="structured-result" aria-label="结构化研究结果">
    <header class="result-header">
      <span>STRUCTURED RESEARCH</span>
      <strong>{{ taskTypeLabel(result.taskType) }}</strong>
    </header>

    <section class="result-summary" aria-labelledby="result-summary-title">
      <div class="result-summary-heading"><h3 id="result-summary-title">研究摘要</h3><span class="claim-kind" :class="`is-${result.summaryKind || 'inference'}`">{{ kindLabel(result.summaryKind || 'inference') }}</span></div>
      <p>{{ result.directAnswer }}</p>
    </section>

    <ResultClaimSection v-if="claims.keyFindings.length" title="关键发现" :items="claims.keyFindings" @citations="emitCitations" />
    <ResultClaimSection v-if="claims.recommendations.length" title="可执行建议" :items="claims.recommendations" forced-kind="recommendation" @citations="emitCitations" />
    <ResultClaimSection v-if="claims.risks.length" title="风险与约束" :items="claims.risks" @citations="emitCitations" />
    <ResultClaimSection v-if="claims.assumptions.length" title="关键假设" :items="claims.assumptions" @citations="emitCitations" />
    <ResultClaimSection v-if="claims.uncertainties.length" title="未知项" :items="claims.uncertainties" @citations="emitCitations" />

    <details v-if="taskSections.length" class="task-result-details">
      <summary>按任务整理的研究结果</summary>
      <section v-for="section in taskSections" :key="section.key" class="task-evidence-section" :class="`is-${section.status}`">
        <header><h4>{{ section.title }}</h4><span>{{ evidenceSectionLabel(section.status) }}</span></header>
        <ResultClaimList v-if="section.items.length" :items="section.items" @citations="emitCitations" />
        <p v-if="section.caveat" class="section-caveat">{{ section.caveat }}</p>
      </section>
    </details>

    <section class="evidence-coverage" :class="`is-${coverage.state}`" aria-live="polite">
      <div><span>证据状态</span><strong>{{ coverage.label }}</strong></div>
      <p>{{ coverage.detail }}</p>
    </section>

    <section v-if="analyticsSnapshot" class="analytics-snapshot-provenance" data-testid="analytics-snapshot-provenance" aria-labelledby="analytics-snapshot-title">
      <div>
        <h3 id="analytics-snapshot-title">数据快照</h3>
        <small>指标 {{ analyticsSnapshot.metricId }} · 数据版本 {{ analyticsSnapshot.dataVersion }}</small>
      </div>
      <p>快照 #{{ analyticsSnapshot.id }}<template v-if="analyticsSnapshot.sampleSize !== null"> · 样本 {{ analyticsSnapshot.sampleSize }}</template><template v-if="analyticsSnapshot.missingCount !== null"> · 缺失 {{ analyticsSnapshot.missingCount }}</template></p>
      <p v-if="analyticsSnapshot.filters.length" class="analytics-snapshot-filters">筛选：{{ analyticsSnapshot.filters.join('；') }}</p>
      <a :href="analyticsSnapshot.href" data-testid="analytics-snapshot-backlink">查看对应指标</a>
    </section>

    <section v-if="citationItems.length" class="result-citations" aria-labelledby="result-citations-title">
      <div>
        <h3 id="result-citations-title">引用与版本</h3>
        <small>{{ evidenceVersionLabel }}</small>
      </div>
      <ol>
        <li v-for="(citation, index) in citationItems" :key="citation.sourceId">
          <span>[{{ index + 1 }}]</span>
          <div><strong>{{ citation.title }}</strong><small>{{ citation.publisher || `来源 #${citation.sourceId}` }} · {{ citationAvailabilityLabel(citation.availability) }}</small></div>
        </li>
      </ol>
      <button data-testid="structured-result-citations" type="button" :aria-label="`查看本次研究的 ${citationItems.length} 条引用`" @click="emitCitations(citationSourceIds)">
        查看 {{ citationItems.length }} 条引用
      </button>
    </section>

    <section v-if="nextQuestions.length" class="next-questions" aria-labelledby="next-questions-title">
      <h3 id="next-questions-title">建议的后续问题</h3>
      <ul><li v-for="question in nextQuestions" :key="question">{{ question }}</li></ul>
    </section>
  </article>
</template>

<script setup>
import { computed, defineComponent, h } from 'vue'

const kindLabels = {
  fact: '事实',
  inference: '推断',
  recommendation: '建议',
  methodology: '研究方法',
}
const taskTypeLabels = {
  case_analysis: '案例分析',
  case_comparison: '多案例比较',
  technology_assessment: '技术路线评估',
  policy_lookup: '政策研究',
  source_verification: '来源核验',
  general_research: '综合创业研究',
}
const sectionLabels = {
  businessModel: '商业模式', targetCustomers: '目标客户', revenueModel: '收入方式', costsAndResources: '成本与资源',
  technicalRoute: '技术路线', successFactors: '关键成功因素', replicableElements: '可借鉴做法',
  nonReplicableConditions: '不可复制条件', userFit: '用户适配性', actions: '行动建议',
  commonalities: '共同点', differences: '关键差异', regionalAndPolicyContext: '地区与政策环境', conclusion: '结论',
  costStructure: '成本结构', dataAndInfrastructure: '数据与基础设施', capabilityGaps: '能力缺口', dependencies: '依赖条件',
  complianceRisks: '合规风险', operatingRisks: '运营风险', alternatives: '替代路线', roadmap: '实施路径', experiments: '验证实验',
  applicableRegions: '适用地区', applicableIndustries: '适用行业', validity: '有效期', eligibilityConditions: '申请条件',
  supportMeasures: '支持措施', conflicts: '冲突证据', expirationRisks: '失效风险', verificationNeeded: '仍需核验',
  publisherAssessment: '发布者评估', supportedClaims: '可支撑结论', unsupportedClaims: '不支持的结论', invalidityReasons: '失效原因',
  businessModelComparison: '商业模式', technicalPath: '技术路径', targetCustomer: '目标客户', outcome: '结果',
  regionalContext: '地区环境', evidenceStrength: '证据强度',
}

const props = defineProps({ result: { type: Object, default: null } })
const emit = defineEmits(['citations'])
const supported = computed(() => props.result?.schemaVersion === 'phase3-structured-result-v1' && typeof props.result?.directAnswer === 'string' && props.result.directAnswer.trim().length > 0)
const citationItems = computed(() => Array.isArray(props.result?.citations) ? props.result.citations.filter((citation) => Number.isInteger(Number(citation?.sourceId)) && Number(citation.sourceId) > 0) : [])
const citationSourceIds = computed(() => citationItems.value.map((citation) => Number(citation.sourceId)))
const claims = computed(() => ({
  keyFindings: claimList(props.result?.keyFindings, 'methodology'), recommendations: claimList(props.result?.recommendations, 'recommendation'),
  risks: claimList(props.result?.risks, 'inference'), assumptions: claimList(props.result?.assumptions, 'methodology'), uncertainties: claimList(props.result?.uncertainties, 'methodology'),
}))
const nextQuestions = computed(() => Array.isArray(props.result?.nextQuestions) ? props.result.nextQuestions.filter((value) => typeof value === 'string' && value.trim()) : [])
const taskSections = computed(() => collectTaskSections(props.result?.taskResult))
const evidenceVersionLabel = computed(() => props.result?.evidenceVersion ? `证据版本 ${props.result.evidenceVersion}` : '未提供证据版本')
const coverage = computed(() => coverageSummary(props.result, citationItems.value))
const analyticsSnapshot = computed(() => analyticsSnapshotSummary(props.result?.analyticsSnapshot, props.result?.dataVersion))

const ResultClaimList = defineComponent({
  name: 'ResultClaimList',
  props: { items: { type: Array, default: () => [] }, forcedKind: { type: String, default: '' } },
  emits: ['citations'],
  setup(componentProps, { emit: componentEmit }) {
    return () => h('ul', { class: 'claim-list' }, componentProps.items.map((claim) => h('li', { key: claim.id || claim.text }, [
      h('span', { class: ['claim-kind', `is-${componentProps.forcedKind || claim.kind || 'methodology'}`] }, kindLabel(componentProps.forcedKind || claim.kind)),
      h('p', claim.text),
      claimSourceIds(claim).length ? h('button', {
        type: 'button', class: 'claim-citations', 'aria-label': '查看支撑此结论的引用', onClick: () => componentEmit('citations', claimSourceIds(claim)),
      }, citationReferenceLabel(claimSourceIds(claim))) : null,
    ])) )
  },
})
const ResultClaimSection = defineComponent({
  name: 'ResultClaimSection',
  props: { title: { type: String, required: true }, items: { type: Array, default: () => [] }, forcedKind: { type: String, default: '' } },
  emits: ['citations'],
  setup(componentProps, { emit: componentEmit }) {
    return () => h('section', { class: 'result-claim-section' }, [
      h('h3', componentProps.title),
      h(ResultClaimList, { items: componentProps.items, forcedKind: componentProps.forcedKind, onCitations: (sourceIds) => componentEmit('citations', sourceIds) }),
    ])
  },
})

function claimList(value, fallbackKind = 'methodology') {
  if (!Array.isArray(value)) return []
  return value.map((claim, index) => {
    if (typeof claim === 'string' && claim.trim()) return { id: `${fallbackKind}-${index}`, text: claim.trim(), kind: fallbackKind, sourceIds: [] }
    if (!claim || typeof claim !== 'object') return null
    if (typeof claim.text === 'string' && claim.text.trim()) return { ...claim, kind: claim.kind || claim.evidenceType || fallbackKind }
    if (typeof claim.reason === 'string' && typeof claim.nextAction === 'string' && claim.reason.trim() && claim.nextAction.trim()) {
      return { ...claim, id: claim.id || `recommendation-${index}`, text: `${claim.reason.trim()} 下一步：${claim.nextAction.trim()}`, kind: 'recommendation' }
    }
    return null
  }).filter(Boolean)
}
function claimSourceIds(claim) { return Array.isArray(claim?.sourceIds) ? claim.sourceIds.filter((sourceId) => Number.isInteger(Number(sourceId)) && Number(sourceId) > 0).map(Number) : [] }
function kindLabel(kind) { return kindLabels[kind] || kindLabels.methodology }
function taskTypeLabel(type) { return taskTypeLabels[type] || '研究结果' }
function evidenceSectionLabel(status) { return ({ known: '有可用证据', unknown: '证据未知', not_applicable: '不适用' }[status] || '待核验') }
function citationAvailabilityLabel(status) { return ({ current: '已核验', stale: '可能已更新', unavailable: '当前不可用' }[status] || '待核验') }
function citationReferenceLabel(sourceIds) { return `依据 [${sourceIds.join('、')}]` }
function emitCitations(sourceIds) { emit('citations', Array.isArray(sourceIds) ? sourceIds : []) }

function collectTaskSections(taskResult) {
  if (!taskResult || typeof taskResult !== 'object') return []
  const sections = []
  const add = (key, section, title = sectionLabels[key] || key) => {
    if (!isEvidenceSection(section)) return
    sections.push({ key: `${key}-${sections.length}`, title, status: section.status, items: claimList(section.items), caveat: typeof section.caveat === 'string' ? section.caveat : '' })
  }
  if (Array.isArray(taskResult.sections)) taskResult.sections.forEach((section) => add(section?.id || 'section', section?.content, section?.title || '研究内容'))
  else if (taskResult.sections && typeof taskResult.sections === 'object') Object.entries(taskResult.sections).forEach(([key, section]) => add(key, section))
  Object.entries(taskResult).forEach(([key, section]) => {
    if (key !== 'sections') add(key, section)
  })
  if (Array.isArray(taskResult.comparisons)) taskResult.comparisons.forEach((comparison) => add(comparison?.dimension || 'comparison', comparison?.analysis))
  return sections
}
function isEvidenceSection(value) { return value && typeof value === 'object' && ['known', 'unknown', 'not_applicable'].includes(value.status) && Array.isArray(value.items) }
function coverageSummary(result, citations) {
  const taskResult = result?.taskResult || {}
  const sourceVerificationConflict = taskResult.type === 'source_verification' && taskResult.verdict === 'conflicting'
  const conflict = sourceVerificationConflict || (isEvidenceSection(taskResult.conflicts) && taskResult.conflicts.status === 'known')
  const unavailable = citations.some((citation) => ['stale', 'unavailable'].includes(citation.availability))
  const data = result?.evidenceCoverage || {}
  const factCount = Number(data.factClaimCount || 0)
  const citedCount = Number(data.citedFactClaimCount || 0)
  const missingCount = Number(data.missingEvidenceFactCount || 0)
  const ratio = typeof data.ratio === 'number' ? data.ratio : null
  if (conflict) return { state: 'conflict', label: '存在冲突', detail: '已发现来源之间的差异，结论应结合冲突内容人工核验。' }
  if (unavailable) return { state: 'stale', label: '来源已失效或数据已更新', detail: '至少一条已保存引用不再是当前可用版本；历史结果不会被静默改写。' }
  if (!factCount || missingCount > 0 || taskResult.evidenceStatus === 'insufficient') return { state: 'insufficient', label: '证据不足', detail: missingCount ? `仍有 ${missingCount} 条事实缺少可用来源。` : '当前资料不足以支撑事实性结论。' }
  if (ratio === 1 && citedCount === factCount) return { state: 'sufficient', label: '已核验且充分', detail: `已为 ${citedCount}/${factCount} 条事实结论绑定本次研究的引用。` }
  if (citedCount > 0 || ratio !== null) return { state: 'limited', label: '已核验但有限', detail: `已为 ${citedCount}/${factCount} 条事实结论绑定引用，仍需结合资料范围判断。` }
  return { state: 'unknown', label: '证据状态待核验', detail: '结果未提供可用的证据覆盖信息。' }
}
function analyticsSnapshotSummary(value, resultDataVersion) {
  if (!value || typeof value !== 'object' || !Number.isInteger(Number(value.analyticsSnapshotId)) || Number(value.analyticsSnapshotId) <= 0
    || typeof value.metricId !== 'string' || !value.metricId.trim() || typeof value.dataVersion !== 'string' || !value.dataVersion.trim()) return null
  const snapshot = value.snapshot && typeof value.snapshot === 'object' ? value.snapshot : {}
  const filters = value.filters && typeof value.filters === 'object' ? value.filters : {}
  const filterEntries = ['regionId', 'industryTagId', 'technologyTagId', 'regionRole', 'dateFrom', 'dateTo']
    .flatMap((key) => analyticsFilterEntry(key, filters[key]))
  return {
    id: Number(value.analyticsSnapshotId),
    metricId: value.metricId.trim(),
    dataVersion: value.dataVersion.trim() || String(resultDataVersion || '').trim(),
    sampleSize: nonNegativeNumber(snapshot.sampleSize),
    missingCount: nonNegativeNumber(snapshot.missingCount),
    filters: filterEntries.flatMap(([key, filterValue]) => formatAnalyticsFilter(key, filterValue)),
    href: analyticsSnapshotHref(value.metricId.trim(), filterEntries),
  }
}
function nonNegativeNumber(value) { return Number.isFinite(Number(value)) && Number(value) >= 0 ? Number(value) : null }
function analyticsFilterEntry(key, value) {
  if (value === null || value === undefined || value === '') return []
  if (!['string', 'number', 'boolean'].includes(typeof value)) return []
  return [[key, String(value)]]
}
function formatAnalyticsFilter(key, value) {
  const labels = { regionId: '地区', industryTagId: '行业', technologyTagId: '技术', regionRole: '地区角色', dateFrom: '开始时间', dateTo: '结束时间' }
  return [`${labels[key]} ${String(value)}`]
}
function analyticsSnapshotHref(metricId, filterEntries) {
  const params = new URLSearchParams({ metricId })
  filterEntries.forEach(([key, value]) => params.set(key, value))
  return `/analytics?${params.toString()}`
}
</script>

<style scoped>
.result-summary-heading{display:flex;align-items:center;justify-content:space-between;gap:10px}
.structured-result{display:grid;gap:18px;min-width:0;padding:2px 0 4px;color:#252a25;font-family:'Noto Serif SC',Songti SC,STSong,SimSun,serif}.result-header{display:flex;align-items:baseline;justify-content:space-between;gap:12px;padding-bottom:10px;border-bottom:1px solid #ccd0ca}.result-header span{color:#707770;font-family:'Bookman Old Style',Georgia,serif;font-size:.64rem;font-weight:700}.result-header strong{font-size:.74rem;font-weight:700}.result-summary{padding:14px 16px;border:1px solid #ccd0ca;border-radius:6px;background:#f2f3ef}.structured-result h3,.structured-result h4{margin:0;color:#222722;font-family:'Noto Serif SC',Songti SC,STSong,SimSun,serif;font-size:.92rem;font-weight:600}.result-summary p{margin:9px 0 0;overflow-wrap:anywhere;line-height:1.78}.result-claim-section{padding-top:14px;border-top:1px solid #d7dad5}.claim-list{display:grid;gap:10px;margin:10px 0 0;padding:0;list-style:none}.claim-list li{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:start;gap:7px 9px;min-width:0}.claim-kind{display:inline-flex;align-items:center;min-height:22px;padding:0 6px;border:1px solid #bfc5bd;border-radius:999px;color:#485048;font-family:system-ui,sans-serif;font-size:.62rem;font-weight:700;white-space:nowrap}.claim-kind.is-fact{border-color:#719279;color:#315c42}.claim-kind.is-recommendation{border-color:#777d76;color:#313631}.claim-list p{margin:0;min-width:0;overflow-wrap:anywhere;line-height:1.68}.claim-citations,.result-citations button{display:inline-flex;align-items:center;justify-content:center;min-height:34px;padding:0 9px;border:1px solid #bfc5bd;border-radius:3px;background:#fbfbf8;color:#333933;font-family:inherit;font-size:.68rem;font-weight:700;white-space:nowrap}.claim-citations:is(:hover,:focus-visible),.result-citations button:is(:hover,:focus-visible){border-color:#747b74;background:#eceee8}.claim-citations:focus-visible,.result-citations button:focus-visible,.task-result-details summary:focus-visible{outline:2px solid rgba(74,82,74,.34);outline-offset:2px}.task-result-details{padding-top:14px;border-top:1px solid #d7dad5}.task-result-details summary{min-height:36px;cursor:pointer;color:#303630;font-size:.78rem;font-weight:700}.task-evidence-section{padding:13px 0;border-top:1px solid #dde0da}.task-evidence-section:first-of-type{margin-top:8px}.task-evidence-section>header{display:flex;align-items:baseline;justify-content:space-between;gap:12px}.task-evidence-section>header span{color:#687068;font-size:.66rem}.section-caveat{margin:8px 0 0;padding-left:10px;border-left:2px solid #aeb4ac;color:#596059;font-size:.76rem;line-height:1.62;overflow-wrap:anywhere}.evidence-coverage{display:grid;gap:5px;padding:13px 15px;border:1px solid #c7ccc5;border-left:3px solid #737a72;background:#f2f3ef}.evidence-coverage>div{display:flex;align-items:baseline;justify-content:space-between;gap:12px}.evidence-coverage span{color:#687068;font-size:.67rem;font-weight:700}.evidence-coverage strong{font-size:.78rem}.evidence-coverage p{margin:0;color:#525a52;font-size:.74rem;line-height:1.6;overflow-wrap:anywhere}.evidence-coverage.is-sufficient{border-left-color:#4f6f58}.evidence-coverage.is-conflict,.evidence-coverage.is-insufficient,.evidence-coverage.is-stale{border-left-color:#8a5047}.result-citations{display:grid;gap:10px;padding-top:14px;border-top:1px solid #d7dad5}.result-citations>div{display:flex;align-items:baseline;justify-content:space-between;gap:12px}.result-citations small{color:#737a72;font-family:'Bookman Old Style',Georgia,serif;font-size:.61rem;overflow-wrap:anywhere}.result-citations ol{display:grid;gap:7px;margin:0;padding:0;list-style:none}.result-citations li{display:grid;grid-template-columns:28px minmax(0,1fr);gap:8px;min-width:0}.result-citations li>span{color:#707770;font-family:'Bookman Old Style',Georgia,serif;font-size:.72rem}.result-citations li div{display:grid;gap:3px;min-width:0}.result-citations li strong{overflow-wrap:anywhere;font-size:.77rem}.result-citations li small{font-family:inherit;font-size:.67rem}.result-citations button{justify-self:start}.next-questions{padding-top:14px;border-top:1px solid #d7dad5}.next-questions ul{display:grid;gap:5px;margin:9px 0 0;padding-left:19px}.next-questions li{overflow-wrap:anywhere;font-size:.78rem;line-height:1.62}@media(max-width:640px),(pointer:coarse){.claim-list li{grid-template-columns:auto minmax(0,1fr)}.claim-citations{grid-column:2;justify-self:start;min-height:44px}.result-citations button{min-height:44px}.result-header,.result-citations>div,.evidence-coverage>div{align-items:flex-start;flex-direction:column;gap:5px}.result-summary{padding:13px}.task-result-details summary{min-height:44px;padding-top:4px}.task-evidence-section>header{align-items:flex-start;flex-direction:column;gap:4px}}@media(prefers-reduced-motion:reduce){.claim-citations,.result-citations button{transition:none}}
.evidence-coverage{border-left-width:1px}.section-caveat{border-left-width:1px}.analytics-snapshot-provenance{display:grid;gap:5px;padding:14px 15px;border:1px solid #c7ccc5;background:#f2f3ef}.analytics-snapshot-provenance>div{display:flex;align-items:baseline;justify-content:space-between;gap:12px}.analytics-snapshot-provenance h3{font-size:.84rem}.analytics-snapshot-provenance small{color:#687068;font-family:'Bookman Old Style',Georgia,serif;font-size:.63rem;overflow-wrap:anywhere}.analytics-snapshot-provenance p{margin:0;color:#525a52;font-size:.74rem;line-height:1.6;overflow-wrap:anywhere}.analytics-snapshot-filters{color:#626a62}.analytics-snapshot-provenance a{justify-self:start;display:inline-flex;align-items:center;min-height:34px;padding:0 9px;border:1px solid #bfc5bd;border-radius:3px;background:#fbfbf8;color:#333933;font-size:.68rem;font-weight:700;text-decoration:none}.analytics-snapshot-provenance a:is(:hover,:focus-visible){border-color:#747b74;background:#eceee8}.analytics-snapshot-provenance a:focus-visible{outline:2px solid rgba(74,82,74,.34);outline-offset:2px}@media(max-width:640px),(pointer:coarse){.analytics-snapshot-provenance a{min-height:44px}.analytics-snapshot-provenance>div{align-items:flex-start;flex-direction:column;gap:5px}}@media(prefers-reduced-motion:reduce){.analytics-snapshot-provenance a{transition:none}}
</style>
