<template>
  <div class="analysis-workspace">
    <div class="analysis-workspace-nav" aria-label="资料分析视图">
      <RouterLink to="/analysis" :class="{ 'is-current': route.name === 'analysis-overview' }">资料概览</RouterLink>
      <RouterLink to="/analytics" :class="{ 'is-current': route.name === 'analytics-dashboard' }">研究指标</RouterLink>
      <RouterLink to="/regions" :class="{ 'is-current': route.name === 'region-directory' }">地区分布</RouterLink>
    </div>
    <component :is="currentView" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AnalysisOverviewView from '@/views/AnalysisOverviewView.vue'
import AnalyticsDashboardView from '@/views/AnalyticsDashboardView.vue'
import RegionDirectoryView from '@/views/RegionDirectoryView.vue'

const route = useRoute()
const currentView = computed(() => ({
  'analysis-overview': AnalysisOverviewView,
  'analytics-dashboard': AnalyticsDashboardView,
  'region-directory': RegionDirectoryView,
})[route.name] || AnalysisOverviewView)
</script>
