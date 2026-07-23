<template>
  <div class="page-stack case-analysis-page">
    <RouterLink class="back-link case-analysis-back" :to="`/cases/${id}`">
      <ArrowLeft :size="16" />返回案例详情
    </RouterLink>

    <header class="case-analysis-head">
      <div>
        <span class="caption">CASE ANALYSIS / V1</span>
        <h2>案例分析</h2>
        <p>围绕已发布并完成人工核验的案例、政策与来源，生成可追溯的结构化判断。</p>
      </div>
      <div class="case-analysis-provider" :class="{ 'is-ready': providerReady }">
        <BrainCircuit :size="20" />
        <span>{{ providerReady ? `${capabilities.provider?.provider} / ${capabilities.provider?.model}` : '模型未启用' }}</span>
      </div>
    </header>

    <section v-if="pageLoading" class="case-analysis-state" role="status">
      <span class="case-analysis-loader" aria-hidden="true"></span>
      <strong>正在准备案例证据...</strong>
    </section>

    <section v-else-if="pageError" class="case-analysis-state is-error" role="alert">
      <AlertTriangle :size="22" />
      <strong>{{ pageError }}</strong>
      <button class="button button-ghost" type="button" @click="loadPage">重新读取</button>
    </section>

    <template v-else>
      <section class="case-analysis-subject" aria-labelledby="analysis-case-title">
        <span>分析对象</span>
        <div>
          <h3 id="analysis-case-title">{{ caseItem.title }}</h3>
          <p>{{ caseItem.summary || '该案例暂无摘要。' }}</p>
        </div>
        <dl>
          <div><dt>地区</dt><dd>{{ caseItem.regionName || '-' }}</dd></div>
          <div><dt>领域</dt><dd>{{ caseItem.category || '-' }}</dd></div>
          <div><dt>来源</dt><dd>{{ caseItem.sourceTitle || '-' }}</dd></div>
        </dl>
      </section>

      <section v-if="!providerReady" class="case-analysis-state is-muted" role="status">
        <BrainCircuit :size="24" />
        <div>
          <strong>智能体模型尚未配置或未启用</strong>
          <p>管理员需要先完成 API Base URL、准确 Model ID 与 API Key 配置，并通过连接测试。</p>
        </div>
      </section>

      <form v-else class="case-analysis-command" @submit.prevent="runAnalysis">
        <label for="case-analysis-question">补充关注点 <span>可选，最多 500 字</span></label>
        <textarea
          id="case-analysis-question"
          v-model="userQuestion"
          rows="3"
          maxlength="500"
          placeholder="例如：这个案例当前最需要验证的技术风险是什么？"
          :disabled="analyzing"
        ></textarea>
        <div>
          <small>{{ userQuestion.length }} / 500</small>
          <button class="button icon-text-button" type="submit" :disabled="analyzing">
            <BrainCircuit :size="17" />{{ analyzing ? '正在分析...' : result ? '重新分析' : '开始案例分析' }}
          </button>
        </div>
      </form>

      <section v-if="analyzing" class="case-analysis-progress" aria-live="polite">
        <span class="case-analysis-loader" aria-hidden="true"></span>
        <div>
          <strong>正在核对证据并生成结构化分析</strong>
          <p>请勿重复提交。结果将附带模型、生成时间和来源引用。</p>
        </div>
      </section>

      <section v-else-if="analysisError" class="case-analysis-state is-error" role="alert">
        <AlertTriangle :size="22" />
        <div>
          <strong>分析未完成</strong>
          <p>{{ analysisError }}</p>
        </div>
        <button class="button button-ghost icon-text-button" type="button" @click="runAnalysis">
          <RefreshCw :size="15" />重试
        </button>
      </section>

      <article v-else-if="result" class="case-analysis-result" aria-labelledby="case-analysis-result-title">
        <div class="case-analysis-result-head">
          <div>
            <span class="caption">GENERATED ANALYSIS</span>
            <h3 id="case-analysis-result-title">{{ result.summary }}</h3>
          </div>
          <span class="evidence-pill" :class="{ 'is-insufficient': result.evidenceStatus !== 'sufficient' }">
            <ShieldCheck :size="15" />{{ result.evidenceStatus === 'sufficient' ? '证据已关联' : '证据不足' }}
          </span>
        </div>

        <div v-if="result.evidenceStatus !== 'sufficient'" class="case-analysis-evidence-warning">
          <AlertTriangle :size="20" />
          <p>当前案例或关联来源尚未完成 AI 证据核验，因此没有调用模型生成事实性结论。</p>
        </div>

        <div class="case-analysis-sections">
          <section>
            <span>01</span>
            <div><h4>商业模式</h4><p>{{ result.businessModel }}</p></div>
          </section>
          <section>
            <span>02</span>
            <div><h4>技术评估</h4><p>{{ result.technicalAssessment }}</p></div>
          </section>
          <section>
            <span>03</span>
            <div><h4>机会</h4><ul><li v-for="item in result.opportunities" :key="item">{{ item }}</li><li v-if="!result.opportunities?.length">证据不足</li></ul></div>
          </section>
          <section>
            <span>04</span>
            <div><h4>风险</h4><ul><li v-for="item in result.risks" :key="item">{{ item }}</li><li v-if="!result.risks?.length">证据不足</li></ul></div>
          </section>
          <section>
            <span>05</span>
            <div><h4>建议行动</h4><ol><li v-for="item in result.recommendedActions" :key="item">{{ item }}</li><li v-if="!result.recommendedActions?.length">补充人工核验后再分析</li></ol></div>
          </section>
        </div>

        <section class="case-analysis-citations">
          <div class="case-analysis-citations-head">
            <div><span class="caption">EVIDENCE</span><h4>来源引用</h4></div>
            <strong>{{ result.citations?.length || 0 }}</strong>
          </div>
          <details v-for="citation in result.citations" :key="`${citation.sourceId}-${citation.claim}`">
            <summary>
              <span>{{ citation.title }}</span>
              <ChevronDown :size="16" />
            </summary>
            <p>{{ citation.claim }}</p>
            <a :href="citation.url" target="_blank" rel="noreferrer">
              查看来源 #{{ citation.sourceId }}<ExternalLink :size="14" />
            </a>
          </details>
          <p v-if="!result.citations?.length" class="muted">没有可展示的已核验引用。</p>
        </section>

        <footer class="case-analysis-meta">
          <p><Sparkles :size="15" />本页包含 AI 生成内容，请结合原始来源和人工判断使用。</p>
          <dl>
            <div><dt>模型</dt><dd>{{ result.provider }} / {{ result.model }}</dd></div>
            <div><dt>提示词版本</dt><dd>{{ result.promptVersion }}</dd></div>
            <div><dt>置信度</dt><dd>{{ formatConfidence(result.confidence) }}</dd></div>
            <div><dt>生成时间</dt><dd>{{ formatDate(result.generatedAt) }}</dd></div>
          </dl>
        </footer>
      </article>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  AlertTriangle,
  ArrowLeft,
  BrainCircuit,
  ChevronDown,
  ExternalLink,
  RefreshCw,
  ShieldCheck,
  Sparkles,
} from 'lucide-vue-next'
import { getCaseDetail } from '@/api/case'
import { analyzeCase, getAiCapabilities } from '@/api/ai'

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
})

