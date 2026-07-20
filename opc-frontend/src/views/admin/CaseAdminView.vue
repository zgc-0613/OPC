<template>
  <div class="admin-stack">
    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>{{ editingId ? '编辑案例' : '新增案例' }}</h2>
          <p>案例记录用于沉淀 AI + OPC / 一人公司相关应用场景和典型实践。</p>
        </div>
      </div>

      <form class="admin-form" @submit.prevent="submitForm">
        <label class="span-2">
          <span>案例标题 *</span>
          <input v-model.trim="form.title" required />
        </label>
        <label>
          <span>地区 *</span>
          <select v-model.number="form.regionId" required>
            <option value="">请选择地区</option>
            <option v-for="region in regions" :key="region.id" :value="region.id">{{ region.name }}</option>
          </select>
        </label>
        <label>
          <span>领域 *</span>
          <input v-model.trim="form.category" required placeholder="例如：AI 电商 / 内容创业" />
        </label>
        <label>
          <span>主体</span>
          <input v-model.trim="form.actorName" placeholder="企业、团队或个人" />
        </label>
        <label>
          <span>来源 *</span>
          <select v-model.number="form.sourceId" required>
            <option value="">请选择来源</option>
            <option v-for="source in sources" :key="source.id" :value="source.id">{{ source.title }}</option>
          </select>
        </label>
        <div class="quick-source-toggle">
          <button class="button button-ghost" type="button" @click="showQuickSource = !showQuickSource">
            {{ showQuickSource ? '收起新增来源' : '快速新增来源' }}
          </button>
        </div>
        <div v-if="showQuickSource" class="quick-source-box span-3">
          <label class="span-2">
            <span>新来源标题 *</span>
            <input v-model.trim="quickSource.title" placeholder="例如：案例报道或原始来源标题" />
          </label>
          <label>
            <span>来源类型</span>
            <select v-model="quickSource.sourceType">
              <option value="web">网页</option>
              <option value="file">文件</option>
              <option value="paper">文献</option>
              <option value="news">新闻</option>
              <option value="other">其他</option>
            </select>
          </label>
          <label>
            <span>发布机构</span>
            <input v-model.trim="quickSource.publisher" placeholder="例如：媒体、平台或机构" />
          </label>
          <label class="span-2">
            <span>来源链接</span>
            <input v-model.trim="quickSource.url" placeholder="https://..." />
          </label>
          <label>
            <span>访问日期</span>
            <input v-model="quickSource.accessedAt" type="date" />
          </label>
          <div class="admin-actions span-3">
            <button class="button" type="button" :disabled="sourceCreating" @click="createSourceInline">
              {{ sourceCreating ? '正在新增...' : '新增并选中来源' }}
            </button>
          </div>
        </div>
        <label>
          <span>访问日期 *</span>
          <input v-model="form.accessedAt" required type="date" />
        </label>
        <label>
          <span>状态 *</span>
          <select v-model="form.status" required>
            <option value="published">已发布</option>
            <option value="draft">草稿</option>
            <option value="pending">待校对</option>
            <option value="archived">归档</option>
          </select>
        </label>
        <label>
          <span>校对人</span>
          <input v-model.trim="form.reviewer" />
        </label>
        <label class="span-3">
          <span>摘要 *</span>
          <textarea v-model.trim="form.summary" required rows="4"></textarea>
        </label>
        <label class="span-3">
          <span>商业模式</span>
          <textarea v-model.trim="form.businessModel" rows="4"></textarea>
        </label>
        <label class="span-3">
          <span>AI 工具</span>
          <textarea v-model.trim="form.aiTools" rows="3"></textarea>
        </label>
        <label class="span-3">
          <span>成果</span>
          <textarea v-model.trim="form.outcome" rows="3"></textarea>
        </label>
        <label class="span-3">
          <span>标签</span>
          <input v-model.trim="form.tags" placeholder="多个标签用中文逗号隔开" />
        </label>
        <label class="span-2">
          <span>原文链接</span>
          <input v-model.trim="form.originalUrl" placeholder="https://..." />
        </label>
        <label>
          <span>本地文件</span>
          <input v-model.trim="form.localFile" />
        </label>
        <div class="admin-actions span-3">
          <button class="button" type="submit">{{ editingId ? '保存案例' : '新增案例' }}</button>
          <button v-if="editingId" class="button button-ghost" type="button" @click="resetForm">取消编辑</button>
        </div>
      </form>
    </section>

    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>案例列表</h2>
          <p>点击编辑会读取完整详情，再回填到上方表单。</p>
        </div>
        <button class="button button-ghost" type="button" @click="loadCases">刷新</button>
      </div>

      <div v-if="loading" class="muted">正在加载案例...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>标题</th>
              <th>地区</th>
              <th>领域</th>
              <th>主体</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in cases" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.title }}</td>
              <td>{{ item.regionName || '-' }}</td>
              <td>{{ item.category || '-' }}</td>
              <td>{{ item.actorName || '-' }}</td>
              <td><span class="status-pill">{{ item.status || '-' }}</span></td>
              <td>
                <div class="row-actions">
                  <button type="button" @click="startEdit(item)">编辑</button>
                  <button type="button" class="danger" @click="removeCase(item)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { createCase, deleteCase, getCaseDetail, getCases, updateCase } from '@/api/case'
