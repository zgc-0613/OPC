<template>
  <div class="page-stack policy-index-page archive-workspace-page">
    <section class="panel filter-panel policy-filter-panel scroll-reveal" @pointermove="handlePolicySpotlight">
      <div class="policy-motion-field" aria-hidden="true">
        <span class="policy-ray policy-ray-one"></span>
        <span class="policy-ray policy-ray-two"></span>
        <span class="policy-ray policy-ray-three"></span>
        <span class="policy-node policy-node-one"></span>
        <span class="policy-node policy-node-two"></span>
        <span class="policy-node policy-node-three"></span>
        <span class="policy-scan"></span>
      </div>
      <div class="section-header">
        <div>
          <h2>政策检索</h2>
          <p>点击地区或涉及主题即可自动筛选政策，关键词输入后自动搜索。</p>
        </div>
        <button class="button button-export" type="button" @click="exportPolicies">导出 Excel</button>
      </div>

      <div class="policy-summary-strip">
        <div>
          <span>政策总量</span>
          <strong>{{ allPolicies.length }}</strong>
        </div>
        <div>
          <span>当前结果</span>
          <strong>{{ policies.length }}</strong>
        </div>
        <div>
          <span>覆盖地区</span>
          <strong>{{ coveredRegionCount }}</strong>
        </div>
        <div>
          <span>涉及主题</span>
          <strong>{{ usedPolicyTypeCount }}</strong>
        </div>
      </div>
      <p class="policy-summary-note">统计口径：主分类用于研究统计，涉及主题按七类标签筛选，一项政策可涉及多个主题。</p>

      <div class="auto-filter-grid">
        <label>
          <span>关键词检索</span>
          <input v-model.trim="query.keyword" placeholder="搜索标题、摘要、标签" />
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
          <span>涉及主题</span>
          <div class="custom-select" :class="{ open: policyTypeMenuOpen }">
            <button class="custom-select-trigger" type="button" @click="togglePolicyTypeMenu">
              <span>{{ selectedPolicyTypeLabel }}</span>
              <b></b>
            </button>
            <div v-if="policyTypeMenuOpen" class="custom-select-menu">
              <button
                v-for="item in policyTypeOptions"
                :key="item.value"
                type="button"
                :class="{ active: query.policyType === item.value }"
                @click="selectPolicyType(item.value)"
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

    <section class="panel policy-index-panel scroll-reveal" @pointermove="handlePolicySpotlight">
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
      <div v-else class="table-wrap policy-table-wrap">
        <table class="policy-table">
          <thead>
            <tr>
              <th>标题</th>
              <th>地区</th>
              <th>发文单位</th>
              <th>发布日期</th>
              <th>标签</th>
              <th>点击量</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="policy in paginatedPolicies" :key="policy.id" class="scroll-reveal">
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
              <td>
                <span class="visit-count-pill">{{ getPolicyPv(policy.id) }} 次</span>
              </td>
              <td><span class="status-pill">{{ materialNatureText(policy) }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="policies.length" class="policy-pagination" aria-label="政策分页">
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
import { getPolicies } from '@/api/policy'
import { getRegions } from '@/api/region'
import { exportPolicies } from '@/api/export'
import { getVisitRankings } from '@/api/visit'
import { recordSearchKeyword } from '@/api/searchLog'

const route = useRoute()
const loading = ref(false)
const error = ref('')
const policies = ref([])
const allPolicies = ref([])
const regions = ref([])
const policyVisitRankings = ref([])
const currentPage = ref(1)
const regionMenuOpen = ref(false)
const policyTypeMenuOpen = ref(false)
const sortMenuOpen = ref(false)
const sortMode = ref('latest')
const pageSize = 10
let revealObserver
let searchLogTimer = null
const query = reactive({
  keyword: '',
  regionId: '',
  policyType: '',
})

const sortOptions = [
  { label: '最新发布', value: 'latest' },
  { label: '最早发布', value: 'oldest' },
  { label: '浏览最多', value: 'popular' },
  { label: '标题顺序', value: 'title' },
]

const policyTypeOptions = [
  { label: '全部类型', value: '' },
  { label: '算力技术', value: 'computing_support' },
  { label: '财政激励', value: 'funding_subsidy' },
  { label: '场景开放', value: 'scenario_demand' },
  { label: '创业生态', value: 'ecology_support' },
  { label: '金融资本', value: 'investment' },
  { label: '制度治理', value: 'governance_market' },
  { label: '人才培育', value: 'talent_service' },
]

// The filter labels follow the revised four-character taxonomy. Keep legacy
// labels readable so previously imported policy records remain searchable.
const legacyPolicyTagAliases = {
  computing_support: ['算力与技术基础设施'],
  funding_subsidy: ['资金补贴与财政激励'],
  scenario_demand: ['场景与应用推广'],
  ecology_support: ['生态社区支持', '生态建设与社区支持'],
  investment: ['投融资与金融服务'],
  governance_market: ['制度治理与市场环境'],
  talent_service: ['人才与高校支持'],
}

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

const selectedRegionLabel = computed(() => {
  if (!query.regionId) {
    return '全部地区'
  }
  return regions.value.find((region) => region.id === Number(query.regionId))?.name || '全部地区'
})

const selectedPolicyTypeLabel = computed(() => policyTypeLabels[query.policyType] || '全部类型')

const selectedSortLabel = computed(() => sortOptions.find((item) => item.value === sortMode.value)?.label || '最新发布')

const hasActiveFilter = computed(() => Boolean(query.keyword || query.regionId || query.policyType))

const coveredRegionCount = computed(() => new Set(allPolicies.value.map((item) => item.regionId).filter(Boolean)).size)

const usedPolicyTypeCount = computed(() =>
  policyTypeOptions.filter((item) => item.value && allPolicies.value.some((policy) => matchesPolicyType(policy, item.value))).length,
)

const totalPages = computed(() => Math.max(1, Math.ceil(policies.value.length / pageSize)))

const sortedPolicies = computed(() => {
  const items = [...policies.value]
  if (sortMode.value === 'oldest') {
    return items.sort((left, right) => comparePolicyDate(left, right, 'asc'))
  }
  if (sortMode.value === 'popular') {
    return items.sort((left, right) => {
      const visitDifference = getPolicyPv(right.id) - getPolicyPv(left.id)
      return visitDifference || comparePolicyDate(left, right, 'desc')
    })
  }
  if (sortMode.value === 'title') {
    return items.sort((left, right) => {
      const titleDifference = String(left.title || '').localeCompare(String(right.title || ''), 'zh-CN', {
        numeric: true,
        sensitivity: 'base',
      })
      return titleDifference || comparePolicyDate(left, right, 'desc')
    })
  }
  return items.sort((left, right) => comparePolicyDate(left, right, 'desc'))
})

const paginatedPolicies = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return sortedPolicies.value.slice(start, start + pageSize)
})

