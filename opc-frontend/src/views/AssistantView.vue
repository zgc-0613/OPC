<template>
  <div class="assistant-page">
    <section v-if="pageLoading" class="assistant-page-state" role="status">
      <span class="assistant-spinner" aria-hidden="true"></span>
      <div>
        <strong>正在准备研究工作台</strong>
        <p>读取地区目录与智能体可用状态。</p>
      </div>
    </section>

    <section v-else-if="pageError" class="assistant-page-state is-error" role="alert">
      <AlertTriangle :size="22" aria-hidden="true" />
      <div>
        <strong>研究助手暂时无法读取</strong>
        <p>{{ pageError }}</p>
      </div>
      <button class="button button-ghost" type="button" @click="loadPage">
        <RefreshCw :size="15" aria-hidden="true" />重新读取
      </button>
    </section>

    <div v-else class="assistant-workspace">
      <aside class="assistant-profile" aria-labelledby="assistant-profile-title">
        <div class="assistant-profile-head">
          <div>
            <span class="caption">PROFILE / 01</span>
            <h2 id="assistant-profile-title">创业画像</h2>
          </div>
          <SlidersHorizontal :size="20" aria-hidden="true" />
        </div>

        <p class="assistant-profile-intro">
          先确定业务边界，再从 SoloFirm 已核验资料中寻找可参考的路径。
        </p>

        <form class="assistant-profile-form" @submit.prevent="requestAdvice('initial')">
          <label>
            <span>创业类型</span>
            <select v-model="profile.ventureType" required :disabled="submitting">
              <option value="solo_company">一人公司</option>
              <option value="individual_business">个体经营</option>
              <option value="small_team">小型创业团队</option>
              <option value="exploring">尚在探索</option>
            </select>
          </label>

          <label>
            <span>所在地区</span>
            <select v-model="profile.regionId" required :disabled="submitting">
              <option value="" disabled>选择地区</option>
              <option v-for="region in regions" :key="region.id" :value="String(region.id)">
                {{ region.name }}
              </option>
            </select>
          </label>

          <label class="assistant-industry-field">
            <span>目标行业</span>
            <div class="assistant-industry-combobox">
              <input
                id="assistant-industry-input"
                v-model="industryQuery"
                type="text"
                maxlength="80"
                role="combobox"
                aria-autocomplete="list"
                aria-controls="assistant-industry-listbox"
                :aria-expanded="industryOpen"
                :aria-activedescendant="activeIndustryId"
                autocomplete="off"
                placeholder="搜索并选择行业"
                required
                :disabled="submitting"
                @focus="openIndustryOptions"
                @input="handleIndustryInput"
                @keydown="handleIndustryKeydown"
                @blur="closeIndustryOptions"
              />
              <Search :size="16" aria-hidden="true" />
              <ul
                v-if="industryOpen"
                id="assistant-industry-listbox"
                class="assistant-industry-listbox"
                role="listbox"
                aria-label="可选行业"
              >
                <li
                  v-for="(industry, index) in filteredIndustries"
                  :id="`assistant-industry-option-${industry.tagId}`"
                  :key="industry.tagId"
                  role="option"
                  :aria-selected="String(profile.industryTagId) === String(industry.tagId)"
                  :class="{ 'is-active': activeIndustryIndex === index }"
                  @mousedown.prevent="selectIndustry(industry)"
                >
                  <span>{{ industry.name }}</span>
                  <small>{{ industry.caseUsageCount + industry.policyUsageCount }} 条资料</small>
                </li>
                <li v-if="!filteredIndustries.length" class="is-empty" aria-disabled="true">
                  暂无匹配行业，可继续输入原始行业文本
                </li>
              </ul>
            </div>
            <small v-if="profile.industryTagId" class="assistant-industry-selected">
              已选择规范行业：{{ profile.industry }}
            </small>
            <div v-if="industrySuggestion" class="assistant-industry-suggestion" role="status">
              <div>
                <span>{{ industrySuggestionLabel }}</span>
                <strong>{{ industrySuggestion.name }}</strong>
                <small>
                  原始输入“{{ industrySuggestion.originalText }}” · {{ formatConfidence(industrySuggestion.confidence) }}
                </small>
              </div>
              <div class="assistant-suggestion-actions">
                <button class="button" type="button" @click="confirmSuggestedIndustry">确认使用</button>
                <button class="button button-ghost" type="button" @click="rejectSuggestedIndustry">重新选择</button>
              </div>
            </div>
            <div v-if="!profile.industryTagId && profile.industry.trim()" class="assistant-industry-resolution-actions">
              <button
                class="button button-ghost"
                type="button"
                :disabled="industryResolutionLoading || submitting"
                @click="requestIndustryRecommendation"
              >
                <Sparkles :size="15" aria-hidden="true" />
                {{ industryResolutionLoading ? '正在识别行业' : 'AI 推荐规范行业' }}
              </button>
              <small v-if="industryResolutionError" role="alert">{{ industryResolutionError }}</small>
            </div>
          </label>

          <div class="assistant-form-pair">
            <label>
              <span>当前阶段</span>
              <select v-model="profile.stage" required :disabled="submitting">
                <option value="idea">想法形成</option>
                <option value="validation">需求验证</option>
                <option value="early_operation">早期运营</option>
                <option value="growth">增长阶段</option>
              </select>
            </label>
            <label>
              <span>可投入预算</span>
              <select v-model="profile.budgetRange" required :disabled="submitting">
                <option value="under_100k">10 万元以内</option>
                <option value="100k_500k">10-50 万元</option>
                <option value="500k_1m">50-100 万元</option>
                <option value="over_1m">100 万元以上</option>
                <option value="undecided">尚未确定</option>
              </select>
            </label>
          </div>

          <label>
            <span>当前目标</span>
            <textarea
              v-model.trim="profile.goal"
              rows="3"
              maxlength="200"
              placeholder="例如：在三个月内验证首批付费客户"
              required
              :disabled="submitting"
            ></textarea>
          </label>

          <label>
            <span>已有资源 <small>可选</small></span>
            <textarea
              v-model.trim="profile.existingResources"
              rows="3"
              maxlength="300"
              placeholder="产品原型、客户线索、行业经验或合作伙伴"
              :disabled="submitting"
            ></textarea>
          </label>

          <label>
            <span>最想解决的问题 <small>可选</small></span>
            <textarea
              v-model.trim="profile.userQuestion"
              rows="3"
              maxlength="500"
              placeholder="例如：应当优先验证哪类客户？"
              :disabled="submitting"
            ></textarea>
          </label>

          <div class="assistant-readiness" aria-live="polite">
            <div class="assistant-provider-note" :class="{ 'is-ready': providerReady }">
              <span></span>
              <div>
                <strong>{{ providerReady ? '模型已配置' : '模型尚未启用' }}</strong>
                <small>{{ providerLabel }}</small>
              </div>
            </div>
            <div class="assistant-provider-note" :class="readinessStatusClass">
              <span></span>
              <div>
                <strong>{{ readinessTitle }}</strong>
                <small v-if="readiness">
                  已选 {{ readiness.selectedEvidenceCount || 0 }} · 案例 {{ readiness.verifiedCaseCount || 0 }} · 选入政策 {{ readiness.selectedPolicyCount ?? readiness.verifiedPolicyCount ?? 0 }} · 来源 {{ readiness.verifiedSourceCount || 0 }}
                </small>
                <small v-if="readiness" class="assistant-policy-composition">
                  可用：直接行业政策 {{ readiness.directIndustryPolicyCount || 0 }} · 通用政策 {{ readiness.generalPolicyCount || 0 }}
                </small>
                <small v-if="readiness?.unclassifiedPolicyCount" class="assistant-policy-composition is-pending">
                  地区参考政策 {{ readiness.unclassifiedPolicyCount }} 条，尚未标注适用行业
                </small>
                <small v-else-if="readinessError" class="is-error">{{ readinessError }}</small>
                <small v-else>选择地区和行业后自动检查</small>
              </div>
            </div>
            <p v-if="readinessUi.warning" class="assistant-readiness-warning">
              当前只有部分有效证据，可以继续生成；结论会限制在已有来源范围内。
            </p>
            <ul v-if="readinessReasons.length" class="assistant-readiness-reasons">
              <li v-for="reason in readinessReasons" :key="reason">{{ reason }}</li>
            </ul>
          </div>

          <button class="button assistant-submit" type="submit" :disabled="submitting || !providerReady || !readinessUi.canSubmit || Boolean(industrySuggestion)">
            <FileSearch :size="17" aria-hidden="true" />
            {{ submitting ? '正在研究...' : turns.length ? '生成新的研究建议' : '开始创业研究' }}
            <ArrowRight :size="16" aria-hidden="true" />
          </button>
        </form>
      </aside>

      <main class="assistant-thread" aria-labelledby="assistant-thread-title">
        <header class="assistant-thread-head">
          <div>
            <span class="caption">SOLOFIRM RESEARCH DESK</span>
            <h2 id="assistant-thread-title">研究对话</h2>
          </div>
          <div class="assistant-thread-status">
            <BrainCircuit :size="18" aria-hidden="true" />
            <span>{{ providerReady ? 'Evidence-aware' : 'Unavailable' }}</span>
          </div>
        </header>

        <section v-if="!providerReady" class="assistant-empty-state is-disabled" role="status">
          <BrainCircuit :size="30" aria-hidden="true" />
          <div>
            <strong>智能体模型尚未配置或未启用</strong>
            <p>管理员完成 API Base URL、Model ID、API Key 配置并通过连接测试后，本页即可开始研究。</p>
          </div>
        </section>

        <section v-else-if="submitting" class="assistant-thinking" role="status">
          <span class="assistant-spinner" aria-hidden="true"></span>
          <div><strong>正在核对本地资料</strong><p>检索案例与政策，并验证模型引用。</p></div>
        </section>

        <section
          v-else-if="requestError"
          class="assistant-request-error"
          role="alert"
          :data-diagnostic="requestErrorDiagnostic || undefined"
        >
          <AlertTriangle :size="20" aria-hidden="true" />
          <div><strong>{{ requestErrorTitle }}</strong><p>{{ requestError }}</p></div>
          <button class="button button-ghost" type="button" @click="retryLastRequest">
            <RefreshCw :size="15" />重试
          </button>
        </section>

        <section v-else-if="!turns.length" class="assistant-empty-state">
          <Sparkles :size="30" aria-hidden="true" />
          <div>
            <span class="caption">READY WHEN YOU ARE</span>
            <h3>从一份清晰的创业画像开始。</h3>
            <p>助手会检索本地已发布、已核验的案例和政策，再生成可追溯的判断与行动顺序。</p>
          </div>
          <ol>
            <li><span>01</span>梳理创业条件</li>
            <li><span>02</span>匹配本地资料</li>
            <li><span>03</span>形成下一步行动</li>
          </ol>
        </section>

        <div v-else class="assistant-transcript" aria-live="polite">
          <article v-for="(turn, index) in turns" :key="turn.id" class="assistant-turn">
            <section class="assistant-user-entry">
              <div class="assistant-entry-index">{{ String(index + 1).padStart(2, '0') }}</div>
              <div>
                <span class="caption">YOUR BRIEF</span>
                <h3>{{ turn.profile.industry }} · {{ labelFor('stage', turn.profile.stage) }}</h3>
                <div class="assistant-brief-meta">
                  <span><MapPin :size="14" />{{ turn.profile.regionName }}</span>
                  <span><Building2 :size="14" />{{ labelFor('ventureType', turn.profile.ventureType) }}</span>
                  <span>{{ labelFor('budgetRange', turn.profile.budgetRange) }}</span>
                </div>
                <p>{{ turn.question || turn.profile.goal }}</p>
              </div>
            </section>

            <section class="assistant-response">
              <div class="assistant-response-head">
                <div>
                  <span class="caption">RESEARCH RESPONSE</span>
                  <h3>{{ turn.result.summary }}</h3>
                </div>
                <span class="assistant-evidence-pill" :class="`is-${turn.result.evidenceStatus}`">
                  <ShieldCheck :size="15" />{{ evidenceLabel(turn.result.evidenceStatus) }}
                </span>
              </div>

              <div v-if="turn.result.evidenceStatus === 'insufficient'" class="assistant-evidence-warning">
                <AlertTriangle :size="20" aria-hidden="true" />
                      <div>
                        <p>{{ turn.result.recommendedDirection }}</p>
                        <ul v-if="turn.result.evidenceReasons?.length">
                          <li v-for="reason in turn.result.evidenceReasons" :key="reason">{{ reason }}</li>
                        </ul>
                      </div>
              </div>

              <template v-else>
                <section class="assistant-direction">
                  <span>建议方向</span>
                  <p>{{ turn.result.recommendedDirection }}</p>
                </section>

                <div class="assistant-advice-grid">
                  <section>
                    <span>01</span>
                    <h4>可把握的机会</h4>
                    <ul><li v-for="item in turn.result.opportunities" :key="item">{{ item }}</li></ul>
                  </section>
                  <section>
                    <span>02</span>
                    <h4>需要验证的风险</h4>
                    <ul><li v-for="item in turn.result.risks" :key="item">{{ item }}</li></ul>
                  </section>
                  <section>
                    <span>03</span>
                    <h4>行动顺序</h4>
                    <ol><li v-for="item in turn.result.actionPlan" :key="item">{{ item }}</li></ol>
                  </section>
                </div>

                <section class="assistant-library">
                  <div class="assistant-section-title">
                    <div><span class="caption">LOCAL EVIDENCE</span><h4>匹配到的本地资料</h4></div>
                    <strong>{{ evidenceCount(turn.result) }}</strong>
                  </div>

                  <div class="assistant-library-columns">
                    <div>
                      <h5>案例</h5>
                      <RouterLink
                        v-for="item in turn.result.matchedCases"
                        :key="`case-${item.id}`"
                        class="assistant-library-row"
                        :to="item.detailUrl"
                      >
                        <span><strong>{{ item.title }}</strong><small>{{ item.matchReason || item.category || item.regionName }}</small></span>
                        <ArrowRight :size="15" aria-hidden="true" />
                      </RouterLink>
                      <p v-if="!turn.result.matchedCases?.length" class="assistant-library-empty">暂无已核验案例</p>
                    </div>
                    <div>
                      <h5>政策</h5>
                      <RouterLink
                        v-for="item in turn.result.matchedPolicies"
                        :key="`policy-${item.id}`"
                        class="assistant-library-row"
                        :to="item.detailUrl"
                      >
                        <span><strong>{{ item.title }}</strong><small>{{ item.matchReason || item.policyType || item.regionName }}</small></span>
                        <ArrowRight :size="15" aria-hidden="true" />
                      </RouterLink>
                      <p v-if="!turn.result.matchedPolicies?.length" class="assistant-library-empty">本次未选入可用政策</p>
                    </div>
                  </div>
                </section>

                <section class="assistant-citations">
                  <div class="assistant-section-title">
                    <div><span class="caption">CITATIONS</span><h4>来源依据</h4></div>
                    <strong>{{ turn.result.citations?.length || 0 }}</strong>
                  </div>
                  <details v-for="citation in turn.result.citations" :key="`${citation.sourceId}-${citation.claim}`">
                    <summary>
                      <span>{{ citation.title }}</span>
                      <ChevronDown :size="16" aria-hidden="true" />
                    </summary>
                    <p>{{ citation.claim }}</p>
                    <a :href="citation.url" target="_blank" rel="noreferrer">
                      查看原始来源 #{{ citation.sourceId }}<ExternalLink :size="14" aria-hidden="true" />
                    </a>
                  </details>
                </section>
              </template>

              <footer class="assistant-result-meta">
                <p><Sparkles :size="15" />AI 生成内容仅用于研究参考，请结合原始来源和实际调研判断。</p>
                <dl>
                  <div><dt>模型</dt><dd>{{ turn.result.provider }} / {{ turn.result.model }}</dd></div>
                  <div><dt>置信度</dt><dd>{{ formatConfidence(turn.result.confidence) }}</dd></div>
                  <div><dt>生成时间</dt><dd>{{ formatDate(turn.result.generatedAt) }}</dd></div>
                </dl>
              </footer>
            </section>
          </article>

        </div>

        <form v-if="turns.length && providerReady" class="assistant-followup" @submit.prevent="requestAdvice('followup')">
          <label for="assistant-followup-question">补充问题并重新生成 <span>{{ followup.length }} / 500</span></label>
          <div>
            <textarea
              id="assistant-followup-question"
              v-model.trim="followup"
              rows="2"
              maxlength="500"
              placeholder="围绕同一份创业画像继续提问"
              required
              :disabled="submitting"
            ></textarea>
            <button class="button" type="submit" :disabled="submitting || !followup.trim()" aria-label="发送追问">
              <Send :size="17" aria-hidden="true" />
            </button>
          </div>
        </form>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  AlertTriangle,
  ArrowRight,
  BrainCircuit,
  Building2,
  ChevronDown,
  ExternalLink,
  FileSearch,
  MapPin,
  RefreshCw,
  Search,
  Send,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
} from 'lucide-vue-next'
import {
  checkEntrepreneurshipReadiness,
  getAiCapabilities,
  getEntrepreneurshipAdvice,
  resolveIndustryWithAi,
} from '@/api/ai'
import { getRegions } from '@/api/region'
import { getIndustryTags } from '@/api/tag'
import {
  confirmIndustrySuggestion,
  createLatestRequestGate,
  decideIndustryResolution,
  industrySuggestionKey,
  readinessPresentation,
} from '@/utils/assistantWorkflow'

