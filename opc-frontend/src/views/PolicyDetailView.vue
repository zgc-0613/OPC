<template>
  <div class="page-stack detail-page policy-detail-page">
    <RouterLink class="back-link detail-back-link" to="/policies">返回政策库</RouterLink>

    <section class="panel detail-panel">
      <div v-if="loading" class="muted">正在加载政策详情...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <article v-else class="detail detail-article" @pointermove="handleDetailSpotlight">
        <div class="detail-hero-card">
          <span class="caption">policy detail</span>
          <h2>{{ policy.title }}</h2>
          <div v-if="formatTags(policy.tags).length" class="chip-row detail-chip-row">
            <span v-for="tag in formatTags(policy.tags)" :key="tag" class="chip">{{ tag }}</span>
          </div>
        </div>

        <div class="meta-grid">
          <span><b>地区</b>{{ policy.regionName || '-' }}</span>
          <span><b>发文单位</b>{{ policy.issuingBody || '-' }}</span>
          <span><b>文号</b>{{ policy.documentNo || '-' }}</span>
          <span><b>发布日期</b>{{ policy.publishDate || '-' }}</span>
          <span><b>实施时间</b>{{ policy.effectiveDate || '-' }}</span>
          <span><b>有效时长</b>{{ policy.validPeriod || '-' }}</span>
        </div>

        <section class="detail-section">
          <h3>摘要</h3>
          <p>{{ policy.summary || '-' }}</p>
        </section>

        <section class="detail-section">
          <h3>政策要点</h3>
          <pre>{{ policy.keyPoints || '-' }}</pre>
        </section>

        <section class="detail-section">
          <h3>支持措施</h3>
          <pre>{{ policy.supportMeasures || '-' }}</pre>
        </section>

        <section class="detail-section">
          <h3>来源</h3>
          <p class="source-actions">
            <a v-if="policy.originalUrl" class="button button-ghost" :href="policy.originalUrl" target="_blank" rel="noreferrer">
              政策原文
            </a>
            <a v-if="policy.evidenceUrl" class="button button-ghost" :href="policy.evidenceUrl" target="_blank" rel="noreferrer">
              辅证链接
            </a>
            <span v-if="!policy.originalUrl && !policy.evidenceUrl" class="muted">暂无来源链接</span>
          </p>
        </section>
      </article>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getPolicyDetail } from '@/api/policy'
import { recordVisit } from '@/api/visit'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})

const loading = ref(false)
const error = ref('')
const policy = ref({})

onMounted(async () => {
  loading.value = true
  try {
    policy.value = await getPolicyDetail(props.id)
    recordVisit({
      pagePath: `/policies/${props.id}`,
      pageTitle: policy.value.title || `政策详情 #${props.id}`,
      targetType: 'policy',
      targetId: Number(props.id),
      referer: document.referrer || '/policies',
    }).catch(() => {})
  } catch (err) {
    error.value = '政策详情暂时无法读取，请确认数据库服务是否运行。'
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
