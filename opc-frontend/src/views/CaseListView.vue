<template>
  <div class="page-stack">
    <section class="panel filter-panel">
      <div class="section-header">
        <div>
          <h2>案例库</h2>
          <p>展示 AI + OPC / 一人公司相关案例。</p>
        </div>
      </div>
    </section>

    <section class="panel">
      <div v-if="loading" class="muted">正在加载案例...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="!cases.length" class="muted">暂无案例数据。</div>
      <div v-else class="case-grid">
        <RouterLink v-for="item in cases" :key="item.id" class="list-row link-row" :to="`/cases/${item.id}`">
          <div>
            <span class="caption">{{ item.regionName || '-' }} / {{ item.category || '-' }}</span>
            <strong>{{ item.title }}</strong>
            <p>{{ item.summary || '暂无摘要' }}</p>
          </div>
          <span class="status-pill">{{ item.status || '-' }}</span>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getCases } from '@/api/case'

const loading = ref(false)
const error = ref('')
const cases = ref([])

onMounted(async () => {
  loading.value = true
  try {
    cases.value = await getCases()
  } catch (err) {
    error.value = err.message || '案例数据加载失败'
  } finally {
    loading.value = false
  }
})
</script>
