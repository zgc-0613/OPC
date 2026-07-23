<template>
  <th :aria-sort="ariaSort">
    <button
      class="admin-sort-button"
      type="button"
      :class="{ 'is-active': isActive, 'is-default': isDefaultColumn && !activeColumn }"
      :title="buttonTitle"
      :aria-label="buttonTitle"
      @click="$emit('toggle', column)"
    >
      <span>{{ label }}</span>
      <ArrowUp v-if="isActive && direction === 'asc'" :size="14" aria-hidden="true" />
      <ArrowDown v-else-if="isActive && direction === 'desc'" :size="14" aria-hidden="true" />
      <ArrowUpDown v-else :size="14" aria-hidden="true" />
    </button>
    <span
      class="admin-column-resizer"
      role="separator"
      tabindex="0"
      aria-orientation="vertical"
      :aria-label="`调整${label}列宽，使用左右方向键可微调`"
      @pointerdown.stop.prevent="$emit('resize-start', $event, column)"
      @keydown.left.prevent="$emit('resize-by', column, -16)"
      @keydown.right.prevent="$emit('resize-by', column, 16)"
    ></span>
  </th>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-vue-next'

const props = defineProps({
  activeColumn: { type: String, default: null },
  column: { type: String, required: true },
  defaultColumn: { type: String, default: 'id' },
  direction: { type: String, default: null },
  label: { type: String, required: true },
})

defineEmits(['resize-by', 'resize-start', 'toggle'])

const isActive = computed(() => props.activeColumn === props.column)
const isDefaultColumn = computed(() => props.column === props.defaultColumn)
const ariaSort = computed(() => {
  if (isActive.value) {
    return props.direction === 'desc' ? 'descending' : 'ascending'
  }
  return isDefaultColumn.value && !props.activeColumn ? 'ascending' : 'none'
})
const buttonTitle = computed(() => {
  if (!isActive.value) {
    return `按${props.label}由小到大排序`
  }
  if (props.direction === 'asc') {
    return `当前按${props.label}由小到大，点击切换为由大到小`
  }
  return `当前按${props.label}由大到小，点击恢复默认的 ID 由小到大排序`
})
</script>
