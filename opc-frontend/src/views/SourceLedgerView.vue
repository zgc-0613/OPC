<template>
  <div class="page-stack source-ledger-page archive-workspace-page">
    <section class="panel filter-panel source-filter-panel scroll-reveal" @pointermove="handleSourceSpotlight">
      <div class="section-header">
        <div>
          <h2>来源检索</h2>
          <p>展示来源链接、文件名、访问日期和状态，方便回到原始出处复核。</p>
        </div>
      </div>

      <div class="source-summary-strip">
        <div>
          <span>来源总量</span>
          <strong>{{ sources.length }}</strong>
        </div>
        <div>
          <span>当前结果</span>
          <strong>{{ filteredSources.length }}</strong>
        </div>
        <div>
          <span>有效链接</span>
          <strong>{{ linkedSourceCount }}</strong>
        </div>
        <div>
          <span>已记录日期</span>
          <strong>{{ accessedSourceCount }}</strong>
        </div>
      </div>
      <p class="source-summary-note">统计口径：基于当前台账记录，随筛选条件实时更新；来源链接可回溯原始网页，访问日期均已记录。</p>

      <div class="auto-filter-grid">
        <label>
          <span>关键词检索</span>
          <input v-model.trim="query.keyword" placeholder="搜索标题、发布机构、来源链接" />
        </label>
        <label>
          <span>来源类型</span>
          <div class="custom-select" :class="{ open: sourceTypeMenuOpen }">
            <button class="custom-select-trigger" type="button" @click="toggleSourceTypeMenu">
              <span>{{ selectedSourceTypeLabel }}</span>
              <b></b>
            </button>
            <div v-if="sourceTypeMenuOpen" class="custom-select-menu">
              <button type="button" :class="{ active: !query.sourceType }" @click="selectSourceType('')">
                全部类型
              </button>
              <button
                v-for="type in sourceTypeOptions"
                :key="type"
                type="button"
                :class="{ active: query.sourceType === type }"
                @click="selectSourceType(type)"
              >
                {{ type }}
              </button>
            </div>
          </div>
        </label>
        <label>
          <span>状态</span>
          <div class="custom-select" :class="{ open: statusMenuOpen }">
            <button class="custom-select-trigger" type="button" @click="toggleStatusMenu">
              <span>{{ selectedStatusLabel }}</span>
              <b></b>
            </button>
            <div v-if="statusMenuOpen" class="custom-select-menu">
              <button type="button" :class="{ active: !query.status }" @click="selectStatus('')">
                全部状态
              </button>
              <button
                v-for="status in statusOptions"
                :key="status"
                type="button"
                :class="{ active: query.status === status }"
                @click="selectStatus(status)"
              >
                {{ status }}
              </button>
            </div>
          </div>
        </label>
      </div>
    </section>

    <section class="panel source-ledger-panel scroll-reveal" @pointermove="handleSourceSpotlight">
      <div class="section-header compact-header">
        <div>
          <h2>来源记录</h2>
          <p>{{ resultText }}</p>
        </div>
        <button v-if="hasActiveFilter" class="button button-ghost" type="button" @click="resetFilters">
          清除筛选
        </button>
      </div>

      <div v-if="loading" class="muted">正在加载来源...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="!filteredSources.length" class="empty-state">
        <strong>暂无匹配来源</strong>
        <span>可以换一个类型、状态或关键词试试。</span>
      </div>
      <div v-else class="source-ledger-list">
        <article
          v-for="(source, index) in paginatedSources"
          :key="source.id"
          class="source-ledger-row scroll-reveal"
          :style="{ '--i': index }"
        >
          <div class="source-ledger-main">
            <div class="case-index-meta">
              <span>类型：{{ source.sourceType || '未标注' }}</span>
              <span>主要单位：{{ source.publisher || '未标注' }}</span>
              <span>访问日期：{{ source.accessedAt || '未标注' }}</span>
            </div>
            <strong>{{ source.title }}</strong>
            <div class="source-actions">
              <a v-if="source.url" class="button button-ghost" :href="source.url" target="_blank" rel="noreferrer">
                打开来源链接
              </a>
              <span v-if="source.localFile" class="chip">文件：{{ source.localFile }}</span>
            </div>
          </div>
          <span class="status-pill">{{ source.status || '-' }}</span>
        </article>
      </div>

      <div v-if="filteredSources.length" class="source-pagination" aria-label="来源分页">
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
import { getSources } from '@/api/source'

