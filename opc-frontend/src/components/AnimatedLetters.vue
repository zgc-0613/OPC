<template>
  <p ref="root" class="animated-letters" :aria-label="text">
    <span
      v-for="(character, index) in characters"
      :key="index"
      aria-hidden="true"
      :style="{ opacity: characterOpacity(index) }"
    >{{ character }}</span>
  </p>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = defineProps({
  text: {
    type: String,
    required: true,
  },
})

const root = ref(null)
const progress = ref(0)
const characters = computed(() => Array.from(props.text))
let frameId = 0

function clamp(value) {
  return Math.min(Math.max(value, 0), 1)
}

function characterOpacity(index) {
  const total = Math.max(characters.value.length, 1)
  const characterProgress = index / total
  const start = characterProgress - 0.1
  const end = characterProgress + 0.05
  const localProgress = clamp((progress.value - start) / Math.max(end - start, 0.01))
  return (0.45 + localProgress * 0.55).toFixed(3)
}

function updateProgress() {
  frameId = 0
  if (!root.value) {
    return
  }
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    progress.value = 1
    return
  }
  const rect = root.value.getBoundingClientRect()
  const start = window.innerHeight * 0.8
  const end = window.innerHeight * 0.2
  const distance = start - end + rect.height
  progress.value = clamp((start - rect.top) / Math.max(distance, 1))
}

function requestUpdate() {
  if (!frameId) {
    frameId = window.requestAnimationFrame(updateProgress)
  }
}

onMounted(() => {
  updateProgress()
  window.addEventListener('scroll', requestUpdate, { passive: true })
  window.addEventListener('resize', requestUpdate)
})

onUnmounted(() => {
  window.cancelAnimationFrame(frameId)
  window.removeEventListener('scroll', requestUpdate)
  window.removeEventListener('resize', requestUpdate)
})
</script>
