<template>
  <div ref="transcript" class="conversation" @scroll="handleScroll">
    <button v-if="hasMore" class="load-older" type="button" :disabled="loadingOlder" @click="$emit('load-older')">
      <LoaderCircle v-if="loadingOlder" class="spin" :size="15" /><ArrowUp v-else :size="15" />{{ loadingOlder ? '正在读取' : '加载更早消息' }}
    </button>

    <section v-if="!messages.length && draftMode" class="conversation-empty">
      <span class="caption">START WITH A REAL TASK</span>
      <h2>从一个清晰的创业问题开始。</h2>
      <div class="starter-grid">
        <button v-for="item in starters" :key="item.title" type="button" @click="$emit('prefill', item.prompt)"><component :is="item.icon" :size="19" /><strong>{{ item.title }}</strong><span>{{ item.detail }}</span></button>
      </div>
    </section>
    <section v-else-if="!messages.length" class="conversation-empty"><FileSearch :size="28" /><h2>这个会话还没有消息。</h2></section>

    <article v-for="message in messages" :key="message.messageId" class="message" :class="`is-${message.role}`">
      <header><span>{{ message.role === 'user' ? '你的问题' : 'SOLOFIRM 智能体' }}</span><time>{{ formatDate(message.createdAt) }}</time></header>
      <p v-if="message.role === 'user'">{{ message.content }}</p>
      <div v-else class="assistant-markdown" v-html="renderSafeMarkdown(message.content)"></div>
      <footer v-if="message.role === 'assistant'">
        <button type="button" @click="copyMessage(message)"><Check v-if="copiedId === message.messageId" :size="15" /><Copy v-else :size="15" />{{ copiedId === message.messageId ? '已复制' : '复制回答' }}</button>
        <button v-if="message.citations?.length" class="citation-trigger" type="button" @click="$emit('citations', message)"><BookOpen :size="15" />{{ message.citations.length }} 条引用</button>
        <button v-if="message.runId" type="button" @click="$emit('process', message)"><ListTree :size="15" />研究过程</button>
        <span>AI 生成内容，请核对原始来源</span>
      </footer>
    </article>

    <AssistantRunProgress :run="run" :network-status="networkStatus" :cancelling="cancelling" @cancel="$emit('cancel')" @retry="$emit('retry')" @resume="$emit('resume')" />
    <div ref="bottom"></div>
    <button v-if="showJump" class="jump-bottom" type="button" @click="scrollToEnd('smooth')"><ArrowDown :size="17" />回到底部</button>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import { ArrowDown, ArrowUp, BookOpen, BriefcaseBusiness, Check, Copy, FileCheck2, FileSearch, GitCompareArrows, Landmark, ListTree, LoaderCircle } from 'lucide-vue-next'
import AssistantRunProgress from './AssistantRunProgress.vue'
import { renderSafeMarkdown } from '@/utils/safeMarkdown'

defineProps({ messages: { type: Array, default: () => [] }, run: { type: Object, default: null }, hasMore: Boolean, loadingOlder: Boolean, draftMode: Boolean, networkStatus: { type: String, default: 'connected' }, cancelling: Boolean })
defineEmits(['load-older', 'prefill', 'citations', 'process', 'cancel', 'retry', 'resume'])
const transcript = ref(null)
const bottom = ref(null)
const showJump = ref(false)
const copiedId = ref(null)
const starters = [
  { icon: Landmark, title: '查找扶持政策', detail: '按地区与行业检索可用支持', prompt: '请查找当前地区与目标行业可用的创业扶持政策，并说明适用条件。' },
  { icon: GitCompareArrows, title: '比较创业案例', detail: '对比路径、资源与结果', prompt: '请比较两个与我目标行业相近的创业案例，指出关键差异和可借鉴做法。' },
  { icon: BriefcaseBusiness, title: '评估技术路线', detail: '识别成本、风险和落地顺序', prompt: '请评估我的技术路线，重点分析实施成本、主要风险和优先验证步骤。' },
  { icon: FileCheck2, title: '核验资料来源', detail: '确认政策或案例证据链', prompt: '请核验一条与本次研究相关的政策或案例来源，并说明其能支撑哪些结论。' },
]

