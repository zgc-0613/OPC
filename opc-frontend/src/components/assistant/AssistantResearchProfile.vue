<template>
  <section class="research-profile" :class="{ 'is-readonly': !editable }" aria-labelledby="research-profile-title">
    <header>
      <div>
        <span class="caption">RESEARCH BOUNDARY</span>
        <h2 id="research-profile-title">{{ editable ? '确定本次研究边界' : '本次研究条件' }}</h2>
      </div>
      <button v-if="!editable" class="secondary-command" type="button" aria-label="基于当前研究条件新建研究" :disabled="forking" @click="emit('fork')"><CopyPlus :size="15" />{{ forking ? '正在创建分支' : '基于这些条件新建研究' }}</button>
    </header>

    <div v-if="!editable" class="profile-summary">
      <span v-for="item in summary" :key="item.label"><small>{{ item.label }}</small>{{ item.value }}</span>
    </div>

    <details v-else :open="profileStartsOpen" class="profile-editor">
      <summary><SlidersHorizontal :size="17" />研究画像 <span>可在发送第一条问题前修改</span></summary>
      <div class="profile-fields">
        <label class="field-venture"><span>创业类型</span><select :value="modelValue.ventureType" @change="set('ventureType', $event.target.value)">
          <option value="solo_company">一人公司</option><option value="individual_business">个体经营</option>
          <option value="small_team">小型创业团队</option><option value="exploring">尚在探索</option>
        </select></label>
        <label class="field-region"><span>所在地区</span><select :value="modelValue.regionId" @change="set('regionId', $event.target.value)">
          <option value="">暂未确定</option><option v-for="region in regions" :key="region.id" :value="String(region.id)">{{ region.name }}</option>
        </select></label>
        <label class="field-industry"><span>目标行业</span>
          <div class="industry-combobox" @focusout="handleIndustryFocusOut">
            <div class="industry-combobox-control" @click="openIndustryOptions">
              <input
                ref="industryInput"
                :value="industryQuery"
                maxlength="80"
                placeholder="选择或输入行业"
                role="combobox"
                aria-autocomplete="list"
                aria-haspopup="listbox"
                aria-controls="assistant-industry-listbox"
                :aria-expanded="industryOpen"
                :aria-activedescendant="activeIndustryId || undefined"
                @click.stop="openIndustryOptions"
                @input="updateIndustry($event.target.value)"
                @keydown="handleIndustryKeydown"
              />
              <button class="industry-combobox-toggle" type="button" :aria-label="industryOpen ? '收起行业选项' : '展开行业选项'" @click.stop="toggleIndustryOptions">
                <ChevronDown :size="16" aria-hidden="true" />
              </button>
            </div>
            <div v-if="industryOpen" id="assistant-industry-listbox" ref="industryListbox" class="industry-listbox" role="listbox" aria-label="标准行业">
              <button
                v-for="(item, index) in filteredIndustries"
                :id="`assistant-industry-option-${item.tagId}`"
                :key="item.tagId"
                type="button"
                role="option"
                tabindex="-1"
                :aria-selected="String(modelValue.industryTagId || '') === String(item.tagId)"
                :class="{ active: index === activeIndustryIndex }"
                @mousedown.prevent
                @click="selectIndustry(item)"
              >{{ item.name }}</button>
              <p v-if="!filteredIndustries.length" class="industry-empty" role="status">没有本地匹配，将使用 AI 解析该行业</p>
            </div>
          </div>
        </label>
        <label class="field-stage"><span>当前阶段</span><select :value="modelValue.stage" @change="set('stage', $event.target.value)">
          <option value="idea">想法形成</option><option value="validation">需求验证</option><option value="early_operation">早期运营</option><option value="growth">增长阶段</option>
        </select></label>
        <label class="field-budget"><span>可投入预算</span><select :value="modelValue.budgetRange" @change="set('budgetRange', $event.target.value)">
          <option value="under_100k">10 万元以内</option><option value="100k_500k">10-50 万元</option><option value="500k_1m">50-100 万元</option><option value="over_1m">100 万元以上</option><option value="undecided">尚未确定</option>
        </select></label>
        <label class="wide field-goal"><span>研究目标</span><input :value="modelValue.goal" maxlength="200" placeholder="例如：验证首批付费客户" @input="set('goal', $event.target.value)" /></label>
        <label class="wide field-resources"><span>已有资源 <small>可选</small></span><textarea :value="modelValue.existingResources" rows="2" maxlength="300" placeholder="产品原型、客户线索或行业经验" @input="set('existingResources', $event.target.value)"></textarea></label>
      </div>
      <div v-if="industryResolutionLoading || industryResolutionError || industrySuggestion || industryResolutionRejected" class="industry-resolution" :role="industryResolutionError ? 'alert' : 'status'">
        <p v-if="industryResolutionLoading">正在匹配标准行业标签…</p>
        <p v-else-if="industryResolutionError">{{ industryResolutionError }}</p>
        <template v-else-if="industrySuggestion">
          <p>建议匹配“{{ industrySuggestion.name }}”，是否采用为目标行业？</p>
          <div class="industry-resolution-actions">
            <button type="button" @click="emit('confirm-industry')">采用“{{ industrySuggestion.name }}”</button>
            <button type="button" @click="emit('reject-industry')">保留原始输入</button>
          </div>
        </template>
        <p v-else class="industry-resolution-rejected">{{ industryResolutionRejected }}</p>
      </div>
    </details>

    <div class="profile-status" aria-live="polite">
      <span :class="{ ready: agentReady }"><i></i><strong>{{ agentReady ? '智能体可用' : '智能体暂不可用' }}</strong><small>{{ providerLabel }}</small></span>
      <span :class="readinessTone"><i></i><strong>{{ readinessTitle }}</strong><small>{{ readinessDetail }}</small></span>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ChevronDown, CopyPlus, SlidersHorizontal } from 'lucide-vue-next'

