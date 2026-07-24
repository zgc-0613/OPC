<template>
  <form class="evidence-editor" @submit.prevent="submit">
    <header class="evidence-editor__header">
      <div>
        <span class="caption">CORRECT IN PLACE</span>
        <h3>修正{{ typeLabel }}资料</h3>
        <p>保存后会重新计算审核条件；已核验资料的证据状态会自动回到待审核。</p>
      </div>
      <button type="button" class="button button-ghost" :disabled="saving" @click="cancel">取消</button>
    </header>

    <section v-for="section in sections" :key="section.title" class="evidence-editor__section">
      <div class="evidence-editor__section-title">
        <strong>{{ section.title }}</strong>
        <span>{{ section.description }}</span>
      </div>
      <div class="evidence-editor__fields">
        <label v-for="field in section.fields" :key="field.key" :class="{ 'is-wide': field.wide }">
          <span>{{ field.label }}<b v-if="field.required"> *</b></span>
          <textarea
            v-if="field.kind === 'textarea'"
            v-model="form[field.key]"
            :rows="field.rows || 5"
            :placeholder="field.placeholder || ''"
            @input="markDirty"
          ></textarea>
          <select v-else-if="field.kind === 'select'" v-model="form[field.key]" @change="markDirty">
            <option v-for="option in optionsFor(field)" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <input
            v-else
            v-model="form[field.key]"
            :type="field.kind || 'text'"
            :placeholder="field.placeholder || ''"
            @input="markDirty"
          />
          <small v-if="errors[field.key]" class="evidence-editor__error">{{ errors[field.key] }}</small>
        </label>
      </div>
    </section>

    <div v-if="error" class="evidence-editor__error-banner" role="alert">
      <p class="error">{{ error }}</p>
      <button v-if="isConflict" type="button" class="button button-secondary" @click="$emit('reload')">重新加载资料</button>
    </div>
    <footer class="evidence-editor__footer">
      <span>{{ dirty ? '有尚未保存的修改' : '尚未修改' }}</span>
      <button class="button" type="submit" :disabled="saving || !dirty">{{ saving ? '正在保存...' : '保存并重新检查' }}</button>
    </footer>
  </form>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { buildEvidenceEditPayload, hydrateEvidenceEditForm } from '@/utils/evidenceWorkbench'

const props = defineProps({
  detail: { type: Object, required: true },
  regions: { type: Array, default: () => [] },
  sources: { type: Array, default: () => [] },
  saving: Boolean,
  error: { type: String, default: '' },
})
const emit = defineEmits(['save', 'cancel', 'dirty', 'reload'])
const form = reactive({})
const errors = reactive({})
const dirty = ref(false)
const isConflict = computed(() => props.error.includes('其他操作') || props.error.includes('重新加载'))

const typeLabel = computed(() => ({ case: '案例', policy: '政策', source: '来源' }[props.detail.itemType] || ''))
const commonStatusOptions = [
  { value: 'draft', label: '草稿' },
  { value: 'pending', label: '待发布' },
  { value: 'published', label: '已发布' },
  { value: 'archived', label: '已归档' },
]

const definitions = {
  source: [
    { title: '来源标识', description: '标题、发布机构和原文地址共同决定来源可信度。', fields: [
      { key: 'title', label: '来源标题', required: true, wide: true },
      { key: 'sourceType', label: '来源类型', required: true },
      { key: 'publisher', label: '发布机构', required: true },
      { key: 'url', label: '原文链接', kind: 'url', wide: true },
      { key: 'localFile', label: '本地文件', wide: true },
    ] },
    { title: '发布与记录', description: '只有已发布且链接完整的来源才能被核验。', fields: [
      { key: 'accessedAt', label: '访问日期', kind: 'date', required: true },
      { key: 'status', label: '发布状态', kind: 'select', options: commonStatusOptions, required: true },
      { key: 'notes', label: '资料备注', kind: 'textarea', rows: 5, wide: true },
    ] },
  ],
  case: [
    { title: '基本信息', description: '核对案例主体、地区、行业和来源关系。', fields: [
      { key: 'title', label: '案例标题', required: true, wide: true },
      { key: 'regionId', label: '地区', kind: 'select', optionType: 'region', required: true },
      { key: 'category', label: '行业领域', required: true },
      { key: 'actorName', label: '主体名称' },
      { key: 'sourceId', label: '关联来源', kind: 'select', optionType: 'source', required: true },
      { key: 'tags', label: '标签', wide: true },
    ] },
    { title: '证据内容', description: '摘要和经营信息会直接进入智能体证据上下文。', fields: [
      { key: 'summary', label: '案例摘要', kind: 'textarea', rows: 6, required: true, wide: true },
      { key: 'businessModel', label: '商业模式', kind: 'textarea', rows: 4, wide: true },
      { key: 'aiTools', label: '技术与工具', kind: 'textarea', rows: 4, wide: true },
      { key: 'outcome', label: '实施结果', kind: 'textarea', rows: 4, wide: true },
    ] },
    { title: '溯源与发布', description: '原文地址、访问日期和发布状态用于复核。', fields: [
      { key: 'originalUrl', label: '原文链接', kind: 'url', wide: true },
      { key: 'localFile', label: '本地文件', wide: true },
      { key: 'accessedAt', label: '访问日期', kind: 'date', required: true },
      { key: 'status', label: '发布状态', kind: 'select', options: commonStatusOptions, required: true },
      { key: 'reviewer', label: '资料复核人' },
    ] },
  ],
  policy: [
    { title: '政策标识', description: '核对政策名称、发文机关、文号、地区和来源。', fields: [
      { key: 'title', label: '政策标题', required: true, wide: true },
      { key: 'regionId', label: '地区', kind: 'select', optionType: 'region', required: true },
      { key: 'issuingBody', label: '发文机关', required: true },
      { key: 'documentNo', label: '文号' },
      { key: 'sourceId', label: '关联来源', kind: 'select', optionType: 'source', required: true },
      { key: 'policyLevel', label: '政策层级', required: true },
      { key: 'policyType', label: '政策类型', required: true },
      { key: 'tags', label: '标签', wide: true },
    ] },
    { title: '政策内容', description: '摘要、要点和支持措施会直接参与证据分析。', fields: [
      { key: 'summary', label: '政策摘要', kind: 'textarea', rows: 6, required: true, wide: true },
      { key: 'keyPoints', label: '关键要点', kind: 'textarea', rows: 5, wide: true },
      { key: 'supportMeasures', label: '支持措施', kind: 'textarea', rows: 5, wide: true },
      { key: 'validPeriod', label: '有效期限', wide: true },
    ] },
    { title: '时间与溯源', description: '发布日期、原文和证据地址用于复核真实性。', fields: [
      { key: 'publishDate', label: '发布日期', kind: 'date' },
      { key: 'effectiveDate', label: '生效日期', kind: 'date' },
      { key: 'originalUrl', label: '原文链接', kind: 'url', wide: true },
      { key: 'evidenceUrl', label: '证据链接', kind: 'url', wide: true },
      { key: 'localFile', label: '本地文件', wide: true },
      { key: 'accessedAt', label: '访问日期', kind: 'date', required: true },
      { key: 'status', label: '发布状态', kind: 'select', options: commonStatusOptions, required: true },
      { key: 'reviewer', label: '资料复核人' },
    ] },
  ],
}

