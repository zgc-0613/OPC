<template>
  <div class="page-stack">
    <RouterLink class="back-link" to="/cases">返回案例库</RouterLink>

    <section class="panel">
      <div v-if="loading" class="muted">正在加载案例详情...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <article v-else class="detail">
        <h2>{{ item.title }}</h2>
        <div class="meta-grid">
          <span><b>地区</b>{{ item.regionName || '-' }}</span>
          <span><b>领域</b>{{ item.category || '-' }}</span>
          <span><b>主体</b>{{ item.actorName || '-' }}</span>
          <span><b>状态</b>{{ item.status || '-' }}</span>
        </div>

        <h3>摘要</h3>
        <p>{{ item.summary }}</p>

        <h3>商业模式</h3>
        <pre>{{ item.businessModel || '-' }}</pre>

        <h3>AI 工具</h3>
        <pre>{{ item.aiTools || '-' }}</pre>

        <h3>成果</h3>
        <pre>{{ item.outcome || '-' }}</pre>
      </article>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getCaseDetail } from '@/api/case'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})

const loading = ref(false)
const error = ref('')
const item = ref({})

onMounted(async () => {
  loading.value = true
  try {
    item.value = await getCaseDetail(props.id)
  } catch (err) {
    error.value = err.message || '案例详情加载失败'
  } finally {
    loading.value = false
  }
})
</script>
