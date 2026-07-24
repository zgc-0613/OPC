<template>
  <div class="admin-stack">
    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>{{ editingId ? '编辑标签' : '新增标签' }}</h2>
          <p>标签用于给政策和案例做主题分类，例如资金补贴、算力支持、场景需求。</p>
        </div>
      </div>

      <form class="admin-form three-cols" @submit.prevent="submitForm">
        <label>
          <span>标签名称 *</span>
          <input v-model.trim="form.name" required placeholder="例如：资金补贴" />
        </label>
        <label>
          <span>标签类型 *</span>
          <select v-model="form.tagType" required>
            <option value="policy">政策标签</option>
            <option value="case">案例标签</option>
            <option value="common">通用标签</option>
          </select>
        </label>
        <label>
          <span>排序</span>
          <input v-model.number="form.sortOrder" type="number" min="0" />
        </label>
        <label class="settings-toggle-row">
          <input v-model="form.isIndustry" type="checkbox" />
          <span>
            行业标签
            <small>纳入用户端行业选择与政策、案例的共同分析范围。</small>
          </span>
        </label>
        <div class="admin-actions">
          <button class="button" type="submit">{{ editingId ? '保存修改' : '新增标签' }}</button>
          <button v-if="editingId" class="button button-ghost" type="button" @click="resetForm">取消编辑</button>
        </div>
      </form>
    </section>

    <section class="admin-panel">
      <div class="admin-section-head">
        <div>
          <h2>标签列表</h2>
          <p>当前数据库中的标签记录。</p>
        </div>
      </div>

      <div v-if="loading" class="muted">正在加载标签...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>名称</th>
              <th>类型</th>
              <th>行业维度</th>
              <th>排序</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="tag in tags" :key="tag.id">
              <td>{{ tag.id }}</td>
              <td>{{ tag.name }}</td>
              <td><span class="chip">{{ tag.tagType }}</span></td>
              <td><span class="chip">{{ tag.isIndustry ? '是' : '否' }}</span></td>
              <td>{{ tag.sortOrder ?? 0 }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" @click="startEdit(tag)">编辑</button>
                  <button type="button" class="danger" @click="removeTag(tag)">删除</button>
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
import { createTag, deleteTag, getTags, updateTag } from '@/api/tag'

const loading = ref(false)
const error = ref('')
const tags = ref([])
const editingId = ref(null)
const form = reactive({
  name: '',
  tagType: 'policy',
  isIndustry: false,
  sortOrder: 0,
})

async function loadTags() {
  loading.value = true
  error.value = ''
  try {
    tags.value = await getTags()
  } catch (err) {
    error.value = err.message || '标签加载失败'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = null
  form.name = ''
  form.tagType = 'policy'
  form.isIndustry = false
  form.sortOrder = 0
}

function startEdit(tag) {
  editingId.value = tag.id
  form.name = tag.name || ''
  form.tagType = tag.tagType || 'policy'
  form.isIndustry = Boolean(tag.isIndustry)
  form.sortOrder = tag.sortOrder ?? 0
}

async function submitForm() {
  const payload = { ...form }
  if (editingId.value) {
    await updateTag(editingId.value, payload)
  } else {
    await createTag(payload)
  }
  resetForm()
  await loadTags()
}

async function removeTag(tag) {
  if (!window.confirm(`确认删除标签「${tag.name}」吗？`)) {
    return
  }
  await deleteTag(tag.id)
  await loadTags()
}

onMounted(loadTags)
</script>
