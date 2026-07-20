<template>
  <div class="page-stack home-archive-page">
    <section class="archive-hero prisma-hero">
      <div class="prisma-hero-frame">
        <video
          class="prisma-hero-media"
          src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260405_170732_8a9ccda6-5cff-4628-b164-059c500a2b41.mp4"
          autoplay
          loop
          muted
          playsinline
        ></video>
        <div class="noise-overlay" aria-hidden="true"></div>
        <div class="prisma-hero-gradient" aria-hidden="true"></div>

        <header class="home-landing-nav prisma-hero-nav">
          <nav class="home-landing-links prisma-hanging-nav" aria-label="首页导航">
            <RouterLink to="/">首页</RouterLink>
            <RouterLink to="/regions">地区目录</RouterLink>
            <RouterLink to="/policies">政策库</RouterLink>
            <RouterLink to="/cases">案例库</RouterLink>
            <RouterLink to="/sources">来源台账</RouterLink>
          </nav>

          <RouterLink
            class="prisma-hero-account-entry"
            :class="{
              'is-authenticated': Boolean(currentUser),
              'is-pending': accountLoginNoticeVisible,
            }"
            :to="accountEntryTarget"
            :aria-label="currentUser ? `前往 ${currentUser.username} 的个人主页` : '登录后进入个人主页'"
            :title="currentUser ? '前往个人主页' : '登录后进入个人主页'"
            @click="handleAccountEntryClick"
          >
            <UserRound :size="25" :stroke-width="1.7" aria-hidden="true" />
          </RouterLink>

          <Transition name="account-toast">
            <div
              v-if="accountLoginNoticeVisible"
              class="prisma-account-login-notice"
              role="status"
              aria-live="polite"
            >
              <LogIn :size="18" aria-hidden="true" />
              <span>
                <strong>请先登录</strong>
                <small>3 秒后前往登录页</small>
              </span>
            </div>
          </Transition>
        </header>

        <div class="archive-hero-copy prisma-hero-copy">
          <div class="prisma-hero-title-wrap">
            <div class="prisma-hero-brand-lockup">
              <BrandMark />
              <h1 class="prisma-hero-title">
                <WordsPullUp text="SoloFirm" />
              </h1>
            </div>
          </div>
          <div class="prisma-hero-aside">
            <p>
              汇聚 AI + OPC 相关政策、案例与来源资料，帮助创业者快速定位地区机会、政策支持与可参考案例。
            </p>
            <div class="archive-hero-actions">
              <RouterLink class="button prisma-primary-cta" to="/policies">
                <span>进入政策库</span>
                <span class="prisma-cta-icon" aria-hidden="true"><ArrowRight :size="18" /></span>
              </RouterLink>
              <RouterLink class="button button-ghost prisma-ghost-cta" to="/cases">查看案例库</RouterLink>
              <RouterLink
                class="button button-ghost prisma-ghost-cta prisma-login-cta"
                :class="accountButtonClass"
                :to="accountTarget"
                :aria-label="currentUser ? `退出用户 ${currentUser.username}` : '登录'"
                :title="currentUser ? '退出登录' : '登录'"
                :aria-disabled="accountSigningOut || undefined"
                @click="handleAccountButtonClick"
                @pointerdown="handleAccountPointerDown"
                @pointerup="releaseAccountPreview"
                @pointercancel="clearAccountPreview"
                @pointerleave="handleAccountPointerLeave"
                @contextmenu="handleAccountContextMenu"
              >
                <span v-if="currentUser" class="prisma-account-icon" aria-hidden="true">
                  <UserRound class="prisma-account-icon--current" :size="17" />
                  <LogOut class="prisma-account-icon--destination" :size="17" />
                </span>
                <LogIn v-else :size="17" aria-hidden="true" />
                <span v-if="currentUser" class="prisma-account-copy" aria-hidden="true">
                  <span class="prisma-account-state prisma-account-state--current prisma-account-label">
                    <span>已登录用户：</span>
                    <strong class="prisma-account-username">{{ currentUser.username }}</strong>
                  </span>
                  <span class="prisma-account-state prisma-account-state--destination">
                    {{ accountSigningOut ? '正在退出...' : '退出登录' }}
                  </span>
                </span>
                <span v-else>登录</span>
              </RouterLink>
            </div>
          </div>
        </div>
      </div>

      <a class="home-scroll-cue" href="#home-data-view" aria-label="跳转到资料分析概览"></a>
    </section>

    <section class="prisma-about" aria-labelledby="prisma-about-title">
      <div class="prisma-about-card">
        <h2 id="prisma-about-title" class="prisma-about-title">
          <WordsPullUpMultiStyle :segments="aboutSegments" />
        </h2>
        <AnimatedLetters :text="aboutBody" />
        <div class="archive-hero-proof prisma-about-proof" aria-label="平台核心能力">
          <span>地区分类检索</span>
          <span>来源出处追溯</span>
          <span>摘要标签分析</span>
        </div>
      </div>
    </section>

    <section id="home-data-view" class="prisma-features">
      <div class="bg-noise" aria-hidden="true"></div>
      <header class="prisma-features-heading">
        <WordsPullUpMultiStyle :segments="featureHeadingSegments" tag="h2" />
      </header>

      <div class="prisma-feature-grid">
        <article class="prisma-feature-card prisma-feature-card--video scroll-reveal" style="--feature-index: 0">
          <video
            src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260406_133058_0504132a-0cf3-4450-a370-8ea3b05c95d4.mp4"
            autoplay
            loop
            muted
            playsinline
          ></video>
          <div class="prisma-feature-video-gradient" aria-hidden="true"></div>
          <strong>你的智能创业研究画布。</strong>
        </article>

        <article class="prisma-feature-card scroll-reveal" style="--feature-index: 1">
          <img
            class="prisma-feature-icon"
            src="https://images.higgs.ai/?default=1&output=webp&url=https%3A%2F%2Fd8j0ntlcm91z4.cloudfront.net%2Fuser_38xzZboKViGWJOttwIXH07lWA1P%2Fhf_20260405_171918_4a5edc79-d78f-4637-ac8b-53c43c220606.png&w=1280&q=85"
            alt=""
          />
          <div class="prisma-feature-title">
            <h3>政策索引。</h3>
            <span>01</span>
          </div>
          <ul>
            <li><Check :size="16" />按地区与政策类型筛选</li>
            <li><Check :size="16" />标题、摘要与标签检索</li>
            <li><Check :size="16" />政策原文与辅证链接</li>
            <li><Check :size="16" />Excel 导出与点击热度</li>
          </ul>
          <RouterLink class="prisma-learn-link" to="/policies">
            <span>进入政策库</span><ArrowRight :size="17" />
          </RouterLink>
        </article>

        <article class="prisma-feature-card scroll-reveal" style="--feature-index: 2">
          <img
            class="prisma-feature-icon"
            src="https://images.higgs.ai/?default=1&output=webp&url=https%3A%2F%2Fd8j0ntlcm91z4.cloudfront.net%2Fuser_38xzZboKViGWJOttwIXH07lWA1P%2Fhf_20260405_171741_ed9845ab-f5b2-4018-8ce7-07cc01823522.png&w=1280&q=85"
            alt=""
          />
          <div class="prisma-feature-title">
            <h3>案例洞察。</h3>
            <span>02</span>
          </div>
          <ul>
            <li><Check :size="16" />按地区、类型与关键词定位</li>
            <li><Check :size="16" />商业模式与 AI 工具拆解</li>
            <li><Check :size="16" />成果、主体与来源追溯</li>
          </ul>
          <RouterLink class="prisma-learn-link" to="/cases">
            <span>查看案例库</span><ArrowRight :size="17" />
          </RouterLink>
        </article>

        <article class="prisma-feature-card scroll-reveal" style="--feature-index: 3">
          <img
            class="prisma-feature-icon"
            src="https://images.higgs.ai/?default=1&output=webp&url=https%3A%2F%2Fd8j0ntlcm91z4.cloudfront.net%2Fuser_38xzZboKViGWJOttwIXH07lWA1P%2Fhf_20260405_171809_f56666dc-c099-4778-ad82-9ad4f209567b.png&w=1280&q=85"
            alt=""
          />
          <div class="prisma-feature-title">
            <h3>证据台账。</h3>
            <span>03</span>
          </div>
          <ul>
            <li><Check :size="16" />来源链接与访问日期留痕</li>
            <li><Check :size="16" />地区资料覆盖实时汇总</li>
            <li><Check :size="16" />状态、文件与发布单位复核</li>
          </ul>
          <RouterLink class="prisma-learn-link" to="/sources">
            <span>打开来源台账</span><ArrowRight :size="17" />
          </RouterLink>
        </article>
      </div>

      <header class="home-section-heading prisma-features-heading scroll-reveal">
        <WordsPullUpMultiStyle :segments="analysisHeadingSegments" tag="h2" />
      </header>

      <div v-if="loading" class="analysis-state analysis-state--loading" role="status" aria-live="polite">
        正在同步资料分析数据...
      </div>
      <div v-else-if="error" class="analysis-state analysis-state--error" role="alert">
        {{ error }}。分析结构与真实接口请求已保留，请确认后端服务和数据库状态。
      </div>

      <section id="home-analysis" class="analysis-grid archive-analysis-grid" @pointermove="handlePanelSpotlight">
      <article class="panel analysis-panel wide-panel public-visit-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>平台访问热度</h2>
            <p>基于公开页面访问记录动态统计，反映近期内容关注情况。</p>
          </div>
          <span class="analysis-badge">LIVE</span>
        </div>

        <div class="public-visit-layout">
          <div class="public-visit-summary">
            <div>
              <span>总访问量</span>
              <strong>{{ formatNumber(visitSummary.totalPv) }}</strong>
              <small>PV</small>
            </div>
            <div>
              <span>独立访客</span>
              <strong>{{ formatNumber(visitSummary.totalUv) }}</strong>
              <small>UV</small>
            </div>
            <div>
              <span>今日访问</span>
              <strong>{{ formatNumber(visitSummary.todayPv) }}</strong>
              <small>今日 PV</small>
            </div>
          </div>

          <div class="public-visit-trend">
            <span>最近七天趋势</span>
            <div v-if="visitTrend.length" class="public-visit-bars">
              <i
                v-for="item in visitTrendBars"
                :key="item.date"
                :style="{ height: `${item.height}%` }"
                :title="`${item.date}: ${item.pv}`"
              ></i>
            </div>
            <p v-else class="muted">暂无访问趋势。</p>
          </div>

          <div class="public-visit-hot">
            <div>
              <span>热门政策</span>
              <strong>{{ topPolicy?.title || '暂无数据' }}</strong>
            </div>
            <div>
              <span>热门案例</span>
              <strong>{{ topCase?.title || '暂无数据' }}</strong>
            </div>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel wide-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>地区资料排行</h2>
            <p>合并政策和案例的地区字段统计。</p>
          </div>
          <span class="analysis-badge">TOP {{ topRegions.length }}</span>
        </div>

        <div v-if="!topRegions.length" class="muted">暂无地区统计数据。</div>
        <div v-else class="rank-chart">
          <div v-for="item in topRegions" :key="item.name" class="rank-row">
            <span class="rank-name">{{ item.name }}</span>
            <div class="rank-track">
              <span :style="{ width: `${item.percent}%` }"></span>
            </div>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>来源追溯概览</h2>
            <p>统计来源链接、访问日期与出处信息的记录情况。</p>
          </div>
        </div>

        <div class="trace-list source-trace-list">
          <div v-for="item in sourceTraceStats" :key="item.name" class="trace-item">
            <div>
              <strong>{{ item.rate }}%</strong>
              <span>{{ item.name }}</span>
              <small>{{ item.count }}/{{ item.total }}</small>
            </div>
            <div class="trace-ring" :style="{ '--value': `${item.rate}%`, '--color': item.color }">
              <span>{{ item.rate }}%</span>
            </div>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>高频标签</h2>
            <p>从政策和案例标签字段提取高频主题。</p>
          </div>
        </div>

        <div v-if="!tagStats.length" class="muted">暂无标签统计数据。</div>
        <div v-else class="tag-cloud">
          <span
            v-for="tag in tagStats"
            :key="tag.name"
            :style="{ '--weight': tag.weight }"
          >
            {{ tag.name }} <b>{{ tag.count }}</b>
          </span>
        </div>
      </article>

      <article class="panel analysis-panel wide-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>政策发布趋势</h2>
            <p>按月份统计政策发布日期。</p>
          </div>
          <div class="policy-trend-range" role="group" aria-label="政策发布趋势时间范围">
            <button
              v-for="option in publishTrendRangeOptions"
              :key="option.value"
              type="button"
              :class="{ 'is-active': publishTrendRange === option.value }"
              :aria-pressed="publishTrendRange === option.value"
              @click="publishTrendRange = option.value"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div v-if="!publishTrend.length" class="muted">暂无发布日期数据。</div>
        <div v-else class="trend-chart">
          <div class="policy-trend-plot" @mouseleave="clearActivePublishTrendPoint()">
            <svg viewBox="0 0 640 210" role="group" aria-label="政策发布时间趋势，可聚焦数据点查看当月政策发布数量">
              <title>政策发布趋势，共 {{ publishTrendTotal }} 条政策</title>
              <defs>
                <linearGradient id="trendFill" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stop-color="#4F6F58" stop-opacity="0.2" />
                  <stop offset="100%" stop-color="#4F6F58" stop-opacity="0" />
                </linearGradient>
              </defs>
              <polygon class="trend-area" :points="trendAreaPoints" fill="url(#trendFill)" />
              <polyline class="trend-line" :points="trendLinePoints" fill="none" stroke="#222522" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" />
              <g v-for="(point, index) in trendDots" :key="point.label">
                <circle
                  class="policy-trend-hit-area"
                  :cx="point.x"
                  :cy="point.y"
                  r="14"
                  tabindex="0"
                  role="button"
                  :aria-label="`${formatMonthLabel(point.label)}发布 ${point.count} 条政策`"
                  @mouseenter="setActivePublishTrendPoint(point)"
                  @focus="setActivePublishTrendPoint(point)"
                  @blur="clearActivePublishTrendPoint(point)"
                  @click="setActivePublishTrendPoint(point)"
                  @keyup.enter.space.prevent="setActivePublishTrendPoint(point)"
                />
                <circle
                  class="trend-dot"
                  :class="{ 'is-active': activePublishTrendPoint?.label === point.label }"
                  :style="{ animationDelay: `${320 + index * 60}ms` }"
                  :cx="point.x"
                  :cy="point.y"
                  r="5"
                  fill="#4F6F58"
                  aria-hidden="true"
                />
              </g>
            </svg>
            <div
              v-if="activePublishTrendPoint"
              class="policy-trend-tooltip"
              :class="{ 'is-below': activePublishTrendPoint.y < 74 }"
              :style="publishTrendTooltipStyle"
              role="status"
            >
              <strong>{{ formatMonthLabel(activePublishTrendPoint.label) }}</strong>
              <span>发布 <b>{{ activePublishTrendPoint.count }}</b> 条政策</span>
            </div>
          </div>
          <div class="policy-trend-labels" aria-hidden="true">
            <span
              v-for="(item, index) in publishTrendLabels"
              :key="item.label"
              :class="{ 'is-first': index === 0, 'is-last': index === publishTrendLabels.length - 1 }"
              :style="{ left: `${(item.x / 640) * 100}%` }"
            >{{ item.label }}</span>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel insight-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>资料观察</h2>
            <p>基于当前记录生成的概览性线索。</p>
          </div>
        </div>

        <div class="insight-list">
          <p v-for="insight in insights" :key="insight">{{ insight }}</p>
        </div>
      </article>

      <article class="panel analysis-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>案例领域分布</h2>
            <p>按案例类型字段统计应用方向。</p>
          </div>
        </div>

        <div v-if="!caseCategoryStats.length" class="muted">暂无案例领域数据。</div>
        <div v-else class="category-list">
          <div v-for="item in caseCategoryStats" :key="item.name" class="category-row">
            <div>
              <span>{{ item.name }}</span>
              <strong>{{ item.count }} 条</strong>
            </div>
            <p>{{ item.percent }}%</p>
            <i :style="{ width: `${item.rate}%` }"></i>
          </div>
        </div>
      </article>

      <article class="panel analysis-panel archive-recent-panel compact-recent-panel scroll-reveal">
        <div class="section-header">
          <div>
            <h2>最近更新</h2>
            <p>最新 5 条资料记录。</p>
          </div>
        </div>

        <div v-if="!compactRecentUpdates.length" class="muted">暂无最近更新。</div>
        <div v-else class="timeline-list archive-timeline compact-recent-list">
          <div v-for="item in compactRecentUpdates" :key="`${item.itemType}-${item.itemId}`" class="list-row">
            <div>
              <span class="caption">{{ item.itemType }}</span>
              <strong>{{ item.title }}</strong>
            </div>
            <span class="muted">{{ item.updatedDate || '-' }}</span>
          </div>
        </div>
      </article>
      </section>
    </section>

    <footer class="home-contact-footer scroll-reveal">
      <section class="home-contact-card" aria-labelledby="home-contact-title">
        <div>
          <span class="home-contact-kicker">CONTACT</span>
          <h2 id="home-contact-title">联系我们，<span>共建 OPC 智能创业索引</span></h2>
          <p>
            如果你关注一人公司、AI 创业、政策资料整理或案例共建，欢迎通过以下方式与我们联系。
          </p>

          <div class="home-contact-methods" aria-label="联系方式">
            <div class="home-contact-method">
              <span class="home-contact-icon" aria-hidden="true"><MapPin :size="18" /></span>
              <div>
                <strong>所属单位</strong>
                <p>西北工业大学软件学院</p>
              </div>
            </div>

            <div class="home-contact-method">
              <span class="home-contact-icon" aria-hidden="true"><Mail :size="18" /></span>
              <div>
                <strong>联系人</strong>
                <p class="home-contact-person">
                  <span>王兵书</span>
                  <a href="mailto:wangbingshu@nwpu.edu.cn">wangbingshu@nwpu.edu.cn</a>
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="home-site-footer" aria-label="网站页脚">
        <div>
          <div class="home-footer-brand">
            <BrandMark />
            <strong>SoloFirm</strong>
          </div>
          <p>聚合 AI + OPC 相关政策、案例与来源资料，帮助创业者和研究者更快理解一人公司的机会结构。</p>
        </div>

        <div class="home-footer-contact">
          <strong>联系我们</strong>
          <span>西北工业大学软件学院</span>
          <a href="mailto:wangbingshu@nwpu.edu.cn">王兵书 wangbingshu@nwpu.edu.cn</a>
        </div>
      </section>
    </footer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Check, LogIn, LogOut, Mail, MapPin, UserRound } from 'lucide-vue-next'