const PROFILE_STORAGE_KEY = 'opc_assistant_profile_v1'

const dictionaries = {
  ventureType: {
    solo_company: '一人公司',
    individual_business: '个体经营',
    small_team: '小型创业团队',
    exploring: '尚在探索',
  },
  stage: {
    idea: '想法形成',
    validation: '需求验证',
    early_operation: '早期运营',
    growth: '增长阶段',
  },
  budgetRange: {
    under_100k: '10 万元以内',
    '100k_500k': '10-50 万元',
    '500k_1m': '50-100 万元',
    over_1m: '100 万元以上',
    undecided: '预算未定',
  },
}

const pageLoading = ref(false)
const pageError = ref('')
const regions = ref([])
const industries = ref([])
const capabilities = ref({})
const readiness = ref(null)
const readinessChecking = ref(false)
const readinessError = ref('')
const submitting = ref(false)
const requestError = ref('')
const requestErrorDiagnostic = ref('')
const turns = ref([])
const followup = ref('')
const lastMode = ref('initial')
const industryQuery = ref('')
const industryOpen = ref(false)
const activeIndustryIndex = ref(-1)
const industrySuggestion = ref(null)
const rejectedIndustrySuggestionKey = ref('')
const industryResolutionLoading = ref(false)
const industryResolutionError = ref('')
const readinessGate = createLatestRequestGate()
const industryResolutionGate = createLatestRequestGate()
let readinessTimer