const props = defineProps({
  modelValue: { type: Object, required: true }, editable: Boolean,
  regions: { type: Array, default: () => [] }, industries: { type: Array, default: () => [] },
  readiness: { type: Object, default: null }, readinessLoading: Boolean,
  readinessError: { type: String, default: '' }, agentReady: Boolean,
  industryResolutionLoading: Boolean,
  industryResolutionError: { type: String, default: '' },
  industryResolutionRejected: { type: String, default: '' },
  industrySuggestion: { type: Object, default: null },
  providerLabel: { type: String, default: '等待管理员配置' },
  forking: Boolean,
})
const emit = defineEmits(['update:modelValue', 'fork', 'confirm-industry', 'reject-industry'])
const profileStartsOpen = !globalThis.matchMedia?.('(max-width: 720px)').matches
const industryInput = ref(null)
const industryListbox = ref(null)
const industryQuery = ref(props.modelValue.industry || '')
const industryOpen = ref(false)
const activeIndustryIndex = ref(-1)
const stageNames = { idea: '想法形成', validation: '需求验证', early_operation: '早期运营', growth: '增长阶段' }
const ventureNames = { solo_company: '一人公司', individual_business: '个体经营', small_team: '小型团队', exploring: '尚在探索' }
const budgetNames = { under_100k: '10 万元以内', '100k_500k': '10-50 万元', '500k_1m': '50-100 万元', over_1m: '100 万元以上', undecided: '尚未确定' }
const summary = computed(() => [
  { label: '类型', value: ventureNames[props.modelValue.ventureType] || '未设置' },
  { label: '地区', value: regionName(props.modelValue.regionId) },
  { label: '行业', value: props.modelValue.industry || '未设置' },
  { label: '阶段', value: stageNames[props.modelValue.stage] || '未设置' },
  { label: '预算', value: budgetNames[props.modelValue.budgetRange] || '未设置' },
])
const readinessTitle = computed(() => props.readinessLoading ? '正在核验证据' : ({ sufficient: '证据充分', partial: '证据有限，可继续', insufficient: '当前证据不足' }[props.readiness?.readinessStatus] || '等待证据预检'))
const readinessTone = computed(() => ({ ready: props.readiness?.readinessStatus === 'sufficient', partial: props.readiness?.readinessStatus === 'partial', error: Boolean(props.readinessError) }))
const readinessDetail = computed(() => {
  if (props.readinessError) return props.readinessError
  if (!props.readiness) return '选择地区与行业后自动核验'
  return `案例 ${props.readiness.verifiedCaseCount || 0} · 选入政策 ${props.readiness.selectedPolicyCount ?? props.readiness.verifiedPolicyCount ?? 0} · 直接行业政策 ${props.readiness.directIndustryPolicyCount || 0} · 通用政策 ${props.readiness.generalPolicyCount || 0} · 来源 ${props.readiness.verifiedSourceCount || 0}`
})
const filteredIndustries = computed(() => {
  const query = normalizeIndustry(industryQuery.value)
  if (!query) return props.industries
  return props.industries.filter((item) => normalizeIndustry(item.name).includes(query))
})
const activeIndustryId = computed(() => {
  const item = filteredIndustries.value[activeIndustryIndex.value]
  return item ? `assistant-industry-option-${item.tagId}` : ''
})