import AnimatedLetters from '@/components/AnimatedLetters.vue'
import BrandMark from '@/components/BrandMark.vue'
import WordsPullUp from '@/components/WordsPullUp.vue'
import WordsPullUpMultiStyle from '@/components/WordsPullUpMultiStyle.vue'
import { getDashboardSummary } from '@/api/dashboard'
import { getCases } from '@/api/case'
import { getPolicies } from '@/api/policy'
import { getSources } from '@/api/source'
import { getVisitRankings, getVisitSummary, getVisitTrend } from '@/api/visit'
import { getUserProfile, isUserAuthenticated, logoutUser } from '@/api/auth'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const summary = ref({})
const policies = ref([])
const cases = ref([])
const sources = ref([])
const visitSummary = ref({})
const visitTrend = ref([])
const policyVisitRankings = ref([])
const caseVisitRankings = ref([])
const currentUser = ref(isUserAuthenticated() ? getUserProfile() : null)
const currentUsernameLength = computed(() => Array.from(currentUser.value?.username || '').length)
const accountTarget = computed(() => (currentUser.value ? '/' : '/login'))
const accountEntryTarget = computed(() => (currentUser.value ? '/account' : '/login'))
const accountPreviewActive = ref(false)
const accountSigningOut = ref(false)
const accountLoginNoticeVisible = ref(false)
const accountButtonClass = computed(() => ({
  'is-authenticated': Boolean(currentUser.value),
  'is-compact-account': currentUsernameLength.value > 10,
  'is-extra-long-account': currentUsernameLength.value > 18,
  'is-account-preview': accountPreviewActive.value,
  'is-signing-out': accountSigningOut.value,
}))
const publishTrendRange = ref('quarter')
const activePublishTrendPoint = ref(null)
let revealObserver
let accountPreviewTimer
let accountLoginRedirectTimer

