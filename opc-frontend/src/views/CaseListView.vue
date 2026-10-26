<template>
  <div class="case-explorer">
    <div class="case-explorer-main">
      <form class="policy-search" role="search" @submit.prevent="runSearch">
        <Search :size="25" aria-hidden="true" />
        <label class="sr-only" for="case-keyword">搜索案例关键词</label>
        <input id="case-keyword" v-model.trim="query.keyword" placeholder="搜索案例、创业主体、行业或 AI 工具..." />
        <button type="submit">搜索</button>
      </form>
      <div class="policy-explorer-filters">
        <label><span>地区</span><select v-model="query.regionId"><option value="">全部地区</option><option v-for="region in visibleRegions" :key="region.id" :value="String(region.id)">{{ region.name }}</option></select></label>
        <label><span>案例类型</span><select v-model="query.category"><option v-for="item in categoryOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label><span>排序方式</span><select v-model="sortMode"><option v-for="item in sortOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
      </div>
      <div v-if="query.industryTagId" class="case-active-filter" data-testid="active-industry-filter" role="status">行业：{{ selectedIndustryLabel }} <button type="button" @click="clearIndustryFilter">清除</button></div>
      <div class="policy-explorer-status"><span>{{ resultText }}</span><button v-if="hasActiveFilter" type="button" @click="resetFilters">清空筛选</button></div>
      <div v-if="error" class="policy-explorer-error" role="alert">{{ error }}</div>
      <div v-else-if="loading" class="policy-explorer-empty" role="status">正在同步案例资料…</div>
      <div v-else-if="!cases.length" class="policy-explorer-empty">暂无匹配案例。试试调整关键词或筛选条件。</div>
      <div v-else class="case-explorer-list">
        <button v-for="item in paginatedCases" :key="item.id" class="case-explorer-row" :class="{ 'is-selected': selectedCase?.id === item.id }" type="button" :aria-pressed="selectedCase?.id === item.id" @click="selectedCaseId = item.id">
          <span class="case-explorer-row-icon"><BriefcaseBusiness :size="20" /></span>
          <span class="case-explorer-row-content"><span class="case-explorer-row-meta">{{ item.regionName || '地区未标注' }} · {{ item.category || '类型未标注' }} · {{ formatCaseDate(item.accessedAt) }}</span><strong>{{ item.title }}</strong><span class="case-explorer-row-summary">{{ item.summary || '暂无摘要，打开详情可查看完整记录。' }}</span></span>
          <ArrowRight :size="18" />
        </button>
      </div>
      <div v-if="cases.length" class="case-pagination" aria-label="案例分页">
        <button type="button" :disabled="currentPage === 1" @click="goToPage(currentPage - 1)">上一页</button>
        <button v-for="page in paginationPages" :key="page" type="button" :class="{ active: page === currentPage }" @click="goToPage(page)">{{ page }}</button>
        <button type="button" :disabled="currentPage === totalPages" @click="goToPage(currentPage + 1)">下一页</button>
        <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
      </div>
      <p class="policy-explorer-note">案例根据已收录公开资料整理，详情请结合完整记录核对。</p>
    </div>

    <aside class="policy-preview case-preview" aria-label="案例预览">
      <template v-if="selectedCase">
        <div class="policy-preview-top"><span>{{ selectedCase.category || '案例资料' }}</span><span>案例预览</span></div>
        <h2>{{ selectedCase.title }}</h2>
        <div class="policy-preview-meta"><span>{{ selectedCase.actorName || '主体未标注' }}</span><span>{{ selectedCase.regionName || '地区未标注' }}</span></div>
        <div class="policy-preview-region">{{ selectedCase.subcategory || '细分类型未标注' }}</div>
        <section class="policy-preview-section"><h3>摘要</h3><p>{{ selectedCase.summary || '暂无摘要，请打开完整案例详情。' }}</p></section>
        <section v-if="selectedCase.businessModel" class="policy-preview-section"><h3>商业模式</h3><p>{{ selectedCase.businessModel }}</p></section>
        <section v-if="selectedCase.outcome" class="policy-preview-section"><h3>实践成果</h3><p>{{ selectedCase.outcome }}</p></section>
        <RouterLink class="policy-preview-detail-link" :to="'/cases/' + selectedCase.id"><BriefcaseBusiness :size="18" />查看完整详情 <ArrowRight :size="18" /></RouterLink>
      </template>
      <div v-else class="policy-preview-placeholder"><BriefcaseBusiness :size="32" /><strong>案例详情将在这里显示</strong><p>选择左侧案例，查看摘要与实践信息。</p></div>
    </aside>
  </div>
</template>
<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight, BriefcaseBusiness, Search } from 'lucide-vue-next'
import { getCases } from '@/api/case'
import { getRegions } from '@/api/region'
import { getVisitRankings } from '@/api/visit'
import { recordSearchKeyword } from '@/api/searchLog'
import { getIndustryTags } from '@/api/tag'