watch(filteredIndustries, () => { activeIndustryIndex.value = -1 })
watch(() => props.modelValue.industry, (value) => {
  if (value !== industryQuery.value) industryQuery.value = value || ''
})

function set(field, value) { emit('update:modelValue', { ...props.modelValue, [field]: value }) }
function updateIndustry(value) {
  industryQuery.value = value
  const industry = value.trim()
  const match = props.industries.find((item) => item.name?.toLocaleLowerCase() === industry.toLocaleLowerCase())
  emit('update:modelValue', { ...props.modelValue, industry, industryTagId: match ? String(match.tagId) : '' })
  industryOpen.value = true
}
async function openIndustryOptions() {
  industryOpen.value = true
  await nextTick()
  industryInput.value?.focus()
}
function toggleIndustryOptions() {
  if (industryOpen.value) industryOpen.value = false
  else openIndustryOptions()
}
function selectIndustry(item) {
  industryQuery.value = item.name
  emit('update:modelValue', { ...props.modelValue, industry: item.name, industryTagId: String(item.tagId) })
  industryOpen.value = false
  activeIndustryIndex.value = -1
  nextTick(() => industryInput.value?.focus())
}
async function handleIndustryKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    industryOpen.value = false
    activeIndustryIndex.value = -1
    return
  }
  if (event.key === 'Enter' && industryOpen.value && activeIndustryIndex.value >= 0) {
    event.preventDefault()
    selectIndustry(filteredIndustries.value[activeIndustryIndex.value])
    return
  }
  if (!['ArrowDown', 'ArrowUp'].includes(event.key)) return
  event.preventDefault()
  industryOpen.value = true
  const count = filteredIndustries.value.length
  if (!count) return
  if (event.key === 'ArrowDown') activeIndustryIndex.value = (activeIndustryIndex.value + 1) % count
  else activeIndustryIndex.value = activeIndustryIndex.value <= 0 ? count - 1 : activeIndustryIndex.value - 1
  await nextTick()
  const activeOption = activeIndustryId.value
    ? industryListbox.value?.querySelector(`#${activeIndustryId.value}`)
    : null
  if (typeof activeOption?.scrollIntoView === 'function') {
    activeOption.scrollIntoView({ block: 'nearest' })
  }
}
function handleIndustryFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) industryOpen.value = false
}
function normalizeIndustry(value) { return String(value || '').trim().toLocaleLowerCase() }
function regionName(id) { return props.regions.find((item) => String(item.id) === String(id))?.name || '未设置' }
</script>