async function copyMessage(message) {
  await navigator.clipboard?.writeText(message.content)
  copiedId.value = message.messageId
  window.setTimeout(() => { if (copiedId.value === message.messageId) copiedId.value = null }, 1600)
}
function handleScroll() {
  const el = transcript.value
  if (!el) return
  showJump.value = el.scrollHeight - el.scrollTop - el.clientHeight > 180
}
async function scrollToEnd(behavior = 'auto') {
  await nextTick()
  transcript.value?.scrollTo({ top: transcript.value.scrollHeight, behavior: reducedMotion() ? 'auto' : behavior })
  showJump.value = false
}
function scrollSnapshot() { return { height: transcript.value?.scrollHeight || 0, top: transcript.value?.scrollTop || 0 } }
async function restoreSnapshot(snapshot) { await nextTick(); if (transcript.value) transcript.value.scrollTop = snapshot.top + transcript.value.scrollHeight - snapshot.height }
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '' }
function reducedMotion() { return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches }
defineExpose({ scrollToEnd, scrollSnapshot, restoreSnapshot, element: transcript })
</script>

<style scoped>
.conversation{position:relative;min-height:0;overflow:auto;padding:18px max(24px,calc((100% - 880px)/2)) 32px;scrollbar-gutter:stable}.load-older{display:flex;align-items:center;justify-content:center;gap:7px;min-height:40px;margin:0 auto 14px;border:0;background:transparent;color:#5c635c}.conversation-empty{display:grid;justify-items:center;gap:9px;max-width:880px;margin:8vh auto 0;text-align:center}.conversation-empty h2{margin:0;font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:clamp(1.3rem,3vw,2rem);font-weight:500}.caption{color:#737a72;font-family:'Bookman Old Style',Georgia,serif;font-size:.66rem;font-weight:700}.starter-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1px;width:100%;margin-top:20px;border:1px solid #ccd0ca;background:#ccd0ca}.starter-grid button{display:grid;grid-template-columns:26px minmax(0,1fr);gap:3px 10px;min-height:96px;padding:17px;border:0;background:#fbfbf7;color:#282d28;text-align:left}.starter-grid button svg{grid-row:1/3}.starter-grid strong{font-family:'Noto Serif SC',STSong,SimSun,serif;font-size:.88rem}.starter-grid span{color:#747a73;font-size:.7rem}.message{max-width:880px;margin:0 auto;padding:20px 0;border-bottom:1px solid #d7dad5}.message.is-user{margin-top:8px;padding:16px;background:#f1f0e9}.message header{display:flex;align-items:center;justify-content:space-between;gap:14px;margin-bottom:10px}.message header span{color:#4f564f;font-size:.68rem;font-weight:800}.message time{color:#858b84;font-size:.62rem}.message>p{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.75}.assistant-markdown{overflow-wrap:anywhere;color:#272c27;line-height:1.78}.assistant-markdown :deep(h1),.assistant-markdown :deep(h2),.assistant-markdown :deep(h3){margin:1.1em 0 .45em;font-family:'Noto Serif SC',STSong,SimSun,serif;font-weight:600}.assistant-markdown :deep(h1){font-size:1.35rem}.assistant-markdown :deep(h2){font-size:1.15rem}.assistant-markdown :deep(h3){font-size:1rem}.assistant-markdown :deep(p){margin:.65em 0}.assistant-markdown :deep(pre){overflow:auto;padding:13px;border:1px solid #d1d4cf;background:#f2f1ec}.assistant-markdown :deep(code){font-family:Consolas,'Courier New',monospace;font-size:.86em}.assistant-markdown :deep(table){display:block;width:100%;overflow:auto;border-collapse:collapse}.assistant-markdown :deep(th),.assistant-markdown :deep(td){padding:8px 10px;border:1px solid #d0d4ce;text-align:left}.assistant-markdown :deep(a){color:#305b41;text-decoration:underline;text-underline-offset:3px}.message footer{display:flex;flex-wrap:wrap;align-items:center;gap:4px 13px;margin-top:13px;color:#777d76;font-size:.65rem}.message footer button{display:flex;align-items:center;gap:5px;min-height:36px;padding:0;border:0;background:transparent;color:#4d554d}.message footer span{margin-left:auto}.jump-bottom{position:sticky;bottom:12px;display:flex;align-items:center;gap:6px;min-height:42px;margin:0 auto;padding:0 13px;border:1px solid #bfc5bd;border-radius:3px;background:#fbfbf7;color:#303630}.spin{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:640px){.conversation{padding:12px 14px 24px}.starter-grid{grid-template-columns:1fr}.starter-grid button{min-height:88px}.message.is-user{padding:14px}.message footer span{width:100%;margin-left:0}.message footer button{min-height:44px}}@media(prefers-reduced-motion:reduce){.spin{animation:none}}
</style>
