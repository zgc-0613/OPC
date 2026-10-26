<template>
  <div ref="homeRoot" class="atlas-home">
    <section class="atlas-hero" aria-labelledby="atlas-hero-title">
      <div class="atlas-hero-image" aria-hidden="true"></div>
      <div class="atlas-hero-shade" aria-hidden="true"></div>
      <div class="atlas-hero-inner">
        <h1 id="atlas-hero-title">发现一个人的<br /><em>更多可能</em></h1>
        <p class="atlas-hero-subtitle">AI + OPC</p>
        <p class="atlas-hero-description">在庞大信息中，找到与你相关的政策、案例、高校实践与平台资源，连接更广阔的机会。</p>
        <div class="atlas-hero-actions">
          <RouterLink class="atlas-button atlas-button-primary" to="/analysis">开始探索 <ArrowRight :size="21" /></RouterLink>
        </div>
        <div class="atlas-hero-scroll" aria-hidden="true"><span>向下探索</span><span class="atlas-hero-scroll-line"></span></div>
      </div>
      <nav class="atlas-hero-destinations" aria-label="快速前往各板块">
        <RouterLink class="atlas-map-label atlas-map-label-university" to="/university-opc" aria-label="进入高校 OPC"><span class="atlas-map-symbol"><GraduationCap :size="25" /></span><span class="atlas-map-copy"><strong>高校</strong><small>探索高校社区<br />走近创业实践</small></span><ArrowUpRight class="atlas-map-arrow" :size="18" /></RouterLink>
        <RouterLink class="atlas-map-label atlas-map-label-policy" to="/policies" aria-label="进入政策索引"><span class="atlas-map-symbol"><FileText :size="24" /></span><span class="atlas-map-copy"><strong>政策</strong><small>解读政策要点<br />把握发展方向</small></span><ArrowUpRight class="atlas-map-arrow" :size="18" /></RouterLink>
        <RouterLink class="atlas-map-label atlas-map-label-case" to="/cases" aria-label="进入案例索引"><span class="atlas-map-symbol"><Search :size="24" /></span><span class="atlas-map-copy"><strong>案例</strong><small>观察真实案例<br />借鉴可行路径</small></span><ArrowUpRight class="atlas-map-arrow" :size="18" /></RouterLink>
        <RouterLink class="atlas-map-label atlas-map-label-platform" to="/platforms" aria-label="进入平台集合"><span class="atlas-map-symbol"><Layers3 :size="24" /></span><span class="atlas-map-copy"><strong>平台</strong><small>连接 OPC 资源<br />找到合适入口</small></span><ArrowUpRight class="atlas-map-arrow" :size="18" /></RouterLink>
      </nav>
    </section>

    <section class="atlas-home-intro" aria-labelledby="atlas-intro-title">
      <div class="atlas-section-label" data-reveal>01 / 探索路径</div>
      <div class="atlas-intro-heading" data-reveal>
        <h2 id="atlas-intro-title">让信息形成线索</h2>
        <p>用一致的索引方式连接政策、创业案例与高校实践；需要深入研究时，再进入资料分析工作区。</p>
      </div>
      <div class="atlas-path-grid">
        <RouterLink class="atlas-path-card" to="/policies" data-reveal><span class="atlas-path-index">01 / POLICY</span><Landmark :size="30" :stroke-width="1.4" /><h3>政策索引</h3><p>按地区、类型和主题定位政策，查看原文及来源。</p><ArrowUpRight class="atlas-path-arrow" :size="20" /></RouterLink>
        <RouterLink class="atlas-path-card" to="/cases" data-reveal><span class="atlas-path-index">02 / CASE</span><BriefcaseBusiness :size="30" :stroke-width="1.4" /><h3>案例索引</h3><p>追踪创业主体、模式、工具与成果的公开记录。</p><ArrowUpRight class="atlas-path-arrow" :size="20" /></RouterLink>
        <RouterLink class="atlas-path-card" to="/analysis" data-reveal><span class="atlas-path-index">03 / RESEARCH</span><ChartNoAxesCombined :size="30" :stroke-width="1.4" /><h3>资料分析</h3><p>汇总资料概览、研究指标和地区覆盖情况。</p><ArrowUpRight class="atlas-path-arrow" :size="20" /></RouterLink>
      </div>
    </section>

    <section class="atlas-home-secondary" aria-label="更多探索方向">
      <RouterLink to="/platforms" data-reveal><span>资源导航 / 04</span><h2>平台集合</h2><p>按分类浏览 OPC 生态、地方服务与政务专区的网站入口。</p><ArrowUpRight :size="22" /></RouterLink>
      <RouterLink to="/university-opc" data-reveal><span>高校实践 / 05</span><h2>高校 OPC</h2><p>查看高校社区、支持措施、活动及创业案例。</p><ArrowUpRight :size="22" /></RouterLink>
    </section>

    <footer class="atlas-home-footer">
      <div class="atlas-home-footer-top"><span>壹企经纬 · SoloFirm</span><RouterLink to="/sources">查看来源台账 <ArrowUpRight :size="15" /></RouterLink></div>
      <section class="atlas-home-contact" aria-labelledby="atlas-contact-title">
        <div class="atlas-contact-intro" data-reveal><span class="atlas-section-label">02 / 合作联系</span><h2 id="atlas-contact-title">联系我们</h2><p>关于 OPC 研究、平台合作与资料交流，欢迎来信。</p></div>
        <address class="atlas-contact-person" data-reveal><span>西北工业大学软件学院</span><strong>王兵书</strong><a href="mailto:wangbingshu@mail.nwpu.edu.cn">wangbingshu@mail.nwpu.edu.cn <ArrowUpRight :size="19" /></a></address>
      </section>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ArrowRight, ArrowUpRight, BriefcaseBusiness, ChartNoAxesCombined, FileText, GraduationCap, Landmark, Layers3, Search } from 'lucide-vue-next'

const homeRoot = ref(null)
let revealObserver

onMounted(() => {
  if (!('IntersectionObserver' in window) || window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches) return
  const targets = homeRoot.value?.querySelectorAll('[data-reveal]') ?? []
  revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return
      entry.target.classList.remove('is-reveal-pending')
      revealObserver.unobserve(entry.target)
    })
  }, { threshold: 0.12, rootMargin: '0px 0px -32px 0px' })
  targets.forEach((target) => {
    if (target.getBoundingClientRect().top < window.innerHeight * 0.85) return
    target.classList.add('is-reveal-pending')
    revealObserver.observe(target)
  })
})

onUnmounted(() => revealObserver?.disconnect())
</script>
