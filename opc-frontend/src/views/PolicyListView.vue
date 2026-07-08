<template>
  <div class="page-stack">
    <section class="panel filter-panel">
      <div class="section-header">
        <div>
          <span class="caption">policy index</span>
          <h2>政策检索</h2>
          <p>点击地区或类型即可自动筛选政策，关键词输入后自动搜索。</p>
        </div>
        <button class="button button-export" type="button" @click="exportPolicies">导出政策 Excel</button>
      </div>

      <div class="auto-filter-grid">
        <label>
          <span>关键词检索</span>
          <input v-model.trim="query.keyword" placeholder="搜索标题、摘要、标签" />
        </label>
        <label>
          <span>地区</span>
          <select v-model="query.regionId">
            <option value="">全部地区</option>
            <option v-for="region in visibleRegions" :key="region.id" :value="region.id">
              {{ region.name }}
            </option>
          </select>
        </label>
        <label>
          <span>政策类型</span>
          <select v-model="query.policyType">
            <option v-for="item in policyTypeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
        </label>
      </div>
    </section>

    <section class="panel">
      <div class="section-header compact-header">
        <div>
          <h2>政策索引</h2>
          <p>{{ resultText }}</p>
        </div>
        <button v-if="hasActiveFilter" class="button button-ghost" type="button" @click="resetFilters">
          清除筛选
        </button>
      </div>

      <div v-if="loading" class="muted">正在加载政策...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="!policies.length" class="empty-state">
        <strong>暂无匹配政策</strong>
        <span>可以换一个地区、类型或关键词试试。</span>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>标题</th>
              <th>地区</th>
              <th>发文单位</th>
              <th>发布日期</th>
              <th>标签</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="policy in policies" :key="policy.id">
              <td>
                <RouterLink :to="`/policies/${policy.id}`">{{ policy.title }}</RouterLink>
              </td>
              <td>{{ policy.regionName || '-' }}</td>
              <td>{{ policy.issuingBody || '-' }}</td>
              <td>{{ policy.publishDate || '-' }}</td>
              <td>
                <div v-if="formatTags(policy.tags).length" class="chip-row">
                  <span v-for="tag in formatTags(policy.tags)" :key="tag" class="chip">{{ tag }}</span>
                </div>
                <span v-else>-</span>
              </td>
              <td><span class="status-pill">{{ policy.status || '-' }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getPolicies } from '@/api/policy'
import { getRegions } from '@/api/region'
import { exportPolicies } from '@/api/export'

const route = useRoute()
const loading = ref(false)
const error = ref('')
const policies = ref([])
const allPolicies = ref([])
const regions = ref([])
const query = reactive({
  keyword: '',
  regionId: '',
  policyType: '',
})

const policyTypeOptions = [
  { label: '全部类型', value: '' },
  { label: '综合政策', value: 'comprehensive' },
  { label: '算力支持', value: 'computing_support' },
  { label: '资金补贴', value: 'funding_subsidy' },
  { label: '场景需求', value: 'scenario_demand' },
  { label: '人才服务', value: 'talent_service' },
  { label: '投资融资', value: 'investment' },
  { label: '其他', value: 'other' },
]

const policyTypeLabels = policyTypeOptions.reduce((map, item) => {
  if (item.value) {
    map[item.value] = item.label
  }
  return map
}, {})

const visibleRegions = computed(() => {
  const usedRegionIds = new Set(allPolicies.value.map((item) => item.regionId).filter(Boolean))
  if (!usedRegionIds.size) {
    return regions.value.slice(0, 24)
  }
  return regions.value.filter((region) => usedRegionIds.has(region.id)).slice(0, 24)
})

const hasActiveFilter = computed(() => Boolean(query.keyword || query.regionId || query.policyType))

const resultText = computed(() => {
  const parts = []
  if (query.policyType) {
    parts.push(`类型：${policyTypeLabels[query.policyType] || query.policyType}`)
  }
  const region = regions.value.find((item) => item.id === Number(query.regionId))
  if (region) {
    parts.push(`地区：${region.name}`)
  }
  if (query.keyword) {
    parts.push(`关键词：${query.keyword}`)
  }
  return parts.length ? `当前筛选 ${parts.join(' / ')}，共 ${policies.value.length} 条。` : `当前展示全部政策，共 ${policies.value.length} 条。`
})

async function loadPolicies() {
  loading.value = true
  error.value = ''
  try {
    const params = {
      keyword: query.keyword || undefined,
      regionId: query.regionId || undefined,
      policyType: query.policyType || undefined,
    }
    policies.value = await getPolicies(params)
  } catch (err) {
    error.value = err.message || '政策数据加载失败'
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.keyword = ''
  query.regionId = ''
  query.policyType = ''
}

function formatTags(tags) {
  if (!tags) {
    return []
  }
  return String(tags)
    .split(/[,，、]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
    .slice(0, 4)
}

let keywordTimer = null
watch(
  () => query.keyword,
  () => {
    window.clearTimeout(keywordTimer)
    keywordTimer = window.setTimeout(loadPolicies, 260)
  },
)

watch(
  () => [query.regionId, query.policyType],
  loadPolicies,
)

onMounted(async () => {
  const [regionList, policyList] = await Promise.all([getRegions(), getPolicies()])
  regions.value = regionList
  allPolicies.value = policyList
  query.regionId = route.query.regionId ? String(route.query.regionId) : ''
  if (query.regionId) {
    await loadPolicies()
  } else {
    policies.value = policyList
  }
})
</script>