const profile = reactive({
  ventureType: 'solo_company',
  regionId: '',
  industryTagId: '',
  industry: '',
  stage: 'validation',
  budgetRange: 'under_100k',
  goal: '',
  existingResources: '',
  userQuestion: '',
})

const providerReady = computed(() => Boolean(
  capabilities.value?.provider?.available
  && capabilities.value?.capabilities?.some(
    (item) => item.id === 'entrepreneurship-advisor' && item.available,
  ),
))

const providerLabel = computed(() => {
  if (!providerReady.value) return '等待管理员完成模型配置'
  return `${capabilities.value.provider.provider} / ${capabilities.value.provider.model}`
})
const requestErrorTitle = computed(() => {
  if (requestErrorDiagnostic.value === 'TRUNCATED_RESPONSE') return '模型输出被截断'
  if (['MISSING_CITATIONS', 'UNKNOWN_SOURCE_ID', 'BLANK_CLAIM'].includes(requestErrorDiagnostic.value)) {
    return '模型引用未通过核验'
  }
  if (['INVALID_JSON', 'MISSING_FIELD', 'INVALID_CONFIDENCE', 'ABNORMAL_FINISH_REASON']
    .includes(requestErrorDiagnostic.value)) {
    return '模型返回格式错误'
  }
  return '本次研究未完成'
})

