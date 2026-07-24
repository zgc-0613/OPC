<template>
  <div class="page-stack home-archive-page">
    <section class="archive-hero prisma-hero">
      <div class="prisma-hero-backdrop" aria-hidden="true"></div>
      <div class="prisma-hero-frame">
        <video
          class="prisma-hero-media"
          src="/media/solofirm-hero-loop.mp4"
          autoplay
          loop
          muted
          playsinline
        ></video>
        <div class="noise-overlay" aria-hidden="true"></div>
        <div class="prisma-hero-gradient" aria-hidden="true"></div>
        <div class="prisma-hero-film-index" aria-hidden="true">
          <span></span>
          <span></span>
          <span></span>
          <span></span>
          <span></span>
          <i></i>
        </div>

        <header class="home-landing-nav prisma-hero-nav">
          <nav class="home-landing-links prisma-hanging-nav" aria-label="首页导航">
            <RouterLink to="/">首页</RouterLink>
            <RouterLink to="/analysis">资料分析</RouterLink>
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
              汇聚 AI + OPC 相关政策、案例与可信资料。<br />
              帮助创业者快速发现地区机遇、政策支持与可参考的案例。
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

      <a class="home-scroll-cue" href="#home-data-view" aria-label="跳转到资料工作台"></a>
    </section>

    <section class="prisma-about" aria-labelledby="prisma-about-title">
      <div class="prisma-about-card">
        <h2 id="prisma-about-title" class="prisma-about-title">
          <WordsPullUpMultiStyle :segments="aboutSegments" />
        </h2>
        <AnimatedLetters :text="aboutBody" />
      </div>
    </section>

    <section id="home-data-view" class="prisma-features">
      <div class="bg-noise" aria-hidden="true"></div>
      <header class="prisma-features-heading">
        <WordsPullUpMultiStyle :segments="featureHeadingSegments" tag="h2" />
      </header>

      <div class="prisma-feature-grid">
        <article class="prisma-feature-card prisma-feature-card--video scroll-reveal" style="--feature-index: 0">
          <div class="prisma-feature-video-loop" aria-hidden="true">
            <video
              ref="featureVideoPrimary"
              class="prisma-feature-video-layer is-active"
              src="/media/solofirm-feature-canvas.mp4"
              autoplay
              muted
              playsinline
              preload="auto"
              @ended="handleFeatureVideoEnded(0)"
            ></video>
            <video
              ref="featureVideoSecondary"
              class="prisma-feature-video-layer"
              src="/media/solofirm-feature-canvas.mp4"
              muted
              playsinline
              preload="auto"
              @ended="handleFeatureVideoEnded(1)"
            ></video>
          </div>
          <div class="prisma-feature-video-gradient" aria-hidden="true"></div>
          <strong>您的智能创业研究画布。</strong>
        </article>

        <RouterLink class="prisma-feature-card prisma-feature-card--link scroll-reveal" style="--feature-index: 1" to="/policies">
          <img
            class="prisma-feature-icon"
            src="/media/solofirm-policy-index-icon.webp"
            alt=""
          />
          <div class="prisma-feature-title">
            <h3>政策索引。</h3>
            <span>01</span>
          </div>
          <ul>
            <li><Check :size="16" />按地区与政策类型筛选</li>
            <li><Check :size="16" />标题、摘要及标签检索</li>
            <li><Check :size="16" />政策原文与佐证链接</li>
            <li><Check :size="16" />数据导出与热度排行</li>
          </ul>
          <span class="prisma-learn-link" aria-hidden="true">
            <span>进入政策库</span><ArrowRight :size="17" />
          </span>
        </RouterLink>

        <RouterLink class="prisma-feature-card prisma-feature-card--link scroll-reveal" style="--feature-index: 2" to="/cases">
          <img
            class="prisma-feature-icon"
            src="/media/solofirm-case-insight-icon.webp"
            alt=""
          />
          <div class="prisma-feature-title">
            <h3>案例洞察。</h3>
            <span>02</span>
          </div>
          <ul>
            <li><Check :size="16" />按地区、类型与关键词定位</li>
            <li><Check :size="16" />商业模式与 AI 智能体解析</li>
            <li><Check :size="16" />成果归属与来源追溯</li>
          </ul>
          <span class="prisma-learn-link" aria-hidden="true">
            <span>查看案例库</span><ArrowRight :size="17" />
          </span>
        </RouterLink>

        <RouterLink class="prisma-feature-card prisma-feature-card--link scroll-reveal" style="--feature-index: 3" to="/sources">
          <img
            class="prisma-feature-icon"
            src="/media/solofirm-evidence-ledger-icon.webp"
            alt=""
          />
          <div class="prisma-feature-title">
            <h3>证据台账。</h3>
            <span>03</span>
          </div>
          <ul>
            <li><Check :size="16" />来源链接与访问日期记录</li>
            <li><Check :size="16" />地区资料覆盖实时汇总</li>
            <li><Check :size="16" />状态、文件与发布单位一览</li>
          </ul>
          <span class="prisma-learn-link" aria-hidden="true">
            <span>查看来源台账</span><ArrowRight :size="17" />
          </span>
        </RouterLink>
      </div>

    </section>

    <footer class="home-contact-footer scroll-reveal">
      <section class="home-contact-card" aria-labelledby="home-contact-title">
        <div class="home-contact-folio" aria-hidden="true">
          <span><i></i></span>
          <span><i></i></span>
          <span><i></i></span>
        </div>
        <div>
          <span class="home-contact-kicker">CONTACT</span>
          <h2 id="home-contact-title">联系我们，<span>共建 AI + OPC 创业索引</span></h2>
          <p>
            如您关注一人公司、AI 创业、政策信息索引或案例共建，欢迎通过以下方式与我们联系。
          </p>

          <div class="home-contact-methods" aria-label="联系方式">
            <div class="home-contact-method">
              <span
                class="home-contact-icon"
                aria-hidden="true"
                @pointerenter="handleContactIconPointerMove"
                @pointermove="handleContactIconPointerMove"
                @pointerleave="handleContactIconPointerLeave"
                @pointercancel="handleContactIconPointerLeave"
              ><MapPin :size="18" /></span>
              <div>
                <strong>所属单位</strong>
                <p>西北工业大学软件学院</p>
              </div>
            </div>

            <div class="home-contact-method">
              <span
                class="home-contact-icon"
                aria-hidden="true"
                @pointerenter="handleContactIconPointerMove"
                @pointermove="handleContactIconPointerMove"
                @pointerleave="handleContactIconPointerLeave"
                @pointercancel="handleContactIconPointerLeave"
              ><Mail :size="18" /></span>
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
          <p class="home-footer-summary">汇聚 AI + OPC 相关政策、案例与来源资料，助力创业者发现区域机遇与可溯源的创业方向。</p>
          <p class="home-footer-legal">
            Copyright © 2026
            <a href="https://findopc.online/">SoloFirm®</a>
            - All rights reserved
          </p>
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
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Check, LogIn, LogOut, Mail, MapPin, UserRound } from 'lucide-vue-next'
import AnimatedLetters from '@/components/AnimatedLetters.vue'
import BrandMark from '@/components/BrandMark.vue'
import WordsPullUp from '@/components/WordsPullUp.vue'
import WordsPullUpMultiStyle from '@/components/WordsPullUpMultiStyle.vue'
import { getUserProfile, isUserAuthenticated, logoutUser } from '@/api/auth'

