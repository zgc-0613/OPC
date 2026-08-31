<template>
  <div class="page-stack case-index-page archive-workspace-page">
    <section class="panel filter-panel case-filter-panel scroll-reveal" @pointermove="handleCaseSpotlight">
      <div class="section-header">
        <div>
          <h2>案例检索</h2>
          <p>按地区、类型和关键词快速定位 AI + OPC / 一人公司案例。</p>
        </div>
        <span class="analysis-badge">{{ cases.length }} records</span>
      </div>

      <div class="case-summary-strip">
        <div>
          <span>案例总量</span>
          <strong>{{ allCases.length }}</strong>
        </div>
        <div>
          <span>当前结果</span>
          <strong>{{ cases.length }}</strong>
        </div>
        <div>
          <span>覆盖地区</span>
          <strong>{{ coveredRegionCount }}</strong>
        </div>
        <div>
          <span>案例类型</span>
          <strong>{{ usedCategoryCount }}</strong>
        </div>
      </div>
      <p class="case-summary-note">统计口径：基于当前资料库记录，随筛选条件实时更新；类型字段按标签合并统计。</p>

      <div
        v-if="query.industryTagId"
        class="active-scope-filter"
        data-testid="active-industry-filter"
        role="status"
      >
        <div>
          <span>行业范围</span>
          <strong>{{ selectedIndustryLabel }}</strong>
        </div>
        <button class="button button-ghost" type="button" aria-label="清除行业筛选" @click="clearIndustryFilter">
          清除
        </button>
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
        <label>
          <span>排序方式</span>
          <div class="custom-select" :class="{ open: sortMenuOpen }">
            <button class="custom-select-trigger" type="button" @click="toggleSortMenu">
              <span>{{ selectedSortLabel }}</span>
              <b></b>
            </button>
            <div v-if="sortMenuOpen" class="custom-select-menu">
              <button
                v-for="item in sortOptions"
                :key="item.value"
                type="button"
                :class="{ active: sortMode === item.value }"
                @click="selectSort(item.value)"
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
              <span>{{ item.actorName || '未标注主体' }}</span>
              <span>{{ formatCaseDate(item.accessedAt) }}</span>
            </div>
            <div class="case-classification-row" aria-label="案例分类">
              <span class="case-taxonomy-tag case-taxonomy-major">{{ item.category || '未标注大类' }}</span>
              <span class="case-taxonomy-tag case-taxonomy-minor">{{ item.subcategory || '未标注小类' }}</span>
            </div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.summary || '暂无摘要' }}</p>
            <div v-if="formatTags(item.tags).length" class="chip-row case-tag-row">
              <span v-for="tag in formatTags(item.tags)" :key="tag" class="chip">{{ tag }}</span>
            </div>
          </div>
          <div class="case-index-side">
            <span class="visit-count-pill">{{ getCasePv(item.id) }} 次浏览</span>
            <span class="status-pill">{{ item.status || '-' }}</span>
          </div>
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
import { useRoute } from 'vue-router'
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