const readinessUi = computed(() => readinessPresentation(
  readiness.value?.readinessStatus,
  { loading: readinessChecking.value, error: Boolean(readinessError.value) },
))
const readinessReasons = computed(() => readiness.value?.reasons || [])
const readinessTitle = computed(() => {
  if (readinessChecking.value) return '正在核验证据'
  if (readinessError.value) return '证据预检失败'
  const labels = {
    sufficient: '证据充分',
    partial: '证据有限，可继续',
    insufficient: '证据不足',
  }
  return labels[readiness.value?.readinessStatus] || '等待证据预检'
})
const readinessStatusClass = computed(() => ({
  'is-ready': readiness.value?.readinessStatus === 'sufficient',
  'is-partial': readiness.value?.readinessStatus === 'partial',
  'is-error': Boolean(readinessError.value),
}))
const industrySuggestionLabel = computed(() => {
  const method = industrySuggestion.value?.method
  if (method === 'fuzzy') return '找到相近行业，请确认'
  if (method === 'ai') return 'AI 推荐行业，请确认'
  return '行业推荐需要确认'
})
const filteredIndustries = computed(() => {
  const query = industryQuery.value.trim().toLowerCase()
  const values = query
    ? industries.value.filter((item) => item.name?.toLowerCase().includes(query))
    : industries.value
  return values.slice(0, 12)
})
const activeIndustryId = computed(() => {
  const item = filteredIndustries.value[activeIndustryIndex.value]
  return item ? `assistant-industry-option-${item.tagId}` : undefined
})

onMounted(() => {
  restoreProfile()
  loadPage()
})

onBeforeUnmount(() => window.clearTimeout(readinessTimer))

watch(profile, (value) => {
  sessionStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(value))
}, { deep: true })

watch(
  () => [profile.regionId, profile.industryTagId, profile.industry],
  () => scheduleReadiness(),
)

async function loadPage() {
  pageLoading.value = true
  pageError.value = ''
  try {
    const [regionData, capabilityData, industryData] = await Promise.all([
      getRegions(),
      getAiCapabilities(),
      getIndustryTags(),
    ])
    regions.value = Array.isArray(regionData) ? regionData : []
    capabilities.value = capabilityData || {}
    industries.value = Array.isArray(industryData) ? industryData : []
    const restored = industries.value.find((item) => String(item.tagId) === String(profile.industryTagId))
    if (restored) {
      profile.industry = restored.name
      industryQuery.value = restored.name
    } else {
      industryQuery.value = profile.industry
    }
    scheduleReadiness()
  } catch (err) {
    pageError.value = err.message || '研究助手页面暂时无法读取。'
  } finally {
    pageLoading.value = false
  }
}

