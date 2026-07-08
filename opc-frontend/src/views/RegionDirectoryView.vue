<template>
  <div class="page-stack">
    <section class="panel filter-panel">
      <div class="section-header">
        <div>
          <span class="caption">regional index</span>
          <h2>地区目录</h2>
          <p>按省份查看政策与案例资料覆盖情况，数据来自当前后端接口。</p>
        </div>
        <span class="analysis-badge">{{ visibleRegions.length }} regions</span>
      </div>

      <div class="auto-filter-grid single-filter-grid">
        <label>
          <span>地区关键词</span>
          <input v-model.trim="keyword" placeholder="搜索省份、政策、案例或标签" />
        </label>
      </div>
    </section>

    <section class="panel">
      <div class="section-header compact-header">
        <div>
          <h2>省份资料覆盖</h2>
          <p>{{ resultText }}</p>
        </div>
      </div>

      <div v-if="loading" class="muted">正在加载地区数据...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="!visibleRegions.length" class="empty-state">
        <strong>暂无匹配地区</strong>
        <span>可以换一个省份、政策关键词或案例关键词试试。</span>
      </div>
      <div v-else class="region-index-grid">
        <RouterLink
          v-for="(item, index) in visibleRegions"
          :key="item.id || item.name"
          class="region-index-card"
          :style="{ '--i': index }"
          :to="{ path: '/policies', query: { regionId: item.id } }"
        >
          <span class="region-index-no">{{ String(index + 1).padStart(2, '0') }}</span>
          <div>
            <strong>{{ item.name }}</strong>
            <small>{{ item.level || 'province' }}</small>
          </div>
          <div class="region-index-bars">
            <div>
              <span>政策</span>
              <i :style="{ width: `${item.policyPercent}%` }"></i>
              <b>{{ item.policyCount }}</b>
            </div>
            <div>
              <span>案例</span>
              <i :style="{ width: `${item.casePercent}%` }"></i>
              <b>{{ item.caseCount }}</b>
            </div>
          </div>
          <em>{{ item.totalCount }}</em>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getCases } from '@/api/case'
import { getPolicies } from '@/api/policy'
import { getRegions } from '@/api/region'

const loading = ref(false)
const error = ref('')
const keyword = ref('')
const regions = ref([])
const policies = ref([])
const cases = ref([])

const regionRows = computed(() => {
  const policyMap = countByRegion(policies.value)
  const caseMap = countByRegion(cases.value)
  const maxPolicy = Math.max(...Array.from(policyMap.values()), 1)
  const maxCase = Math.max(...Array.from(caseMap.values()), 1)

  return regions.value
    .filter((region) => region.name !== '中国' && region.level !== 'country')
    .map((region) => {
      const policyCount = policyMap.get(region.id) || 0
      const caseCount = caseMap.get(region.id) || 0
      return {
        ...region,
        policyCount,
        caseCount,
        totalCount: policyCount + caseCount,
        policyPercent: Math.max(policyCount ? 8 : 0, Math.round((policyCount / maxPolicy) * 100)),
        casePercent: Math.max(caseCount ? 8 : 0, Math.round((caseCount / maxCase) * 100)),
      }
    })
    .sort((a, b) => b.totalCount - a.totalCount || (a.sortOrder || 0) - (b.sortOrder || 0))
})

const visibleRegions = computed(() => {
  const text = keyword.value.toLowerCase()
  if (!text) {
    return regionRows.value
  }
  return regionRows.value.filter((region) => {
    const relatedPolicies = policies.value.filter((item) => item.regionId === region.id)
    const relatedCases = cases.value.filter((item) => item.regionId === region.id)
    return [region.name, region.level, ...relatedPolicies.map(searchText), ...relatedCases.map(searchText)]
      .join(' ')
      .toLowerCase()
      .includes(text)
  })
})

const resultText = computed(() => {
  const total = regionRows.value.filter((item) => item.totalCount > 0).length
  return keyword.value
    ? `当前关键词「${keyword.value}」匹配 ${visibleRegions.value.length} 个地区。`
    : `当前 ${total} 个地区已有政策或案例资料。`
})

function countByRegion(list) {
  const map = new Map()
  list.forEach((item) => {
    if (!item.regionId) {
      return
    }
    map.set(item.regionId, (map.get(item.regionId) || 0) + 1)
  })
  return map
}

function searchText(item) {
  return [item.title, item.summary, item.tags, item.category, item.policyType].filter(Boolean).join(' ')
}

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const [regionList, policyList, caseList] = await Promise.all([getRegions(), getPolicies(), getCases()])
    regions.value = regionList
    policies.value = policyList
    cases.value = caseList
  } catch (err) {
    error.value = err.message || '地区数据加载失败'
  } finally {
    loading.value = false
  }
})
</script>
