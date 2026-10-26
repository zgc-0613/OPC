<template>
  <div class="atlas-region-directory">
    <section class="atlas-region-command" aria-label="地区检索">
      <div class="atlas-region-command-head">
        <div>
          <span class="atlas-region-kicker">REGION INDEX / 地区目录</span>
          <h2>从地区，找到相关政策与案例</h2>
          <p>搜索省份或资料关键词，查看各地区的收录情况。</p>
        </div>
        <div class="atlas-region-compact-stats" aria-label="资料统计">
          <div><strong>{{ loading || error ? '—' : coveredRegionCount }}</strong><span>已有资料地区</span></div>
          <div><strong>{{ loading || error ? '—' : policyTotal }}</strong><span>政策资料</span></div>
          <div><strong>{{ loading || error ? '—' : caseTotal }}</strong><span>案例资料</span></div>
        </div>
      </div>

      <label class="atlas-region-search">
        <Search :size="21" aria-hidden="true" />
        <span class="sr-only">地区关键词</span>
        <input v-model.trim="keyword" type="search" placeholder="搜索省份、政策主题、案例或标签…" />
      </label>
      <div class="atlas-region-controls">
        <label class="atlas-region-covered-toggle"><input v-model="onlyCovered" type="checkbox" />只看有资料的地区</label>
        <label class="atlas-region-sort">排序 <select v-model="sortMode"><option value="coverage">资料最多</option><option value="name">地区名称</option></select></label>
        <span class="atlas-region-result-count">{{ resultText }}</span>
      </div>
    </section>

    <div v-if="error" class="atlas-region-alert" role="alert">
      <AlertCircle :size="20" aria-hidden="true" />
      <span>{{ error }}</span>
      <button type="button" @click="loadRegionData"><RotateCcw :size="15" />重新加载</button>
    </div>

    <section v-if="!error" class="atlas-region-explorer" aria-label="地区资料浏览">
      <div class="atlas-region-list-panel">
        <div class="atlas-region-pane-heading"><div><span>01 / BROWSE</span><h3>地区列表</h3></div><small v-if="!error && !loading">{{ visibleRegions.length }} 个地区</small></div>
        <div v-if="loading" class="atlas-region-list-state">正在加载地区资料…</div>
        <div v-else-if="!visibleRegions.length" class="atlas-region-list-state">暂无匹配地区，试试其他关键词或关闭筛选。</div>
        <div v-else class="atlas-region-list">
          <button v-for="(item, index) in visibleRegions" :key="item.id || item.name" type="button" class="atlas-region-row" :class="{ 'is-selected': selectedRegion?.id === item.id }" :aria-pressed="selectedRegion?.id === item.id" @click="selectedRegionId = item.id">
            <span class="atlas-region-row-number">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="atlas-region-row-name"><strong>{{ item.name }}</strong><small>{{ regionAlias(item.name) }}</small></span>
            <span class="atlas-region-row-count"><b>{{ item.policyCount }}</b> 政策 <i>·</i> <b>{{ item.caseCount }}</b> 案例</span>
            <ArrowRight :size="17" aria-hidden="true" />
          </button>
        </div>
      </div>

      <aside class="atlas-region-preview" aria-label="地区资料预览">
        <template v-if="selectedRegion">
          <div class="atlas-region-pane-heading"><div><span>02 / REGION PROFILE</span><h3>地区资料概览</h3></div><MapPin :size="20" aria-hidden="true" /></div>
          <div class="atlas-region-preview-identity"><span>当前地区</span><h2>{{ selectedRegion.name }}</h2><small>{{ regionAlias(selectedRegion.name) }}</small></div>
          <p class="atlas-region-preview-total">当前收录 <strong>{{ selectedRegion.totalCount }}</strong> 条相关资料</p>
          <div class="atlas-region-preview-links">
            <RouterLink :to="{ path: '/policies', query: { regionId: selectedRegion.id } }"><FileText :size="22" /><span><small>政策资料</small><strong>{{ selectedRegion.policyCount }} 条</strong></span><ArrowRight :size="18" /></RouterLink>
            <RouterLink :to="{ path: '/cases', query: { regionId: selectedRegion.id } }"><BriefcaseBusiness :size="22" /><span><small>案例资料</small><strong>{{ selectedRegion.caseCount }} 条</strong></span><ArrowRight :size="18" /></RouterLink>
          </div>
          <div class="atlas-region-preview-samples">
            <div><h4>收录政策</h4><span>{{ selectedPolicySamples.length ? '查看收录记录' : '暂无收录' }}</span></div>
            <RouterLink v-for="item in selectedPolicySamples" :key="item.id" :to="'/policies/' + item.id">{{ item.title }}<ArrowRight :size="15" /></RouterLink>
          </div>
          <div class="atlas-region-preview-samples">
            <div><h4>相关案例</h4><span>{{ selectedCaseSamples.length ? '查看收录记录' : '暂无收录' }}</span></div>
            <RouterLink v-for="item in selectedCaseSamples" :key="item.id" :to="'/cases/' + item.id">{{ item.title }}<ArrowRight :size="15" /></RouterLink>
          </div>
        </template>
        <div v-else class="atlas-region-preview-empty"><MapPin :size="34" /><strong>选择一个地区</strong><p>在左侧选中省份后，这里会显示政策、案例与资料入口。</p></div>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { AlertCircle, ArrowRight, BriefcaseBusiness, FileText, MapPin, RotateCcw, Search } from 'lucide-vue-next'
