<template>
  <aside class="review-queue" aria-label="证据审核队列">
    <header class="review-queue__header">
      <div>
        <span class="caption">REVIEW QUEUE</span>
        <strong>{{ total }} 项资料</strong>
      </div>
      <span v-if="selectedCount" class="review-queue__count">已选 {{ selectedCount }}</span>
    </header>

    <div v-if="loading" class="review-queue__state" role="status">正在读取待审队列...</div>
    <div v-else-if="!groups.length" class="review-queue__state">
      <strong>当前筛选下暂无资料</strong>
      <span>调整状态、类型或搜索条件后重试。</span>
    </div>
    <div v-else class="review-queue__groups">
      <section v-for="group in groups" :key="group.key" class="review-group">
        <button
          v-if="group.source"
          type="button"
          class="review-group__source"
          :class="{ 'is-active': selectedKey === itemKey(group.source) }"
          @click="$emit('select', group.source)"
        >
          <input
            type="checkbox"
            :checked="selectedKeys.has(itemKey(group.source))"
            aria-label="选择来源"
            @click.stop
            @change="$emit('toggle', group.source, $event.target.checked)"
          />
          <span class="review-group__source-copy">
            <small>来源 · {{ statusLabel(group.source.evidenceStatus) }}</small>
            <strong>{{ group.source.title }}</strong>
          </span>
          <span class="review-dot" :class="group.source.reviewable ? 'is-ready' : 'is-blocked'"></span>
        </button>
        <div v-else class="review-group__source review-group__source--missing">
          <span class="review-group__source-copy">
            <small>来源关系</small>
            <strong>{{ group.title }}</strong>
          </span>
        </div>

        <button
          v-for="item in group.items"
          :key="itemKey(item)"
          type="button"
          class="review-queue-item"
          :class="{ 'is-active': selectedKey === itemKey(item) }"
          @click="$emit('select', item)"
        >
          <input
            type="checkbox"
            :checked="selectedKeys.has(itemKey(item))"
            :aria-label="`选择${typeLabel(item.itemType)} ${item.title}`"
            @click.stop
            @change="$emit('toggle', item, $event.target.checked)"
          />
          <span class="review-queue-item__copy">
            <small>{{ typeLabel(item.itemType) }} · {{ statusLabel(item.evidenceStatus) }}</small>
            <strong>{{ item.title }}</strong>
            <span v-if="item.blockingReasons?.length">{{ item.blockingReasons[0] }}</span>
            <span v-else>资料完整待核验</span>
          </span>
          <time>{{ formatDate(item.updatedAt) }}</time>
        </button>
      </section>
    </div>

    <nav v-if="totalPages > 1" class="review-queue__pagination" aria-label="审核队列分页">
      <button type="button" :disabled="page <= 1 || loading" @click="$emit('page', page - 1)">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button type="button" :disabled="page >= totalPages || loading" @click="$emit('page', page + 1)">下一页</button>
    </nav>
  </aside>
</template>

<script setup>
defineProps({
  groups: { type: Array, default: () => [] },
  loading: Boolean,
  selectedKey: { type: String, default: '' },
  selectedKeys: { type: Set, default: () => new Set() },
  selectedCount: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  totalPages: { type: Number, default: 1 },
})

defineEmits(['select', 'toggle', 'page'])

const itemKey = (item) => `${item.itemType}:${item.itemId}`
const typeLabel = (type) => ({ case: '案例', policy: '政策', source: '来源' }[type] || type)
const statusLabel = (status) => ({ legacy_unverified: '待审核', verified: '已核验', excluded: '已排除' }[status] || status)
const formatDate = (value) => value ? new Date(value).toLocaleDateString('zh-CN') : '-'
</script>

<style scoped>
.review-queue { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; height: 100%; min-height: 0; border-right: 1px solid #cfd3ce; background: #f7f8f4; }
.review-queue__header { display: flex; align-items: end; justify-content: space-between; gap: 16px; padding: 22px 20px 18px; border-bottom: 1px solid #d7dad5; }
.review-queue__header div { display: grid; gap: 7px; }.review-queue__header strong { color: #181a18; font-family: 'Noto Serif SC', STSong, SimSun, serif; font-size: 1.05rem; font-weight: 600; }
.review-queue__count { color: #35523d; font-size: .75rem; }.review-queue__state { display: grid; align-content: center; justify-items: start; gap: 8px; min-height: 300px; padding: 24px; color: #646b65; }.review-queue__state strong { color: #282c28; }
.review-queue__groups { min-height: 0; overflow: auto; }.review-group { border-bottom: 1px solid #d7dad5; }
.review-group__source, .review-queue-item { width: 100%; border: 0; border-radius: 0; background: transparent; color: inherit; text-align: left; }
.review-group__source { display: grid; grid-template-columns: 18px minmax(0, 1fr) 10px; align-items: center; gap: 10px; padding: 15px 18px; border-bottom: 1px solid #e0e2de; }
.review-group__source:hover, .review-group__source.is-active, .review-queue-item:hover, .review-queue-item.is-active { background: #eceee9; }
.review-group__source--missing { grid-template-columns: minmax(0, 1fr); color: #6c554f; }.review-group__source-copy, .review-queue-item__copy { display: grid; min-width: 0; gap: 4px; }
.review-group__source small, .review-queue-item small { color: #69706a; font-family: 'Bookman Old Style', Georgia, serif; font-size: .67rem; letter-spacing: 0; text-transform: uppercase; }
.review-group__source strong, .review-queue-item strong { overflow: hidden; color: #202320; font-family: 'Noto Serif SC', STSong, SimSun, serif; font-size: .88rem; font-weight: 600; line-height: 1.45; text-overflow: ellipsis; white-space: nowrap; }
.review-dot { width: 8px; height: 8px; border-radius: 50%; background: #a78044; }.review-dot.is-ready { background: #3e6749; }.review-dot.is-blocked { background: #8b4038; }
.review-queue-item { display: grid; grid-template-columns: 18px minmax(0, 1fr) auto; align-items: start; gap: 10px; padding: 14px 18px 14px 28px; border-bottom: 1px solid #e5e7e2; }
.review-queue-item__copy > span { overflow: hidden; color: #747a75; font-size: .72rem; text-overflow: ellipsis; white-space: nowrap; }.review-queue-item time { color: #7b817c; font-family: 'Bookman Old Style', Georgia, serif; font-size: .66rem; }
.review-queue input { width: 15px; height: 15px; accent-color: #202320; }
.review-queue__pagination { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); align-items: center; gap: 10px; padding: 14px 18px; border-top: 1px solid #d7dad5; }
.review-queue__pagination button { width: 92px; min-height: 34px; justify-self: start; border: 1px solid #cbd0ca; background: #fff; color: #252925; }
.review-queue__pagination button:last-child { justify-self: end; }
.review-queue__pagination span { color: #646b65; font-size: .74rem; white-space: nowrap; }
@media (max-width: 980px) { .review-queue { height: auto; min-height: 560px; border-right: 0; } }
@media (max-width: 420px) { .review-queue__pagination { gap: 8px; padding-inline: 12px; }.review-queue__pagination button { width: 84px; } }
</style>