const route = useRoute()
const loading = ref(false)
const error = ref('')
const cases = ref([])
const allCases = ref([])
const regions = ref([])
const industryTags = ref([])
const caseVisitRankings = ref([])
const currentPage = ref(1)
const selectedCaseId = ref(null)
const regionMenuOpen = ref(false)
const categoryMenuOpen = ref(false)
const sortMenuOpen = ref(false)
const sortMode = ref('latest')
const pageSize = 10
let revealObserver
let searchLogTimer = null
let casesLoaded = false
let loadedIndustryTagId = ''
const query = reactive({
  keyword: route.query.keyword ? String(route.query.keyword).slice(0, 100) : '',
  regionId: route.query.regionId ? String(route.query.regionId) : '',
  industryTagId: normalizePositiveInteger(route.query.industryTagId),
  category: route.query.category ? String(route.query.category).slice(0, 100) : '',
})

const sortOptions = [
  { label: '最新收录', value: 'latest' },
  { label: '最早收录', value: 'oldest' },
  { label: '浏览最多', value: 'popular' },
  { label: '标题顺序', value: 'title' },
]

const majorCategories = ['内容创作', '商业增长', '软件工具', '教育人才', '产业应用', '创业支撑']

const categoryOptions = computed(() => {
  const base = [{ label: '全部类型', value: '' }]
  return base.concat(majorCategories.map((name) => ({ label: name, value: name })))
})

const visibleRegions = computed(() => {
  const usedRegionIds = new Set(allCases.value.map((item) => item.regionId).filter(Boolean))
  if (!usedRegionIds.size) {
    return regions.value.slice(0, 24)
  }
  return regions.value.filter((region) => usedRegionIds.has(region.id)).slice(0, 24)
})

const hasActiveFilter = computed(() => Boolean(query.keyword || query.regionId || query.industryTagId || query.category))

const selectedRegionLabel = computed(() => {
  if (!query.regionId) {
    return '全部地区'
  }
  return regions.value.find((region) => region.id === Number(query.regionId))?.name || '全部地区'
})

const selectedCategoryLabel = computed(() => query.category || '全部类型')

const selectedIndustryLabel = computed(() => {
  const selectedId = Number(query.industryTagId)
  return industryTags.value.find((item) => Number(item.tagId ?? item.id) === selectedId)?.name
    || `行业标签 #${query.industryTagId}`
})

const selectedSortLabel = computed(() => sortOptions.find((item) => item.value === sortMode.value)?.label || '最新收录')

const coveredRegionCount = computed(() => new Set(allCases.value.map((item) => item.regionId).filter(Boolean)).size)

const usedCategoryCount = computed(() => new Set(allCases.value.map((item) => item.category).filter(Boolean)).size)

const totalPages = computed(() => Math.max(1, Math.ceil(cases.value.length / pageSize)))

const sortedCases = computed(() => {
  const items = [...cases.value]
  if (sortMode.value === 'oldest') {
    return items.sort((left, right) => compareCaseDate(left, right, 'asc'))
  }
  if (sortMode.value === 'popular') {
    return items.sort((left, right) => {
      const visitDifference = getCasePv(right.id) - getCasePv(left.id)
      return visitDifference || compareCaseDate(left, right, 'desc')
    })
  }
  if (sortMode.value === 'title') {
    return items.sort((left, right) => {
      const titleDifference = String(left.title || '').localeCompare(String(right.title || ''), 'zh-CN', {
        numeric: true,
        sensitivity: 'base',
      })
      return titleDifference || compareCaseDate(left, right, 'desc')
    })
  }
  return items.sort((left, right) => compareCaseDate(left, right, 'desc'))
})

const paginatedCases = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return sortedCases.value.slice(start, start + pageSize)
})

const selectedCase = computed(() => paginatedCases.value.find((item) => item.id === selectedCaseId.value) || paginatedCases.value[0] || null)

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
  if (query.industryTagId) {
    parts.push(`行业：${selectedIndustryLabel.value}`)
  }
  return parts.length ? `当前筛选 ${parts.join(' / ')}，共 ${cases.value.length} 条。` : `当前展示全部案例，共 ${cases.value.length} 条。`
})

const caseVisitMap = computed(() => {
  const map = new Map()
  caseVisitRankings.value.forEach((item) => {
    map.set(Number(item.targetId), Number(item.pv || 0))
  })
  return map
})

async function loadCases() {
  loading.value = true
  error.value = ''
  try {
    if (!casesLoaded || loadedIndustryTagId !== query.industryTagId) {
      allCases.value = await getCases(caseRequestParams())
      loadedIndustryTagId = query.industryTagId
      casesLoaded = true
    }
    cases.value = filterCases(allCases.value)
    currentPage.value = 1
  } catch (err) {
    error.value = '案例资料暂时无法读取，请确认数据库服务是否运行。'
  } finally {
    loading.value = false
    await nextTick()
    setupScrollReveal()
  }
}