const publishTrendRangeOptions = [
  { value: 'quarter', label: '近一季度', months: 3 },
  { value: 'halfYear', label: '近半年', months: 6 },
  { value: 'year', label: '近一年', months: 12 },
  { value: 'all', label: '有史以来', months: null },
]

const aboutSegments = [
  { text: 'SoloFirm，', className: 'prisma-about-normal' },
  { text: '一人公司的智能创业索引。', className: 'prisma-about-serif' },
  { text: '汇聚政策、案例、来源与地区数据。', className: 'prisma-about-normal' },
]

const aboutBody =
  '围绕 AI + OPC 与一人公司创业，我们把分散的政策、案例和来源资料整理为可检索、可比较、可回到原始出处复核的公开索引。新增记录会随接口数据同步进入分析视图。'

const featureHeadingSegments = [
  { text: '面向创业者与研究者的资料工作流。', className: 'prisma-feature-heading-primary' },
  { text: '从地区机会出发，沿证据回到原始出处。', className: 'prisma-feature-heading-muted' },
]

const analysisHeadingSegments = [
  { text: '资料分析概览，随当前数据实时更新。', className: 'prisma-feature-heading-primary' },
  { text: '新增政策或案例后，统计、趋势与观察同步变化。', className: 'prisma-feature-heading-muted' },
]

