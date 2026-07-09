<template>
  <div class="page-stack region-directory-page">
    <section class="panel filter-panel region-hero-panel scroll-reveal" @pointermove="handleRegionSpotlight">
      <div class="section-header">
        <div>
          <span class="caption">regional index</span>
          <h2>地区目录</h2>
          <p>按省份查看政策与案例资料覆盖情况，数据随当前后端接口实时变化。</p>
        </div>
        <span class="analysis-badge">{{ visibleRegions.length }} 个地区</span>
      </div>

      <div class="region-summary-strip">
        <div>
          <span>收录地区</span>
          <strong>{{ regionRows.length }}</strong>
          <small>已排除上级地区</small>
        </div>
        <div>
          <span>已有资料地区</span>
          <strong>{{ coveredRegionCount }}</strong>
          <small>政策或案例至少一项</small>
        </div>
        <div>
          <span>政策资料</span>
          <strong>{{ policyTotal }}</strong>
          <small>按地区归集</small>
        </div>
        <div>
          <span>案例资料</span>
          <strong>{{ caseTotal }}</strong>
          <small>按地区归集</small>
        </div>
      </div>

      <div class="auto-filter-grid single-filter-grid">
        <label>
          <span>地区关键词</span>
          <input v-model.trim="keyword" placeholder="搜索省份、政策、案例或标签" />
        </label>
      </div>
    </section>

    <section class="panel region-directory-panel scroll-reveal">
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
      <div v-else class="region-index-grid" @pointermove="handleRegionSpotlight">
        <RouterLink
          v-for="(item, index) in visibleRegions"
          :key="item.id || item.name"
          class="region-index-card scroll-reveal"
          :style="{ '--i': index }"
          :to="{ path: '/policies', query: { regionId: item.id } }"
        >
          <span class="region-index-no">{{ String(index + 1).padStart(2, '0') }}</span>
          <div>
            <strong>{{ item.name }}</strong>
            <small>{{ regionAlias(item.name) }}</small>
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
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { getCases } from '@/api/case'
import { getPolicies } from '@/api/policy'
import { getRegions } from '@/api/region'

const loading = ref(false)
const error = ref('')
const keyword = ref('')
const regions = ref([])
const policies = ref([])
const cases = ref([])
let revealObserver

const policyTotal = computed(() => policies.value.length)
const caseTotal = computed(() => cases.value.length)

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

const coveredRegionCount = computed(() => regionRows.value.filter((item) => item.totalCount > 0).length)

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
  return keyword.value
    ? `当前关键词「${keyword.value}」匹配 ${visibleRegions.value.length} 个地区。`
    : `当前 ${coveredRegionCount.value} 个地区已有政策或案例资料。`
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

function regionAlias(name) {
  const aliases = {
    北京市: 'Beijing',
    天津市: 'Tianjin',
    河北省: 'Hebei',
    山西省: 'Shanxi',
    内蒙古自治区: 'Inner Mongolia',
    辽宁省: 'Liaoning',
    吉林省: 'Jilin',
    黑龙江省: 'Heilongjiang',
    上海市: 'Shanghai',
    江苏省: 'Jiangsu',
    浙江省: 'Zhejiang',
    安徽省: 'Anhui',
    福建省: 'Fujian',
    江西省: 'Jiangxi',
    山东省: 'Shandong',
    河南省: 'Henan',
    湖北省: 'Hubei',
    湖南省: 'Hunan',
    广东省: 'Guangdong',
    广西壮族自治区: 'Guangxi',
    海南省: 'Hainan',
    重庆市: 'Chongqing',
    四川省: 'Sichuan',
    贵州省: 'Guizhou',
    云南省: 'Yunnan',
    西藏自治区: 'Tibet',
    陕西省: 'Shaanxi',
    甘肃省: 'Gansu',
    青海省: 'Qinghai',
    宁夏回族自治区: 'Ningxia',
    新疆维吾尔自治区: 'Xinjiang',
    香港特别行政区: 'Hong Kong',
    澳门特别行政区: 'Macao',
    台湾省: 'Taiwan',
  }
  return aliases[name] || 'Region'
}

function handleRegionSpotlight(event) {
  const target = event.target.closest('.region-index-card, .region-hero-panel, .region-summary-strip div')
  if (!target) {
    return
  }
  const rect = target.getBoundingClientRect()
  target.style.setProperty('--spotlight-x', `${event.clientX - rect.left}px`)
  target.style.setProperty('--spotlight-y', `${event.clientY - rect.top}px`)
}

function setupScrollReveal() {
  revealObserver?.disconnect()
  const items = document.querySelectorAll('.route-region-directory .scroll-reveal')
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
      threshold: 0.14,
      rootMargin: '0px 0px -70px',
    },
  )

  items.forEach((item) => revealObserver.observe(item))
}

onMounted(async () => {
  await nextTick()
  setupScrollReveal()
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
    await nextTick()
    setupScrollReveal()
  }
})

onUnmounted(() => {
  revealObserver?.disconnect()
})
</script>
