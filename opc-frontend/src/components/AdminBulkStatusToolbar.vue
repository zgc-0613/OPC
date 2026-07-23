<template>
  <div class="admin-bulk-toolbar">
    <div class="admin-bulk-summary" role="status" aria-live="polite">
      <strong>{{ selectedCount ? `已选择 ${selectedCount} 项` : '批量修改状态' }}</strong>
      <span>{{ selectedCount ? '选择目标状态后统一应用' : '请先勾选需要处理的列表项' }}</span>
    </div>
    <label>
      <span>目标状态</span>
      <select
        :value="modelValue"
        :disabled="busy"
        @change="$emit('update:modelValue', $event.target.value)"
      >
        <option value="">请选择</option>
        <option v-for="option in options" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
    </label>
    <button
      class="button"
      type="button"
      :disabled="busy || !selectedCount || !modelValue"
      @click="$emit('apply')"
    >
      <ListChecks :size="16" aria-hidden="true" />
      {{ busy ? '正在更新...' : '应用状态' }}
    </button>
    <button
      class="button button-ghost"
      type="button"
      :disabled="busy || !selectedCount"
      @click="$emit('clear')"
    >
      <X :size="16" aria-hidden="true" />
      取消选择
    </button>
  </div>
</template>

<script setup>
import { ListChecks, X } from 'lucide-vue-next'

defineProps({
  busy: { type: Boolean, default: false },
  modelValue: { type: String, default: '' },
  options: { type: Array, required: true },
  selectedCount: { type: Number, default: 0 },
})

defineEmits(['apply', 'clear', 'update:modelValue'])
</script>
