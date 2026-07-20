<template>
  <component
    :is="tag"
    ref="root"
    class="words-pull-up"
    :class="{ 'is-visible': visible }"
    :aria-label="text"
  >
    <span
      v-for="(word, index) in words"
      :key="`${word}-${index}`"
      class="words-pull-up__clip"
      aria-hidden="true"
    >
      <span
        class="words-pull-up__word"
        :style="{ transitionDelay: `${index * 80}ms` }"
      >
        {{ word }}<sup v-if="showAsterisk && index === words.length - 1" class="words-pull-up__asterisk">*</sup>
      </span>
    </span>
  </component>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = defineProps({
  text: {
    type: String,
    required: true,
  },
  tag: {
    type: String,
    default: 'span',
  },
  showAsterisk: {
    type: Boolean,
    default: false,
  },
})

const root = ref(null)
const visible = ref(false)
const words = computed(() => props.text.trim().split(/\s+/).filter(Boolean))
let observer

onMounted(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || !('IntersectionObserver' in window)) {
    visible.value = true
    return
  }

  observer = new IntersectionObserver(
    ([entry]) => {
      if (entry.isIntersecting) {
        visible.value = true
        observer.disconnect()
      }
    },
    { threshold: 0.18 },
  )
  observer.observe(root.value)
})

onUnmounted(() => observer?.disconnect())
</script>