const pageLoading = ref(false)
const pageError = ref('')
const caseItem = ref({})
const capabilities = ref({})
const userQuestion = ref('')
const analyzing = ref(false)
const analysisError = ref('')
const result = ref(null)

const providerReady = computed(() => Boolean(
  capabilities.value?.provider?.available
  && capabilities.value?.capabilities?.some((item) => item.id === 'case-analysis' && item.available),
))

onMounted(loadPage)

async function loadPage() {
  pageLoading.value = true
  pageError.value = ''
  try {
    const [caseResult, capabilityResult] = await Promise.all([
      getCaseDetail(props.id),
      getAiCapabilities(),
    ])
    caseItem.value = caseResult
    capabilities.value = capabilityResult
  } catch (err) {
    pageError.value = err.message || '案例分析页面暂时无法读取。'
  } finally {
    pageLoading.value = false
  }
}

async function runAnalysis() {
  if (analyzing.value) return
  analyzing.value = true
  analysisError.value = ''
  try {
    result.value = await analyzeCase(props.id, userQuestion.value)
  } catch (err) {
    analysisError.value = err.message || '案例分析请求失败，请稍后重试。'
  } finally {
    analyzing.value = false
  }
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function formatConfidence(value) {
  if (typeof value !== 'number') return '-'
  return `${Math.round(value * 100)}%`
}
</script>

<style scoped>
.case-analysis-page {
  max-width: 1220px;
  margin: 0 auto;
  padding-bottom: 80px;
  color: #181a18;
}

.case-analysis-back {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  width: max-content;
}

.case-analysis-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 32px;
  padding: clamp(34px, 5vw, 72px) 0 30px;
  border-bottom: 1px solid #cfd3ce;
}