async function requestAdvice(mode) {
  if (submitting.value || !providerReady.value) return
  if (mode === 'followup' && !followup.value.trim()) return
  requestErrorDiagnostic.value = ''

  if (mode === 'initial' || !readiness.value?.evidenceAvailable) {
    const ready = await checkReadiness()
    if (!ready) {
      requestError.value = readinessError.value || (readinessReasons.value.length
        ? `当前证据尚未就绪：${readinessReasons.value.join('；')}`
        : '当前证据尚未就绪，请调整地区或行业后重试。')
      return
    }
  }

  const region = regions.value.find((item) => String(item.id) === String(profile.regionId))
  const snapshot = {
    ...profile,
    regionName: region?.name || '所选地区',
  }
  const question = mode === 'followup' ? followup.value.trim() : profile.userQuestion.trim()
  const payload = {
    ventureType: profile.ventureType,
    regionId: Number(profile.regionId),
    industryTagId: profile.industryTagId ? Number(profile.industryTagId) : undefined,
    industry: profile.industry.trim(),
    stage: profile.stage,
    budgetRange: profile.budgetRange,
    goal: profile.goal.trim(),
    existingResources: profile.existingResources.trim(),
    userQuestion: question,
  }

  submitting.value = true
  requestError.value = ''
  lastMode.value = mode
  try {
    const result = await getEntrepreneurshipAdvice(payload)
    turns.value.push({
      id: `${Date.now()}-${turns.value.length}`,
      profile: snapshot,
      question,
      result,
    })
    if (mode === 'followup') followup.value = ''
    if (mode === 'initial') profile.userQuestion = ''
    await nextTick()
    document.querySelector('.assistant-turn:last-of-type')?.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'start' })
  } catch (err) {
    requestErrorDiagnostic.value = err.diagnosticCode
      || err.response?.data?.data?.diagnosticCode
      || ''
    requestError.value = err.message || '创业研究请求失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}

function retryLastRequest() {
  requestAdvice(lastMode.value)
}

function openIndustryOptions() {
  industryOpen.value = true
  activeIndustryIndex.value = filteredIndustries.value.length ? 0 : -1
}

function closeIndustryOptions() {
  window.setTimeout(() => {
    industryOpen.value = false
    activeIndustryIndex.value = -1
  }, 100)
}

function handleIndustryInput() {
  profile.industry = industryQuery.value.trim()
  const selected = industries.value.find(
    (item) => item.name?.toLowerCase() === industryQuery.value.trim().toLowerCase(),
  )
  profile.industryTagId = selected ? String(selected.tagId) : ''
  industrySuggestion.value = null
  industryResolutionError.value = ''
  industryResolutionGate.begin()
  industryResolutionLoading.value = false
  industryOpen.value = true
  activeIndustryIndex.value = filteredIndustries.value.length ? 0 : -1
}

function handleIndustryKeydown(event) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    industryOpen.value = true
    activeIndustryIndex.value = Math.min(activeIndustryIndex.value + 1, filteredIndustries.value.length - 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndustryIndex.value = Math.max(activeIndustryIndex.value - 1, 0)
  } else if (event.key === 'Enter' && industryOpen.value && activeIndustryIndex.value >= 0) {
    event.preventDefault()
    selectIndustry(filteredIndustries.value[activeIndustryIndex.value])
  } else if (event.key === 'Escape') {
    industryOpen.value = false
  }
}

function selectIndustry(industry) {
  profile.industryTagId = String(industry.tagId)
  profile.industry = industry.name
  industryQuery.value = industry.name
  industryOpen.value = false
  activeIndustryIndex.value = -1
  industrySuggestion.value = null
  rejectedIndustrySuggestionKey.value = ''
  industryResolutionError.value = ''
}

function scheduleReadiness() {
  window.clearTimeout(readinessTimer)
  readinessGate.begin()
  readinessChecking.value = false
  readiness.value = null
  readinessError.value = ''
  if (!profile.regionId || (!profile.industryTagId && !profile.industry.trim())) return
  readinessTimer = window.setTimeout(() => checkReadiness(), 420)
}

async function checkReadiness() {
  if (!profile.regionId || (!profile.industryTagId && !profile.industry.trim())) return false
  const requestId = readinessGate.begin()
  readinessChecking.value = true
  readinessError.value = ''
  try {
    const result = await checkEntrepreneurshipReadiness({
      regionId: Number(profile.regionId),
      industryTagId: profile.industryTagId ? Number(profile.industryTagId) : undefined,
      industry: profile.industry.trim(),
    })
    if (!readinessGate.isCurrent(requestId)) return false
    readiness.value = result
    const decision = decideIndustryResolution(
      result?.resolvedIndustryTag,
      profile.industry,
      profile.industryTagId,
      rejectedIndustrySuggestionKey.value,
    )
    if (decision.action === 'accept') {
      applyIndustrySelection(decision.selection)
      return false
    }
    industrySuggestion.value = decision.suggestion
    const readinessDecision = readinessPresentation(result?.readinessStatus)
    return readinessDecision.canSubmit && !industrySuggestion.value && Boolean(profile.industryTagId)
  } catch (err) {
    if (!readinessGate.isCurrent(requestId)) return false
    readiness.value = null
    readinessError.value = err.message || '证据预检失败'
    return false
  } finally {
    if (readinessGate.isCurrent(requestId)) readinessChecking.value = false
  }
}

async function requestIndustryRecommendation() {
  const originalText = profile.industry.trim()
  if (!originalText || industryResolutionLoading.value) return
  const requestId = industryResolutionGate.begin()
  industryResolutionLoading.value = true
  industryResolutionError.value = ''
  try {
    const resolution = await resolveIndustryWithAi(originalText)
    if (!industryResolutionGate.isCurrent(requestId) || profile.industry.trim() !== originalText) return
    const decision = decideIndustryResolution(
      resolution,
      originalText,
      profile.industryTagId,
      rejectedIndustrySuggestionKey.value,
    )
    if (decision.action === 'accept') {
      applyIndustrySelection(decision.selection)
    } else if (decision.action === 'confirm') {
      industrySuggestion.value = decision.suggestion
    } else if (decision.action === 'unresolved') {
      industryResolutionError.value = '暂未找到可靠的规范行业，请继续输入或从列表选择。'
    }
  } catch (err) {
    if (industryResolutionGate.isCurrent(requestId)) {
      industryResolutionError.value = err.message || '行业识别失败，请稍后重试。'
    }
  } finally {
    if (industryResolutionGate.isCurrent(requestId)) industryResolutionLoading.value = false
  }
}

function confirmSuggestedIndustry() {
  if (!industrySuggestion.value) return
  applyIndustrySelection(confirmIndustrySuggestion(industrySuggestion.value))
}

function rejectSuggestedIndustry() {
  if (!industrySuggestion.value) return
  rejectedIndustrySuggestionKey.value = industrySuggestionKey(industrySuggestion.value)
  industrySuggestion.value = null
  profile.industryTagId = ''
  readiness.value = null
  scheduleReadiness()
}

function applyIndustrySelection(selection) {
  if (!selection) return
  profile.industryTagId = selection.industryTagId
  profile.industry = selection.industry
  industryQuery.value = selection.query
  industrySuggestion.value = null
  rejectedIndustrySuggestionKey.value = ''
  industryResolutionError.value = ''
}

