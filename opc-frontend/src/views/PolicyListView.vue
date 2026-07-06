<template>
  <div class="page-stack">
    <section class="panel filter-panel">
      <div class="section-header">
        <div>
          <h2>政策检索</h2>
          <p>按关键词、地区和政策类型查看已收录政策。</p>
        </div>
        <button class="button button-export" type="button" @click="exportPolicies">导出政策 Excel</button>
      </div>

      <form class="filters" @submit.prevent="loadPolicies">
        <label>
          <span>关键词</span>
          <input v-model="query.keyword" placeholder="搜索标题、摘要、标签" />
        </label>
        <label>
          <span>地区</span>
          <select v-model="query.regionId">
            <option value="">全部地区</option>
            <option v-for="region in regions" :key="region.id" :value="region.id">
              {{ region.name }}
            </option>
          </select>
        </label>
        <label>
          <span>类型</span>
          <select v-model="query.policyType">
            <option value="">全部类型</option>
            <option value="comprehensive">综合政策</option>
            <option value="computing_support">算力支持</option>
            <option value="funding_subsidy">资金补贴</option>
            <option value="scenario_demand">场景需求</option>
            <option value="talent_service">人才服务</option>
            <option value="investment">投资融资</option>
            <option value="other">其他</option>
          </select>
        </label>
        <button class="button" type="submit">筛选</button>
      </form>
    </section>

    <section class="panel">
      <div v-if="loading" class="muted">正在加载政策...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="!policies.length" class="muted">暂无政策数据。</div>
      <div v-else class="table-wrap">
        <table>
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
            <tr v-for="policy in policies" :key="policy.id">
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
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getPolicies } from '@/api/policy'
import { getRegions } from '@/api/region'
import { exportPolicies } from '@/api/export'

const loading = ref(false)
const error = ref('')
const policies = ref([])
const regions = ref([])
const query = reactive({
  keyword: '',
  regionId: '',
  policyType: '',
})

async function loadPolicies() {
  loading.value = true
  error.value = ''
  try {
    const params = {
      keyword: query.keyword || undefined,
      regionId: query.regionId || undefined,
      policyType: query.policyType || undefined,
    }
    policies.value = await getPolicies(params)
  } catch (err) {
    error.value = err.message || '政策数据加载失败'
  } finally {
    loading.value = false
  }
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

onMounted(async () => {
  regions.value = await getRegions()
  await loadPolicies()
})
</script>
