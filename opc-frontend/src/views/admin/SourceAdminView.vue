<template>
  <div class="admin-stack">
    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>{{ editingId ? '编辑来源' : '新增来源' }}</h2>
          <p>来源用于追溯政策、案例或文献的原始出处。</p>
        </div>
      </div>

      <form class="admin-form" @submit.prevent="submitForm">
        <div v-if="formError" class="error span-3" role="alert">{{ formError }}</div>
        <label class="span-2">
          <span>来源标题 *</span>
          <input
            v-model.trim="form.title"
            required
            placeholder="例如：北京市人工智能产业政策原文"
            @input="formError = ''"
          />
        </label>
        <label>
          <span>来源类型 *</span>
          <select v-model="form.sourceType" required>
            <option value="web">网页</option>
            <option value="file">文件</option>
            <option value="paper">文献</option>
            <option value="news">新闻</option>
            <option value="other">其他</option>
          </select>
        </label>
        <label>
          <span>发布机构</span>
          <input v-model.trim="form.publisher" placeholder="例如：北京市政府" />
        </label>
        <label class="span-2">
          <span>来源链接</span>
          <input v-model.trim="form.url" placeholder="https://..." />
        </label>
        <label>
          <span>本地文件名</span>
          <input v-model.trim="form.localFile" placeholder="文件名或路径" />
        </label>
        <label>
          <span>访问日期 *</span>
          <input v-model="form.accessedAt" required type="date" />
        </label>
        <label>
          <span>状态 *</span>
          <select v-model="form.status" required>
            <option value="active">可用</option>
            <option value="pending">待补充</option>
            <option value="archived">归档</option>
          </select>
        </label>
        <label class="span-3">
          <span>备注</span>
          <textarea v-model.trim="form.notes" rows="3" placeholder="补充说明"></textarea>
        </label>
        <div class="admin-actions span-3">
          <button class="button" type="submit">{{ editingId ? '保存修改' : '新增来源' }}</button>
          <button v-if="editingId" class="button button-ghost" type="button" @click="resetForm">取消编辑</button>
        </div>
      </form>
    </section>

    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>来源列表</h2>
          <p>政策和案例详情页会引用这里的来源记录。</p>
        </div>
      </div>

      <div v-if="loading" class="muted">正在加载来源...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>标题</th>
              <th>类型</th>
              <th>发布机构</th>
              <th>访问日期</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="source in sources" :key="source.id">
              <td>{{ source.id }}</td>
              <td>{{ source.title }}</td>
              <td><span class="chip">{{ source.sourceType }}</span></td>
              <td>{{ source.publisher || '-' }}</td>
              <td>{{ source.accessedAt || '-' }}</td>
              <td><span class="status-pill">{{ sourceStatusLabel(source.status) }}</span></td>
              <td>
                <div class="row-actions">
                  <button type="button" @click="startEdit(source)">编辑</button>
                  <button type="button" class="danger" @click="removeSource(source)">删除</button>
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
import { createSource, deleteSource, getSources, updateSource } from '@/api/source'

const today = new Date().toISOString().slice(0, 10)
const loading = ref(false)
const error = ref('')
const formError = ref('')
const sources = ref([])
const editingId = ref(null)
const form = reactive({
  title: '',
  sourceType: 'web',
  publisher: '',
  url: '',
  localFile: '',
  accessedAt: today,
  notes: '',
  status: 'active',
})

async function loadSources() {
  loading.value = true
  error.value = ''
  try {
    sources.value = await getSources()
  } catch (err) {
    error.value = err.message || '来源加载失败'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = null
  formError.value = ''
  Object.assign(form, {
    title: '',
    sourceType: 'web',
    publisher: '',
    url: '',
    localFile: '',
    accessedAt: today,
    notes: '',
    status: 'active',
  })
}

function startEdit(source) {
  editingId.value = source.id
  formError.value = ''
  Object.assign(form, {
    title: source.title || '',
    sourceType: source.sourceType || 'web',
    publisher: source.publisher || '',
    url: source.url || '',
    localFile: source.localFile || '',
    accessedAt: source.accessedAt || today,
    notes: source.notes || '',
    status: source.status || 'active',
  })
}

async function submitForm() {
  const payload = { ...form }
  formError.value = ''
  try {
    if (editingId.value) {
      await updateSource(editingId.value, payload)
    } else {
      await createSource(payload)
    }
  } catch (err) {
    formError.value = err.message || '来源保存失败'
    return
  }
  resetForm()
  await loadSources()
}

function sourceStatusLabel(status) {
  return {
    active: '可用',
    pending: '待补充',
    archived: '归档',
  }[status] || status || '-'
}

async function removeSource(source) {
  if (!window.confirm(`确认删除来源「${source.title}」吗？`)) {
    return
  }
  await deleteSource(source.id)
  await loadSources()
}

onMounted(loadSources)
</script>
