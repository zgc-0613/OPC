<template>
  <div class="admin-stack">
    <section class="admin-panel admin-hero">
      <div>
        <span class="caption">DATA OPERATIONS</span>
        <h2>录入、校对、维护政策与案例资料</h2>
        <p>第一版后台先解决团队成员可视化维护数据的问题，不做复杂权限和工作流。</p>
      </div>
      <div class="admin-quick">
        <RouterLink class="button" to="/admin/policies">维护政策</RouterLink>
        <RouterLink class="button button-ghost" to="/admin/sources">维护来源</RouterLink>
      </div>
    </section>

    <section class="admin-grid">
      <RouterLink class="admin-card" to="/admin/policies">
        <span>01</span>
        <strong>政策管理</strong>
        <p>新增、编辑、删除政策记录，维护摘要、标签、来源和实施字段。</p>
      </RouterLink>
      <RouterLink class="admin-card" to="/admin/cases">
        <span>02</span>
        <strong>案例管理</strong>
        <p>维护 AI + OPC / 一人公司案例，沉淀应用场景和成果。</p>
      </RouterLink>
      <RouterLink class="admin-card" to="/admin/sources">
        <span>03</span>
        <strong>来源管理</strong>
        <p>维护原文链接、文件名、访问日期和来源说明。</p>
      </RouterLink>
      <RouterLink class="admin-card" to="/admin/tags">
        <span>04</span>
        <strong>标签管理</strong>
        <p>维护政策、案例通用标签，为筛选和摘要分析服务。</p>
      </RouterLink>
    </section>
  </div>
</template>
