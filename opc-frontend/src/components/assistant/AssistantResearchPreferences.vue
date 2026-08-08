<template>
  <section class="research-preferences" aria-labelledby="research-preferences-title">
    <button
      class="preferences-trigger"
      data-testid="open-research-preferences"
      type="button"
      :aria-expanded="open"
      aria-controls="research-preferences-panel"
      @click="openPanel"
    >
      <SlidersHorizontal :size="16" aria-hidden="true" />
      <span id="research-preferences-title">长期研究偏好</span>
      <span class="preferences-trigger-note">仅在你明确保存并应用时使用</span>
      <ChevronUp v-if="open" :size="16" aria-hidden="true" />
      <ChevronDown v-else :size="16" aria-hidden="true" />
    </button>

    <div v-if="open" id="research-preferences-panel" class="preferences-panel" :aria-busy="loading">
      <p class="preferences-intro">这些偏好由你管理，不会从对话中自动提取，也不会改写已创建研究的条件。</p>
      <p v-if="loading" class="preferences-status" role="status">正在读取已保存的偏好</p>
      <template v-else-if="error">
        <p class="preferences-error" role="alert">{{ error }}</p>
        <button class="retry-preferences" type="button" @click="load">重新读取</button>
      </template>

      <form v-else class="preferences-form" @submit.prevent="save">
        <label class="memory-switch">
          <input v-model="form.memoryEnabled" type="checkbox" />
          <span>
            <strong>允许作为新研究的预填建议</strong>
            <small>关闭后，系统不会把这些长期偏好带入研究上下文。</small>
          </span>
        </label>

        <div class="preference-fields">
          <label><span>常用地区</span><input v-model="form.commonRegion" maxlength="120" autocomplete="address-level1" /></label>
          <label><span>常用行业</span><input v-model="form.commonIndustry" maxlength="120" /></label>
          <label><span>技术方向</span><input v-model="form.technologyDirection" maxlength="80" /></label>
          <label><span>创业阶段</span><select v-model="form.ventureStage"><option value="">未设置</option><option value="idea">想法形成</option><option value="validation">需求验证</option><option value="early_operation">早期运营</option><option value="growth">增长阶段</option></select></label>
          <label><span>预算范围</span><select v-model="form.budgetRange"><option value="">未设置</option><option value="under_100k">10 万元以内</option><option value="100k_500k">10-50 万元</option><option value="500k_1m">50-100 万元</option><option value="over_1m">100 万元以上</option><option value="undecided">尚未确定</option></select></label>
          <label class="wide"><span>团队能力</span><textarea v-model="form.teamCapabilities" rows="2" maxlength="500" /></label>
          <label class="wide"><span>已有资源</span><textarea v-model="form.existingResources" rows="2" maxlength="500" /></label>
          <label class="wide"><span>关注的政策类型</span><textarea v-model="form.policyFocus" rows="2" maxlength="500" /></label>
        </div>

        <p v-if="notice" class="preferences-status" role="status">{{ notice }}</p>

        <footer class="preferences-actions">
          <button class="save-preferences" type="submit" :disabled="saving">{{ saving ? '正在保存' : '保存偏好' }}</button>
          <button
            v-if="canApply"
            class="apply-preferences"
            data-testid="apply-research-preferences"
            type="button"
            :disabled="!form.memoryEnabled"
            @click="apply"
          >用于新建研究</button>
          <p v-else class="immutable-note">当前研究条件已固定；可在新建研究时应用这些偏好。</p>
          <button class="delete-preferences" type="button" :disabled="deleting" @click="confirmDelete = !confirmDelete">删除全部</button>
        </footer>

        <div v-if="confirmDelete" class="delete-confirmation" role="alert">
          <span>删除后不会影响历史研究和已保存报告。</span>
          <button type="button" :disabled="deleting" @click="clear">{{ deleting ? '正在删除' : '确认删除' }}</button>
          <button type="button" :disabled="deleting" @click="confirmDelete = false">取消</button>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { ChevronDown, ChevronUp, SlidersHorizontal } from 'lucide-vue-next'
import { clearResearchPreferences, getResearchPreferences, updateResearchPreferences } from '@/api/ai'

defineProps({ canApply: Boolean })
const emit = defineEmits(['apply'])

const emptyPreference = () => ({
  memoryEnabled: false,
  commonRegion: '',
  commonIndustry: '',
  technologyDirection: '',
  ventureStage: '',
  budgetRange: '',
  teamCapabilities: '',
  existingResources: '',
  policyFocus: '',
})

const open = ref(false)
const loaded = ref(false)
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const confirmDelete = ref(false)
const error = ref('')
const notice = ref('')
const form = ref(emptyPreference())

async function openPanel() {
  open.value = !open.value
  if (open.value) await load()
}

async function load() {
  if (loaded.value || loading.value) return
  loading.value = true
  error.value = ''
  try {
    form.value = normalizePreference(await getResearchPreferences())
    loaded.value = true
  } catch (requestError) {
    error.value = requestError.message || '长期研究偏好暂时无法读取。'
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    form.value = normalizePreference(await updateResearchPreferences(toPayload(form.value)))
    loaded.value = true
    notice.value = form.value.memoryEnabled ? '偏好已保存，并可在新建研究时由你手动应用。' : '偏好已保存且保持关闭，不会进入研究上下文。'
  } catch (requestError) {
    error.value = requestError.message || '长期研究偏好暂时无法保存。'
  } finally {
    saving.value = false
  }
}

function apply() {
  if (!form.value.memoryEnabled) return
  emit('apply', { ...form.value })
  notice.value = '已填入新的本地研究草稿；发送前仍可修改研究条件。'
}

