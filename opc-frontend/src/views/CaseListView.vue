<template>
  <div class="page-stack case-index-page">
    <section class="panel filter-panel case-filter-panel scroll-reveal" @pointermove="handleCaseSpotlight">
      <div class="section-header">
        <div>
          <span class="caption">case index</span>
          <h2>案例库</h2>
          <p>按地区、类型和关键词快速定位 AI + OPC / 一人公司案例。</p>
        </div>
        <span class="analysis-badge">{{ cases.length }} records</span>
      </div>

      <div class="case-summary-strip">
        <div>
          <span>案例总量</span>
          <strong>{{ allCases.length }}</strong>
          <small>当前资料库记录</small>
        </div>
        <div>
          <span>当前结果</span>
          <strong>{{ cases.length }}</strong>
          <small>随筛选实时变化</small>
        </div>
        <div>
          <span>覆盖地区</span>
          <strong>{{ coveredRegionCount }}</strong>
          <small>存在案例记录</small>
        </div>
        <div>
          <span>案例类型</span>
          <strong>{{ usedCategoryCount }}</strong>
          <small>按类型字段统计</small>
        </div>
      </div>

      <div class="auto-filter-grid">
        <label>
          <span>关键词检索</span>
          <input v-model.trim="query.keyword" placeholder="搜索标题、摘要、主体或标签" />
        </label>
        <label>
          <span>地区</span>
          <div class="custom-select" :class="{ open: regionMenuOpen }">
            <button class="custom-select-trigger" type="button" @click="toggleRegionMenu">
              <span>{{ selectedRegionLabel }}</span>
              <b></b>
            </button>
            <div v-if="regionMenuOpen" class="custom-select-menu">
              <button type="button" :class="{ active: !query.regionId }" @click="selectRegion('')">
                全部地区
              </button>
              <button
                v-for="region in visibleRegions"
                :key="region.id"
                type="button"
                :class="{ active: query.regionId === String(region.id) }"
                @click="selectRegion(region.id)"
              >
                {{ region.name }}
              </button>
            </div>
          </div>
        </label>
        <label>
          <span>案例类型</span>
          <div class="custom-select" :class="{ open: categoryMenuOpen }">
            <button class="custom-select-trigger" type="button" @click="toggleCategoryMenu">
              <span>{{ selectedCategoryLabel }}</span>
              <b></b>
            </button>
            <div v-if="categoryMenuOpen" class="custom-select-menu">
              <button
                v-for="item in categoryOptions"
                :key="item.value"
                type="button"
                :class="{ active: query.category === item.value }"
                @click="selectCategory(item.value)"
              >
                {{ item.label }}
              </button>
            </div>
          </div>
        </label>
      </div>
    </section>

    <section class="panel case-index-panel scroll-reveal" @pointermove="handleCaseSpotlight">
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
        <RouterLink
          v-for="(item, index) in paginatedCases"
          :key="item.id"
          class="case-index-row scroll-reveal"
          :style="{ '--i': index }"
          :to="`/cases/${item.id}`"
        >
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
      <div v-if="cases.length" class="case-pagination" aria-label="案例分页">
        <button type="button" :disabled="currentPage === 1" @click="goToPage(currentPage - 1)">上一页</button>
        <button
          v-for="page in paginationPages"
          :key="page"
          type="button"
          :class="{ active: page === currentPage }"
          @click="goToPage(page)"
        >
          {{ page }}
        </button>
        <button type="button" :disabled="currentPage === totalPages" @click="goToPage(currentPage + 1)">下一页</button>
        <span>第 {{ currentPage }} / {{ totalPages }} 页，每页 10 条</span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { getCases } from '@/api/case'
import { getRegions } from '@/api/region'

const loading = ref(false)
const error = ref('')
const cases = ref([])
const allCases = ref([])
const regions = ref([])
const currentPage = ref(1)
const regionMenuOpen = ref(false)
const categoryMenuOpen = ref(false)
const pageSize = 10
let revealObserver
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

