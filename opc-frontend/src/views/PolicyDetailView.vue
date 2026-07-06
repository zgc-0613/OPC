<template>
  <div class="page-stack">
    <RouterLink class="back-link" to="/policies">返回政策库</RouterLink>

    <section class="panel">
      <div v-if="loading" class="muted">正在加载政策详情...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <article v-else class="detail">
        <h2>{{ policy.title }}</h2>
        <div class="meta-grid">
          <span><b>地区</b>{{ policy.regionName || '-' }}</span>
          <span><b>发文单位</b>{{ policy.issuingBody || '-' }}</span>
          <span><b>文号</b>{{ policy.documentNo || '-' }}</span>
          <span><b>发布日期</b>{{ policy.publishDate || '-' }}</span>
          <span><b>实施时间</b>{{ policy.effectiveDate || '-' }}</span>
          <span><b>有效时长</b>{{ policy.validPeriod || '-' }}</span>
        </div>

        <h3>摘要</h3>
        <p>{{ policy.summary }}</p>

        <h3>政策要点</h3>
        <pre>{{ policy.keyPoints || '-' }}</pre>

        <h3>支持措施</h3>
        <pre>{{ policy.supportMeasures || '-' }}</pre>

        <h3>来源</h3>
        <p class="source-actions">
          <a v-if="policy.originalUrl" class="button button-ghost" :href="policy.originalUrl" target="_blank" rel="noreferrer">
            政策原文
          </a>
          <a v-if="policy.evidenceUrl" class="button button-ghost" :href="policy.evidenceUrl" target="_blank" rel="noreferrer">
            辅证链接
          </a>
        </p>
      </article>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getPolicyDetail } from '@/api/policy'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})

const loading = ref(false)
const error = ref('')
const policy = ref({})

onMounted(async () => {
  loading.value = true
  try {
    policy.value = await getPolicyDetail(props.id)
  } catch (err) {
    error.value = err.message || '政策详情加载失败'
  } finally {
    loading.value = false
  }
})
</script>
