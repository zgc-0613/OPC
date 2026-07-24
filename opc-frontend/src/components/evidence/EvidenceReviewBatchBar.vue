<template>
  <div class="evidence-batch-bar" :class="{ 'is-visible': selectedCount > 0 }" aria-live="polite">
    <div>
      <strong>已选择 {{ selectedCount }} 项</strong>
      <span>操作前将预检并显示阻止项与依赖影响。</span>
    </div>
    <div class="evidence-batch-bar__actions">
      <button type="button" class="button button-secondary" :disabled="busy || !selectedCount" @click="$emit('action', 'legacy_unverified')">移回待审</button>
      <button type="button" class="button button-secondary" :disabled="busy || !selectedCount" @click="$emit('action', 'excluded')">批量排除</button>
      <button type="button" class="button" :disabled="busy || !selectedCount" @click="$emit('action', 'verified')">批量批准</button>
      <button type="button" class="evidence-batch-bar__clear" :disabled="busy" @click="$emit('clear')">清除选择</button>
    </div>
  </div>
</template>

<script setup>
defineProps({ selectedCount: { type: Number, default: 0 }, busy: Boolean })
defineEmits(['action', 'clear'])
</script>

<style scoped>
.evidence-batch-bar { display: none; align-items: center; justify-content: space-between; gap: 22px; padding: 15px 18px; border: 1px solid #c7ccc6; background: #f2f3ef; }.evidence-batch-bar.is-visible { display: flex; }.evidence-batch-bar > div:first-child { display: grid; gap: 4px; }.evidence-batch-bar strong { color: #232723; font-family: 'Noto Serif SC', STSong, SimSun, serif; }.evidence-batch-bar span { color: #687069; font-size: .73rem; }.evidence-batch-bar__actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }.evidence-batch-bar__clear { min-height: 38px; border: 0; background: transparent; color: #4d544e; text-decoration: underline; text-underline-offset: 3px; }
@media (max-width: 760px) { .evidence-batch-bar { align-items: stretch; flex-direction: column; }.evidence-batch-bar__actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }.evidence-batch-bar__actions .button { justify-content: center; } }
</style>