import { getCases } from '@/api/case'
import { getPolicies } from '@/api/policy'
import { getRegions } from '@/api/region'

const loading = ref(false)
const error = ref('')
const keyword = ref('')
const onlyCovered = ref(false)
const sortMode = ref('coverage')
const selectedRegionId = ref(null)
const regions = ref([])
const policies = ref([])
const cases = ref([])

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
      const policyCount = policyMap.get(String(region.id)) || 0
      const caseCount = caseMap.get(String(region.id)) || 0
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
  const filtered = regionRows.value.filter((region) => {
    if (onlyCovered.value && !region.totalCount) return false
    if (!text) return true
    const relatedPolicies = policies.value.filter((item) => String(item.regionId) === String(region.id))
    const relatedCases = cases.value.filter((item) => String(item.regionId) === String(region.id))
    return [region.name, region.level, ...relatedPolicies.map(searchText), ...relatedCases.map(searchText)].join(' ').toLowerCase().includes(text)
  })
  return sortMode.value === 'name' ? filtered.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN')) : filtered
})

const selectedRegion = computed(() => visibleRegions.value.find((item) => String(item.id) === String(selectedRegionId.value)) || visibleRegions.value[0] || null)
const selectedPolicySamples = computed(() => policies.value.filter((item) => String(item.regionId) === String(selectedRegion.value?.id)).slice(0, 2))
const selectedCaseSamples = computed(() => cases.value.filter((item) => String(item.regionId) === String(selectedRegion.value?.id)).slice(0, 2))

const resultText = computed(() => {
  if (error.value) return '数据暂时不可用'
  if (loading.value) return '正在更新…'
  return `找到 ${visibleRegions.value.length} 个地区`
})

function countByRegion(list) {
  const map = new Map()
  list.forEach((item) => {
    if (!item.regionId) {
      return
    }
    const regionId = String(item.regionId)
    map.set(regionId, (map.get(regionId) || 0) + 1)
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

async function loadRegionData() {
  loading.value = true
  error.value = ''
  try {
    const [regionList, policyList, caseList] = await Promise.all([getRegions(), getPolicies(), getCases()])
    regions.value = regionList
    policies.value = policyList
    cases.value = caseList
  } catch (err) {
    error.value = '地区资料暂时无法读取，请确认数据库服务是否运行。'
  } finally {
    loading.value = false
  }
}

onMounted(loadRegionData)
</script>
