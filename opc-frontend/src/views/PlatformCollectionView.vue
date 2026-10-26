<template>
  <div class="platform-directory">
    <header class="platform-directory-head">
      <div>
        <span class="platform-directory-eyebrow">PLATFORM DIRECTORY / 平台导航</span>
        <h2>找到适合你的 OPC 平台</h2>
        <p>全国生态、地方服务、政务专区与运营商配套入口，按资源包分类浏览。</p>
      </div>
      <div class="platform-directory-total"><strong>{{ loading ? '…' : sites.length }}</strong><span>个网站入口</span></div>
    </header>

    <div class="platform-directory-toolbar">
      <label class="platform-directory-search"><Search :size="20" aria-hidden="true" /><span class="sr-only">搜索平台</span><input v-model.trim="keyword" type="search" placeholder="搜索平台名称、运营主体或服务…" /></label>
      <label class="platform-directory-select"><span class="sr-only">选择平台分类</span><select v-model="selectedCategory"><option value="">全部分类</option><option v-for="category in categories" :key="category" :value="category">{{ category }}</option></select><ChevronDown :size="17" aria-hidden="true" /></label>
    </div>

    <div class="platform-directory-status">
      <span>{{ loading ? '正在加载平台资料…' : `找到 ${filteredSites.length} 个网站 · ${groupedSites.length} 个分类` }}</span>
      <button v-if="keyword || selectedCategory" type="button" @click="clearFilters">清空筛选</button>
    </div>
    <div v-if="loadError" class="platform-directory-notice" role="alert"><Info :size="17" /><span>平台资料暂时无法读取，请稍后重试。</span><button type="button" @click="loadSites">重新加载</button></div>

    <div v-if="loading" class="platform-directory-empty">正在加载平台资料…</div>
    <div v-else-if="loadError" class="platform-directory-empty">数据库连接恢复后将显示平台资料。</div>
    <div v-else-if="!filteredSites.length" class="platform-directory-empty"><Globe2 :size="28" /><strong>没有找到匹配的平台</strong><span>试试其他关键词或分类。</span></div>
    <div v-else class="platform-directory-groups">
      <section v-for="(group, index) in groupedSites" :key="group.category" class="platform-directory-group">
        <div class="platform-directory-group-head"><span class="platform-directory-group-no">{{ String(index + 1).padStart(2, '0') }}</span><h3>{{ group.category }}</h3><span class="platform-directory-group-count">{{ group.sites.length }} 个网站</span></div>
        <div class="platform-directory-grid">
          <article v-for="site in group.sites" :key="site.id" class="platform-directory-card">
            <div class="platform-directory-card-head"><span class="platform-directory-logo" aria-hidden="true"><img v-if="safeLogoUrl(site.logoUrl) && !failedLogos.has(site.id)" :src="safeLogoUrl(site.logoUrl)" alt="" loading="lazy" decoding="async" @error="markLogoFailed(site.id)" /><span v-else>{{ logoFallback(site.name) }}</span></span><div><h4>{{ site.name }}</h4><span>{{ site.domain }}</span></div></div>
            <span class="platform-directory-card-category">{{ site.category }}</span>
            <p>{{ site.summary }}</p>
            <dl><div><dt>运营主体</dt><dd>{{ site.owner || '未标注' }}</dd></div><div><dt>主要服务</dt><dd>{{ site.services || '未标注' }}</dd></div></dl>
            <a v-if="safeSiteUrl(site.url)" class="platform-directory-visit" :href="safeSiteUrl(site.url)" target="_blank" rel="noopener noreferrer" :aria-label="`访问${site.name}网站（新窗口）`">访问网站 <ArrowUpRight :size="17" /></a>
            <span v-else class="platform-directory-visit is-disabled">链接待核验</span>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowUpRight, ChevronDown, Globe2, Info, Search } from 'lucide-vue-next'
import { getPlatformSites } from '@/api/platformSite'

const sites = ref([])
const loading = ref(true)
const loadError = ref(false)
const keyword = ref('')
const selectedCategory = ref('')
const failedLogos = ref(new Set())

const categories = computed(() => [...new Set(sites.value.map((site) => site.category).filter(Boolean))])
const filteredSites = computed(() => {
  const search = keyword.value.toLocaleLowerCase()
  return sites.value.filter((site) => {
    if (selectedCategory.value && site.category !== selectedCategory.value) return false
    if (!search) return true
    return [site.name, site.category, site.owner, site.services, site.summary, site.domain]
      .filter(Boolean).join(' ').toLocaleLowerCase().includes(search)
  })
})
const groupedSites = computed(() => {
  const groups = new Map()
  filteredSites.value.forEach((site) => {
    if (!groups.has(site.category)) groups.set(site.category, [])
    groups.get(site.category).push(site)
  })
  return [...groups].map(([category, categorySites]) => ({ category, sites: categorySites }))
})

function safeSiteUrl(value) {
  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : null
  } catch {
    return null
  }
}

function safeLogoUrl(value) {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' ? url.href : null
  } catch {
    return null
  }
}

function logoFallback(name) {
  return Array.from((name || '网站').replace(/[^\p{L}\p{N}]/gu, '')).slice(0, 3).join('').toLocaleUpperCase()
}

function markLogoFailed(id) {
  const next = new Set(failedLogos.value)
  next.add(id)
  failedLogos.value = next
}

function clearFilters() {
  keyword.value = ''
  selectedCategory.value = ''
}

async function loadSites() {
  loading.value = true
  loadError.value = false
  try {
    const response = await getPlatformSites()
    if (!Array.isArray(response)) throw new Error('Invalid platform response')
    sites.value = response
  } catch {
    sites.value = []
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(loadSites)
</script>