const router = useRouter()
const currentUser = ref(isUserAuthenticated() ? getUserProfile() : null)
const currentUsernameLength = computed(() => Array.from(currentUser.value?.username || '').length)
const accountTarget = computed(() => (currentUser.value ? '/' : '/login'))
const accountEntryTarget = computed(() => (currentUser.value ? '/account' : '/login'))
const accountPreviewActive = ref(false)
const accountSigningOut = ref(false)
const accountLoginNoticeVisible = ref(false)
const featureVideoPrimary = ref(null)
const featureVideoSecondary = ref(null)
const accountButtonClass = computed(() => ({
  'is-authenticated': Boolean(currentUser.value),
  'is-compact-account': currentUsernameLength.value > 10,
  'is-extra-long-account': currentUsernameLength.value > 18,
  'is-account-preview': accountPreviewActive.value,
  'is-signing-out': accountSigningOut.value,
}))
let revealObserver
let accountPreviewTimer
let accountLoginRedirectTimer
let featureVideoFrame
let featureVideoTransitionTimer
let activeFeatureVideoIndex = 0
let featureVideoTransitioning = false

const featureVideoFadeDuration = 1400
const featureVideoFadeLead = 1.7

const aboutSegments = [
  { text: 'SoloFirm，', className: 'prisma-about-normal' },
  { text: '一人公司的智能创业索引。', className: 'prisma-about-serif' },
  { text: '汇聚政策、案例、数据来源与区域信息。', className: 'prisma-about-normal prisma-about-subtitle' },
]

const aboutBody =
  '围绕 AI + OPC 及一人公司创业场景，我们构建了全国政策、案例与来源资料的公开索引。支持快速检索、横向比较，并可一键回查原始文件。'