async function handleAccountButtonClick(event) {
  if (!currentUser.value) {
    return
  }
  event.preventDefault()
  if (accountSigningOut.value) {
    return
  }

  clearAccountPreview()
  accountSigningOut.value = true
  try {
    await logoutUser()
  } catch {
    // logoutUser clears the local session even when the server is unavailable.
  } finally {
    currentUser.value = null
    accountSigningOut.value = false
  }
}

function handleAccountEntryClick(event) {
  if (currentUser.value) {
    return
  }

  event.preventDefault()
  if (accountLoginNoticeVisible.value) {
    return
  }

  accountLoginNoticeVisible.value = true
  window.clearTimeout(accountLoginRedirectTimer)
  accountLoginRedirectTimer = window.setTimeout(() => {
    accountLoginNoticeVisible.value = false
    router.push('/login')
  }, 3000)
}

function handleAccountPointerDown(event) {
  if (!currentUser.value || event.pointerType === 'mouse') {
    return
  }
  window.clearTimeout(accountPreviewTimer)
  accountPreviewTimer = window.setTimeout(() => {
    accountPreviewActive.value = true
  }, 280)
}

function releaseAccountPreview() {
  window.clearTimeout(accountPreviewTimer)
  if (accountPreviewActive.value) {
    accountPreviewTimer = window.setTimeout(() => {
      accountPreviewActive.value = false
    }, 220)
  }
}