.case-analysis-head h2 {
  margin: 8px 0 12px;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: clamp(2.2rem, 5vw, 4.7rem);
  font-weight: 500;
  line-height: 1;
}

.case-analysis-head p {
  max-width: 680px;
  margin: 0;
  color: #555c56;
  line-height: 1.75;
}

.case-analysis-provider {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid #c8ccc7;
  border-radius: 6px;
  background: #f1f2ee;
  color: #686f69;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 0.82rem;
}

.case-analysis-provider.is-ready {
  color: #284d33;
}

.case-analysis-subject {
  display: grid;
  grid-template-columns: 110px minmax(0, 1.6fr) minmax(280px, 0.8fr);
  gap: 28px;
  padding: 30px 0;
  border-bottom: 1px solid #d7dad5;
}

.case-analysis-subject > span,
.case-analysis-subject dt {
  color: #747b75;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 0.72rem;
  text-transform: uppercase;
}

.case-analysis-subject h3 {
  margin: 0 0 8px;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: clamp(1.35rem, 2.2vw, 2rem);
}

.case-analysis-subject p {
  margin: 0;
  color: #555c56;
  line-height: 1.7;
}

.case-analysis-subject dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.case-analysis-subject dl div {
  display: grid;
  gap: 6px;
}

.case-analysis-subject dd {
  margin: 0;
  color: #303530;
}

.case-analysis-command {
  display: grid;
  gap: 12px;
  padding: 30px 0;
}

.case-analysis-command label {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  color: #252825;
  font-weight: 700;
}

.case-analysis-command label span,
.case-analysis-command small {
  color: #737a74;
  font-weight: 400;
}

.case-analysis-command textarea {
  width: 100%;
  min-height: 112px;
  resize: vertical;
  border: 1px solid #c5cac4;
  border-radius: 7px;
  background: #fbfbf8;
  color: #181a18;
  line-height: 1.65;
}

.case-analysis-command textarea:focus {
  border-color: #252825;
  outline: 2px solid rgba(37, 40, 37, 0.12);
  outline-offset: 1px;
}

.case-analysis-command > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.case-analysis-state,
.case-analysis-progress {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 28px 0;
  padding: 22px 0;
  border-top: 1px solid #d1d5d0;
  border-bottom: 1px solid #d1d5d0;
}

.case-analysis-state > div,
.case-analysis-progress > div {
  display: grid;
  flex: 1;
  gap: 5px;
}

.case-analysis-state p,
.case-analysis-progress p {
  margin: 0;
  color: #686f69;
}

.case-analysis-state.is-error {
  color: #742e26;
  border-color: #d4b4ad;
}

.case-analysis-state.is-muted {
  color: #555c56;
}

.case-analysis-loader {
  width: 19px;
  height: 19px;
  flex: 0 0 19px;
  border: 2px solid #c9cec8;
  border-top-color: #252825;
  border-radius: 50%;
  animation: analysis-spin 720ms linear infinite;
}

