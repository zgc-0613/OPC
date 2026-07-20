<template>
  <component
    :is="tag"
    ref="root"
    class="words-pull-up words-pull-up--multi"
    :class="{ 'is-visible': visible }"
    :aria-label="accessibleText"
  >
    <span
      v-for="(word, index) in words"
      :key="`${word.text}-${index}`"
      class="words-pull-up__clip"
      aria-hidden="true"
    >
      <span
        class="words-pull-up__word"
        :class="word.className"
        :style="{ transitionDelay: `${index * 80}ms` }"
      >
        {{ word.text }}
      </span>
    </span>
  </component>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = defineProps({
  segments: {
    type: Array,
    required: true,
  },
  tag: {
    type: String,
    default: 'span',
  },
})

const root = ref(null)
const visible = ref(false)
const accessibleText = computed(() => props.segments.map((segment) => segment.text).join(' '))
const words = computed(() =>
  props.segments.flatMap((segment) =>
    String(segment.text)
      .trim()
      .split(/\s+/)
      .filter(Boolean)
      .map((text) => ({ text, className: segment.className || '' })),
  ),
)
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
