<template>
  <section class="research-task" :class="{ 'is-readonly': !editable }" aria-labelledby="research-task-title">
    <header>
      <div>
        <span class="caption">RESEARCH TASK</span>
        <h2 id="research-task-title">{{ editable ? '选择研究任务' : '研究任务' }}</h2>
      </div>
      <small v-if="editable">任务边界会在首次发送时固定</small>
    </header>

    <div v-if="!editable" class="task-summary">
      <strong>{{ selectedTask.label }}</strong>
      <span>{{ readOnlyDetail }}</span>
      <dl v-if="draft.taskType === 'technology_assessment'" class="technology-summary">
        <template v-for="item in technologySummary" :key="item.label">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </template>
      </dl>
    </div>

    <details v-else open class="task-editor">
      <summary>研究类型 <span>选择后仅使用受控条件创建研究</span></summary>
      <div class="task-options" role="group" aria-label="研究任务类型">
        <button
          v-for="task in tasks"
          :key="task.id"
          type="button"
          :data-testid="`research-task-${task.id}`"
          :class="{ selected: draft.taskType === task.id }"
          :aria-pressed="draft.taskType === task.id ? 'true' : 'false'"
          @click="selectTask(task.id)"
        >
          <component :is="taskIcons[task.id]" :size="17" aria-hidden="true" />
          <span><strong>{{ task.label }}</strong><small>{{ task.detail }}</small></span>
        </button>
      </div>

      <p v-if="loading" class="task-state" role="status">正在读取已发布、已核验的研究资料。</p>
      <p v-else-if="candidateError" class="task-state is-error" role="alert">{{ candidateError }}</p>

      <div v-if="draft.taskType" class="task-fields">
        <label v-if="needsCases" class="task-field">
          <span>{{ draft.taskType === 'case_analysis' ? '选择一个已核验案例' : '选择 2-3 个已核验案例' }}</span>
          <select
            data-testid="task-case-selector"
            :multiple="draft.taskType === 'case_comparison'"
            :size="draft.taskType === 'case_comparison' ? Math.min(4, Math.max(2, caseOptions.length)) : 1"
            :value="draft.caseIds.map(String)"
            @change="updateCaseIds"
          >
            <option v-if="draft.taskType === 'case_analysis'" value="">请选择案例</option>
            <option v-for="item in caseOptions" :key="item.id" :value="String(item.id)">
              {{ item.title }}{{ item.regionName ? ` · ${item.regionName}` : '' }}
            </option>
          </select>
          <small v-if="!caseOptions.length && !loading">当前没有可选的已核验案例。</small>
        </label>

        <fieldset v-if="draft.taskType === 'case_comparison'" class="task-field task-dimensions">
          <legend>比较维度，至少选择一项</legend>
          <label v-for="dimension in comparisonDimensions" :key="dimension.id">
            <input
              type="checkbox"
              :value="dimension.id"
              :checked="draft.comparisonDimensions.includes(dimension.id)"
              @change="toggleDimension(dimension.id, $event.target.checked)"
            />
            <span>{{ dimension.label }}</span>
          </label>
        </fieldset>

        <label v-if="draft.taskType === 'technology_assessment'" class="task-field">
          <span>技术标签 <small>优先选择已发布标签</small></span>
          <select
            data-testid="task-technology-tag"
            :value="draft.technologyTagId || ''"
            @change="updateTechnologyTagId($event.target.value)"
          >
            <option value="">未匹配标签</option>
            <option v-for="item in technologyOptions" :key="item.id" :value="String(item.id)">{{ item.name }}</option>
          </select>
        </label>

        <label v-if="draft.taskType === 'technology_assessment'" class="task-field">
          <span>技术方向补充说明</span>
          <input
            data-testid="task-technology-text"
            :value="draft.technologyText"
            maxlength="120"
            placeholder="例如：面向小微企业的智能客服工作流"
            @input="updateField('technologyText', $event.target.value)"
          />
        </label>

        <label v-if="draft.taskType === 'technology_assessment'" class="task-field task-field-wide">
          <span>应用场景 <small>当前研究要解决的具体问题</small></span>
          <textarea
            data-testid="task-application-scenario"
            :value="draft.applicationScenario"
            rows="2"
            maxlength="500"
            placeholder="例如：为小微企业客服提供可追溯回答"
            @input="updateField('applicationScenario', $event.target.value)"
          ></textarea>
        </label>

        <label v-if="draft.taskType === 'technology_assessment'" class="task-field task-field-wide">
          <span>团队能力</span>
          <textarea
            data-testid="task-team-capabilities"
            :value="draft.teamCapabilities"
            rows="2"
            maxlength="500"
            placeholder="工程、产品、数据与行业经验"
            @input="updateField('teamCapabilities', $event.target.value)"
          ></textarea>
        </label>

        <label v-if="draft.taskType === 'technology_assessment'" class="task-field">
          <span>实施周期</span>
          <select data-testid="task-timeline" :value="draft.timeline" @change="updateField('timeline', $event.target.value)">
            <option v-for="item in TECHNOLOGY_TIMELINES" :key="item.id" :value="item.id">{{ item.label }}</option>
          </select>
        </label>

        <label v-if="draft.taskType === 'technology_assessment'" class="task-field task-field-wide">
          <span>本次研究可用资源 <small>可覆盖创业画像中的长期信息</small></span>
          <textarea
            data-testid="task-existing-resources"
            :value="draft.existingResources"
            rows="2"
            maxlength="500"
            placeholder="原型、数据、客户线索、预算或合作资源"
            @input="updateField('existingResources', $event.target.value)"
          ></textarea>
        </label>

        <label v-if="draft.taskType === 'technology_assessment'" class="task-field task-field-wide">
          <span>约束条件</span>
          <textarea
            data-testid="task-constraints"
            :value="draft.constraints"
            rows="2"
            maxlength="800"
            placeholder="合规、数据边界、预算、部署环境等约束"
            @input="updateField('constraints', $event.target.value)"
          ></textarea>
        </label>

        <label v-if="draft.taskType === 'source_verification'" class="task-field">
          <span>指定已核验来源，可选</span>
          <select data-testid="task-source-selector" :value="draft.sourceId || ''" @change="updateSourceId($event.target.value)">
            <option value="">不指定来源，核验问题中的结论</option>
            <option v-for="item in sourceOptions" :key="item.id" :value="String(item.id)">
              {{ item.title }}{{ item.publisher ? ` · ${item.publisher}` : '' }}
            </option>
          </select>
          <small v-if="!sourceOptions.length && !loading">没有可选来源时，系统只会核验本次问题中的明确结论。</small>
        </label>

        <label class="task-field output-depth">
          <span>成果深度</span>
          <select data-testid="task-output-depth" :value="draft.outputDepth" @change="updateField('outputDepth', $event.target.value)">
            <option value="concise">简要</option>
            <option value="standard">标准</option>
            <option value="deep">深入</option>
          </select>
        </label>
      </div>
    </details>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { BriefcaseBusiness, FileCheck2, Files, GitCompareArrows, Landmark, Route } from 'lucide-vue-next'