<style scoped>
.research-profile {
  --assistant-field-border: #c1c6bf;
  --assistant-field-bg: #fdfdf9;
  --assistant-field-color: #252a25;
  --assistant-field-focus: #606860;
  container-type: inline-size;
  container-name: research-profile;
  max-height: min(48dvh, 420px);
  overflow: auto;
  scrollbar-gutter: stable;
  padding: 18px 24px;
  border-bottom: 1px solid #d0d3ce;
  background: #f6f5ef;
}
.research-profile > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
.research-profile h2 { margin: 4px 0 0; font-family: 'Noto Serif SC', STSong, SimSun, serif; font-size: 1rem; font-weight: 500; }
.caption { color: #5f665f; font-family: 'Bookman Old Style', Georgia, serif; font-size: .63rem; font-weight: 700; }
.secondary-command { display: flex; align-items: center; gap: 6px; min-height: 40px; padding: 0 11px; border: 1px solid #b9bfb8; border-radius: 3px; background: #fbfbf7; color: #303630; }
.secondary-command:focus-visible { border-color: #747b74; background: #eceee8; }
.secondary-command:focus-visible { outline: 2px solid rgba(74,82,74,.34); outline-offset: 2px; }
.secondary-command:active { background: #e1e4dc; }
.secondary-command:disabled { cursor: wait; opacity: .62; }
.profile-summary { display: flex; flex-wrap: wrap; gap: 8px 20px; margin-top: 13px; }
.profile-summary span { display: grid; gap: 2px; color: #303630; font-size: .78rem; }
.profile-summary small { color: #5f665f; font-size: .62rem; }
.profile-editor { margin-top: 13px; }
.profile-editor summary { display: flex; align-items: center; gap: 7px; min-height: 40px; cursor: pointer; color: #3c423c; font-size: .76rem; font-weight: 700; }
.profile-editor summary span { margin-left: auto; color: #5f665f; font-size: .65rem; font-weight: 400; }
.profile-fields { display: grid; grid-template-columns: repeat(6,minmax(0,1fr)); gap: 11px; margin-top: 9px; }
.profile-fields label { display: grid; gap: 5px; min-width: 0; }
.profile-fields :is(.field-venture,.field-region,.field-industry){grid-column:span 2}
.profile-fields :is(.field-stage,.field-budget,.field-goal,.field-resources){grid-column:span 3}
.profile-fields label > span { color: #555b55; font-size: .68rem; font-weight: 700; }
.profile-fields :is(input, select, textarea),
.industry-combobox-control {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  border: 1px solid var(--assistant-field-border);
  border-radius: 3px;
  background: var(--assistant-field-bg);
  color: var(--assistant-field-color);
}
.profile-fields :is(input, select) { height: 40px; padding: 0 9px; }
.profile-fields textarea { padding: 8px 9px; resize: vertical; }
.profile-fields :is(input, select, textarea):focus-visible {
  border-color: var(--assistant-field-focus);
  outline: 2px solid rgba(96, 104, 96, .28);
  outline-offset: 1px;
}
.industry-combobox { position: relative; width: 100%; min-width: 0; max-width: 100%; }
.industry-combobox-control { display: grid; grid-template-columns: minmax(0,1fr) 40px; height: 40px; overflow: hidden; }
.industry-combobox-control:focus-within { border-color: var(--assistant-field-focus); box-shadow: 0 0 0 2px rgba(96, 104, 96, .28); }
.industry-combobox-control input {
  height: 100%;
  padding: 0 4px 0 9px;
  overflow: hidden;
  border: 0;
  border-radius: 0;
  background: transparent;
  outline: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.industry-combobox-toggle { display: grid; width: 40px; height: 100%; padding: 0; place-items: center; border: 0; background: transparent; color: #5c635c; }
.industry-listbox {
  position: absolute;
  z-index: 30;
  top: calc(100% + 4px);
  left: 0;
  display: grid;
  width: 100%;
  max-width: 100%;
  max-height: min(240px,40vh);
  overflow: auto;
  border: 1px solid var(--assistant-field-border);
  border-radius: 3px;
  background: var(--assistant-field-bg);
  box-shadow: 0 6px 18px rgba(30, 34, 30, .12);
}
.industry-listbox > [role=option] { min-height: 40px; padding: 8px 10px; border: 0; background: transparent; color: var(--assistant-field-color); text-align: left; }
.industry-listbox > [role=option].active,
.industry-listbox > [role=option]:focus-visible { background: #e7e9e4; }
@media (hover: hover) and (pointer: fine) {
  .industry-listbox > [role=option]:hover { background: #e7e9e4; }
}
.industry-listbox > [role=option][aria-selected=true] { font-weight: 700; }
.industry-empty { margin: 0; padding: 12px; color: #5f665f; font-size: .69rem; line-height: 1.5; }
.industry-resolution { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 11px; padding-top: 10px; border-top: 1px solid #d4d7d1; color: #555b55; font-size: .72rem; }
.industry-resolution p { margin: 0; }
.industry-resolution-rejected { color: #6d674e; }
.industry-resolution-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.industry-resolution-actions button { min-height: 38px; padding: 0 10px; border: 1px solid #b8beb7; background: #fbfbf7; color: #303630; }
.industry-resolution-actions button:first-child { border-color: #303630; background: #303630; color: #fff; }
.profile-status { display: flex; flex-wrap: wrap; gap: 8px 22px; margin-top: 13px; padding-top: 11px; border-top: 1px solid #d4d7d1; }
.profile-status > span { display: grid; grid-template-columns: 9px auto; column-gap: 7px; align-items: center; }
.profile-status i { grid-row: 1/3; width: 7px; height: 7px; border-radius: 50%; background: #8b4039; }
.profile-status .ready i { background: #3f684a; }
.profile-status .partial i { background: #806731; }
.profile-status strong { font-size: .7rem; }
.profile-status small { color: #5f665f; font-size: .62rem; }
@container research-profile (min-width:521px) and (max-width:680px) {
  .profile-fields { grid-template-columns: 1fr 1fr; }
  .profile-fields :is(.field-venture,.field-region,.field-stage,.field-budget,.field-goal,.field-resources) { grid-column: span 1; }
  .profile-fields .field-industry { grid-column: span 2; }
  .industry-resolution { align-items: flex-start; flex-direction: column; }
}
@container research-profile (max-width:520px) {
  .profile-fields { grid-template-columns: 1fr; }
  .profile-fields > label { grid-column: span 1; }
  .industry-resolution-actions { display: grid; width: 100%; }
  .industry-resolution-actions button { min-height: 44px; }
  .industry-listbox { max-height: min(200px,32vh); }
}
@media(max-width:720px) {
  .research-profile { padding: 14px 16px; }
  .research-profile > header { align-items: center; }
  .secondary-command { width: 44px; padding: 0; font-size: 0; }
  .secondary-command svg { margin: auto; }
  .profile-editor summary { min-height: 44px; }
  .profile-editor summary span { display: none; }
  .profile-fields :is(input, select, textarea), .industry-combobox-control { min-height: 44px; }
  .industry-listbox>[role=option]{min-height:44px}
  .profile-status { display: grid; gap: 8px; }
}
@media(min-width:641px) and (max-width:1023px) and (pointer:coarse) {
  .secondary-command, .profile-editor summary { min-height: 44px; }
  .profile-fields :is(input, select, textarea), .industry-combobox-control { min-height: 44px; }
  .industry-combobox-control { grid-template-columns: minmax(0,1fr) 44px; }
  .industry-combobox-toggle { width: 44px; min-width: 44px; }
  .industry-listbox > [role=option], .industry-resolution-actions button { min-height: 44px; }
}
@media(prefers-reduced-motion:reduce) {
  .industry-listbox, .industry-combobox-toggle { scroll-behavior: auto; transition: none; }
}
</style>
<style scoped>
.research-profile > header { display: grid; grid-template-columns: minmax(0, 1fr) auto; }
.secondary-command {
  min-width: 0;
  justify-content: center;
  white-space: nowrap;
  font-size: .73rem;
  line-height: 1;
  border-color: rgba(101, 109, 100, .38);
  background: rgba(251, 251, 247, .78);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .84), inset 0 -1px 0 rgba(41, 47, 40, .08), 0 1px 1px rgba(32, 37, 31, .06);
  backdrop-filter: blur(12px) saturate(1.08);
  -webkit-backdrop-filter: blur(12px) saturate(1.08);
  transition: transform var(--duration-fast) var(--ease-out), background-color var(--duration-fast) ease, border-color var(--duration-fast) ease, box-shadow var(--duration-fast) ease;
}
@container research-profile (max-width:540px) {
  .research-profile > header { gap: 10px; }
  .secondary-command { gap: 4px; padding: 0 8px; font-size: .67rem; }
}
@media (max-width:720px) {
  .secondary-command { width: 44px; padding: 0; font-size: 0; }
  .secondary-command svg { margin: auto; }
}
@media (hover: hover) and (pointer: fine) {
  .secondary-command:hover { transform: translateY(-1px); border-color: rgba(82, 91, 81, .68); background: rgba(255, 255, 252, .88); box-shadow: inset 0 1px 0 rgba(255, 255, 255, .94), inset 0 -1px 0 rgba(41, 47, 40, .08), 0 4px 12px rgba(32, 37, 31, .1); }
}
.secondary-command:active { transform: scale(.975); }
@media (prefers-reduced-motion: reduce) { .secondary-command { transition: none; transform: none; } }
</style>
