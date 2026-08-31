<template>
  <div class="page-stack detail-page case-detail-page">
    <RouterLink class="back-link detail-back-link" to="/cases">返回案例库</RouterLink>

    <section class="panel detail-panel">
      <div v-if="loading" class="muted">正在加载案例详情...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <article v-else class="detail detail-article" @pointermove="handleDetailSpotlight">
        <div class="detail-hero-card">
          <span class="caption">case detail</span>
          <h2>{{ item.title }}</h2>
          <p v-if="item.articleTitle" class="case-article-title">原文：{{ item.articleTitle }}</p>
          <div class="case-classification-row" aria-label="案例分类">
            <span class="case-taxonomy-tag case-taxonomy-major">{{ item.category || '未标注大类' }}</span>
            <span class="case-taxonomy-tag case-taxonomy-minor">{{ item.subcategory || '未标注小类' }}</span>
          </div>
          <div v-if="formatTags(item.tags).length" class="chip-row detail-chip-row">
            <span v-for="tag in formatTags(item.tags)" :key="tag" class="chip">{{ tag }}</span>
          </div>
        </div>

        <div class="meta-grid">
          <span><b>地区</b>{{ item.regionName || '-' }}</span>
          <span><b>大类</b>{{ item.category || '-' }}</span>
          <span><b>小类</b>{{ item.subcategory || '-' }}</span>
          <span><b>主体</b>{{ item.actorName || '-' }}</span>
          <span><b>状态</b>{{ item.status || '-' }}</span>
          <span><b>来源</b>{{ item.sourceTitle || '-' }}</span>
          <span><b>访问日期</b>{{ item.accessedAt || '-' }}</span>
        </div>

        <section class="detail-section">
          <h3>摘要</h3>
          <p>{{ item.summary || '-' }}</p>
        </section>

        <section class="detail-section">
          <h3>商业模式</h3>
          <pre>{{ item.businessModel || '-' }}</pre>
        </section>

        <section class="detail-section">
          <h3>AI 工具</h3>
          <pre>{{ item.aiTools || '-' }}</pre>
        </section>

        <section class="detail-section">
          <h3>成果</h3>
          <pre>{{ item.outcome || '-' }}</pre>
        </section>

        <section class="detail-section">
          <h3>来源追溯</h3>
          <p class="source-actions">
            <a v-if="item.originalUrl" class="button button-ghost" :href="item.originalUrl" target="_blank" rel="noreferrer">
              查看原始来源
            </a>
            <span v-else class="muted">暂无原始链接</span>
          </p>
        </section>
      </article>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getCaseDetail } from '@/api/case'
import { recordVisit } from '@/api/visit'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})

const loading = ref(false)
const error = ref('')
const item = ref({})

onMounted(async () => {
  loading.value = true
  try {
    item.value = await getCaseDetail(props.id)
    recordVisit({
      pagePath: `/cases/${props.id}`,
      pageTitle: item.value.title || `案例详情 #${props.id}`,
      targetType: 'case',
      targetId: Number(props.id),
      referer: document.referrer || '/cases',
    }).catch(() => {})
  } catch (err) {
    error.value = '案例详情暂时无法读取，请确认数据库服务是否运行。'
  } finally {
    loading.value = false
  }
})

function formatTags(tags) {
  if (!tags) {
    return []
  }
  return String(tags)
    .split(/[,，、;；]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

function handleDetailSpotlight(event) {
  const target = event.target.closest('.detail-hero-card, .meta-grid span, .detail-section')
  if (!target) {
    return
  }
  const rect = target.getBoundingClientRect()
  target.style.setProperty('--spotlight-x', `${event.clientX - rect.left}px`)
  target.style.setProperty('--spotlight-y', `${event.clientY - rect.top}px`)
}
</script>
