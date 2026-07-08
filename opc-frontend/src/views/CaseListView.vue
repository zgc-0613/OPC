<template>
  <div class="page-stack">
    <section class="panel filter-panel">
      <div class="section-header">
        <div>
          <span class="caption">case index</span>
          <h2>案例库</h2>
          <p>按地区、类型和关键词快速定位 AI + OPC / 一人公司案例。</p>
        </div>
        <span class="analysis-badge">{{ cases.length }} records</span>
      </div>

      <div class="auto-filter-grid">
        <label>
          <span>关键词检索</span>
          <input v-model.trim="query.keyword" placeholder="搜索标题、摘要、主体或标签" />
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
          <span>案例类型</span>
          <select v-model="query.category">
            <option v-for="item in categoryOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
        </label>
      </div>
    </section>

    <section class="panel">
      <div class="section-header compact-header">
        <div>
          <h2>案例索引</h2>
          <p>{{ resultText }}</p>
        </div>
        <button v-if="hasActiveFilter" class="button button-ghost" type="button" @click="resetFilters">
          清除筛选
        </button>
      </div>

      <div v-if="loading" class="muted">正在加载案例...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="!cases.length" class="empty-state">
        <strong>暂无匹配案例</strong>
        <span>可以换一个地区、类型或关键词试试。</span>
      </div>
      <div v-else class="case-index-list">
        <RouterLink v-for="item in cases" :key="item.id" class="case-index-row" :to="`/cases/${item.id}`">
          <span class="case-index-no">{{ item.id }}</span>
          <div class="case-index-main">
            <div class="case-index-meta">
              <span>{{ item.regionName || '未标注地区' }}</span>
              <span>{{ item.category || '未标注类型' }}</span>
              <span>{{ item.actorName || '未标注主体' }}</span>
            </div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.summary || '暂无摘要' }}</p>
            <div v-if="formatTags(item.tags).length" class="chip-row">
              <span v-for="tag in formatTags(item.tags)" :key="tag" class="chip">{{ tag }}</span>
            </div>
          </div>
          <span class="status-pill">{{ item.status || '-' }}</span>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { getCases } from '@/api/case'
import { getRegions } from '@/api/region'

const loading = ref(false)
const error = ref('')
const cases = ref([])
const allCases = ref([])
const regions = ref([])
const query = reactive({
  keyword: '',
  regionId: '',
  category: '',
})

const categoryOptions = computed(() => {
  const names = Array.from(new Set(allCases.value.map((item) => item.category).filter(Boolean)))
  const base = [{ label: '全部类型', value: '' }]
  return base.concat(names.map((name) => ({ label: name, value: name })))
})

const visibleRegions = computed(() => {
  const usedRegionIds = new Set(allCases.value.map((item) => item.regionId).filter(Boolean))
  if (!usedRegionIds.size) {
    return regions.value.slice(0, 24)
  }
  return regions.value.filter((region) => usedRegionIds.has(region.id)).slice(0, 24)
})

const hasActiveFilter = computed(() => Boolean(query.keyword || query.regionId || query.category))

const resultText = computed(() => {
  const parts = []
  if (query.category) {
    parts.push(`类型：${query.category}`)
  }
  const region = regions.value.find((item) => item.id === Number(query.regionId))
  if (region) {
    parts.push(`地区：${region.name}`)
  }
  if (query.keyword) {
    parts.push(`关键词：${query.keyword}`)
  }
  return parts.length ? `当前筛选 ${parts.join(' / ')}，共 ${cases.value.length} 条。` : `当前展示全部案例，共 ${cases.value.length} 条。`
})

async function loadCases() {
  loading.value = true
  error.value = ''
  try {
    cases.value = await getCases({
      keyword: query.keyword || undefined,
      regionId: query.regionId || undefined,
      category: query.category || undefined,
    })
  } catch (err) {
    error.value = err.message || '案例数据加载失败'
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.keyword = ''
  query.regionId = ''
  query.category = ''
}

function formatTags(tags) {
  if (!tags) {
    return []
  }
  return String(tags)
    .split(/[,，、;；]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
    .slice(0, 6)
}

let keywordTimer = null
watch(
  () => query.keyword,
  () => {
    window.clearTimeout(keywordTimer)
    keywordTimer = window.setTimeout(loadCases, 260)
  },
)

watch(
  () => [query.regionId, query.category],
  loadCases,
)

onMounted(async () => {
  const [regionList, caseList] = await Promise.all([getRegions(), getCases()])
  regions.value = regionList
  allCases.value = caseList
  await loadCases()
})
</script>
