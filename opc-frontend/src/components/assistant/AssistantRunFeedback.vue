<template>
  <section v-if="eligible" class="run-feedback" aria-labelledby="run-feedback-title">
    <button
      class="feedback-trigger"
      data-testid="open-run-feedback"
      type="button"
      :aria-expanded="open"
      aria-controls="run-feedback-panel"
      @click="openPanel"
    >
      <MessageSquareText :size="15" aria-hidden="true" />
      <span id="run-feedback-title">评价本次研究</span>
      <ChevronUp v-if="open" :size="15" aria-hidden="true" />
      <ChevronDown v-else :size="15" aria-hidden="true" />
    </button>
    <div v-if="open" id="run-feedback-panel" class="feedback-panel" :aria-busy="loading">
      <p v-if="loading" role="status">正在读取你的评价</p>
      <p v-else-if="error" class="feedback-error" role="alert">{{ error }}</p>
      <form v-else @submit.prevent="save">
        <fieldset>
          <legend>这次研究是否有帮助？</legend>
          <label><input v-model="form.rating" :name="radioGroupName" type="radio" value="helpful" @change="normalizeReason" />有帮助</label>
          <label><input v-model="form.rating" :name="radioGroupName" type="radio" value="not_helpful" @change="normalizeReason" />仍需改进</label>
        </fieldset>
        <label><span>原因</span><select v-model="form.reason"><option v-for="reason in reasons" :key="reason.id" :value="reason.id">{{ reason.label }}</option></select></label>
        <label><span>补充说明（可选）</span><textarea v-model="form.comment" rows="2" maxlength="500" /></label>
        <p v-if="notice" class="feedback-notice" role="status">{{ notice }}</p>
        <footer><button type="submit" :disabled="saving">{{ saving ? '正在提交' : '提交评价' }}</button></footer>
      </form>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ChevronDown, ChevronUp, MessageSquareText } from 'lucide-vue-next'
import { getResearchRunFeedback, updateResearchRunFeedback } from '@/api/ai'

const props = defineProps({ run: { type: Object, default: null } })
const options = {
  helpful: [
    { id: 'accurate_and_useful', label: '准确且有帮助' },
    { id: 'clear_and_actionable', label: '清晰且可执行' },
    { id: 'good_evidence', label: '引用和证据充分' },
    { id: 'other', label: '其他' },
  ],
  not_helpful: [
    { id: 'missing_evidence', label: '证据不足' },
    { id: 'incorrect_claim', label: '事实不准确' },
    { id: 'not_relevant', label: '与研究目标不相关' },
    { id: 'unclear', label: '表达不清晰' },
    { id: 'too_slow', label: '耗时过长' },
    { id: 'other', label: '其他' },
  ],
}
const open = ref(false)
const loaded = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const form = ref(createInitialForm())
const eligible = computed(() => props.run?.feedbackEligible === true && Number(props.run?.runId) > 0)
const radioGroupName = computed(() => `research-run-feedback-${Number(props.run?.runId) || 'unknown'}`)
const reasons = computed(() => options[form.value.rating])
let requestSequence = 0

watch(() => props.run?.runId, (runId, previousRunId) => {
  if (String(runId || '') === String(previousRunId || '')) return
  requestSequence += 1
  loaded.value = false
  loading.value = false
  saving.value = false
  error.value = ''
  notice.value = ''
  form.value = createInitialForm()
  if (open.value && eligible.value) load()
})

async function openPanel() {
  open.value = !open.value
  if (open.value) await load()
}

async function load() {
  if (loaded.value || loading.value || !eligible.value) return
  const runId = Number(props.run.runId)
  const sequence = ++requestSequence
  loading.value = true
  error.value = ''
  try {
    const feedback = await getResearchRunFeedback(runId)
    if (sequence !== requestSequence || Number(props.run?.runId) !== runId) return
    if (feedback) {
      form.value = {
        rating: feedback.rating,
        reason: feedback.reason,
        comment: feedback.comment || '',
        revision: Number(feedback.revision || 0),
      }
    }
    normalizeReason()
    loaded.value = true
  } catch (requestError) {
    if (sequence === requestSequence) error.value = requestError.message || '评价暂时无法读取。'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function normalizeReason() {
  if (!options[form.value.rating].some((option) => option.id === form.value.reason)) {
    form.value.reason = options[form.value.rating][0].id
  }
}

async function save() {
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const feedback = await updateResearchRunFeedback(props.run.runId, {
      rating: form.value.rating,
      reason: form.value.reason,
      comment: form.value.comment.trim(),
      expectedRevision: form.value.revision,
    })
    form.value.revision = Number(feedback.revision || form.value.revision)
    form.value.comment = feedback.comment || ''
    notice.value = '评价已保存。'
  } catch (requestError) {
    error.value = requestError.message || '评价未保存，请重新读取后再试。'
  } finally {
    saving.value = false
  }
}

function createInitialForm() {
  return { rating: 'helpful', reason: 'accurate_and_useful', comment: '', revision: 0 }
}
</script>

<style scoped>
.run-feedback{display:grid;gap:7px;width:min(100%,880px);margin:4px auto 0;padding:0 2px}.feedback-trigger{display:inline-flex;align-items:center;justify-content:flex-start;gap:7px;min-height:38px;width:max-content;padding:0 9px;border:1px solid #bfc5bd;border-radius:3px;background:#fbfbf8;color:#303630;font:inherit;font-size:.69rem;font-weight:700}.feedback-trigger:focus-visible,.feedback-panel :is(input,select,textarea,button):focus-visible{outline:2px solid #4f6f58;outline-offset:2px}.feedback-panel{display:grid;gap:9px;padding:11px 12px;border:1px solid #c5cac3;border-radius:3px;background:#f3f4f0}.feedback-panel p{margin:0;font-size:.69rem;line-height:1.55}.feedback-error{color:#7a3731}.feedback-notice{color:#3f684a}.feedback-panel form{display:grid;gap:9px}.feedback-panel fieldset{display:flex;align-items:center;flex-wrap:wrap;gap:8px 13px;margin:0;padding:0;border:0}.feedback-panel legend,.feedback-panel label>span{color:#4f574f;font-size:.67rem;font-weight:700}.feedback-panel fieldset label{display:inline-flex;align-items:center;gap:5px;color:#303630;font-size:.71rem}.feedback-panel input[type="radio"]{width:16px;height:16px;margin:0;accent-color:#4f6f58}.feedback-panel>form>label{display:grid;gap:5px}.feedback-panel :is(select,textarea){width:100%;min-width:0;border:1px solid #aeb4ac;border-radius:3px;background:#fff;color:#252a25;font:inherit;font-size:.75rem;line-height:1.5}.feedback-panel select{height:40px;padding:0 9px}.feedback-panel textarea{padding:8px 9px;resize:vertical}.feedback-panel footer button{min-height:38px;padding:0 11px;border:1px solid #303630;border-radius:3px;background:#303630;color:#fff;font:inherit;font-size:.69rem;font-weight:700}.feedback-panel footer button:focus-visible{background:#3b413b}.feedback-panel footer button:disabled{opacity:.55;cursor:not-allowed}@media (hover: hover) and (pointer: fine){.feedback-trigger:hover{border-color:#747b74;background:#eceee8}.feedback-panel footer button:hover{background:#3b413b}}@media(max-width:640px),(pointer:coarse){.feedback-trigger,.feedback-panel footer button{min-height:44px}.feedback-panel fieldset{align-items:flex-start;flex-direction:column}.run-feedback{padding:0 2px}}@media(prefers-reduced-motion:reduce){.feedback-trigger{transition:none}}
</style>