const selectedRegionLabel = computed(() => {
  if (!query.regionId) {
    return '全部地区'
  }
  return regions.value.find((region) => region.id === Number(query.regionId))?.name || '全部地区'
})

const selectedCategoryLabel = computed(() => query.category || '全部类型')

const coveredRegionCount = computed(() => new Set(allCases.value.map((item) => item.regionId).filter(Boolean)).size)

const usedCategoryCount = computed(() => new Set(allCases.value.map((item) => item.category).filter(Boolean)).size)

const totalPages = computed(() => Math.max(1, Math.ceil(cases.value.length / pageSize)))

const paginatedCases = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return cases.value.slice(start, start + pageSize)
})

const paginationPages = computed(() => {
  const total = totalPages.value
  const start = Math.max(1, Math.min(currentPage.value - 2, total - 4))
  const end = Math.min(total, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

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
    if (!allCases.value.length) {
      allCases.value = await getCases()
    }
    cases.value = filterCases(allCases.value)
    currentPage.value = 1
  } catch (err) {
    error.value = err.message || '案例数据加载失败'
  } finally {
    loading.value = false
    await nextTick()
    setupScrollReveal()
  }
}

function resetFilters() {
  query.keyword = ''
  query.regionId = ''
  query.category = ''
  regionMenuOpen.value = false
  categoryMenuOpen.value = false
  currentPage.value = 1
}

function filterCases(list) {
  const keyword = query.keyword.trim().toLowerCase()
  const regionId = query.regionId ? Number(query.regionId) : null
  return list.filter((item) => {
    if (regionId && Number(item.regionId) !== regionId) {
      return false
    }
    if (query.category && item.category !== query.category) {
      return false
    }
    if (keyword && !caseSearchText(item).toLowerCase().includes(keyword)) {
      return false
    }
    return true
  })
}

function caseSearchText(item) {
  return [item.title, item.summary, item.actorName, item.regionName, item.category, item.tags].filter(Boolean).join(' ')
}

function toggleRegionMenu() {
  regionMenuOpen.value = !regionMenuOpen.value
  if (regionMenuOpen.value) {
    categoryMenuOpen.value = false
  }
}

function toggleCategoryMenu() {
  categoryMenuOpen.value = !categoryMenuOpen.value
  if (categoryMenuOpen.value) {
    regionMenuOpen.value = false
  }
}

function selectRegion(regionId) {
  query.regionId = regionId ? String(regionId) : ''
  regionMenuOpen.value = false
}

function selectCategory(category) {
  query.category = category
  categoryMenuOpen.value = false
}

function goToPage(page) {
  currentPage.value = Math.min(Math.max(page, 1), totalPages.value)
  nextTick(setupScrollReveal)
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

function handleCaseSpotlight(event) {
  const target = event.target.closest('.case-filter-panel, .case-summary-strip div, .case-index-panel, .case-index-row')
  if (!target) {
    return
  }
  const rect = target.getBoundingClientRect()
  target.style.setProperty('--spotlight-x', `${event.clientX - rect.left}px`)
  target.style.setProperty('--spotlight-y', `${event.clientY - rect.top}px`)
}

function setupScrollReveal() {
  revealObserver?.disconnect()
  const items = document.querySelectorAll('.route-case-list .scroll-reveal')
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    items.forEach((item) => item.classList.add('is-visible'))
    return
  }

  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          revealObserver.unobserve(entry.target)
        }
      })
    },
    {
      threshold: 0.12,
      rootMargin: '0px 0px -70px',
    },
  )

  items.forEach((item) => revealObserver.observe(item))
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
  await nextTick()
  setupScrollReveal()
  const [regionList, caseList] = await Promise.all([getRegions(), getCases()])
  regions.value = regionList
  allCases.value = caseList
  cases.value = filterCases(caseList)
  currentPage.value = 1
  await nextTick()
  setupScrollReveal()
})

onUnmounted(() => {
  window.clearTimeout(keywordTimer)
  revealObserver?.disconnect()
})
</script>
