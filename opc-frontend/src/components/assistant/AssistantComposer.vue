<template>
  <form class="composer" @submit.prevent="$emit('send')">
    <div class="composer-meta">
      <span>{{ disabledReason || '继续研究' }}</span>
      <span v-if="usage">今日 {{ usage.usedTokens || 0 }} / {{ usage.unlimited ? '不限额' : usage.limitTokens }} tokens</span>
    </div>
    <div class="composer-control">
      <textarea ref="input" :value="modelValue" rows="2" maxlength="2000" :disabled="disabled" :placeholder="placeholder" aria-label="研究问题" @input="$emit('update:modelValue', $event.target.value)" @keydown.enter.exact.prevent="submitOnEnter" @keydown.shift.enter.stop></textarea>
      <button v-if="running" class="send-command" type="button" aria-label="取消当前研究" @click="$emit('cancel')"><Square :size="18" /></button>
      <button v-else class="send-command" type="submit" :disabled="disabled || sending || !modelValue.trim()" aria-label="发送研究问题"><LoaderCircle v-if="sending" class="spin" :size="18" /><Send v-else :size="18" /></button>
    </div>
    <small>Enter 发送，Shift + Enter 换行。研究回答应结合引用原文核验。</small>
  </form>
</template>

<script setup>
import { computed, ref } from 'vue'
import { LoaderCircle, Send, Square } from 'lucide-vue-next'

const props = defineProps({ modelValue: { type: String, default: '' }, disabled: Boolean, disabledReason: { type: String, default: '' }, sending: Boolean, running: Boolean, usage: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue', 'send', 'cancel'])
const input = ref(null)
const placeholder = computed(() => props.disabledReason || '输入一个明确的创业研究问题')
function submitOnEnter() { if (!props.disabled && !props.sending && props.modelValue.trim()) emit('send') }
defineExpose({ focus: () => input.value?.focus() })
</script>

<style scoped>
.composer{padding:12px max(24px,calc((100% - 880px)/2)) 16px;border-top:1px solid #cbd0c9;background:#fbfbf7}.composer-meta{display:flex;justify-content:space-between;gap:12px;margin-bottom:6px;color:#6e756e;font-size:.64rem}.composer-control{display:grid;grid-template-columns:minmax(0,1fr) 48px;gap:8px}.composer textarea{width:100%;min-height:62px;max-height:160px;padding:11px 12px;border:1px solid #bfc5bd;border-radius:3px;background:#fff;color:#242924;line-height:1.55;resize:vertical}.send-command{display:grid;place-items:center;width:48px;min-height:48px;align-self:stretch;border:1px solid #242924;border-radius:3px;background:#242924;color:#fff}.send-command:disabled{border-color:#d4d7d2;background:#e4e6e1;color:#8a908a}.composer>small{display:block;margin-top:6px;color:#858b84;font-size:.61rem}.spin{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:640px){.composer{padding:10px 12px calc(12px + env(safe-area-inset-bottom))}.composer-meta span:first-child{max-width:56%}.composer textarea{min-height:54px}.composer>small{display:none}}@media(prefers-reduced-motion:reduce){.spin{animation:none}}
</style>