import { getRegions } from '@/api/region'
import { createSource, getSources } from '@/api/source'

const today = new Date().toISOString().slice(0, 10)
const loading = ref(false)
const error = ref('')
const cases = ref([])
const regions = ref([])
const sources = ref([])
const editingId = ref(null)
const showQuickSource = ref(false)
const sourceCreating = ref(false)

const defaultForm = () => ({
  title: '',
  regionId: '',
  category: '',
  actorName: '',
  sourceId: '',
  summary: '',
  businessModel: '',
  aiTools: '',
  outcome: '',
  tags: '',
  originalUrl: '',
  localFile: '',
  accessedAt: today,
  status: 'published',
  reviewer: '',
})

const form = reactive(defaultForm())
const quickSource = reactive(defaultQuickSource())

async function loadCases() {
  loading.value = true
  error.value = ''
  try {
    cases.value = await getCases()
  } catch (err) {
    error.value = err.message || '案例加载失败'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = null
  Object.assign(form, defaultForm())
}

function defaultQuickSource() {
  return {
    title: '',
    sourceType: 'web',
    publisher: '',
    url: '',
    localFile: '',
    accessedAt: today,
    notes: '',
    status: 'active',
  }
}

async function createSourceInline() {
  if (!quickSource.title) {
    window.alert('请先填写新来源标题')
    return
  }

  sourceCreating.value = true
  try {
    const created = await createSource({ ...quickSource })
    sources.value = await getSources()
    form.sourceId = Number(created.id)
    Object.assign(quickSource, defaultQuickSource())
    showQuickSource.value = false
  } finally {
    sourceCreating.value = false
  }
}

async function startEdit(item) {
  const detail = await getCaseDetail(item.id)
  editingId.value = detail.id
  Object.assign(form, {
    ...defaultForm(),
    ...detail,
    accessedAt: detail.accessedAt || today,
  })
}

function toPayload() {
  return {
    ...form,
    regionId: Number(form.regionId),
    sourceId: Number(form.sourceId),
  }
}

async function submitForm() {
  const payload = toPayload()
  if (editingId.value) {
    await updateCase(editingId.value, payload)
  } else {
    await createCase(payload)
  }
  resetForm()
  await loadCases()
}

async function removeCase(item) {
  if (!window.confirm(`确认删除案例「${item.title}」吗？`)) {
    return
  }
  await deleteCase(item.id)
  await loadCases()
}

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const [regionList, sourceList, caseList] = await Promise.all([getRegions(), getSources(), getCases()])
    regions.value = regionList
    sources.value = sourceList
    cases.value = caseList
  } catch (err) {
    error.value = err.message || '案例加载失败'
  } finally {
    loading.value = false
  }
})
</script>