async function clear() {
  deleting.value = true
  error.value = ''
  notice.value = ''
  try {
    await clearResearchPreferences()
    form.value = emptyPreference()
    loaded.value = true
    confirmDelete.value = false
    notice.value = '长期研究偏好已删除。'
  } catch (requestError) {
    error.value = requestError.message || '长期研究偏好暂时无法删除。'
  } finally {
    deleting.value = false
  }
}

function normalizePreference(value) {
  return { ...emptyPreference(), ...(value || {}) }
}

function toPayload(value) {
  return Object.fromEntries(Object.entries(value).map(([key, field]) => [
    key,
    typeof field === 'string' ? field.trim() : Boolean(field),
  ]))
}
</script>

<style scoped>
.research-preferences{flex:0 0 auto;border-bottom:1px solid #d0d3ce;background:#f9f9f5;color:#282d28}.preferences-trigger{display:grid;grid-template-columns:auto auto minmax(0,1fr) auto;align-items:center;width:100%;min-height:44px;gap:8px;padding:10px 24px;border:0;background:transparent;color:#303630;text-align:left}.preferences-trigger span:first-of-type{font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:.78rem;font-weight:700}.preferences-trigger-note{min-width:0;overflow:hidden;color:#747b74;font-size:.65rem;text-overflow:ellipsis;white-space:nowrap}.preferences-trigger:is(:hover,:focus-visible){background:#eef0eb}.preferences-trigger:focus-visible{outline:2px solid rgba(74,82,74,.34);outline-offset:-2px}.preferences-panel{display:grid;gap:12px;max-height:min(48dvh,480px);overflow:auto;padding:0 24px 16px;scrollbar-gutter:stable}.preferences-intro,.preferences-status,.preferences-error{margin:0;font-size:.7rem;line-height:1.6}.preferences-intro{color:#626a62}.preferences-status{color:#3f684a}.preferences-error{color:#7a3731}.retry-preferences{justify-self:start;min-height:38px;padding:0 11px;border:1px solid #b9bfb8;border-radius:3px;background:#fbfbf7;color:#303630;font:inherit;font-size:.7rem;font-weight:700}.retry-preferences:is(:hover,:focus-visible){border-color:#747b74;background:#eceee8}.retry-preferences:focus-visible{outline:2px solid rgba(74,82,74,.34);outline-offset:2px}.preferences-form{display:grid;gap:13px}.memory-switch{display:flex;align-items:flex-start;gap:10px;padding:10px 12px;border:1px solid #c5cac3;border-radius:3px;background:#f3f4f0;cursor:pointer}.memory-switch input{width:18px;height:18px;margin:1px 0 0;accent-color:#4f6f58}.memory-switch span{display:grid;gap:3px}.memory-switch strong{font-size:.74rem}.memory-switch small{color:#687068;font-size:.67rem;line-height:1.5}.preference-fields{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.preference-fields label{display:grid;gap:5px;min-width:0}.preference-fields label.wide{grid-column:span 3}.preference-fields label>span{color:#555b55;font-size:.67rem;font-weight:700}.preference-fields :is(input,select,textarea){width:100%;min-width:0;border:1px solid #bfc5bd;border-radius:3px;background:#fff;color:#252a25;font:inherit;font-size:.75rem;line-height:1.5}.preference-fields :is(input,select){height:40px;padding:0 9px}.preference-fields textarea{padding:8px 9px;resize:vertical}.preference-fields :is(input,select,textarea):focus-visible{border-color:#606860;outline:2px solid rgba(96,104,96,.28);outline-offset:1px}.preferences-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}.preferences-actions button,.delete-confirmation button{min-height:38px;padding:0 11px;border:1px solid #b9bfb8;border-radius:3px;background:#fbfbf7;color:#303630;font:inherit;font-size:.7rem;font-weight:700}.preferences-actions .save-preferences{border-color:#303630;background:#303630;color:#fff}.preferences-actions button:is(:hover,:focus-visible),.delete-confirmation button:is(:hover,:focus-visible){border-color:#747b74;background:#eceee8}.preferences-actions .save-preferences:is(:hover,:focus-visible){background:#3b413b;color:#fff}.preferences-actions button:focus-visible,.delete-confirmation button:focus-visible{outline:2px solid rgba(74,82,74,.34);outline-offset:2px}.preferences-actions button:disabled,.delete-confirmation button:disabled{opacity:.55;cursor:not-allowed}.immutable-note{margin:0;flex:1 1 220px;color:#686f68;font-size:.67rem;line-height:1.5}.delete-preferences{margin-left:auto}.delete-confirmation{display:flex;align-items:center;flex-wrap:wrap;gap:8px;padding:9px 10px;border:1px solid #d2bdb7;background:#f8efec;color:#6f3b35;font-size:.68rem}.delete-confirmation span{flex:1 1 220px}@media(max-width:720px){.preferences-trigger{padding:10px 16px}.preferences-panel{padding:0 16px 14px}.preference-fields{grid-template-columns:1fr}.preference-fields label.wide{grid-column:span 1}.preferences-actions button,.delete-confirmation button,.retry-preferences{min-height:44px}.delete-preferences{margin-left:0}.preferences-trigger-note{display:none}}@media(max-height:680px){.preferences-panel{max-height:min(34dvh,300px)}}@media(min-width:641px) and (max-width:1023px) and (pointer:coarse){.preferences-actions button,.delete-confirmation button,.retry-preferences{min-height:44px}.preference-fields :is(input,select,textarea){min-height:44px}}@media(prefers-reduced-motion:reduce){.preferences-trigger{transition:none}}
</style>