const paginationPages = computed(() => {
  const total = totalPages.value
  const start = Math.max(1, Math.min(currentPage.value - 2, total - 4))
  const end = Math.min(total, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

const resultText = computed(() => {
  const parts = []
  if (query.policyType) {
    parts.push(`涉及主题：${policyTypeLabels[query.policyType] || query.policyType}`)
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

const policyVisitMap = computed(() => {
  const map = new Map()
  policyVisitRankings.value.forEach((item) => {
    map.set(Number(item.targetId), Number(item.pv || 0))
  })
  return map
})

async function loadPolicies() {
  loading.value = true
  error.value = ''
  try {
    if (!allPolicies.value.length) {
      allPolicies.value = await getPolicies()
    }
    policies.value = filterPolicies(allPolicies.value)
    currentPage.value = 1
  } catch (err) {
    error.value = '政策资料暂时无法读取，请确认数据库服务是否运行。'
  } finally {
    loading.value = false
    await nextTick()
    setupScrollReveal()
  }
}

function filterPolicies(list) {
  const keyword = query.keyword.trim().toLowerCase()
  const regionId = query.regionId ? Number(query.regionId) : null
  return list.filter((policy) => {
    if (regionId && Number(policy.regionId) !== regionId) {
      return false
    }
    if (query.policyType && !matchesPolicyType(policy, query.policyType)) {
      return false
    }
    if (keyword && !policySearchText(policy).toLowerCase().includes(keyword)) {
      return false
    }
    return true
  })
}

function matchesPolicyType(policy, selectedType) {
  if (!selectedType) {
    return true
  }
  const selectedLabel = policyTypeLabels[selectedType]
  const tags = splitPolicyTags(policy.tags)
  return tags.includes(selectedLabel) || legacyPolicyTagAliases[selectedType]?.some((label) => tags.includes(label))
}

function policySearchText(policy) {
  return [
    policy.title,
    policy.summary,
    policy.tags,
    policy.policyType,
    policyTypeLabels[policy.policyType],
    policy.issuingBody,
    policy.regionName,
  ]
    .filter(Boolean)
    .join(' ')
}

function resetFilters() {
  query.keyword = ''
  query.regionId = ''
  query.policyType = ''
  regionMenuOpen.value = false
  policyTypeMenuOpen.value = false
  sortMenuOpen.value = false
  currentPage.value = 1
}

function toggleRegionMenu() {
  regionMenuOpen.value = !regionMenuOpen.value
  if (regionMenuOpen.value) {
    policyTypeMenuOpen.value = false
    sortMenuOpen.value = false
  }
}

function togglePolicyTypeMenu() {
  policyTypeMenuOpen.value = !policyTypeMenuOpen.value
  if (policyTypeMenuOpen.value) {
    regionMenuOpen.value = false
    sortMenuOpen.value = false
  }
}

function toggleSortMenu() {
  sortMenuOpen.value = !sortMenuOpen.value
  if (sortMenuOpen.value) {
    regionMenuOpen.value = false
    policyTypeMenuOpen.value = false
  }
}

function selectRegion(regionId) {
  query.regionId = regionId ? String(regionId) : ''
  regionMenuOpen.value = false
}

function selectPolicyType(policyType) {
  query.policyType = policyType
  policyTypeMenuOpen.value = false
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
  return splitPolicyTags(tags).slice(0, 4)
}

function splitPolicyTags(tags) {
  if (!tags) {
    return []
  }
  return String(tags)
    .split(/[,，、]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

function materialNatureText(policy) {
  if (policy?.materialNatureLabel) return policy.materialNatureLabel
  if (policy?.materialNature === 'consultation_draft' || policy?.status === 'consultation') return '征求意见稿'
  if (policy?.materialNature === 'standard_reference') return '标准规范文件'
  if (policy?.materialNature === 'official_platform_service') return '官方平台/服务信息'
  if (policy?.materialNature === 'formal_policy') return policy.status === 'expired' ? '正式文件（失效）' : '正式文件'
  if (policy?.materialNature) return '其他资料'
  return policy?.status === 'published' ? '正式文件' : (policy?.status || '-')
}

function getPolicyPv(id) {
  return policyVisitMap.value.get(Number(id)) || 0
}

function comparePolicyDate(left, right, direction) {
  const leftTime = parsePolicyDate(left.publishDate)
  const rightTime = parsePolicyDate(right.publishDate)
  if (leftTime === null && rightTime === null) {
    return comparePolicyId(left, right, direction)
  }
  if (leftTime === null) {
    return 1
  }
  if (rightTime === null) {
    return -1
  }
  const dateDifference = direction === 'asc' ? leftTime - rightTime : rightTime - leftTime
  return dateDifference || comparePolicyId(left, right, direction)
}

function comparePolicyId(left, right, direction) {
  const leftId = Number(left.id || 0)
  const rightId = Number(right.id || 0)
  return direction === 'asc' ? leftId - rightId : rightId - leftId
}

function parsePolicyDate(value) {
  if (!value) {
    return null
  }
  const timestamp = Date.parse(String(value))
  return Number.isNaN(timestamp) ? null : timestamp
}

function handlePolicySpotlight(event) {
  const target = event.target.closest('.policy-filter-panel, .policy-summary-strip div, .policy-index-panel, .policy-table tbody tr')
  if (!target) {
    return
  }
  const rect = target.getBoundingClientRect()
  target.style.setProperty('--spotlight-x', `${event.clientX - rect.left}px`)
  target.style.setProperty('--spotlight-y', `${event.clientY - rect.top}px`)
}

function setupScrollReveal() {
  revealObserver?.disconnect()
  const items = document.querySelectorAll('.route-policy-list .scroll-reveal')
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
      await loadPolicies()
      reportPolicySearch()
    }, 260)
  },
)

watch(
  () => [query.regionId, query.policyType],
  loadPolicies,
)

onMounted(async () => {
  await nextTick()
  setupScrollReveal()
  loading.value = true
  error.value = ''
  try {
    const [regionList, policyList] = await Promise.all([
      getRegions(),
      getPolicies(),
    ])
    const visitRankingList = await getVisitRankings({ targetType: 'policy', limit: 200 }).catch(() => [])
    regions.value = regionList
    allPolicies.value = policyList
    policyVisitRankings.value = visitRankingList || []
    query.regionId = route.query.regionId ? String(route.query.regionId) : ''
    if (query.regionId) {
      await loadPolicies()
    } else {
      policies.value = filterPolicies(policyList)
      currentPage.value = 1
    }
  } catch (err) {
    error.value = '政策资料暂时无法读取，请确认数据库服务是否运行。'
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

function reportPolicySearch() {
  const keyword = query.keyword.trim()
  if (keyword.length < 2) {
    return
  }

  searchLogTimer = window.setTimeout(() => {
    recordSearchKeyword({
      keyword,
      searchScope: 'policy',
      resultCount: policies.value.length,
      pagePath: '/policies',
    }).catch(() => {})
  }, 700)
}
</script>