import {
  COMPARISON_DIMENSIONS, RESEARCH_TASKS, RESEARCH_TASK_TYPE_SET,
  TECHNOLOGY_TIMELINES, technologyTimelineLabel,
} from '@/constants/researchTasks'

const props = defineProps({
  modelValue: { type: Object, required: true },
  editable: Boolean,
  caseOptions: { type: Array, default: () => [] },
  sourceOptions: { type: Array, default: () => [] },
  technologyOptions: { type: Array, default: () => [] },
  profileResources: { type: String, default: '' },
  loading: Boolean,
  candidateError: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue'])

const tasks = RESEARCH_TASKS
const comparisonDimensions = COMPARISON_DIMENSIONS
const taskIcons = {
  case_analysis: BriefcaseBusiness,
  case_comparison: GitCompareArrows,
  technology_assessment: Route,
  policy_lookup: Landmark,
  source_verification: FileCheck2,
  general_research: Files,
}
const allowedTaskTypes = RESEARCH_TASK_TYPE_SET
const allowedDimensions = new Set(COMPARISON_DIMENSIONS.map((dimension) => dimension.id))
const draft = ref(normalizeTask(props.modelValue))

const selectedTask = computed(() => tasks.find((task) => task.id === draft.value.taskType)
  || { label: '未单独选择任务', detail: '系统按当前问题判定研究范围' })
const needsCases = computed(() => ['case_analysis', 'case_comparison'].includes(draft.value.taskType))
const selectedTechnology = computed(() => props.technologyOptions.find((item) => Number(item?.id) === Number(draft.value.technologyTagId)))
const technologySummary = computed(() => [
  { label: '技术标签', value: selectedTechnology.value?.name || (draft.value.technologyTagId ? `技术标签 #${draft.value.technologyTagId}` : '未匹配') },
  { label: '补充说明', value: draft.value.technologyText || '未填写' },
  { label: '应用场景', value: draft.value.applicationScenario || '未填写' },
  { label: '团队能力', value: draft.value.teamCapabilities || '未填写' },
  { label: '实施周期', value: technologyTimelineLabel(draft.value.timeline) },
  { label: '可用资源', value: draft.value.existingResources || '未填写' },
  { label: '约束条件', value: draft.value.constraints || '未填写' },
])
const readOnlyDetail = computed(() => {
  if (draft.value.taskType === 'case_comparison') return `${draft.value.caseIds.length} 个比较对象 · ${draft.value.comparisonDimensions.length} 个比较维度`
  if (draft.value.taskType === 'case_analysis') return draft.value.caseIds.length ? '已固定一个案例对象' : '未指定案例对象'
  if (draft.value.taskType === 'source_verification') return draft.value.sourceId ? '已指定来源' : '核验当前问题中的结论'
  if (draft.value.taskType === 'technology_assessment') return draft.value.technologyText || '技术方向待补充'
  return selectedTask.value.detail
})

watch(() => props.modelValue, (value) => { draft.value = normalizeTask(value) }, { deep: true })

function selectTask(taskType) {
  if (!allowedTaskTypes.has(taskType)) return
  update({
    taskType,
    caseIds: ['case_analysis', 'case_comparison'].includes(taskType) ? draft.value.caseIds : [],
    comparisonDimensions: taskType === 'case_comparison' ? draft.value.comparisonDimensions : [],
    sourceId: taskType === 'source_verification' ? draft.value.sourceId : '',
    technologyTagId: taskType === 'technology_assessment' ? draft.value.technologyTagId : '',
    technologyText: taskType === 'technology_assessment' ? draft.value.technologyText : '',
    applicationScenario: taskType === 'technology_assessment' ? draft.value.applicationScenario : '',
    teamCapabilities: taskType === 'technology_assessment' ? draft.value.teamCapabilities : '',
    timeline: taskType === 'technology_assessment' ? draft.value.timeline : '',
    existingResources: taskType === 'technology_assessment'
      ? (draft.value.existingResources || String(props.profileResources || '').trim().slice(0, 500))
      : '',
    constraints: taskType === 'technology_assessment' ? draft.value.constraints : '',
  })
}

function updateCaseIds(event) {
  const validIds = new Set(props.caseOptions.map((item) => Number(item?.id)).filter(Number.isSafeInteger))
  const selected = Array.from(event.target.selectedOptions || [])
    .map((option) => Number(option.value))
    .filter((id) => validIds.has(id))
  const max = draft.value.taskType === 'case_analysis' ? 1 : 3
  update({ caseIds: [...new Set(selected)].slice(0, max) })
}

function toggleDimension(dimension, checked) {
  if (!allowedDimensions.has(dimension)) return
  const selected = new Set(draft.value.comparisonDimensions)
  if (checked && selected.size < 3) selected.add(dimension)
  if (!checked) selected.delete(dimension)
  update({ comparisonDimensions: comparisonDimensions.map((item) => item.id).filter((id) => selected.has(id)) })
}

function updateSourceId(value) {
  const sourceId = Number(value)
  const validIds = new Set(props.sourceOptions.map((item) => Number(item?.id)).filter(Number.isSafeInteger))
  update({ sourceId: validIds.has(sourceId) ? sourceId : '' })
}

function updateTechnologyTagId(value) {
  const technologyTagId = Number(value)
  const validIds = new Set(props.technologyOptions.map((item) => Number(item?.id)).filter(Number.isSafeInteger))
  update({ technologyTagId: validIds.has(technologyTagId) ? technologyTagId : '' })
}

function updateField(field, value) {
  const limits = {
    technologyText: 120,
    applicationScenario: 500,
    teamCapabilities: 500,
    timeline: 120,
    existingResources: 500,
    constraints: 800,
  }
  if (Object.hasOwn(limits, field)) update({ [field]: String(value || '').slice(0, limits[field]) })
  if (field === 'outputDepth' && ['concise', 'standard', 'deep'].includes(value)) update({ outputDepth: value })
}

function update(patch) {
  draft.value = normalizeTask({ ...draft.value, ...patch })
  emit('update:modelValue', draft.value)
}

function normalizeTask(value) {
  const taskType = allowedTaskTypes.has(value?.taskType) ? value.taskType : ''
  const caseIds = [...new Set(Array.isArray(value?.caseIds) ? value.caseIds.map(Number).filter(Number.isSafeInteger) : [])].slice(0, 3)
  const dimensions = [...new Set(Array.isArray(value?.comparisonDimensions) ? value.comparisonDimensions.filter((item) => allowedDimensions.has(item)) : [])].slice(0, 3)
  return {
    taskType,
    caseIds: ['case_analysis', 'case_comparison'].includes(taskType) ? caseIds : [],
    comparisonDimensions: taskType === 'case_comparison' ? dimensions : [],
    sourceId: taskType === 'source_verification' && Number.isSafeInteger(Number(value?.sourceId)) ? Number(value.sourceId) : '',
    technologyTagId: taskType === 'technology_assessment' && Number.isSafeInteger(Number(value?.technologyTagId)) && Number(value.technologyTagId) > 0 ? Number(value.technologyTagId) : '',
    technologyText: taskType === 'technology_assessment' ? String(value?.technologyText || '').trim().slice(0, 120) : '',
    applicationScenario: taskType === 'technology_assessment' ? String(value?.applicationScenario || '').trim().slice(0, 500) : '',
    teamCapabilities: taskType === 'technology_assessment' ? String(value?.teamCapabilities || '').trim().slice(0, 500) : '',
    timeline: taskType === 'technology_assessment' ? String(value?.timeline || '').trim().slice(0, 120) : '',
    existingResources: taskType === 'technology_assessment' ? String(value?.existingResources || '').trim().slice(0, 500) : '',
    constraints: taskType === 'technology_assessment' ? String(value?.constraints || '').trim().slice(0, 800) : '',
    outputDepth: ['concise', 'standard', 'deep'].includes(value?.outputDepth) ? value.outputDepth : 'standard',
  }
}
</script>

<style scoped>
.research-task{flex:0 0 auto;max-height:min(54dvh,540px);overflow:auto;padding:15px 24px;border-bottom:1px solid #d0d3ce;background:#fbfbf7;color:#252a25}.research-task>header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.research-task h2{margin:4px 0 0;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:1rem;font-weight:500}.caption{color:#5f665f;font-family:'Bookman Old Style',Georgia,serif;font-size:.63rem;font-weight:700}.research-task>header>small{margin-top:8px;color:#555d55;font-size:.65rem}.task-editor{margin-top:12px}.task-editor summary{display:flex;align-items:center;min-height:40px;cursor:pointer;color:#3c423c;font-size:.76rem;font-weight:700}.task-editor summary span{margin-left:auto;color:#5f665f;font-size:.65rem;font-weight:400}.task-options{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1px;margin-top:8px;border:1px solid #ccd0ca;background:#ccd0ca}.task-options button{display:grid;grid-template-columns:20px minmax(0,1fr);align-items:start;gap:7px;min-height:70px;padding:11px;border:0;background:#fbfbf7;color:#303630;text-align:left}.task-options button.selected{background:#e8ece5;box-shadow:inset 0 0 0 1px #4f6f58}.task-options button:focus-visible,.task-fields :is(input,select,textarea):focus-visible{outline:2px solid #4f6f58;outline-offset:2px}.task-options span{display:grid;gap:3px;min-width:0}.task-options strong{font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:.75rem}.task-options small{overflow-wrap:anywhere;color:#5c635c;font-size:.63rem;line-height:1.45}.task-state{margin:10px 0 0;color:#4f574f;font-size:.7rem}.task-state.is-error{color:#7a3731}.task-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:11px;margin-top:12px}.task-field{display:grid;gap:5px;min-width:0}.task-field-wide{grid-column:1/-1}.task-field>span,.task-field legend{padding:0;color:#4f574f;font-size:.67rem;font-weight:700}.task-field>span small{color:#5f675f;font-weight:400}.task-field :is(input,select,textarea){width:100%;min-width:0;border:1px solid #aeb4ac;border-radius:6px;background:#fff;color:#252a25;font:inherit;font-size:.75rem;line-height:1.5}.task-field :is(input,select){min-height:40px;padding:0 9px}.task-field textarea{min-height:68px;padding:8px 9px;resize:vertical}.task-field select[multiple]{height:auto;padding:4px}.task-field select[multiple] option{padding:5px}.task-field>small{color:#5c635c;font-size:.63rem;line-height:1.45}.task-dimensions{display:flex;flex-wrap:wrap;align-content:flex-start;gap:7px 13px;margin:0;border:0}.task-dimensions legend{width:100%;margin-bottom:2px}.task-dimensions label{display:inline-flex;align-items:center;gap:5px;color:#303630;font-size:.7rem}.task-dimensions input{width:16px;height:16px;margin:0;accent-color:#4f6f58}.output-depth{max-width:220px}.task-summary{display:flex;flex-wrap:wrap;gap:5px 12px;margin-top:11px;color:#4f574f;font-size:.72rem}.task-summary>strong{font-family:'Noto Serif SC',STSong,SimSun,serif}.task-summary>span{color:#5c635c;overflow-wrap:anywhere}.technology-summary{display:grid;grid-template-columns:max-content minmax(0,1fr);width:100%;gap:5px 12px;margin:5px 0 0;padding-top:9px;border-top:1px solid #d9ddd7}.technology-summary dt{color:#5c635c;font-weight:700}.technology-summary dd{min-width:0;margin:0;color:#343a34;overflow-wrap:anywhere;white-space:pre-wrap}@media(max-width:840px){.task-options{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.research-task{max-height:min(56dvh,520px);padding:13px 16px}.research-task>header>small{display:none}.task-editor summary{min-height:44px}.task-editor summary span{display:none}.task-options{grid-template-columns:1fr}.task-options button{min-height:68px}.task-fields{grid-template-columns:1fr}.task-field-wide{grid-column:auto}.task-field :is(input,select,textarea){min-height:44px}.task-dimensions label{min-height:44px}.output-depth{max-width:none}.technology-summary{grid-template-columns:1fr}.technology-summary dd{margin-bottom:5px}}@media (hover: hover) and (pointer: fine){.task-options button:hover{background:#eef0eb}}@media(prefers-reduced-motion:reduce){.task-options button{transition:none}}
@container assistant-inspector (max-width: 540px){.research-task{max-height:none;padding:13px 16px}.research-task>header>small{display:none}.task-editor summary{min-height:44px}.task-editor summary span{display:none}.task-options{grid-template-columns:1fr}.task-options button{min-height:68px}.task-fields{grid-template-columns:1fr}.task-field-wide{grid-column:auto}.task-field :is(input,select,textarea){min-height:44px}.task-dimensions label{min-height:44px}.output-depth{max-width:none}.technology-summary{grid-template-columns:1fr}.technology-summary dd{margin-bottom:5px}}
</style>
