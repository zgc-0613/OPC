<template>
  <form class="composer" data-workspace-anchor="composer" @submit.prevent="$emit('send')">
    <div class="composer-meta">
      <span>{{ disabledReason || contextLabel }}</span>
      <span v-if="usage" class="usage-summary">{{ usageLabel }}</span>
    </div>
    <p v-if="quotaExhausted" class="composer-quota-state" role="status">
      <strong>今日研究额度已用尽</strong>
      <span>已用 {{ usage.usedTokens ?? 0 }} · 预留 {{ usage.reservedTokens ?? 0 }} · 日上限 {{ usage.dailyLimit ?? 0 }}<template v-if="usage.resetAt"> · {{ formatReset(usage.resetAt) }} 重置</template></span>
    </p>
    <div class="composer-control">
      <textarea ref="input" :value="modelValue" rows="2" maxlength="2000" :disabled="disabled" :placeholder="placeholder" aria-label="研究问题" @input="$emit('update:modelValue', $event.target.value)" @compositionstart="composing = true" @compositionend="composing = false" @keydown="handleKeydown"></textarea>
      <button v-if="running" class="send-command" type="button" aria-label="取消当前研究" @click="$emit('cancel')"><Square :size="18" /></button>
      <button v-else class="send-command" type="submit" :disabled="disabled || sending || !modelValue.trim()" :aria-label="sendLabel"><LoaderCircle v-if="sending" class="spin" :size="18" /><Send v-else :size="18" /></button>
    </div>
    <small>Enter 发送，Shift + Enter 换行。研究回答应结合引用原文核验。</small>
  </form>
</template>

<script setup>
import { computed, ref } from 'vue'
import { LoaderCircle, Send, Square } from 'lucide-vue-next'

const props = defineProps({ modelValue: { type: String, default: '' }, disabled: Boolean, disabledReason: { type: String, default: '' }, sending: Boolean, running: Boolean, newResearch: Boolean, usage: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue', 'send', 'cancel'])
const input = ref(null)
const composing = ref(false)
const contextLabel = computed(() => props.newResearch ? '提出第一个问题' : '继续研究')
const placeholder = computed(() => props.disabledReason || (props.newResearch
  ? '描述本次创业研究问题'
  : '输入一个明确的创业研究问题'))
const sendLabel = computed(() => props.newResearch ? '开始本次研究' : '发送研究问题')
const usageLabel = computed(() => {
  if (!props.usage) return ''
  if (props.usage.unlimited) return `已用 ${props.usage.usedTokens ?? 0} · 不限额`
  return `已用 ${props.usage.usedTokens ?? 0} · 预留 ${props.usage.reservedTokens ?? 0} · 剩余 ${props.usage.remainingTokens ?? 0} / 日上限 ${props.usage.dailyLimit ?? 0}`
})
const quotaExhausted = computed(() => Boolean(props.usage && !props.usage.unlimited && Number(props.usage.remainingTokens) <= 0))
function formatReset(value) { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }
function handleKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey) return
  if (composing.value || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  if (!props.disabled && !props.sending && props.modelValue.trim()) emit('send')
}
defineExpose({ focus: () => input.value?.focus() })
</script>

<style scoped>
.composer{flex:0 0 auto;min-width:0;padding:12px max(24px,calc((100% - 880px)/2)) 16px;border-top:1px solid #cbd0c9;background:#fbfbf7}.composer-meta{display:flex;justify-content:space-between;gap:12px;margin-bottom:6px;color:#6e756e;font-size:.64rem}.composer-quota-state{display:flex;flex-wrap:wrap;justify-content:space-between;gap:4px 12px;margin:0 0 8px;padding:8px 10px;border:1px solid #d1c4a7;background:#f6f2e7;color:#62552f;font-size:.68rem}.composer-quota-state strong{font-size:.7rem}.composer-control{display:grid;grid-template-columns:minmax(0,1fr) 48px;gap:8px}.composer textarea{width:100%;min-height:62px;max-height:160px;padding:11px 12px;border:1px solid #bfc5bd;border-radius:3px;background:#fff;color:#242924;line-height:1.55;resize:vertical}.send-command{display:grid;place-items:center;width:48px;min-height:48px;align-self:stretch;border:1px solid #242924;border-radius:3px;background:#242924;color:#fff}.send-command:focus-visible{background:#3b413b}.send-command:focus-visible{outline:2px solid rgba(74,82,74,.38);outline-offset:2px}.send-command:active{background:#111411}.send-command:disabled{border-color:#d4d7d2;background:#e4e6e1;color:#8a908a}.composer>small{display:block;margin-top:6px;color:#5f665f;font-size:.61rem}.spin{}@media (hover: hover) and (pointer: fine){.send-command:hover:not(:disabled){background:#3b413b}}@media(max-width:640px){.composer{padding:10px 12px calc(12px + env(safe-area-inset-bottom))}.composer-meta{align-items:flex-start;flex-direction:column}.composer-meta span:first-child{max-width:100%}.composer-quota-state{display:grid}.composer textarea{min-height:54px}.composer>small{display:none}}@media(max-height:680px){.composer{padding-top:8px;padding-bottom:max(8px,env(safe-area-inset-bottom))}.composer-meta{margin-bottom:4px}.composer textarea{min-height:48px;max-height:88px}.composer>small{display:none}}
</style>
