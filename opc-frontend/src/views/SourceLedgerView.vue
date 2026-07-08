<template>
  <div class="page-stack">
    <section class="panel filter-panel">
      <div class="section-header">
        <div>
          <span class="caption">source ledger</span>
          <h2>来源台账</h2>
          <p>展示来源链接、文件名、访问日期和状态，方便回到原始出处复核。</p>
        </div>
        <span class="analysis-badge">{{ filteredSources.length }} sources</span>
      </div>

      <div class="auto-filter-grid">
        <label>
          <span>关键词检索</span>
          <input v-model.trim="query.keyword" placeholder="搜索标题、发布机构、备注" />
        </label>
        <label>
          <span>来源类型</span>
          <select v-model="query.sourceType">
            <option value="">全部类型</option>
            <option v-for="type in sourceTypeOptions" :key="type" :value="type">{{ type }}</option>
          </select>
        </label>
        <label>
          <span>状态</span>
          <select v-model="query.status">
            <option value="">全部状态</option>
            <option v-for="status in statusOptions" :key="status" :value="status">{{ status }}</option>
          </select>
        </label>
      </div>
    </section>

    <section class="panel">
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
        <article v-for="source in filteredSources" :key="source.id" class="source-ledger-row">
          <span class="source-ledger-no">{{ source.id }}</span>
          <div class="source-ledger-main">
            <div class="case-index-meta">
              <span>{{ source.sourceType || '未标注类型' }}</span>
              <span>{{ source.publisher || '未标注机构' }}</span>
              <span>{{ source.accessedAt || '未标注访问日期' }}</span>
            </div>
            <strong>{{ source.title }}</strong>
            <p v-if="source.notes">{{ source.notes }}</p>
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
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getSources } from '@/api/source'

const loading = ref(false)
const error = ref('')
const sources = ref([])
const query = reactive({
  keyword: '',
  sourceType: '',
  status: '',
})

const sourceTypeOptions = computed(() => uniqueValues(sources.value.map((item) => item.sourceType)))
const statusOptions = computed(() => uniqueValues(sources.value.map((item) => item.status)))
const hasActiveFilter = computed(() => Boolean(query.keyword || query.sourceType || query.status))

const filteredSources = computed(() => {
  const keyword = query.keyword.toLowerCase()
  return sources.value.filter((item) => {
    const matchKeyword = !keyword || [item.title, item.publisher, item.notes, item.url, item.localFile]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword)
    const matchType = !query.sourceType || item.sourceType === query.sourceType
    const matchStatus = !query.status || item.status === query.status
    return matchKeyword && matchType && matchStatus
  })
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
}

function uniqueValues(values) {
  return Array.from(new Set(values.filter(Boolean))).sort((a, b) => String(a).localeCompare(String(b), 'zh-CN'))
}

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    sources.value = await getSources()
  } catch (err) {
    error.value = err.message || '来源加载失败'
  } finally {
    loading.value = false
  }
})
</script>
