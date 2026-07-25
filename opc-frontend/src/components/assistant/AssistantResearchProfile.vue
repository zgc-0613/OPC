<template>
  <section class="research-profile" :class="{ 'is-readonly': !editable }" aria-labelledby="research-profile-title">
    <header>
      <div>
        <span class="caption">RESEARCH BOUNDARY</span>
        <h2 id="research-profile-title">{{ editable ? '确定本次研究边界' : '本次研究条件' }}</h2>
      </div>
      <button v-if="!editable" class="text-command" type="button" @click="emit('fork')"><CopyPlus :size="15" />基于这些条件新建研究</button>
    </header>

    <div v-if="!editable" class="profile-summary">
      <span v-for="item in summary" :key="item.label"><small>{{ item.label }}</small>{{ item.value }}</span>
    </div>

    <details v-else open class="profile-editor">
      <summary><SlidersHorizontal :size="17" />研究画像 <span>可在发送第一条问题前修改</span></summary>
      <div class="profile-fields">
        <label><span>创业类型</span><select :value="modelValue.ventureType" @change="set('ventureType', $event.target.value)">
          <option value="solo_company">一人公司</option><option value="individual_business">个体经营</option>
          <option value="small_team">小型创业团队</option><option value="exploring">尚在探索</option>
        </select></label>
        <label><span>所在地区</span><select :value="modelValue.regionId" @change="set('regionId', $event.target.value)">
          <option value="">暂未确定</option><option v-for="region in regions" :key="region.id" :value="String(region.id)">{{ region.name }}</option>
        </select></label>
        <label><span>目标行业</span><input :value="modelValue.industry" maxlength="80" list="assistant-industry-options" placeholder="选择或输入行业" @input="updateIndustry($event.target.value)" />
          <datalist id="assistant-industry-options"><option v-for="item in industries" :key="item.tagId" :value="item.name" /></datalist>
        </label>
        <label><span>当前阶段</span><select :value="modelValue.stage" @change="set('stage', $event.target.value)">
          <option value="idea">想法形成</option><option value="validation">需求验证</option><option value="early_operation">早期运营</option><option value="growth">增长阶段</option>
        </select></label>
        <label><span>可投入预算</span><select :value="modelValue.budgetRange" @change="set('budgetRange', $event.target.value)">
          <option value="under_100k">10 万元以内</option><option value="100k_500k">10-50 万元</option><option value="500k_1m">50-100 万元</option><option value="over_1m">100 万元以上</option><option value="undecided">尚未确定</option>
        </select></label>
        <label class="wide"><span>研究目标</span><input :value="modelValue.goal" maxlength="200" placeholder="例如：验证首批付费客户" @input="set('goal', $event.target.value)" /></label>
        <label class="wide"><span>已有资源 <small>可选</small></span><textarea :value="modelValue.existingResources" rows="2" maxlength="300" placeholder="产品原型、客户线索或行业经验" @input="set('existingResources', $event.target.value)"></textarea></label>
      </div>
    </details>

    <div class="profile-status" aria-live="polite">
      <span :class="{ ready: agentReady }"><i></i><strong>{{ agentReady ? '智能体可用' : '智能体暂不可用' }}</strong><small>{{ providerLabel }}</small></span>
      <span :class="readinessTone"><i></i><strong>{{ readinessTitle }}</strong><small>{{ readinessDetail }}</small></span>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { CopyPlus, SlidersHorizontal } from 'lucide-vue-next'

const props = defineProps({
  modelValue: { type: Object, required: true }, editable: Boolean,
  regions: { type: Array, default: () => [] }, industries: { type: Array, default: () => [] },
  readiness: { type: Object, default: null }, readinessLoading: Boolean,
  readinessError: { type: String, default: '' }, agentReady: Boolean,
  providerLabel: { type: String, default: '等待管理员配置' },
})
const emit = defineEmits(['update:modelValue', 'fork'])
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

function set(field, value) { emit('update:modelValue', { ...props.modelValue, [field]: value }) }
function updateIndustry(value) {
  const industry = value.trim()
  const match = props.industries.find((item) => item.name?.toLocaleLowerCase() === industry.toLocaleLowerCase())
  emit('update:modelValue', { ...props.modelValue, industry, industryTagId: match ? String(match.tagId) : '' })
}
function regionName(id) { return props.regions.find((item) => String(item.id) === String(id))?.name || '未设置' }
</script>

<style scoped>
.research-profile{padding:18px 24px;border-bottom:1px solid #d0d3ce;background:#f6f5ef}.research-profile>header{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}.research-profile h2{margin:4px 0 0;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:1rem;font-weight:500}.caption{color:#747a73;font-family:'Bookman Old Style',Georgia,serif;font-size:.63rem;font-weight:700}.text-command{display:flex;align-items:center;gap:6px;min-height:40px;border:0;background:transparent;color:#303630}.profile-summary{display:flex;flex-wrap:wrap;gap:8px 20px;margin-top:13px}.profile-summary span{display:grid;gap:2px;color:#303630;font-size:.78rem}.profile-summary small{color:#777d76;font-size:.62rem}.profile-editor{margin-top:13px}.profile-editor summary{display:flex;align-items:center;gap:7px;min-height:40px;cursor:pointer;color:#3c423c;font-size:.76rem;font-weight:700}.profile-editor summary span{margin-left:auto;color:#777d76;font-size:.65rem;font-weight:400}.profile-fields{display:grid;grid-template-columns:repeat(5,minmax(120px,1fr));gap:11px;margin-top:9px}.profile-fields label{display:grid;gap:5px;min-width:0}.profile-fields label>span{color:#555b55;font-size:.68rem;font-weight:700}.profile-fields .wide{grid-column:span 2}.profile-fields :is(input,select,textarea){width:100%;min-width:0;border:1px solid #c1c6bf;border-radius:3px;background:#fdfdf9;color:#252a25}.profile-fields :is(input,select){height:40px;padding:0 9px}.profile-fields textarea{padding:8px 9px;resize:vertical}.profile-status{display:flex;flex-wrap:wrap;gap:8px 22px;margin-top:13px;padding-top:11px;border-top:1px solid #d4d7d1}.profile-status>span{display:grid;grid-template-columns:9px auto;column-gap:7px;align-items:center}.profile-status i{grid-row:1/3;width:7px;height:7px;border-radius:50%;background:#8b4039}.profile-status .ready i{background:#3f684a}.profile-status .partial i{background:#806731}.profile-status strong{font-size:.7rem}.profile-status small{color:#777d76;font-size:.62rem}@media(max-width:1100px){.profile-fields{grid-template-columns:repeat(3,minmax(120px,1fr))}}@media(max-width:720px){.research-profile{padding:14px 16px}.research-profile>header{align-items:center}.text-command{width:44px;padding:0;font-size:0}.text-command svg{margin:auto}.profile-fields{grid-template-columns:1fr 1fr}.profile-fields .wide{grid-column:1/-1}.profile-editor summary{min-height:44px}.profile-editor summary span{display:none}.profile-fields :is(input,select,textarea){min-height:44px}.profile-status{display:grid;gap:8px}}@media(max-width:440px){.profile-fields{grid-template-columns:1fr}}
</style>
