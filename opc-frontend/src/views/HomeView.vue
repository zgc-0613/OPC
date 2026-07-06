<template>
  <div class="page-stack">
    <section class="hero-panel">
      <div class="hero-copy">
        <span class="caption">OPC POLICY INTELLIGENCE</span>
        <h2>把全国政策、案例和来源变成可检索的研究资料库</h2>
        <p>
          第一版聚焦政策检索、来源追溯、摘要标签和 Excel 导出，先服务会议汇报、论文整理和项目申报。
        </p>
        <div class="hero-actions">
          <RouterLink class="button" to="/policies">进入政策库</RouterLink>
          <RouterLink class="button button-ghost" to="/cases">查看案例库</RouterLink>
        </div>
      </div>

      <div class="signal-board" aria-label="平台数据状态">
        <div class="signal-board-head">
          <span>LIVE INDEX</span>
          <strong>{{ summary.policyCount ?? 0 }}</strong>
        </div>
        <div class="signal-lines">
          <span v-for="index in 16" :key="index"></span>
        </div>
      </div>
    </section>

    <section class="panel panel-raised">
      <div class="section-header compact-header">
        <div>
          <h2>数据概览</h2>
          <p>政策、案例、来源与地区覆盖情况。</p>
        </div>
      </div>

      <div v-if="loading" class="muted">正在加载统计数据...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="stats-grid">
        <div class="stat-card stat-card-hot">
          <span>政策记录</span>
          <strong>{{ summary.policyCount ?? 0 }}</strong>
          <small>Policy</small>
        </div>
        <div class="stat-card">
          <span>案例记录</span>
          <strong>{{ summary.caseCount ?? 0 }}</strong>
          <small>Case</small>
        </div>
        <div class="stat-card">
          <span>来源记录</span>
          <strong>{{ summary.sourceCount ?? 0 }}</strong>
          <small>Source</small>
        </div>
        <div class="stat-card">
          <span>地区覆盖</span>
          <strong>{{ summary.regionCount ?? 0 }}</strong>
          <small>Region</small>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="section-header">
        <div>
          <h2>最近更新</h2>
          <p>来自政策、案例和来源记录的最新数据。</p>
        </div>
      </div>

      <div v-if="!recentUpdates.length" class="muted">暂无最近更新。</div>
      <div v-else class="timeline-list">
        <div v-for="item in recentUpdates" :key="`${item.itemType}-${item.itemId}`" class="list-row">
          <div>
            <span class="caption">{{ item.itemType }}</span>
            <strong>{{ item.title }}</strong>
          </div>
          <span class="muted">{{ item.updatedDate || '-' }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDashboardSummary } from '@/api/dashboard'

const loading = ref(false)
const error = ref('')
const summary = ref({})

const recentUpdates = computed(() => summary.value.recentUpdates || [])

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    summary.value = await getDashboardSummary()
  } catch (err) {
    error.value = err.message || '统计数据加载失败'
  } finally {
    loading.value = false
  }
})
</script>
