<template>
  <div class="page-stack policy-index-page">
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
          <span class="caption">policy index</span>
          <h2>政策检索</h2>
          <p>点击地区或类型即可自动筛选政策，关键词输入后自动搜索。</p>
        </div>
        <button class="button button-export" type="button" @click="exportPolicies">导出政策 Excel</button>
      </div>

      <div class="policy-summary-strip">
        <div>
          <span>政策总量</span>
          <strong>{{ allPolicies.length }}</strong>
          <small>当前资料库记录</small>
        </div>
        <div>
          <span>当前结果</span>
          <strong>{{ policies.length }}</strong>
          <small>随筛选实时变化</small>
        </div>
        <div>
          <span>覆盖地区</span>
          <strong>{{ coveredRegionCount }}</strong>
          <small>存在政策记录</small>
        </div>
        <div>
          <span>政策类型</span>
          <strong>{{ usedPolicyTypeCount }}</strong>
          <small>字段与标签合并</small>
        </div>
      </div>

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
          <span>政策类型</span>
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
              <td><span class="status-pill">{{ policy.status || '-' }}</span></td>
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

const route = useRoute()
const loading = ref(false)
const error = ref('')
const policies = ref([])
const allPolicies = ref([])
const regions = ref([])
const currentPage = ref(1)
const regionMenuOpen = ref(false)
const policyTypeMenuOpen = ref(false)
const pageSize = 10
let revealObserver
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

const policyTypeKeywords = {
  comprehensive: ['综合政策', '综合', '规划', '行动方案', '指导意见', '实施意见'],
  computing_support: ['算力支持', '算力', '计算资源', '智算', '数据中心', '模型'],
  funding_subsidy: ['资金补贴', '资金', '补贴', '奖励', '扶持', '基金', '专项资金'],
  scenario_demand: ['场景需求', '场景', '应用场景', '需求', '揭榜挂帅', '试点示范'],
  talent_service: ['人才服务', '人才', '培训', '高校', '团队', '创业服务'],
  investment: ['投资融资', '投资', '融资', '贷款', '创投', '风投', '基金'],
  other: ['其他'],
}

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

const hasActiveFilter = computed(() => Boolean(query.keyword || query.regionId || query.policyType))

const coveredRegionCount = computed(() => new Set(allPolicies.value.map((item) => item.regionId).filter(Boolean)).size)

const usedPolicyTypeCount = computed(() =>
  policyTypeOptions.filter((item) => item.value && allPolicies.value.some((policy) => matchesPolicyType(policy, item.value))).length,
)

const totalPages = computed(() => Math.max(1, Math.ceil(policies.value.length / pageSize)))

const paginatedPolicies = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return policies.value.slice(start, start + pageSize)
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
    parts.push(`类型/标签：${policyTypeLabels[query.policyType] || query.policyType}`)
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
    if (!allPolicies.value.length) {
      allPolicies.value = await getPolicies()
    }
    policies.value = filterPolicies(allPolicies.value)
    currentPage.value = 1
  } catch (err) {
    error.value = err.message || '政策数据加载失败'
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
  if (policy.policyType === selectedType) {
    return true
  }
  const text = policySearchText(policy)
  return (policyTypeKeywords[selectedType] || []).some((word) => text.includes(word))
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
  currentPage.value = 1
}

function toggleRegionMenu() {
  regionMenuOpen.value = !regionMenuOpen.value
  if (regionMenuOpen.value) {
    policyTypeMenuOpen.value = false
  }
}

function togglePolicyTypeMenu() {
  policyTypeMenuOpen.value = !policyTypeMenuOpen.value
  if (policyTypeMenuOpen.value) {
    regionMenuOpen.value = false
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

function goToPage(page) {
  currentPage.value = Math.min(Math.max(page, 1), totalPages.value)
  nextTick(setupScrollReveal)
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
    keywordTimer = window.setTimeout(loadPolicies, 260)
  },
)

watch(
  () => [query.regionId, query.policyType],
  loadPolicies,
)

onMounted(async () => {
  await nextTick()
  setupScrollReveal()
  const [regionList, policyList] = await Promise.all([getRegions(), getPolicies()])
  regions.value = regionList
  allPolicies.value = policyList
  query.regionId = route.query.regionId ? String(route.query.regionId) : ''
  if (query.regionId) {
    await loadPolicies()
  } else {
    policies.value = filterPolicies(policyList)
    currentPage.value = 1
  }
  await nextTick()
  setupScrollReveal()
})

onUnmounted(() => {
  window.clearTimeout(keywordTimer)
  revealObserver?.disconnect()
})
</script>
