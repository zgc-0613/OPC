<template>
  <RouterView />
  <AuthSuccessTransition />
</template>

<script setup>
import AuthSuccessTransition from '@/components/AuthSuccessTransition.vue'
</script>