const sections = computed(() => definitions[props.detail.itemType] || [])

watch(() => props.detail, (detail) => {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, hydrateEvidenceEditForm(detail.content))
  dirty.value = false
  emit('dirty', false)
}, { immediate: true })

function optionsFor(field) {
  if (field.optionType === 'region') return props.regions.map((item) => ({ value: item.id, label: item.name }))
  if (field.optionType === 'source') return props.sources.map((item) => ({ value: item.id, label: item.title }))
  return field.options || []
}

function markDirty() {
  dirty.value = true
  emit('dirty', true)
}

function validate() {
  Object.keys(errors).forEach((key) => delete errors[key])
  for (const section of sections.value) {
    for (const field of section.fields) {
      if (field.required && (form[field.key] === null || form[field.key] === undefined || String(form[field.key]).trim() === '')) {
        errors[field.key] = `请填写${field.label}`
      }
    }
  }
  return Object.keys(errors).length === 0
}

function submit() {
  if (!validate()) return
  emit('save', buildEvidenceEditPayload(props.detail.itemType, form))
}

function cancel() {
  if (dirty.value && !window.confirm('当前修改尚未保存，确认放弃吗？')) return
  emit('cancel')
}

function beforeUnload(event) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<style scoped>
.evidence-editor { display: grid; gap: 0; min-height: 100%; background: #fbfbf8; }
.evidence-editor__error-banner { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 18px 32px 0; padding: 12px 14px; border: 1px solid #d7b8b3; background: #f8eeeb; }.evidence-editor__error-banner p { margin: 0; }.evidence-editor__error-banner .button { flex: 0 0 auto; }
.evidence-editor__header { display: flex; align-items: start; justify-content: space-between; gap: 24px; padding: 28px 32px; border-bottom: 1px solid #d4d8d2; }.evidence-editor__header h3 { margin: 7px 0 6px; color: #181a18; font-family: 'Noto Serif SC', STSong, SimSun, serif; font-size: 1.55rem; font-weight: 500; }.evidence-editor__header p { margin: 0; color: #626963; font-size: .84rem; line-height: 1.6; }
.evidence-editor__section { display: grid; grid-template-columns: minmax(180px, .36fr) minmax(0, 1fr); gap: 30px; padding: 28px 32px; border-bottom: 1px solid #e0e3de; }.evidence-editor__section-title { display: grid; align-content: start; gap: 7px; }.evidence-editor__section-title strong { color: #252925; font-family: 'Noto Serif SC', STSong, SimSun, serif; }.evidence-editor__section-title span { color: #747b75; font-size: .76rem; line-height: 1.6; }
.evidence-editor__fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }.evidence-editor label { display: grid; align-content: start; gap: 7px; }.evidence-editor label.is-wide { grid-column: 1 / -1; }.evidence-editor label > span { color: #404640; font-size: .76rem; font-weight: 700; }.evidence-editor label b { color: #7d342e; }.evidence-editor input, .evidence-editor select, .evidence-editor textarea { width: 100%; border: 1px solid #cbd0ca; border-radius: 2px; background: #fff; color: #1e211e; }.evidence-editor textarea { resize: vertical; line-height: 1.65; }.evidence-editor__error { color: #80372f; font-size: .72rem; }
.evidence-editor__footer { position: sticky; bottom: 0; z-index: 3; display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 16px 32px; border-top: 1px solid #c9cec8; background: rgba(251, 251, 248, .96); backdrop-filter: blur(8px); }.evidence-editor__footer span { color: #6e756f; font-size: .76rem; }
@media (max-width: 760px) { .evidence-editor__header, .evidence-editor__section { padding: 22px 20px; }.evidence-editor__section { grid-template-columns: 1fr; gap: 18px; }.evidence-editor__fields { grid-template-columns: 1fr; }.evidence-editor label.is-wide { grid-column: auto; }.evidence-editor__footer { padding: 14px 20px; } }
</style>
