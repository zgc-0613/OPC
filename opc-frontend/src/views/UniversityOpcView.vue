<template>
  <div class="university-opc-page">
    <section class="university-opc-intro">
      <p class="eyebrow">高校 OPC · 独立研究板块</p>
      <h2>高校 OPC 支撑网络</h2>
      <p class="intro-copy">集中呈现高校 OPC 社区、支持措施、竞赛活动与高校创业项目。社区、活动和案例分别统计，避免不同研究对象混计。</p>
    </section>

    <section class="university-opc-stats" aria-label="高校 OPC 统计概览">
      <div v-for="stat in stats" :key="stat.label" class="stat-item">
        <span>{{ stat.label }}</span>
        <strong>{{ records.filter((record) => record.type === stat.type).length }}</strong>
        <small>静态预览记录</small>
      </div>
    </section>

    <section class="university-opc-workspace" :aria-labelledby="`${activeTab}-title`">
      <header class="workspace-heading">
        <div>
          <p class="eyebrow">{{ activeTabMeta.subtitle }}</p>
          <h3 :id="`${activeTab}-title`">{{ activeTabMeta.label }}</h3>
        </div>
        <span class="verification-note">数据将按来源核验后发布</span>
      </header>

      <div class="filter-row" aria-label="高校 OPC 筛选条件">
        <label><span>省份</span><select v-model="filters.province"><option value="">全部省份</option><option v-for="province in provinces" :key="province" :value="province">{{ province }}</option></select></label>
        <label><span>证据等级</span><select v-model="filters.evidence"><option value="">全部等级</option><option value="A">A · 官方确认</option><option value="B">B · 官方支持</option><option value="C">C · 官方报道</option></select></label>
        <label><span>关键词</span><input v-model.trim="filters.keyword" placeholder="搜索高校、社区或活动" /></label>
      </div>

      <div v-if="loading" class="university-opc-empty" role="status"><strong>正在加载高校 OPC 预览数据</strong></div>
      <div v-else-if="loadError" class="university-opc-empty" role="alert"><strong>预览数据加载失败</strong><p>{{ loadError }}</p></div>
      <div v-else class="university-opc-records" aria-live="polite">
        <p class="preview-note">当前为静态预览数据，仅供核验，不写入数据库。显示 {{ filteredRecords.length }} / {{ activeRecords.length }} 条。</p>
        <article v-for="record in filteredRecords" :key="record.id" class="university-opc-record">
          <div class="record-heading"><span class="record-id">{{ record.id }}</span><h4>{{ record.name }}</h4><span class="record-status" :class="`status-${record.status}`">{{ statusLabel(record.status) }}</span></div>
          <p class="record-meta">{{ record.institution || '高校未明确' }} · {{ record.province }}{{ record.city ? ` · ${record.city}` : '' }} · 证据 {{ record.grade || '未标注' }}</p>
          <p v-if="record.summary" class="record-summary">{{ record.summary }}</p>
          <a v-if="record.sourceUrl" class="record-source" :href="firstUrl(record.sourceUrl)" target="_blank" rel="noopener noreferrer">查看来源：{{ record.sourceTitle || '原始链接' }}</a>
        </article>
        <div v-if="!filteredRecords.length" class="university-opc-empty"><strong>没有符合条件的记录</strong><p>请调整省份、证据等级或关键词。</p></div>
      </div>
    </section>

    <section class="university-opc-rules">
      <h3>统计边界</h3>
      <ul>
        <li>高校 OPC 社区、支持措施、竞赛活动和高校创业案例分别统计。</li>
        <li>只有官方来源或可追溯报道才能标记为已核验。</li>
        <li>高校社区不计入普通创业案例总量，关联案例通过关系字段连接。</li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const tabKeys = ['communities', 'support', 'activities', 'cases']
const normalizeTab = (tab) => (tabKeys.includes(tab) ? tab : 'communities')
const activeTab = ref(normalizeTab(route.query.tab))
const records = ref([])
const loading = ref(true)
const loadError = ref('')
const filters = reactive({ province: '', evidence: '', keyword: '' })
const tabs = [
  { key: 'communities', label: 'OPC 社区', subtitle: '高校载体与空间' },
  { key: 'support', label: '支持措施', subtitle: '课程、算力与孵化' },
  { key: 'activities', label: '竞赛活动', subtitle: '赛事、实训与路演' },
  { key: 'cases', label: '高校创业案例', subtitle: '学生与高校项目' },
]
const stats = [
  { label: '高校 OPC 社区', type: 'communities' },
  { label: '支持措施', type: 'support' },
  { label: '竞赛与活动', type: 'activities' },
  { label: '高校创业案例', type: 'cases' },
]
const activeTabMeta = computed(() => tabs.find((tab) => tab.key === activeTab.value) || tabs[0])
const activeRecords = computed(() => records.value.filter((record) => record.type === activeTab.value))
const provinces = computed(() => [...new Set(activeRecords.value.map((record) => record.province).filter(Boolean))].sort())
const filteredRecords = computed(() => activeRecords.value.filter((record) => {
  const haystack = `${record.name} ${record.institution} ${record.summary}`.toLowerCase()
  return (!filters.province || record.province === filters.province)
    && (!filters.evidence || record.grade === filters.evidence)
    && (!filters.keyword || haystack.includes(filters.keyword.toLowerCase()))
}))

