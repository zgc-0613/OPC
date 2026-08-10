<template>
  <section class="research-starter" aria-labelledby="research-starter-title">
    <header>
      <span class="caption">NEW RESEARCH</span>
      <h2 id="research-starter-title">从一个明确的问题开始</h2>
      <p>研究条件保持在右侧，确认范围后再开始第一轮检索。</p>
    </header>

    <dl class="research-outline" aria-label="本次研究范围">
      <div>
        <dt>研究条件</dt>
        <dd>{{ profileLabel }}</dd>
      </div>
      <div>
        <dt>研究任务</dt>
        <dd>{{ taskSummary || '尚未选择' }}</dd>
      </div>
    </dl>

    <button class="conditions-command" data-testid="open-research-conditions" type="button" @click="$emit('conditions', $event)">
      <SlidersHorizontal :size="17" aria-hidden="true" />编辑研究条件
    </button>

    <form @submit.prevent="start">
      <label for="assistant-first-question">本次研究问题</label>
      <textarea
        id="assistant-first-question"
        :value="modelValue"
        rows="5"
        maxlength="2000"
        :disabled="disabled || starting"
        placeholder="说明你希望核验、比较或判断的创业问题"
        aria-label="研究问题"
        @input="$emit('update:modelValue', $event.target.value)"
        @compositionstart="composing = true"
        @compositionend="composing = false"
        @keydown="handleKeydown"
      ></textarea>
      <p v-if="error" class="starter-error" role="alert">{{ error }}</p>
      <p v-else-if="launchDisabledReason" class="starter-status" role="status">{{ launchDisabledReason }}</p>
      <footer>
        <small>开始后会创建一份研究记录，并保留本地草稿。</small>
        <button type="submit" :disabled="disabled || launchDisabled || starting || !modelValue.trim()">
          <LoaderCircle v-if="starting" :size="17" aria-hidden="true" />
          <ArrowRight v-else :size="17" aria-hidden="true" />
          {{ starting ? '正在开始' : '开始研究' }}
        </button>
      </footer>
    </form>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ArrowRight, LoaderCircle, SlidersHorizontal } from 'lucide-vue-next'

const props = defineProps({
  modelValue: { type: String, default: '' },
  profileSummary: { type: Array, default: () => [] },
  taskSummary: { type: String, default: '' },
  disabled: Boolean,
  launchDisabled: Boolean,
  launchDisabledReason: { type: String, default: '' },
  starting: Boolean,
  error: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue', 'conditions', 'start'])
const composing = ref(false)
const profileLabel = computed(() => props.profileSummary.filter((item) => String(item || '').trim()).join(' / ') || '可在研究条件中补充')

function start() {
  if (props.disabled || props.launchDisabled || props.starting || !props.modelValue.trim()) return
  emit('start')
}

function handleKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey || composing.value || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  start()
}
</script>
<style scoped>
.caption,
.research-starter footer small { color: #5f665f !important; }
.research-starter{display:flex;flex:1 1 auto;flex-direction:column;width:min(100%,880px);min-width:0;min-height:0;margin:0 auto;overflow-x:hidden;overflow-y:auto;padding:40px max(24px,6vw) 48px;color:#282d28}.research-starter header{max-width:68ch}.caption{display:block;color:#717870;font-family:'Bookman Old Style',Georgia,serif;font-size:.65rem;font-weight:700}.research-starter h2{margin:10px 0 0;font-family:'ZCOOL XiaoWei',STKaiti,KaiTi,serif;font-size:1.65rem;font-weight:400;line-height:1.15}.research-starter header p{margin:10px 0 0;color:#626a62;font-size:.82rem;line-height:1.65}.research-outline{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1px;margin:28px 0 12px;border:1px solid #d0d4ce;background:#d0d4ce}.research-outline div{min-width:0;padding:12px 14px;background:#f5f5f0}.research-outline dt{color:#6c736c;font-size:.66rem;font-weight:700}.research-outline dd{margin:6px 0 0;overflow-wrap:anywhere;font-size:.78rem;line-height:1.55}.conditions-command{display:inline-flex;align-items:center;gap:7px;min-height:40px;padding:0 2px;border:0;border-bottom:1px solid #9da49c;background:transparent;color:#343a34;font:inherit;font-size:.74rem;font-weight:700}.conditions-command:focus-visible,.research-starter textarea:focus-visible,.research-starter footer button:focus-visible{outline:2px solid rgba(74,82,74,.38);outline-offset:3px}.research-starter form{display:grid;gap:8px;margin-top:32px}.research-starter form>label{font-size:.76rem;font-weight:700}.research-starter textarea{width:100%;min-width:0;min-height:138px;padding:14px;border:1px solid #afb6ae;border-radius:3px;background:#fbfbf7;color:#252a25;font:inherit;line-height:1.65;resize:vertical}.starter-error{margin:0;padding:9px 10px;border:1px solid #d2bdb7;background:#f8efec;color:#703731;font-size:.72rem;line-height:1.55}.research-starter footer{display:flex;align-items:center;justify-content:space-between;gap:16px}.research-starter footer small{color:#747b74;font-size:.67rem;line-height:1.5}.research-starter footer button{display:inline-flex;align-items:center;justify-content:center;gap:7px;min-height:44px;padding:0 15px;border:1px solid #282d28;border-radius:3px;background:#282d28;color:#fbfbf7;font:inherit;font-size:.75rem;font-weight:700}.research-starter footer button:active,.conditions-command:active{transform:translateY(1px)}.research-starter footer button:disabled{border-color:#d4d7d2;background:#e4e6e1;color:#8a908a;transform:none}@media (hover: hover) and (pointer: fine){.conditions-command:hover{border-color:#343a34;color:#181c18}.research-starter footer button:hover{background:#3b413b}}@media(max-width:640px){.research-starter{padding:28px 14px calc(28px + env(safe-area-inset-bottom))}.research-starter h2{font-size:1.4rem}.research-outline{grid-template-columns:1fr}.conditions-command{min-height:44px}.research-starter textarea{min-height:126px}.research-starter footer{align-items:stretch;flex-direction:column}.research-starter footer button{width:100%}}@media(max-height:680px){.research-starter{padding-top:24px;padding-bottom:max(24px,env(safe-area-inset-bottom))}.research-starter form{margin-top:22px}.research-starter textarea{min-height:108px}}@media(prefers-reduced-motion:reduce){.research-starter *{scroll-behavior:auto}}
.starter-status{margin:0;padding:9px 10px;border:1px solid #c9cdc7;background:#f2f3ef;color:#576057;font-size:.72rem;line-height:1.55}
</style>