function restoreProfile() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(PROFILE_STORAGE_KEY) || '{}')
    Object.keys(profile).forEach((key) => {
      if (typeof saved[key] === 'string') profile[key] = saved[key]
    })
    industryQuery.value = profile.industry
  } catch {
    sessionStorage.removeItem(PROFILE_STORAGE_KEY)
  }
}

function labelFor(group, value) {
  return dictionaries[group]?.[value] || value || '-'
}

function evidenceLabel(status) {
  const labels = {
    sufficient: '证据充分',
    partial: '部分证据',
    insufficient: '证据不足',
  }
  return labels[status] || '待核对'
}

function evidenceCount(result) {
  return (result.matchedCases?.length || 0) + (result.matchedPolicies?.length || 0)
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function formatConfidence(value) {
  if (typeof value !== 'number') return '-'
  return `${Math.round(value * 100)}%`
}

function reducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}
</script>

<style scoped>
.assistant-page {
  width: 100%;
  color: #181a18;
}

.assistant-workspace {
  display: grid;
  grid-template-columns: minmax(300px, 0.78fr) minmax(0, 1.72fr);
  min-height: 720px;
  border-top: 1px solid #c8ccc7;
  border-bottom: 1px solid #c8ccc7;
  background: #f8f8f4;
}

.assistant-profile {
  padding: clamp(24px, 3vw, 38px);
  border-right: 1px solid #c8ccc7;
  background: #f1f2ed;
}

.assistant-profile-head,
.assistant-thread-head,
.assistant-response-head,
.assistant-section-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.assistant-profile-head h2,
.assistant-thread-head h2 {
  margin: 7px 0 0;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: clamp(1.7rem, 2.8vw, 2.45rem);
  font-weight: 500;
  line-height: 1;
}

.assistant-profile-intro {
  margin: 22px 0 26px;
  color: #565d57;
  font-size: 0.9rem;
  line-height: 1.75;
}

.assistant-profile-form {
  display: grid;
  gap: 17px;
}

.assistant-profile-form label {
  display: grid;
  gap: 8px;
}

.assistant-profile-form label > span,
.assistant-followup > label {
  color: #2b2e2b;
  font-size: 0.78rem;
  font-weight: 700;
}

.assistant-profile-form small {
  color: #727973;
  font-weight: 400;
}

.assistant-profile-form :is(input, select, textarea) {
  border-color: #c3c8c2;
  border-radius: 5px;
  background: #fbfbf8;
}

.assistant-profile-form textarea {
  min-height: 86px;
  line-height: 1.55;
}

.assistant-industry-field {
  position: relative;
}

.assistant-industry-combobox {
  position: relative;
}

.assistant-industry-combobox > input {
  width: 100%;
  padding-right: 40px;
}

.assistant-industry-combobox > svg {
  position: absolute;
  top: 50%;
  right: 14px;
  color: #686f69;
  pointer-events: none;
  transform: translateY(-50%);
}

.assistant-industry-listbox {
  position: absolute;
  z-index: 20;
  top: calc(100% + 6px);
  right: 0;
  left: 0;
  max-height: 260px;
  margin: 0;
  padding: 5px;
  overflow-y: auto;
  border: 1px solid #bfc4be;
  border-radius: 5px;
  background: #fbfbf8;
  box-shadow: 0 12px 24px rgb(28 31 28 / 10%);
  list-style: none;
}

.assistant-industry-listbox li {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 10px;
  border-radius: 3px;
  color: #292d29;
  cursor: pointer;
}

.assistant-industry-listbox li:is(:hover, .is-active),
.assistant-industry-listbox li[aria-selected='true'] {
  background: #e7e9e4;
}

.assistant-industry-listbox li small {
  color: #717872;
  white-space: nowrap;
}

.assistant-industry-listbox li.is-empty {
  color: #6a716b;
  cursor: default;
}

.assistant-industry-selected {
  color: #4b6250 !important;
  line-height: 1.5;
}

.assistant-industry-suggestion {
  display: grid;
  gap: 13px;
  padding: 15px;
  border: 1px solid #c7b991;
  border-radius: 5px;
  background: #f5f1e6;
}

.assistant-industry-suggestion > div:first-child {
  display: grid;
  gap: 4px;
}

.assistant-industry-suggestion span,
.assistant-industry-suggestion small {
  color: #695f45;
  font-size: 0.74rem;
  line-height: 1.5;
}

.assistant-industry-suggestion strong {
  color: #292b28;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: 1rem;
}

.assistant-suggestion-actions,
.assistant-industry-resolution-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.assistant-suggestion-actions .button,
.assistant-industry-resolution-actions .button {
  min-height: 34px;
  padding: 7px 11px;
  font-size: 0.74rem;
}

.assistant-industry-resolution-actions small {
  flex-basis: 100%;
  color: #742e26;
  line-height: 1.5;
}

.assistant-form-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.assistant-provider-note {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 13px 0;
  border-top: 1px solid #ced2cd;
  border-bottom: 1px solid #ced2cd;
}

.assistant-readiness {
  display: grid;
  border-top: 1px solid #ced2cd;
  border-bottom: 1px solid #ced2cd;
}

.assistant-readiness .assistant-provider-note {
  border: 0;
}

.assistant-readiness .assistant-provider-note + .assistant-provider-note {
  border-top: 1px solid #d9dcd8;
}

.assistant-readiness-reasons {
  display: grid;
  gap: 5px;
  margin: 0;
  padding: 0 0 13px 24px;
  color: #6b3c35;
  font-size: 0.75rem;
  line-height: 1.5;
}

.assistant-provider-note > span {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  border-radius: 50%;
  background: #8c918c;
}

.assistant-provider-note.is-ready > span {
  background: #426a4d;
}

.assistant-provider-note.is-partial > span {
  background: #8a6b2f;
}

.assistant-provider-note.is-error > span {
  background: #8a352e;
}

.assistant-provider-note div {
  display: grid;
  gap: 3px;
}

.assistant-provider-note strong {
  font-size: 0.82rem;
}

.assistant-provider-note small {
  color: #686f69;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 0.72rem;
}