function statusLabel(status) {
  return { verified: '已核验', partially_verified: '部分核验', pending: '待核验' }[status] || status || '未标注'
}

function firstUrl(value) {
  return String(value).split(';')[0].trim()
}

onMounted(async () => {
  try {
    const response = await fetch('/api/public/university-opc', { headers: { Accept: 'application/json' } })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const payload = await response.json()
    records.value = Array.isArray(payload?.data) ? payload.data : []
  } catch (error) {
    loadError.value = '请稍后刷新页面重试。'
  } finally {
    loading.value = false
  }
})

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = normalizeTab(tab)
  },
)

</script>

<style scoped>
.university-opc-page{max-width:1120px;margin:0 auto;padding:8px 0 48px;color:#20251f}.university-opc-intro{padding:18px 0 24px;border-bottom:1px solid #cbd0c9}.eyebrow{margin:0 0 7px;color:#596159;font-size:.72rem;font-weight:700}.university-opc-intro h2{margin:0;font-family:"ZCOOL XiaoWei",STKaiti,KaiTi,serif;font-size:2rem;line-height:1.1}.intro-copy{max-width:680px;margin:10px 0 0;color:#606760;font-size:.82rem;line-height:1.7}.university-opc-stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1px;margin-top:24px;border:1px solid #cbd0c9;background:#cbd0c9}.stat-item{display:grid;gap:7px;min-width:0;padding:18px;background:#eceeeb}.stat-item span{color:#596159;font-size:.73rem;font-weight:700}.stat-item strong{font-family:"Bookman Old Style","URW Bookman",Georgia,serif;font-size:1.75rem}.stat-item small{color:#747b75;font-size:.68rem}.university-opc-tabs{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0;margin-top:30px;border:1px solid #8e958d;border-radius:6px;overflow:hidden}.university-opc-tabs button{display:grid;gap:4px;min-height:64px;padding:12px;border:0;border-right:1px solid #cbd0c9;background:#fbfbf8;color:#515752;text-align:left;font:inherit;cursor:pointer}.university-opc-tabs button:last-child{border-right:0}.university-opc-tabs button.active{background:#181a18;color:#fbfbf8}.university-opc-tabs button span{font-size:.78rem;font-weight:700}.university-opc-tabs button small{font-size:.67rem;opacity:.78}.university-opc-tabs button:focus-visible{outline:2px solid #4f6f58;outline-offset:-3px}.university-opc-workspace{margin-top:24px;padding:22px;border:1px solid #cbd0c9;background:#fbfbf8}.workspace-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}.workspace-heading h3{margin:0;font-size:1.12rem}.verification-note{padding:6px 9px;border:1px solid #cbd0c9;color:#596159;font-size:.67rem;white-space:nowrap}.filter-row{display:grid;grid-template-columns:1fr 1fr 1.5fr;gap:12px;margin-top:22px}.filter-row label{display:grid;gap:6px;color:#596159;font-size:.7rem;font-weight:700}.filter-row select,.filter-row input{min-height:42px;padding:0 11px;border:1px solid #8e958d;border-radius:5px;background:#f2f3ef;color:#20251f;font:inherit;font-size:.76rem}.filter-row :is(select,input):focus-visible{outline:2px solid #4f6f58;outline-offset:2px}.university-opc-empty{margin-top:24px;padding:38px 20px;border:1px dashed #bfc5bd;text-align:center}.university-opc-empty strong{font-size:.95rem}.university-opc-empty p{max-width:560px;margin:8px auto 0;color:#686f68;font-size:.76rem;line-height:1.7}.university-opc-rules{margin-top:30px;padding-top:20px;border-top:1px solid #cbd0c9}.university-opc-rules h3{margin:0;font-size:1rem}.university-opc-rules ul{display:grid;gap:7px;margin:12px 0 0;padding-left:18px;color:#606760;font-size:.74rem;line-height:1.6}@media(max-width:720px){.university-opc-stats,.university-opc-tabs{grid-template-columns:repeat(2,minmax(0,1fr))}.university-opc-tabs button:nth-child(2){border-right:0}.filter-row{grid-template-columns:1fr}.workspace-heading{flex-direction:column}.verification-note{white-space:normal}}@media(max-width:460px){.university-opc-page{padding-bottom:32px}.university-opc-workspace{padding:16px}.university-opc-stats{grid-template-columns:1fr 1fr}.university-opc-tabs button{min-height:58px;padding:10px 9px}}
.university-opc-records{display:grid;gap:10px;margin-top:22px}.preview-note{margin:0;color:#747b75;font-size:.7rem}.university-opc-record{padding:14px 16px;border:1px solid #d5d9d3;background:#f7f8f4}.record-heading{display:flex;align-items:center;gap:9px}.record-heading h4{flex:1;margin:0;font-size:.85rem;line-height:1.45}.record-id{color:#747b75;font-family:monospace;font-size:.7rem}.record-status{padding:3px 7px;border-radius:3px;font-size:.65rem;white-space:nowrap}.status-verified{color:#35543d;background:#e1eee4}.status-partially_verified{color:#5c5131;background:#f3ecd1}.status-pending{color:#76542d;background:#f5e7d2}.record-meta,.record-summary{margin:6px 0 0;color:#697169;font-size:.72rem;line-height:1.6}.record-summary{color:#4d554e}.record-source{display:inline-block;margin-top:7px;color:#47624e;font-size:.7rem;text-decoration:underline;text-underline-offset:2px}
</style>