const loading = ref(false)
const error = ref('')
const sources = ref([])
const currentPage = ref(1)
const sourceTypeMenuOpen = ref(false)
const statusMenuOpen = ref(false)
const pageSize = 10
let revealObserver

const query = reactive({
  keyword: '',
  sourceType: '',
  status: '',
})

const sourceTypeOptions = computed(() => uniqueValues(sources.value.map((item) => item.sourceType)))
const statusOptions = computed(() => uniqueValues(sources.value.map((item) => item.status)))
const hasActiveFilter = computed(() => Boolean(query.keyword || query.sourceType || query.status))
const selectedSourceTypeLabel = computed(() => query.sourceType || '全部类型')
const selectedStatusLabel = computed(() => query.status || '全部状态')
const linkedSourceCount = computed(() => sources.value.filter((item) => item.url).length)
const accessedSourceCount = computed(() => sources.value.filter((item) => item.accessedAt).length)

const filteredSources = computed(() => {
  const keyword = query.keyword.toLowerCase()
  return sources.value.filter((item) => {
    const matchKeyword =
      !keyword ||
      [item.title, item.publisher, item.url, item.localFile]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(keyword)
    const matchType = !query.sourceType || item.sourceType === query.sourceType
    const matchStatus = !query.status || item.status === query.status
    return matchKeyword && matchType && matchStatus
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredSources.value.length / pageSize)))

const paginatedSources = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredSources.value.slice(start, start + pageSize)
})

const paginationPages = computed(() => {
  const total = totalPages.value
  const start = Math.max(1, Math.min(currentPage.value - 2, total - 4))
  const end = Math.min(total, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

const resultText = computed(() => {
  return hasActiveFilter.value
    ? `当前筛选共 ${filteredSources.value.length} 条来源。`
    : `当前展示全部来源，共 ${filteredSources.value.length} 条。`
})

function resetFilters() {
  query.keyword = ''
  query.sourceType = ''
  query.status = ''
  sourceTypeMenuOpen.value = false
  statusMenuOpen.value = false
  currentPage.value = 1
}

function toggleSourceTypeMenu() {
  sourceTypeMenuOpen.value = !sourceTypeMenuOpen.value
  if (sourceTypeMenuOpen.value) {
    statusMenuOpen.value = false
  }
}

function toggleStatusMenu() {
  statusMenuOpen.value = !statusMenuOpen.value
  if (statusMenuOpen.value) {
    sourceTypeMenuOpen.value = false
  }
}

function selectSourceType(sourceType) {
  query.sourceType = sourceType
  sourceTypeMenuOpen.value = false
}

function selectStatus(status) {
  query.status = status
  statusMenuOpen.value = false
}

function goToPage(page) {
  currentPage.value = Math.min(Math.max(page, 1), totalPages.value)
  nextTick(setupScrollReveal)
}

function handleSourceSpotlight(event) {
  const target = event.target.closest('.source-filter-panel, .source-summary-strip div, .source-ledger-panel, .source-ledger-row')
  if (!target) {
    return
  }
  const rect = target.getBoundingClientRect()
  target.style.setProperty('--spotlight-x', `${event.clientX - rect.left}px`)
  target.style.setProperty('--spotlight-y', `${event.clientY - rect.top}px`)
}

function setupScrollReveal() {
  revealObserver?.disconnect()
  const items = document.querySelectorAll('.route-source-ledger .scroll-reveal')
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

function uniqueValues(values) {
  return Array.from(new Set(values.filter(Boolean))).sort((a, b) => String(a).localeCompare(String(b), 'zh-CN'))
}

watch(
  () => [query.keyword, query.sourceType, query.status],
  () => {
    currentPage.value = 1
    nextTick(setupScrollReveal)
  },
)

onMounted(async () => {
  await nextTick()
  setupScrollReveal()
  loading.value = true
  error.value = ''
  try {
    sources.value = await getSources()
  } catch (err) {
    error.value = '来源资料暂时无法读取，请确认数据库服务是否运行。'
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