.case-analysis-result {
  margin-top: 30px;
  border-top: 1px solid #c5cac4;
}

.case-analysis-result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 28px;
  padding: 30px 0;
}

.case-analysis-result-head h3 {
  max-width: 850px;
  margin: 8px 0 0;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: clamp(1.75rem, 3.6vw, 3.4rem);
  font-weight: 500;
  line-height: 1.18;
}

.evidence-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 10px;
  border: 1px solid #adc2b2;
  border-radius: 6px;
  background: #edf3ee;
  color: #31553a;
  white-space: nowrap;
}

.evidence-pill.is-insufficient {
  border-color: #d0c5ad;
  background: #f4f1e8;
  color: #665b3d;
}

.case-analysis-evidence-warning {
  display: flex;
  gap: 12px;
  padding: 18px 0;
  border-top: 1px solid #d0c5ad;
  border-bottom: 1px solid #d0c5ad;
  color: #665b3d;
}

.case-analysis-evidence-warning p {
  margin: 0;
}

.case-analysis-sections {
  border-top: 1px solid #d7dad5;
}

.case-analysis-sections section {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  gap: 24px;
  padding: 26px 0;
  border-bottom: 1px solid #d7dad5;
}

.case-analysis-sections section > span {
  color: #7b817c;
  font-family: 'Bookman Old Style', Georgia, serif;
}

.case-analysis-sections h4,
.case-analysis-citations h4 {
  margin: 0 0 10px;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: 1.16rem;
}

.case-analysis-sections p,
.case-analysis-sections ul,
.case-analysis-sections ol {
  margin: 0;
  color: #444a45;
  line-height: 1.75;
}

.case-analysis-citations {
  padding: 34px 0;
}

.case-analysis-citations-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.case-analysis-citations-head h4 {
  margin: 5px 0 0;
}

.case-analysis-citations-head > strong {
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 1.7rem;
}

.case-analysis-citations details {
  border-top: 1px solid #d7dad5;
}

.case-analysis-citations details:last-of-type {
  border-bottom: 1px solid #d7dad5;
}

.case-analysis-citations summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 17px 0;
  cursor: pointer;
  list-style: none;
  color: #252825;
  font-weight: 700;
}

.case-analysis-citations summary::-webkit-details-marker {
  display: none;
}

.case-analysis-citations details[open] summary svg {
  transform: rotate(180deg);
}

.case-analysis-citations summary svg {
  transition: transform 180ms ease;
}

.case-analysis-citations details p {
  margin: 0 0 12px;
  color: #555c56;
  line-height: 1.7;
}

.case-analysis-citations details a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 18px;
  color: #252825;
}

.case-analysis-meta {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(420px, auto);
  gap: 32px;
  padding: 24px 0;
  border-top: 1px solid #c5cac4;
}

.case-analysis-meta > p {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 0;
  color: #555c56;
}

.case-analysis-meta dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  margin: 0;
}

.case-analysis-meta dl div {
  display: grid;
  gap: 4px;
}

.case-analysis-meta dt {
  color: #737a74;
  font-size: 0.72rem;
}

.case-analysis-meta dd {
  margin: 0;
  color: #303530;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 0.84rem;
}

@keyframes analysis-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 860px) {
  .case-analysis-head,
  .case-analysis-subject,
  .case-analysis-meta {
    grid-template-columns: minmax(0, 1fr);
  }

  .case-analysis-provider {
    width: max-content;
  }

  .case-analysis-subject dl {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .case-analysis-subject dl,
  .case-analysis-meta dl {
    grid-template-columns: minmax(0, 1fr);
  }

  .case-analysis-result-head,
  .case-analysis-command > div {
    align-items: stretch;
    flex-direction: column;
  }

  .evidence-pill {
    width: max-content;
  }

  .case-analysis-sections section {
    grid-template-columns: 42px minmax(0, 1fr);
    gap: 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .case-analysis-loader {
    animation: none;
  }

  .case-analysis-citations summary svg {
    transition: none;
  }
}
</style>