function resetFilters() {
  query.keyword = ''
  query.regionId = ''
  query.industryTagId = ''
  query.category = ''
  regionMenuOpen.value = false
  categoryMenuOpen.value = false
  sortMenuOpen.value = false
  currentPage.value = 1
}

function clearIndustryFilter() {
  query.industryTagId = ''
}

function normalizePositiveInteger(value) {
  const raw = value == null ? '' : String(value).trim()
  if (!/^[1-9]\d*$/.test(raw)) {
    return ''
  }
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) ? String(parsed) : ''
}

function caseRequestParams() {
  return query.industryTagId ? { industryTagId: Number(query.industryTagId) } : {}
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
  return [
    item.title,
    item.articleTitle,
    item.summary,
    item.actorName,
    item.regionName,
    item.category,
    item.subcategory,
    item.tags,
  ].filter(Boolean).join(' ')
}

function toggleRegionMenu() {
  regionMenuOpen.value = !regionMenuOpen.value
  if (regionMenuOpen.value) {
    categoryMenuOpen.value = false
    sortMenuOpen.value = false
  }
}

function toggleCategoryMenu() {
  categoryMenuOpen.value = !categoryMenuOpen.value
  if (categoryMenuOpen.value) {
    regionMenuOpen.value = false
    sortMenuOpen.value = false
  }
}

function toggleSortMenu() {
  sortMenuOpen.value = !sortMenuOpen.value
  if (sortMenuOpen.value) {
    regionMenuOpen.value = false
    categoryMenuOpen.value = false
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

function selectSort(value) {
  sortMode.value = value
  sortMenuOpen.value = false
  currentPage.value = 1
  nextTick(setupScrollReveal)
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

function getCasePv(id) {
  return caseVisitMap.value.get(Number(id)) || 0
}

function compareCaseDate(left, right, direction) {
  const leftTime = parseCaseDate(left.accessedAt)
  const rightTime = parseCaseDate(right.accessedAt)
  if (leftTime === null && rightTime === null) {
    return compareCaseId(left, right, direction)
  }
  if (leftTime === null) {
    return 1
  }
  if (rightTime === null) {
    return -1
  }
  const dateDifference = direction === 'asc' ? leftTime - rightTime : rightTime - leftTime
  return dateDifference || compareCaseId(left, right, direction)
}

function compareCaseId(left, right, direction) {
  const leftId = Number(left.id || 0)
  const rightId = Number(right.id || 0)
  return direction === 'asc' ? leftId - rightId : rightId - leftId
}

function parseCaseDate(value) {
  if (!value) {
    return null
  }
  const timestamp = Date.parse(String(value))
  return Number.isNaN(timestamp) ? null : timestamp
}

function formatCaseDate(value) {
  const date = String(value || '').slice(0, 10)
  const match = date.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  return match ? `收录 ${match[1]}.${match[2]}.${match[3]}` : '收录日期待补'
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
    window.clearTimeout(searchLogTimer)
    keywordTimer = window.setTimeout(async () => {
      await loadCases()
      reportCaseSearch()
    }, 260)
  },
)

watch(
  () => [query.regionId, query.industryTagId, query.category],
  loadCases,
)

function runSearch() {
  window.clearTimeout(keywordTimer)
  loadCases()
  reportCaseSearch()
}

onMounted(async () => {
  await nextTick()
  setupScrollReveal()
  loading.value = true
  error.value = ''
  try {
    const [regionList, industryList, caseList] = await Promise.all([
      getRegions(),
      getIndustryTags().catch(() => []),
      getCases(caseRequestParams()),
    ])
    const visitRankingList = await getVisitRankings({ targetType: 'case', limit: 200 }).catch(() => [])
    regions.value = regionList
    industryTags.value = industryList
    allCases.value = caseList
    casesLoaded = true
    loadedIndustryTagId = query.industryTagId
    caseVisitRankings.value = visitRankingList || []
    cases.value = filterCases(caseList)
    currentPage.value = 1
  } catch (err) {
    error.value = '案例资料暂时无法读取，请确认数据库服务是否运行。'
  } finally {
    loading.value = false
    await nextTick()
    setupScrollReveal()
  }
})

onUnmounted(() => {
  window.clearTimeout(keywordTimer)
  window.clearTimeout(searchLogTimer)
  revealObserver?.disconnect()
})

function reportCaseSearch() {
  const keyword = query.keyword.trim()
  if (keyword.length < 2) {
    return
  }

  searchLogTimer = window.setTimeout(() => {
    recordSearchKeyword({
      keyword,
      searchScope: 'case',
      resultCount: cases.value.length,
      pagePath: '/cases',
    }).catch(() => {})
  }, 700)
}
</script>