function clearAccountPreview() {
  window.clearTimeout(accountPreviewTimer)
  accountPreviewActive.value = false
}

function handleAccountPointerLeave(event) {
  if (event.pointerType !== 'mouse') {
    clearAccountPreview()
  }
}

function handleAccountContextMenu(event) {
  if (currentUser.value) {
    event.preventDefault()
  }
}

const typeLabels = {
  comprehensive: '综合政策',
  computing_support: '算力支持',
  funding_subsidy: '资金补贴',
  scenario_demand: '场景需求',
  talent_service: '人才服务',
  investment: '投资融资',
  other: '其他',
}

const chartColors = ['#181A18', '#4F6F58', '#555B56', '#777D78', '#939A94', '#B0B5AF', '#D0D4CF']

const recentUpdates = computed(() => summary.value.recentUpdates || [])
const dedupedRecentUpdates = computed(() => {
  const seen = new Set()
  return recentUpdates.value.filter((item) => {
    const key = `${item.itemType || ''}::${item.title || ''}`
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
})
const compactRecentUpdates = computed(() => dedupedRecentUpdates.value.slice(0, 5))
const topPolicy = computed(() => policyVisitRankings.value[0] || null)
const topCase = computed(() => caseVisitRankings.value[0] || null)
const visitTrendBars = computed(() => {
  const max = Math.max(...visitTrend.value.map((item) => Number(item.pv || 0)), 1)
  return visitTrend.value.map((item) => ({
    ...item,
    height: Math.max(12, Math.round((Number(item.pv || 0) / max) * 100)),
  }))
})
const policyCount = computed(() => Number(summary.value.policyCount ?? policies.value.length))
const caseCount = computed(() => Number(summary.value.caseCount ?? cases.value.length))
const sourceCount = computed(() => Number(summary.value.sourceCount ?? sources.value.length))
const totalRecordCount = computed(() => policyCount.value + caseCount.value + sourceCount.value)
const todayPv = computed(() => Number(visitSummary.value.todayPv || 0))
const coveredRegionCount = computed(() => {
  const fromSummary = summary.value.coveredRegionCount
  if (fromSummary !== undefined && fromSummary !== null) {
    return Number(fromSummary)
  }
  return new Set(
    [...policies.value, ...cases.value]
      .map((item) => item.regionId || item.regionName)
    .filter(Boolean),
  ).size
})
const animatedPolicyCount = useAnimatedNumber(policyCount)
const animatedCaseCount = useAnimatedNumber(caseCount)
const animatedSourceCount = useAnimatedNumber(sourceCount)
const animatedCoveredRegionCount = useAnimatedNumber(coveredRegionCount)
const animatedTotalRecordCount = useAnimatedNumber(totalRecordCount)
const animatedTodayPv = useAnimatedNumber(todayPv)

const overviewBars = computed(() => {
  const trendRows = publishTrend.value.slice(-6).map((item) => ({
    name: item.name,
    label: formatTrendLabel(item.name),
    count: item.count,
  }))
  const rows = trendRows.length
    ? trendRows
    : [
        { name: 'policy', label: '政策', count: policyCount.value },
        { name: 'case', label: '案例', count: caseCount.value },
        { name: 'source', label: '来源', count: sourceCount.value },
        { name: 'region', label: '地区', count: coveredRegionCount.value },
      ]
  const max = Math.max(...rows.map((item) => item.count), 1)
  return rows.map((item) => ({
    ...item,
    height: Math.max(16, Math.round((item.count / max) * 100)),
  }))
})
const overviewChartTitle = computed(() => {
  if (!publishTrend.value.length) {
    return '资料结构速览'
  }
  const year = getTrendYear(publishTrend.value)
  return year ? `近月政策发布（${year}）` : '近月政策发布'
})
const overviewChartCaption = computed(() => (publishTrend.value.length ? '按发布日期统计' : '按记录类型统计'))

function formatTrendLabel(value) {
  const text = String(value || '')
  const match = text.match(/^(\d{4})[-/.年](\d{1,2})/)
  if (!match) {
    return text
  }
  return `${Number(match[2])}月`
}

function getTrendYear(rows) {
  const years = rows
    .map((item) => String(item.name || '').match(/^(\d{4})/)?.[1])
    .filter(Boolean)
  return [...new Set(years)].length === 1 ? years[0] : ''
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function useAnimatedNumber(target) {
  const value = ref(0)
  let frameId = 0

  watch(
    target,
    (next, previous = 0) => {
      window.cancelAnimationFrame(frameId)
      const to = Number(next) || 0
      const from = Number(value.value || previous) || 0

      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || from === to) {
        value.value = to
        return
      }

      const start = window.performance.now()
      const duration = 900
      const tick = (now) => {
        const progress = Math.min((now - start) / duration, 1)
        const eased = 1 - Math.pow(1 - progress, 4)
        value.value = Math.round(from + (to - from) * eased)
        if (progress < 1) {
          frameId = window.requestAnimationFrame(tick)
        }
      }
      frameId = window.requestAnimationFrame(tick)
    },
    { immediate: true },
  )

  return value
}

function handlePanelSpotlight(event) {
  const panel = event.target.closest('.analysis-panel')
  if (!panel) {
    return
  }
  const rect = panel.getBoundingClientRect()
  panel.style.setProperty('--spotlight-x', `${event.clientX - rect.left}px`)
  panel.style.setProperty('--spotlight-y', `${event.clientY - rect.top}px`)
}

const topRegions = computed(() => {
  const rows = countBy([...policies.value, ...cases.value], (item) => item.regionName || '未标注地区').slice(0, 8)
  const max = rows[0]?.count || 1
  return rows.map((item) => ({
    ...item,
    percent: Math.max(8, Math.round((item.count / max) * 100)),
  }))
})

const policyTypeStats = computed(() => {
  const rows = countBy(policies.value, (item) => item.policyType || 'other')
  const total = rows.reduce((sum, item) => sum + item.count, 0) || 1
  return rows.map((item, index) => ({
    ...item,
    label: typeLabels[item.name] || item.name,
    color: chartColors[index % chartColors.length],
    percent: Math.round((item.count / total) * 100),
  }))
})

const tagStats = computed(() => {
  const tags = [...policies.value, ...cases.value].flatMap((item) => splitTags(item.tags))
  const rows = countValues(tags).slice(0, 12)
  const max = rows[0]?.count || 1
  return rows.map((item) => ({
    ...item,
    weight: (0.72 + item.count / max).toFixed(2),
  }))
})

const allPublishTrend = computed(() => {
  const counts = new Map()
  policies.value
    .map((item) => monthKey(item.publishDate))
    .filter(Boolean)
    .forEach((month) => counts.set(month, (counts.get(month) || 0) + 1))

  const months = [...counts.keys()].sort((a, b) => a.localeCompare(b))
  if (!months.length) {
    return []
  }

  return enumerateMonths(months[0], months[months.length - 1]).map((name) => ({
    name,
    count: counts.get(name) || 0,
  }))
})

const publishTrend = computed(() => {
  const option = publishTrendRangeOptions.find((item) => item.value === publishTrendRange.value)
  if (!option?.months) {
    return allPublishTrend.value
  }
  return allPublishTrend.value.slice(-option.months)
})

const publishTrendTotal = computed(() => publishTrend.value.reduce((total, item) => total + item.count, 0))

const trendDots = computed(() => {
  if (!publishTrend.value.length) {
    return []
  }

  const width = 640
  const height = 170
  const top = 20
  const left = 28
  const right = 28
  const max = Math.max(...publishTrend.value.map((item) => item.count), 1)
  const step = publishTrend.value.length > 1 ? (width - left - right) / (publishTrend.value.length - 1) : 0

  return publishTrend.value.map((item, index) => ({
    label: item.name,
    count: item.count,
    x: left + index * step,
    y: top + height - (item.count / max) * height,
  }))
})

const publishTrendLabels = computed(() => {
  const points = trendDots.value
  if (!points.length) {
    return []
  }
  const labelCount = Math.min(points.length, 8)
  const indexes = new Set()
  for (let index = 0; index < labelCount; index += 1) {
    indexes.add(labelCount === 1 ? 0 : Math.round((index * (points.length - 1)) / (labelCount - 1)))
  }
  return [...indexes].map((pointIndex) => ({
    ...points[pointIndex],
    label: points[pointIndex].label,
  }))
})

const publishTrendTooltipStyle = computed(() => {
  if (!activePublishTrendPoint.value) {
    return {}
  }
  return {
    '--policy-tooltip-x': `${(activePublishTrendPoint.value.x / 640) * 100}%`,
    '--policy-tooltip-y': `${(activePublishTrendPoint.value.y / 210) * 100}%`,
  }
})

const trendLinePoints = computed(() => trendDots.value.map((point) => `${point.x},${point.y}`).join(' '))

const trendAreaPoints = computed(() => {
  if (!trendDots.value.length) {
    return ''
  }
  const baseline = 195
  const first = trendDots.value[0]
  const last = trendDots.value[trendDots.value.length - 1]
  return `${first.x},${baseline} ${trendLinePoints.value} ${last.x},${baseline}`
})

const sourceTraceStats = computed(() => {
  const totalSources = sources.value.length
  const totalPolicies = policies.value.length
  const totalItems = policies.value.length + cases.value.length
  return [
    makeTraceItem('来源关联', [...policies.value, ...cases.value].filter((item) => item.sourceId).length, totalItems, '#181A18'),
    makeTraceItem('来源链接', sources.value.filter((item) => item.url).length, totalSources, '#555B56'),
    makeTraceItem('访问日期', sources.value.filter((item) => item.accessedAt).length, totalSources, '#4F6F58'),
  ]
})

const caseCategoryStats = computed(() => {
  const rows = countBy(cases.value, (item) => item.category || '未标注领域').slice(0, 6)
  const total = rows.reduce((sum, item) => sum + item.count, 0) || 1
  const max = rows[0]?.count || 1
  return rows.map((item) => ({
    ...item,
    percent: Math.round((item.count / total) * 100),
    rate: Math.max(8, Math.round((item.count / max) * 100)),
  }))
})

const insights = computed(() => {
  const region = topRegions.value[0]
  const type = policyTypeStats.value[0]
  const tag = tagStats.value[0]
  const trendLast = publishTrend.value[publishTrend.value.length - 1]

  return [
    region ? `当前资料收录最多的地区是「${region.name}」，政策与案例合计 ${region.count} 条。` : '当前还缺少可用于地区分析的资料数据。',
    type ? `政策类型中「${type.label}」占比最高，可作为汇报中的重点方向。` : '当前还缺少可用于类型分析的政策数据。',
    tag ? `高频标签是「${tag.name}」，说明该方向在资料库中出现较多。` : '当前标签还不够完整，建议录入时补齐 3-8 个标签。',
    trendLast ? `最近一期有 ${trendLast.count} 条政策记录，可结合发布时间判断政策热度。` : '建议补齐政策发布日期，用于形成时间趋势分析。',
  ]
})

function countBy(list, picker) {
  return countValues(list.map(picker).filter(Boolean))
}

function countValues(values) {
  const map = new Map()
  values.forEach((value) => {
    map.set(value, (map.get(value) || 0) + 1)
  })
  return Array.from(map.entries())
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
}

function splitTags(tags) {
  if (!tags) {
    return []
  }
  return String(tags)
    .split(/[,，、;；]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

function monthKey(date) {
  if (!date || String(date).length < 7) {
    return ''
  }
  return String(date).slice(0, 7)
}

function enumerateMonths(start, end) {
  const [startYear, startMonth] = start.split('-').map(Number)
  const [endYear, endMonth] = end.split('-').map(Number)
  const startIndex = startYear * 12 + startMonth - 1
  const endIndex = endYear * 12 + endMonth - 1
  const months = []
  for (let index = startIndex; index <= endIndex; index += 1) {
    const year = Math.floor(index / 12)
    const month = (index % 12) + 1
    months.push(`${year}-${String(month).padStart(2, '0')}`)
  }
  return months
}

function formatMonthLabel(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})$/)
  return match ? `${match[1]}年${Number(match[2])}月` : value || '-'
}

function setActivePublishTrendPoint(point) {
  activePublishTrendPoint.value = point
}

function clearActivePublishTrendPoint(point) {
  if (!point || activePublishTrendPoint.value?.label === point.label) {
    activePublishTrendPoint.value = null
  }
}

function makeTraceItem(name, count, total, color) {
  return {
    name,
    count,
    total,
    color,
    rate: total > 0 ? Math.round((count / total) * 100) : 0,
  }
}

function setupScrollReveal() {
  const items = document.querySelectorAll('.scroll-reveal')
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    items.forEach((item) => item.classList.add('is-visible'))
    return
  }

  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          revealObserver.unobserve(entry.target)
        }
      })
    },
    {
      rootMargin: '0px 0px -12% 0px',
      threshold: 0.16,
    },
  )

  items.forEach((item) => revealObserver.observe(item))
}

onMounted(async () => {
  setupScrollReveal()
  loading.value = true
  error.value = ''
  try {
    const [summaryData, policyData, caseData, sourceData] = await Promise.all([
      getDashboardSummary().catch(() => ({})),
      getPolicies(),
      getCases(),
      getSources().catch(() => []),
    ])
    const [visitSummaryData, policyRankData, caseRankData, visitTrendData] = await Promise.all([
      getVisitSummary().catch(() => ({})),
      getVisitRankings({ targetType: 'policy', limit: 5 }).catch(() => []),
      getVisitRankings({ targetType: 'case', limit: 5 }).catch(() => []),
      getVisitTrend({ days: 7 }).catch(() => []),
    ])
    summary.value = summaryData
    policies.value = policyData
    cases.value = caseData
    sources.value = sourceData
    visitSummary.value = visitSummaryData || {}
    policyVisitRankings.value = policyRankData || []
    caseVisitRankings.value = caseRankData || []
    visitTrend.value = visitTrendData || []
  } catch (err) {
    error.value = '资料分析数据暂时无法读取'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  revealObserver?.disconnect()
  window.clearTimeout(accountPreviewTimer)
  window.clearTimeout(accountLoginRedirectTimer)
})
</script>