const featureHeadingSegments = [
  { text: '面向创业者与研究者的资料工作台。', className: 'prisma-feature-heading-primary' },
  { text: '从地区机遇出发，结合案例与政策，发现可溯源的创业方向。', className: 'prisma-feature-heading-muted' },
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

function handleContactIconPointerMove(event) {
  if (event.pointerType !== 'mouse') {
    return
  }

  const icon = event.currentTarget
  icon.classList.add('is-glint-active')
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return
  }

  const rect = icon.getBoundingClientRect()
  const offsetX = event.clientX - rect.left - rect.width / 2
  const offsetY = event.clientY - rect.top - rect.height / 2
  icon.style.setProperty('--contact-glint-x', `${offsetX}px`)
  icon.style.setProperty('--contact-glint-y', `${offsetY}px`)
}

function handleContactIconPointerLeave(event) {
  const icon = event.currentTarget
  icon.classList.remove('is-glint-active')
  icon.style.removeProperty('--contact-glint-x')
  icon.style.removeProperty('--contact-glint-y')
}

function setupScrollReveal() {
  const items = document.querySelectorAll('.route-home .scroll-reveal')
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

function getFeatureVideoPlayers() {
  return [featureVideoPrimary.value, featureVideoSecondary.value]
}

function resetFeatureVideoLayer(video, index) {
  video.pause()
  video.loop = false
  video.classList.remove('is-active', 'is-incoming', 'is-outgoing')
  if (index === 0) {
    video.classList.add('is-active')
  }
  try {
    video.currentTime = 0
  } catch {
    // Metadata can still be loading on the first mount.
  }
}

function finishFeatureVideoCrossfade(outgoing, incoming, incomingIndex) {
  outgoing.classList.remove('is-active', 'is-outgoing')
  outgoing.pause()
  try {
    outgoing.currentTime = 0
  } catch {
    // Keep the next transition available even if the browser is still seeking.
  }
  incoming.classList.remove('is-incoming')
  activeFeatureVideoIndex = incomingIndex
  featureVideoTransitioning = false
}

async function beginFeatureVideoCrossfade() {
  if (featureVideoTransitioning) {
    return
  }

  const players = getFeatureVideoPlayers()
  const outgoing = players[activeFeatureVideoIndex]
  const incomingIndex = activeFeatureVideoIndex === 0 ? 1 : 0
  const incoming = players[incomingIndex]
  if (!outgoing || !incoming) {
    return
  }

  featureVideoTransitioning = true
  window.clearTimeout(featureVideoTransitionTimer)
  incoming.classList.remove('is-active', 'is-outgoing')
  incoming.classList.add('is-incoming')

  try {
    incoming.currentTime = 0
    await incoming.play()
  } catch {
    incoming.classList.remove('is-incoming')
    outgoing.currentTime = 0
    outgoing.play().catch(() => {})
    featureVideoTransitioning = false
    return
  }

  outgoing.classList.add('is-outgoing')
  window.requestAnimationFrame(() => {
    window.requestAnimationFrame(() => incoming.classList.add('is-active'))
  })

  featureVideoTransitionTimer = window.setTimeout(() => {
    finishFeatureVideoCrossfade(outgoing, incoming, incomingIndex)
  }, featureVideoFadeDuration + 80)
}

function monitorFeatureVideoLoop() {
  const activeVideo = getFeatureVideoPlayers()[activeFeatureVideoIndex]
  if (
    activeVideo &&
    Number.isFinite(activeVideo.duration) &&
    activeVideo.duration > featureVideoFadeLead * 2 &&
    activeVideo.duration - activeVideo.currentTime <= featureVideoFadeLead
  ) {
    beginFeatureVideoCrossfade()
  }
  featureVideoFrame = window.requestAnimationFrame(monitorFeatureVideoLoop)
}

function handleFeatureVideoEnded(videoIndex) {
  if (videoIndex === activeFeatureVideoIndex && !featureVideoTransitioning) {
    beginFeatureVideoCrossfade()
  }
}

function setupFeatureVideoLoop() {
  const players = getFeatureVideoPlayers().filter(Boolean)
  if (players.length !== 2) {
    return
  }

  players.forEach(resetFeatureVideoLayer)
  activeFeatureVideoIndex = 0
  featureVideoTransitioning = false
  players[0].play().catch(() => {})
  featureVideoFrame = window.requestAnimationFrame(monitorFeatureVideoLoop)
}

onMounted(() => {
  setupScrollReveal()
  setupFeatureVideoLoop()
})

onUnmounted(() => {
  revealObserver?.disconnect()
  window.clearTimeout(accountPreviewTimer)
  window.clearTimeout(accountLoginRedirectTimer)
  window.clearTimeout(featureVideoTransitionTimer)
  window.cancelAnimationFrame(featureVideoFrame)
  getFeatureVideoPlayers().forEach((video) => video?.pause())
})
</script>