.assistant-provider-note small.is-error {
  color: #742e26;
}

.assistant-readiness-warning {
  margin: 0;
  padding: 0 0 13px 19px;
  color: #665b3d;
  font-size: 0.76rem;
  line-height: 1.55;
}

.assistant-submit {
  justify-content: space-between;
  width: 100%;
  min-height: 48px;
  margin-top: 2px;
}

.assistant-thread {
  display: flex;
  min-width: 0;
  flex-direction: column;
  background: #fbfbf8;
}

.assistant-thread-head {
  align-items: center;
  padding: clamp(24px, 3vw, 38px);
  border-bottom: 1px solid #ced2cd;
}

.assistant-thread-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #606761;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 0.76rem;
}

.assistant-empty-state {
  display: grid;
  min-height: 520px;
  align-content: center;
  gap: 24px;
  padding: clamp(34px, 7vw, 84px);
}

.assistant-empty-state > svg {
  color: #555c56;
}

.assistant-empty-state h3 {
  max-width: 700px;
  margin: 10px 0 14px;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: clamp(2rem, 4.2vw, 4.2rem);
  font-weight: 500;
  line-height: 1.12;
}

.assistant-empty-state p {
  max-width: 620px;
  margin: 0;
  color: #59605a;
  line-height: 1.75;
}

.assistant-empty-state ol {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  margin: 16px 0 0;
  padding: 0;
  border-top: 1px solid #cfd3ce;
  border-bottom: 1px solid #cfd3ce;
  list-style: none;
}

.assistant-empty-state li {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-right: 1px solid #cfd3ce;
  color: #383d39;
}

.assistant-empty-state li:last-child {
  border-right: 0;
}

.assistant-empty-state li span {
  color: #7a807b;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 0.73rem;
}

.assistant-empty-state.is-disabled {
  grid-template-columns: auto minmax(0, 1fr);
  min-height: 420px;
}

.assistant-transcript {
  display: grid;
}

.assistant-turn {
  animation: assistant-rise 360ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.assistant-user-entry {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 22px;
  padding: clamp(25px, 4vw, 44px);
  border-bottom: 1px solid #cfd3ce;
  background: #f1f2ed;
}

.assistant-entry-index {
  color: #777e78;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 1.15rem;
}

.assistant-user-entry h3 {
  margin: 7px 0 12px;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: clamp(1.35rem, 2.5vw, 2.2rem);
  font-weight: 500;
}

.assistant-user-entry p {
  margin: 16px 0 0;
  color: #454b46;
  line-height: 1.7;
}

.assistant-brief-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  color: #666d67;
  font-size: 0.78rem;
}

.assistant-brief-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.assistant-response {
  padding: clamp(28px, 5vw, 54px);
  border-bottom: 1px solid #bfc4be;
}

.assistant-response-head h3 {
  max-width: 760px;
  margin: 8px 0 0;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: clamp(1.8rem, 3.6vw, 3.5rem);
  font-weight: 500;
  line-height: 1.18;
}

.assistant-evidence-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 10px;
  border: 1px solid #abc0b0;
  border-radius: 5px;
  background: #edf3ee;
  color: #31553a;
  font-size: 0.78rem;
  white-space: nowrap;
}

.assistant-evidence-pill.is-partial,
.assistant-evidence-pill.is-insufficient {
  border-color: #cfc4aa;
  background: #f4f0e7;
  color: #665b3d;
}

.assistant-direction {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 24px;
  margin-top: 34px;
  padding: 24px 0;
  border-top: 1px solid #ced2cd;
  border-bottom: 1px solid #ced2cd;
}

.assistant-direction span {
  color: #717872;
  font-size: 0.76rem;
}

.assistant-direction p {
  margin: 0;
  color: #303530;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: 1.14rem;
  line-height: 1.8;
}

.assistant-advice-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-bottom: 1px solid #ced2cd;
}

.assistant-advice-grid section {
  min-width: 0;
  padding: 26px 22px 28px 0;
  border-right: 1px solid #ced2cd;
}

.assistant-advice-grid section + section {
  padding-left: 22px;
}

.assistant-advice-grid section:last-child {
  padding-right: 0;
  border-right: 0;
}

.assistant-advice-grid section > span {
  color: #7b817c;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 0.72rem;
}

.assistant-advice-grid h4,
.assistant-section-title h4 {
  margin: 10px 0 12px;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: 1.06rem;
}

.assistant-advice-grid :is(ul, ol) {
  display: grid;
  gap: 9px;
  margin: 0;
  padding-left: 18px;
  color: #4a504b;
  font-size: 0.86rem;
  line-height: 1.65;
}

.assistant-library,
.assistant-citations {
  padding: 34px 0 0;
}

.assistant-section-title {
  align-items: flex-end;
  margin-bottom: 14px;
}

.assistant-section-title h4 {
  margin: 6px 0 0;
}

.assistant-section-title > strong {
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 1.6rem;
  font-weight: 400;
}

.assistant-library-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid #cfd3ce;
  border-bottom: 1px solid #cfd3ce;
}

.assistant-library-columns > div {
  min-width: 0;
  padding: 20px 22px 20px 0;
}

.assistant-library-columns > div + div {
  padding-right: 0;
  padding-left: 22px;
  border-left: 1px solid #cfd3ce;
}

.assistant-library-columns h5 {
  margin: 0 0 12px;
  color: #777e78;
  font-size: 0.73rem;
  font-weight: 600;
  text-transform: uppercase;
}

.assistant-library-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 0;
  border-top: 1px solid #dde0dc;
  color: #242724;
}

.assistant-library-row:first-of-type {
  border-top: 0;
}

.assistant-library-row > span {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.assistant-library-row strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-library-row small,
.assistant-library-empty {
  color: #707771;
  font-size: 0.76rem;
}

.assistant-library-row svg {
  flex: 0 0 auto;
  transition: transform 180ms ease;
}

.assistant-library-row:hover svg {
  transform: translateX(4px);
}

.assistant-library-empty {
  margin: 0;
  padding: 18px 0;
}

.assistant-citations details {
  border-top: 1px solid #d1d5d0;
}

.assistant-citations details:last-of-type {
  border-bottom: 1px solid #d1d5d0;
}

.assistant-citations summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 0;
  cursor: pointer;
  list-style: none;
  font-weight: 700;
}

.assistant-citations summary::-webkit-details-marker {
  display: none;
}

.assistant-citations details[open] summary svg {
  transform: rotate(180deg);
}

.assistant-citations summary svg {
  flex: 0 0 auto;
  transition: transform 180ms ease;
}

.assistant-citations details p {
  margin: 0 0 12px;
  color: #555c56;
  line-height: 1.7;
}

.assistant-citations details a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 17px;
  color: #252825;
  font-size: 0.82rem;
}

.assistant-result-meta {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, auto);
  gap: 26px;
  margin-top: 34px;
  padding-top: 20px;
  border-top: 1px solid #c7cbc6;
}

.assistant-result-meta > p {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  color: #646b65;
  font-size: 0.8rem;
  line-height: 1.6;
}

.assistant-result-meta dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
}

.assistant-result-meta dl div {
  display: grid;
  gap: 4px;
}

.assistant-result-meta dt {
  color: #777e78;
  font-size: 0.69rem;
}

.assistant-result-meta dd {
  margin: 0;
  color: #353a36;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 0.75rem;
}

.assistant-evidence-warning,
.assistant-thinking,
.assistant-request-error,
.assistant-page-state {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 22px 0;
  border-top: 1px solid #d0c5ad;
  border-bottom: 1px solid #d0c5ad;
  color: #665b3d;
}

.assistant-evidence-warning {
  margin-top: 28px;
}

.assistant-evidence-warning p {
  margin: 0;
  line-height: 1.7;
}

.assistant-evidence-warning ul {
  display: grid;
  gap: 5px;
  margin: 10px 0 0;
  padding-left: 18px;
  line-height: 1.55;
}

.assistant-thinking,
.assistant-request-error {
  margin: 0 clamp(28px, 5vw, 54px);
  border-color: #ced2cd;
  color: #3b403c;
}

.assistant-thinking div,
.assistant-request-error div,
.assistant-page-state div {
  display: grid;
  flex: 1;
  gap: 4px;
}

.assistant-thinking p,
.assistant-request-error p,
.assistant-page-state p {
  margin: 0;
  color: #666d67;
}

.assistant-request-error,
.assistant-page-state.is-error {
  border-color: #d2aaa4;
  color: #742e26;
}

.assistant-page-state {
  margin: 24px 0;
}

.assistant-spinner {
  width: 19px;
  height: 19px;
  flex: 0 0 19px;
  border: 2px solid #c9cec8;
  border-top-color: #252825;
  border-radius: 50%;
  animation: assistant-spin 720ms linear infinite;
}

.assistant-followup {
  margin-top: auto;
  padding: 24px clamp(28px, 5vw, 54px);
  border-top: 1px solid #c8ccc7;
  background: #f1f2ed;
}

.assistant-followup > label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 9px;
}

.assistant-followup > label span {
  color: #767d77;
  font-weight: 400;
}

.assistant-followup > div {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px;
  gap: 10px;
}

.assistant-followup textarea {
  min-height: 58px;
  border-radius: 5px;
  background: #fbfbf8;
}

.assistant-followup .button {
  width: 48px;
  min-height: 48px;
  align-self: end;
  padding: 0;
}

@keyframes assistant-spin {
  to { transform: rotate(360deg); }
}

@keyframes assistant-rise {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 1020px) {
  .assistant-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .assistant-profile {
    border-right: 0;
    border-bottom: 1px solid #c8ccc7;
  }

  .assistant-profile-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .assistant-profile-form > label:nth-of-type(n + 4),
  .assistant-provider-note,
  .assistant-submit {
    grid-column: 1 / -1;
  }

  .assistant-form-pair {
    grid-column: 1 / -1;
  }
}

@media (max-width: 720px) {
  .assistant-workspace {
    min-height: 0;
  }

  .assistant-profile,
  .assistant-thread-head,
  .assistant-user-entry,
  .assistant-response,
  .assistant-followup {
    padding-right: 20px;
    padding-left: 20px;
  }

  .assistant-profile-form {
    grid-template-columns: minmax(0, 1fr);
  }

  .assistant-profile-form > label:nth-of-type(n),
  .assistant-provider-note,
  .assistant-submit,
  .assistant-form-pair {
    grid-column: auto;
  }

  .assistant-form-pair,
  .assistant-advice-grid,
  .assistant-library-columns,
  .assistant-result-meta,
  .assistant-result-meta dl {
    grid-template-columns: minmax(0, 1fr);
  }

  .assistant-thread-head,
  .assistant-response-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .assistant-empty-state {
    min-height: 420px;
    padding: 44px 20px;
  }

  .assistant-empty-state ol {
    grid-template-columns: minmax(0, 1fr);
  }

  .assistant-empty-state li {
    border-right: 0;
    border-bottom: 1px solid #cfd3ce;
  }

  .assistant-empty-state li:last-child {
    border-bottom: 0;
  }

  .assistant-empty-state.is-disabled {
    grid-template-columns: minmax(0, 1fr);
  }

  .assistant-user-entry {
    grid-template-columns: 34px minmax(0, 1fr);
    gap: 10px;
  }

  .assistant-direction {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }

  .assistant-advice-grid section,
  .assistant-advice-grid section + section {
    padding: 22px 0;
    border-right: 0;
    border-bottom: 1px solid #ced2cd;
  }

  .assistant-advice-grid section:last-child {
    border-bottom: 0;
  }

  .assistant-library-columns > div,
  .assistant-library-columns > div + div {
    padding: 18px 0;
    border-left: 0;
  }

  .assistant-library-columns > div + div {
    border-top: 1px solid #cfd3ce;
  }

  .assistant-library-row strong {
    white-space: normal;
  }

  .assistant-thinking,
  .assistant-request-error {
    margin-right: 20px;
    margin-left: 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-spinner,
  .assistant-turn {
    animation: none;
  }

  .assistant-library-row svg,
  .assistant-citations summary svg {
    transition: none;
  }
}
</style>
