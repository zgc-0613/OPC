<template>
  <div class="admin-stack admin-settings-page">
    <section class="admin-panel settings-intro-panel">
      <div>
        <span class="caption">CONTROL CENTER</span>
        <h2>账号、会话与注册安全</h2>
        <p>集中管理注册账号、验证码策略、ALTCHA、SMTP 与智能体模型。敏感密钥只写入后端加密存储，不会返回浏览器。</p>
      </div>
      <div class="settings-health-strip" aria-label="设置状态概览">
        <div>
          <Users :size="18" />
          <span>注册账号</span>
          <strong>{{ userStats.total }}</strong>
        </div>
        <div>
          <ShieldCheck :size="18" />
          <span>可用账号</span>
          <strong>{{ userStats.active }}</strong>
        </div>
        <div>
          <Mail :size="18" />
          <span>邮件投递</span>
          <strong>{{ mail.mailEnabled ? '已启用' : '未启用' }}</strong>
        </div>
      </div>
    </section>

    <nav class="settings-tabs" role="tablist" aria-label="系统设置分类">
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'accounts'"
        :class="{ active: activeTab === 'accounts' }"
        @click="activeTab = 'accounts'"
      >
        <Users :size="17" />账号与会话
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'mail'"
        :class="{ active: activeTab === 'mail' }"
        @click="activeTab = 'mail'"
      >
        <Mail :size="17" />邮件与验证码
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'captcha'"
        :class="{ active: activeTab === 'captcha' }"
        @click="activeTab = 'captcha'"
      >
        <ShieldCheck :size="17" />注册验证
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'approvals'"
        :class="{ active: activeTab === 'approvals' }"
        @click="activeTab = 'approvals'"
      >
        <UserCheck :size="17" />管理员审批
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="activeTab === 'ai'"
        :class="{ active: activeTab === 'ai' }"
        @click="activeTab = 'ai'"
      >
        <BrainCircuit :size="17" />智能体模型
      </button>
    </nav>

    <section v-if="activeTab === 'accounts'" class="admin-panel settings-panel" role="tabpanel">
      <div class="admin-section-head settings-section-head">
        <div>
          <span class="caption">REGISTERED ACCOUNTS</span>
          <h2>已注册账号</h2>
          <p>最多展示最近 200 个账号。禁用账号时会同时撤销该账号的全部登录会话。</p>
        </div>
        <button class="button button-ghost icon-text-button" type="button" :disabled="usersLoading" @click="loadUsers" title="刷新账号">
          <RefreshCw :size="16" />刷新
        </button>
      </div>

      <form class="settings-filter-row" @submit.prevent="loadUsers">
        <label>
          <span>搜索账号</span>
          <input v-model.trim="userQuery.keyword" placeholder="用户名或邮箱" />
        </label>
        <label>
          <span>账号状态</span>
          <select v-model="userQuery.status">
            <option value="">全部状态</option>
            <option value="active">正常</option>
            <option value="disabled">已禁用</option>
          </select>
        </label>
        <button class="button" type="submit" :disabled="usersLoading">查询</button>
      </form>

      <p v-if="usersNotice" class="success settings-notice account-notice" role="status">{{ usersNotice }}</p>

      <div v-if="usersLoading" class="settings-state muted" role="status">正在读取注册账号...</div>
      <div v-else-if="usersError" class="error">{{ usersError }}</div>
      <div v-else-if="!users.length" class="empty-state">
        <strong>暂无匹配账号</strong>
        <p>当前筛选条件下没有注册账号。</p>
      </div>
      <div v-else class="table-wrap settings-user-table-wrap">
        <table class="settings-user-table">
          <thead>
            <tr>
              <th>账号</th>
              <th>状态</th>
              <th>登录方式</th>
              <th>有效会话</th>
              <th>最近登录</th>
              <th>注册时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>
                <strong>{{ user.username }}</strong>
                <small>{{ user.email }}</small>
              </td>
              <td><span class="status-pill" :class="`status-pill--${user.status}`">{{ user.status === 'active' ? '正常' : '已禁用' }}</span></td>
              <td>
                <span class="status-pill" :class="user.passwordConfigured ? 'status-pill--active' : 'status-pill--pending'">
                  {{ user.passwordConfigured ? '密码登录' : '待设置密码' }}
                </span>
              </td>
              <td>{{ user.activeSessionCount || 0 }}</td>
              <td>{{ formatDate(user.lastLoginAt) }}</td>
              <td>{{ formatDate(user.createdAt) }}</td>
              <td>
                <div class="row-actions settings-row-actions">
                  <button type="button" :disabled="userActionId === user.id" @click="toggleUserStatus(user)">
                    <component :is="user.status === 'active' ? Ban : CheckCircle" :size="14" />
                    {{ user.status === 'active' ? '禁用' : '启用' }}
                  </button>
                  <button type="button" :disabled="userActionId === user.id" @click="revokeSessions(user)">
                    <LogOut :size="14" />撤销会话
                  </button>
                  <button class="danger settings-delete-button" type="button" :disabled="userActionId === user.id" @click="removeUser(user)">
                    <Trash2 :size="14" />删除账号
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-else-if="activeTab === 'mail'" class="settings-mail-workspace" role="tabpanel">
      <form class="admin-panel settings-panel mail-settings-panel" @submit.prevent="saveMailSettings">
        <div class="admin-section-head settings-section-head">
          <div>
            <span class="caption">SMTP DELIVERY</span>
            <h2>SMTP 投递</h2>
            <p>字段参考现有推送中心：支持密码保留/清除、连接测试和未保存配置测试。</p>
          </div>
          <span class="status-pill">{{ mail.passwordConfigured ? '密码已配置' : '需要密码' }}</span>
        </div>

        <label class="settings-toggle-row">
          <input v-model="mail.mailEnabled" type="checkbox" />
          <span>
            <strong>启用真实邮件发送</strong>
            <small>关闭时验证码会继续以开发模式返回，便于本地联调。</small>
          </span>
        </label>

        <div class="settings-form-grid">
          <label>
            <span>站点名称</span>
            <input v-model.trim="mail.siteName" placeholder="SoloFirm" />
          </label>
          <label>
            <span>SMTP 主机</span>
            <input v-model.trim="mail.host" placeholder="smtp.qq.com" />
          </label>
          <label>
            <span>SMTP 端口</span>
            <input v-model.number="mail.port" type="number" min="1" max="65535" />
          </label>
          <label>
            <span>安全模式</span>
            <select v-model="mail.securityMode">
              <option value="ssl">SSL</option>
              <option value="starttls">STARTTLS</option>
              <option value="plain">无加密</option>
            </select>
          </label>
          <label>
            <span>SMTP 用户名</span>
            <input v-model.trim="mail.username" autocomplete="username" placeholder="邮箱账号或 API 用户名" />
          </label>
          <label>
            <span>SMTP 密码</span>
            <input v-model="mail.password" type="password" autocomplete="new-password" placeholder="留空保留当前值" />
            <small>{{ mail.passwordConfigured ? '已配置，留空不会覆盖。' : '尚未配置密码。' }}</small>
          </label>
          <label>
            <span>发件人邮箱</span>
            <input v-model.trim="mail.fromEmail" type="email" placeholder="no-reply@example.com" />
          </label>
          <label>
            <span>发件人名称</span>
            <input v-model.trim="mail.fromName" placeholder="SoloFirm" />
          </label>
          <label>
            <span>连接超时（秒）</span>
            <input v-model.number="mail.timeoutSeconds" type="number" min="1" max="60" />
          </label>
          <label class="settings-clear-password">
            <input v-model="mail.clearPassword" type="checkbox" />
            <span>清除当前 SMTP 密码</span>
          </label>
        </div>

        <div class="settings-action-bar">
          <button class="button icon-text-button" type="submit" :disabled="mailSaving">
            <Save :size="16" />{{ mailSaving ? '保存中...' : '保存 SMTP 设置' }}
          </button>
          <button class="button button-ghost icon-text-button" type="button" :disabled="mailTesting" @click="testConnection">
            <PlugZap :size="16" />{{ mailTesting ? '测试中...' : '测试连接' }}
          </button>
        </div>
      </form>

      <section class="admin-panel settings-panel auth-policy-panel">
        <div class="admin-section-head settings-section-head">
          <div>
            <span class="caption">AUTH POLICY</span>
            <h2>验证码与会话</h2>
            <p>调整验证码有效期、再次发送间隔和用户登录会话周期。</p>
          </div>
        </div>
        <div class="settings-form-grid three-up">
          <label>
            <span>验证码有效期（分钟）</span>
            <input v-model.number="mail.verificationCodeMinutes" type="number" min="1" max="60" />
          </label>
          <label>
            <span>再次发送间隔（秒）</span>
            <input v-model.number="mail.resendIntervalSeconds" type="number" min="10" max="3600" />
          </label>
          <label>
            <span>登录会话周期（天）</span>
            <input v-model.number="mail.sessionDays" type="number" min="1" max="365" />
          </label>
        </div>
      </section>

      <section class="admin-panel settings-panel mail-template-panel">
        <div class="admin-section-head settings-section-head">
          <div>
            <span class="caption">VERIFICATION TEMPLATE</span>
            <h2>验证码邮件模板</h2>
            <p v-pre>可使用 {{site_name}}、{{code}}、{{expires_minutes}}。</p>
          </div>
        </div>
        <div class="template-workbench">
          <div class="template-editor">
            <label>
              <span>邮件主题</span>
              <input v-model="mail.verificationSubject" placeholder="[{{site_name}}] 邮箱验证码" />
            </label>
            <label>
              <span>HTML 模板</span>
              <textarea v-model="mail.verificationHtml" rows="14" spellcheck="false"></textarea>
            </label>
          </div>
          <div class="template-preview">
            <span>主题预览</span>
            <strong>{{ previewSubject }}</strong>
            <iframe :srcdoc="previewHtml" title="验证码邮件模板预览" sandbox=""></iframe>
          </div>
        </div>
      </section>

      <section class="admin-panel settings-panel test-mail-panel">
        <div class="admin-section-head settings-section-head">
          <div>
            <span class="caption">DELIVERY TEST</span>
            <h2>发送测试邮件</h2>
            <p>使用当前表单中的 SMTP 和模板设置发送，不要求先保存。</p>
          </div>
        </div>
        <form class="settings-test-row" @submit.prevent="sendTestEmail">
          <label>
            <span>测试收件人</span>
            <input v-model.trim="testRecipient" type="email" required placeholder="test@example.com" />
          </label>
          <button class="button icon-text-button" type="submit" :disabled="testSending">
            <Send :size="16" />{{ testSending ? '发送中...' : '发送测试邮件' }}
          </button>
        </form>
      </section>

      <p v-if="mailNotice" class="success settings-notice" role="status">{{ mailNotice }}</p>
      <p v-if="mailError" class="error settings-notice" role="alert">{{ mailError }}</p>
    </section>

    <section v-else-if="activeTab === 'captcha'" class="admin-panel settings-panel captcha-settings-panel" role="tabpanel">
      <form @submit.prevent="saveCaptchaSettings">
        <div class="admin-section-head settings-section-head">
          <div>
            <span class="caption">ALTCHA PROOF OF WORK</span>
            <h2>注册人机验证</h2>
            <p>在创建账号并发送邮箱验证码前，由浏览器完成自托管工作量证明。登录流程不受影响。</p>
          </div>
          <span class="status-pill">{{ captcha.secretConfigured ? '服务器密钥已配置' : '服务器密钥缺失' }}</span>
        </div>

        <label class="settings-toggle-row">
          <input v-model="captcha.enabled" type="checkbox" :disabled="!captcha.secretConfigured" />
          <span>
            <strong>启用注册 ALTCHA</strong>
            <small>启用后，未通过证明的请求无法发送注册验证码。</small>
          </span>
        </label>

        <div class="settings-form-grid three-up">
          <label>
            <span>算法</span>
            <input :value="captcha.algorithm" readonly />
          </label>
          <label>
            <span>计算成本</span>
            <input v-model.number="captcha.cost" type="number" min="1000" max="50000" step="500" />
          </label>
          <label>
            <span>挑战有效期（秒）</span>
            <input v-model.number="captcha.expiresInSeconds" type="number" min="60" max="900" step="30" />
          </label>
        </div>

        <div class="settings-action-bar">
          <button class="button icon-text-button" type="submit" :disabled="captchaSaving">
            <Save :size="16" />{{ captchaSaving ? '保存中...' : '保存注册验证设置' }}
          </button>
        </div>
      </form>

      <p v-if="captchaNotice" class="success settings-notice" role="status">{{ captchaNotice }}</p>
      <p v-if="captchaError" class="error settings-notice" role="alert">{{ captchaError }}</p>
    </section>

    <section v-else-if="activeTab === 'ai'" class="admin-panel settings-panel ai-settings-panel" role="tabpanel">
      <form @submit.prevent="saveAiSettings">
        <div class="admin-section-head settings-section-head">
          <div>
            <span class="caption">AGENT MODEL</span>
            <h2>智能体模型</h2>
            <p>案例分析由后端调用模型。API Key 使用 AES-GCM 加密，保存后只显示配置状态。</p>
          </div>
          <span class="status-pill" :class="ai.enabled ? 'status-pill--active' : 'status-pill--pending'">
            {{ ai.enabled ? '已启用' : '未启用' }}
          </span>
        </div>

        <div v-if="aiLoading" class="settings-state muted" role="status">正在读取模型配置...</div>
        <template v-else>
          <label class="settings-toggle-row">
            <input v-model="ai.enabled" type="checkbox" />
            <span>
              <strong>启用案例分析模型</strong>
              <small>只有配置完整且连接可用时再启用；生产环境不会回退到测试模型。</small>
            </span>
          </label>

          <div class="settings-form-grid ai-settings-grid">
            <label>
              <span>供应商预设</span>
              <select v-model="ai.provider" @change="applyAiProviderPreset({ forceBaseUrl: true })">
                <option value="deepseek">DeepSeek</option>
              </select>
            </label>
            <label>
              <span>接口格式</span>
              <select v-model="ai.apiFormat">
                <option value="openai_responses">OpenAI Responses</option>
                <option value="openai_compatible">OpenAI Compatible</option>
                <option value="anthropic">Anthropic</option>
                <option value="amazon_bedrock">Amazon Bedrock</option>
                <option value="google_gemini">Google (Gemini)</option>
              </select>
              <small v-if="ai.apiFormat !== 'openai_compatible'">第一阶段仅支持保存该格式；启用和连接测试需要 OpenAI Compatible。</small>
            </label>
            <label class="ai-field-wide">
              <span>API Base URL</span>
              <input v-model.trim="ai.apiBaseUrl" :required="ai.enabled" placeholder="https://your-provider.example/v1" />
            </label>
            <label class="ai-field-wide">
              <span>API Key</span>
              <input v-model="ai.apiKey" type="password" autocomplete="new-password" placeholder="留空保留当前密钥" />
              <small>{{ ai.apiKeyConfigured ? '密钥已加密配置。留空时获取模型会使用已保存密钥，保存时不会覆盖。' : '先填写密钥即可获取模型，只有点击保存配置后才会加密入库。' }}</small>
            </label>
          </div>

          <div class="ai-model-catalog">
            <div class="ai-model-catalog-head">
              <div>
                <span class="caption">MODEL CATALOG</span>
                <h3>模型配置</h3>
                <p>读取供应商模型或填写官方 Model ID，默认模型用于连接测试和智能体请求。</p>
              </div>
              <div class="ai-model-catalog-actions">
                <button
                  class="button button-ghost icon-text-button"
                  type="button"
                  :disabled="!canDiscoverAiModels || aiModelDiscovering"
                  @click="loadAiModels"
                >
                  <Download :size="16" />{{ aiModelDiscovering ? '获取中...' : '获取模型列表' }}
                </button>
                <button class="button button-ghost icon-text-button" type="button" @click="startManualAiModel">
                  <Plus :size="16" />手动填写
                </button>
              </div>
            </div>

            <div class="ai-model-selector-grid">
              <div class="ai-model-selector-field">
                <label id="ai-model-id-label" for="ai-model-id">Model ID</label>
                <div class="ai-model-combobox" @focusout="closeAiModelDropdown">
                  <input
                    id="ai-model-id"
                    ref="aiModelIdInput"
                    v-model.trim="ai.modelId"
                    role="combobox"
                    aria-labelledby="ai-model-id-label"
                    aria-controls="ai-model-options"
                    aria-autocomplete="list"
                    :aria-expanded="aiModelDropdownOpen"
                    autocomplete="off"
                    placeholder="选择供应商模型或填写官方 Model ID"
                    @focus="openAiModelDropdown"
                    @input="handleAiModelInput"
                    @keydown.down.prevent="openAiModelDropdown"
                    @keydown.esc="aiModelDropdownOpen = false"
                  />
                  <button
                    type="button"
                    :aria-label="aiModelDropdownOpen ? '收起模型列表' : '展开模型列表'"
                    :aria-expanded="aiModelDropdownOpen"
                    aria-controls="ai-model-options"
                    @click="toggleAiModelDropdown"
                  >
                    <ChevronDown :size="18" />
                  </button>

                  <div v-if="aiModelDropdownOpen" id="ai-model-options" class="ai-model-options" role="listbox">
                    <button
                      v-for="model in filteredAiModels"
                      :key="model.clientId"
                      type="button"
                      role="option"
                      :aria-selected="model.modelId === ai.modelId"
                      @click="selectAiModel(model)"
                    >
                      <span>
                        <strong>{{ model.modelId }}</strong>
                        <small v-if="model.displayName && model.displayName !== model.modelId">{{ model.displayName }}</small>
                      </span>
                      <CheckCircle v-if="model.modelId === ai.modelId" :size="16" />
                    </button>
                    <div v-if="!filteredAiModels.length" class="ai-model-options-empty">
                      {{ ai.models.length ? '没有匹配的供应商模型，可直接使用当前输入值。' : '尚未获取供应商模型，可直接填写官方 Model ID。' }}
                    </div>
                  </div>
                </div>
                <small>{{ ai.models.length ? `已载入 ${ai.models.length} 个供应商模型，可下拉选择或继续输入。` : '填写 API Key 后获取模型列表，也可以直接输入官方 Model ID。' }}</small>
              </div>

              <label class="ai-model-display-field">
                <span>显示名称</span>
                <input v-model.trim="ai.modelDisplayName" autocomplete="off" placeholder="用于管理界面识别" />
                <small>仅用于 SoloFirm 管理界面显示，不会修改供应商模型。</small>
              </label>
            </div>
          </div>

          <div class="ai-runtime-band" aria-label="模型运行参数">
            <label class="ai-temperature-field">
              <span>Temperature <output>{{ Number(ai.temperature).toFixed(1) }}</output></span>
              <div class="ai-range-control">
                <input v-model.number="ai.temperature" type="range" min="0" max="2" step="0.1" aria-label="Temperature" />
                <div class="ai-range-scale" aria-hidden="true">
                  <span>0</span>
                  <span>1</span>
                  <span>2</span>
                </div>
              </div>
              <small>案例分析建议 0.2，结果更稳定且便于复核。</small>
            </label>
            <label>
              <span>最大输出词元数</span>
              <input v-model.number="ai.maxOutputTokens" type="number" min="1" max="65536" />
            </label>
            <label>
              <span>请求超时（秒）</span>
              <input v-model.number="ai.timeoutSeconds" type="number" min="1" max="180" />
            </label>
            <label>
              <span>失败重试次数</span>
              <input v-model.number="ai.retryCount" type="number" min="0" max="5" />
            </label>
            <label class="ai-daily-quota-field">
              <span>单用户每日词元额度</span>
              <input v-model.number="ai.dailyTokenQuota" type="number" min="0" step="1" />
              <small>填写 0 表示不限制每日词元用量。</small>
            </label>
          </div>

          <div class="ai-connection-state" :class="`is-${ai.lastTestStatus || 'not_tested'}`">
            <Activity :size="18" />
            <div>
              <strong>{{ aiTestLabel }}</strong>
              <small>{{ ai.lastTestMessage || '保存配置后可发起一个低成本连接请求。' }}</small>
            </div>
            <time>{{ formatDate(ai.lastTestedAt) }}</time>
          </div>

          <p v-if="!ai.encryptionReady" class="error settings-notice" role="alert">
            服务器尚未配置 OPC_AI_SETTINGS_MASTER_KEY，当前只能查看设置，不能安全写入或启用 API Key。
          </p>

          <div class="settings-action-bar">
            <button
              class="button icon-text-button"
              type="submit"
              :disabled="aiSaving || (ai.enabled && !ai.modelId) || (!ai.encryptionReady && Boolean(ai.apiKey))"
            >
              <Save :size="16" />{{ aiSaving ? '保存中...' : '保存模型配置' }}
            </button>
            <button
              class="button button-ghost icon-text-button"
              type="button"
              :disabled="aiTesting || !ai.apiKeyConfigured || !ai.apiBaseUrl || !ai.modelId"
              @click="testAiConnection"
            >
              <PlugZap :size="16" />{{ aiTesting ? '测试中...' : '测试连接' }}
            </button>
          </div>
        </template>
      </form>

      <p v-if="aiNotice" class="success settings-notice" role="status">{{ aiNotice }}</p>
      <p v-if="aiError" class="error settings-notice" role="alert">{{ aiError }}</p>
    </section>

    <section v-else class="admin-panel settings-panel admin-approval-panel" role="tabpanel">
      <div class="admin-section-head settings-section-head">
        <div>
          <span class="caption">ADMIN ACCESS REVIEW</span>
          <h2>管理员注册审批</h2>
          <p>新管理员只能提交用户名和密码申请。批准后账号才可登录；所有批准与拒绝记录都会保留，并标记实际操作的管理员。</p>
        </div>
        <button
          class="button button-ghost icon-text-button"
          type="button"
          :disabled="approvalLoading"
          title="刷新管理员审批"
          @click="loadAdminApprovals"
        >
          <RefreshCw :size="16" />{{ approvalLoading ? '刷新中...' : '刷新' }}
        </button>
      </div>

      <div v-if="approvalNotice" class="approval-notice-slot">
        <p class="success settings-notice approval-notice" role="status">{{ approvalNotice }}</p>
      </div>

      <section class="approval-section" aria-labelledby="pending-admin-heading">
        <div class="approval-section-head">
          <div>
            <span class="caption">PENDING REQUESTS</span>
            <h3 id="pending-admin-heading">待审批申请</h3>
          </div>
          <span class="approval-count" aria-label="待审批数量">{{ pendingAdminRequests.length }}</span>
        </div>

        <div v-if="approvalLoading" class="settings-state muted" role="status">正在读取管理员注册申请...</div>
        <div v-else-if="approvalError" class="approval-error" role="alert">
          <strong>申请列表读取失败</strong>
          <p>{{ approvalError }}</p>
          <button class="button button-ghost" type="button" @click="loadAdminApprovals">重新加载</button>
        </div>
        <div v-else-if="!pendingAdminRequests.length" class="empty-state">
          <strong>暂无待审批申请</strong>
          <p>新的管理员注册申请提交后会显示在这里。</p>
        </div>
        <div v-else class="table-wrap settings-user-table-wrap">
          <table class="settings-user-table approval-table">
            <thead>
              <tr>
                <th>申请账号</th>
                <th>状态</th>
                <th>提交时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="requestItem in pendingAdminRequests" :key="requestItem.id">
                <td><strong>{{ requestItem.username }}</strong></td>
                <td><span class="status-pill status-pill--pending">待审批</span></td>
                <td>{{ formatDate(requestItem.createdAt) }}</td>
                <td>
                  <div class="row-actions settings-row-actions approval-actions">
                    <button
                      type="button"
                      :disabled="approvalActionId === requestItem.id"
                      @click="approveRequest(requestItem)"
                    >
                      <CheckCircle :size="14" />
                      {{ approvalActionId === requestItem.id ? '处理中...' : '批准' }}
                    </button>
                    <button
                      class="approval-reject-button"
                      type="button"
                      :disabled="approvalActionId === requestItem.id"
                      @click="rejectRequest(requestItem)"
                    >
                      <XCircle :size="14" />拒绝
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="approval-section approval-history-section" aria-labelledby="approval-history-heading">
        <div class="approval-section-head">
          <div>
            <span class="caption">REVIEW HISTORY</span>
            <h3 id="approval-history-heading">审批记录</h3>
          </div>
          <span class="approval-count" aria-label="审批记录数量">{{ reviewedAdminRequests.length }}</span>
        </div>

        <div v-if="approvalLoading" class="settings-state muted" role="status">正在读取审批记录...</div>
        <div v-else-if="approvalError" class="approval-error" role="alert">
          <strong>审批记录读取失败</strong>
          <p>{{ approvalError }}</p>
        </div>
        <div v-else-if="!reviewedAdminRequests.length" class="empty-state">
          <strong>暂无审批记录</strong>
          <p>管理员批准或拒绝申请后，记录会永久保留在这里。</p>
        </div>
        <div v-else class="table-wrap settings-user-table-wrap">
          <table class="settings-user-table approval-table approval-history-table">
            <thead>
              <tr>
                <th>申请账号</th>
                <th>审批结果</th>
                <th>操作管理员</th>
                <th>提交时间</th>
                <th>处理时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="requestItem in reviewedAdminRequests" :key="requestItem.id">
                <td><strong>{{ requestItem.username }}</strong></td>
                <td>
                  <span
                    class="status-pill"
                    :class="requestItem.status === 'approved' ? 'status-pill--approved' : 'status-pill--rejected'"
                  >
                    {{ requestItem.status === 'approved' ? '已批准' : '已拒绝' }}
                  </span>
                </td>
                <td>
                  <div class="approval-reviewer">
                    <strong>{{ reviewerName(requestItem) }}</strong>
                    <small v-if="requestItem.reviewedBy">管理员 ID {{ requestItem.reviewedBy }}</small>
                  </div>
                </td>
                <td>{{ formatDate(requestItem.createdAt) }}</td>
                <td>{{ formatDate(requestItem.reviewedAt) }}</td>
                <td>
                  <div class="row-actions settings-row-actions approval-history-actions">
                    <button
                      class="danger settings-delete-button"
                      type="button"
                      :disabled="approvalHistoryActionId === requestItem.id"
                      @click="removeApprovalRecord(requestItem)"
                    >
                      <Trash2 :size="14" />{{ approvalHistoryActionId === requestItem.id ? '删除中...' : '删除记录' }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="approval-section admin-account-section" aria-labelledby="admin-account-heading">
        <div class="approval-section-head">
          <div>
            <span class="caption">APPROVED ACCOUNTS</span>
            <h3 id="admin-account-heading">现有管理员</h3>
          </div>
          <span class="approval-count" aria-label="管理员数量">{{ adminAccounts.length }}</span>
        </div>

        <div v-if="approvalLoading" class="settings-state muted" role="status">正在读取管理员账号...</div>
        <div v-else-if="adminAccountsError" class="approval-error" role="alert">
          <strong>管理员列表读取失败</strong>
          <p>{{ adminAccountsError }}</p>
        </div>
        <div v-else-if="!adminAccounts.length" class="empty-state">
          <strong>暂无可用管理员</strong>
          <p>批准注册申请后，管理员账号会显示在这里。</p>
        </div>
        <div v-else class="table-wrap settings-user-table-wrap">
          <table class="settings-user-table approval-table">
            <thead>
              <tr>
                <th>管理员账号</th>
                <th>状态</th>
                <th>最近登录</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="account in adminAccounts" :key="account.id">
                <td><strong>{{ account.username }}</strong></td>
                <td>
                  <span class="status-pill" :class="account.status === 'active' ? 'status-pill--active' : 'status-pill--disabled'">
                    {{ account.status === 'active' ? '正常' : '已停用' }}
                  </span>
                </td>
                <td>{{ formatDate(account.lastLoginAt) }}</td>
                <td>{{ formatDate(account.createdAt) }}</td>
                <td>
                  <div class="row-actions settings-row-actions admin-account-actions">
                    <button
                      class="danger settings-delete-button"
                      type="button"
                      :disabled="adminAccountActionId === account.id || !canDeleteAdminAccount(account)"
                      :title="adminDeleteTitle(account)"
                      @click="removeAdminAccount(account)"
                    >
                      <Trash2 :size="14" />{{ account.username === currentAdminUsername ? '当前账号' : '删除账号' }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  Activity,
  Ban,
  BrainCircuit,
  CheckCircle,
  ChevronDown,
  Download,
  LogOut,
  Mail,
  Plus,
  PlugZap,
  RefreshCw,
  Save,
  Send,
  ShieldCheck,
  Trash2,
  UserCheck,
  Users,
  XCircle,
} from 'lucide-vue-next'
import {
  approveAdminRegistration,
  deleteAdminAccount,
  deleteAdminRegistrationRecord,
  deleteAdminUser,
  discoverAiModels,
  getAdminAccounts,
  getAdminRegistrationRequests,
  getAdminUsers,
  getCaptchaSettings,
  getAiSettings,
  getMailSettings,
  rejectAdminRegistration,
  revokeAdminUserSessions,
  sendMailTest,
  testMailConnection,
  testAiConnection as testAiConnectionApi,
  updateAdminUserStatus,
  updateCaptchaSettings,
  updateAiSettings,
  updateMailSettings,
} from '@/api/adminSettings'
import { getAdminUsername } from '@/api/auth'

const activeTab = ref('accounts')
const users = ref([])
const usersLoading = ref(false)
const usersError = ref('')
const usersNotice = ref('')
const userActionId = ref(null)
const mailSaving = ref(false)
const mailTesting = ref(false)
const testSending = ref(false)
const mailError = ref('')
const mailNotice = ref('')
const testRecipient = ref('')
const captchaSaving = ref(false)
const captchaError = ref('')
const captchaNotice = ref('')
const aiLoading = ref(false)
const aiSaving = ref(false)
const aiTesting = ref(false)
const aiModelDiscovering = ref(false)
const aiModelDropdownOpen = ref(false)
const aiModelSearch = ref('')
const aiModelIdInput = ref(null)
const aiError = ref('')
const aiNotice = ref('')
const adminRequests = ref([])
const adminAccounts = ref([])
const approvalLoading = ref(false)
const approvalError = ref('')
const adminAccountsError = ref('')
const approvalNotice = ref('')
const approvalActionId = ref(null)
const approvalHistoryActionId = ref(null)
const adminAccountActionId = ref(null)
const currentAdminUsername = getAdminUsername()

const userQuery = reactive({
  keyword: '',
  status: '',
})

const DEFAULT_VERIFICATION_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>SoloFirm 邮箱验证码</title>
</head>
<body style="margin:0;padding:24px;background:#eef0eb;color:#181a18;font-family:Georgia,Times New Roman,serif;">
  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">你的 SoloFirm 注册验证码是 {{code}}</div>
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="width:100%;background:#eef0eb;border-collapse:collapse;">
    <tr><td align="center">
      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="width:100%;max-width:640px;overflow:hidden;border:1px solid #cfd3ce;border-radius:8px;background:#fbfbf8;border-collapse:separate;">
        <tr><td style="padding:26px 30px;border-bottom:1px solid #d7dad5;">
          <table role="presentation" cellspacing="0" cellpadding="0" style="border-collapse:collapse;"><tr>
            <td width="64" style="width:64px;vertical-align:middle;"><img src="cid:solofirm-logo" width="52" height="52" alt="SoloFirm" style="display:block;width:52px;height:52px;border:0;border-radius:14px;"></td>
            <td style="vertical-align:middle;"><div style="color:#181a18;font-family:Bookman Old Style,Georgia,serif;font-size:23px;font-weight:700;line-height:1.1;">SoloFirm</div><div style="margin-top:5px;color:#646a65;font-size:12px;line-height:1.2;">{{site_name}} / ACCOUNT VERIFICATION</div></td>
          </tr></table>
        </td></tr>
        <tr><td style="padding:34px 30px 30px;">
          <div style="color:#555c56;font-size:12px;font-weight:700;line-height:1.4;">邮箱安全验证</div>
          <h1 style="margin:9px 0 14px;color:#181a18;font-size:30px;font-weight:500;line-height:1.2;">验证你的邮箱</h1>
          <p style="margin:0 0 22px;color:#4f5650;font-size:15px;line-height:1.75;">你正在登录 {{site_name}}。请使用下面的验证码完成验证：</p>
          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="width:100%;border-radius:6px;background:#181a18;border-collapse:separate;"><tr><td align="center" style="padding:24px 18px;"><div style="margin-bottom:9px;color:#aeb4ae;font-size:11px;line-height:1.2;">VERIFICATION CODE</div><div style="color:#fbfbf8;font-family:Bookman Old Style,Georgia,serif;font-size:36px;font-weight:700;line-height:1;letter-spacing:7px;">{{code}}</div></td></tr></table>
          <p style="margin:22px 0 0;color:#343a35;font-size:14px;line-height:1.7;">验证码将在 <strong>{{expires_minutes}} 分钟</strong>后失效，请勿转发给他人。</p>
          <p style="margin:8px 0 0;color:#737a74;font-size:13px;line-height:1.7;">如果不是你本人操作，可以忽略这封邮件。</p>
        </td></tr>
        <tr><td style="padding:18px 30px;border-top:1px solid #d7dad5;background:#f1f2ee;color:#747b75;font-size:12px;line-height:1.6;">此邮件由 SoloFirm 自动发送，请勿直接回复。</td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`

const mail = reactive({
  mailEnabled: false,
  siteName: 'SoloFirm',
  host: 'smtp.qq.com',
  port: 465,
  username: '',
  password: '',
  passwordConfigured: false,
  clearPassword: false,
  fromEmail: '',
  fromName: 'SoloFirm',
  securityMode: 'ssl',
  timeoutSeconds: 12,
  verificationCodeMinutes: 10,
  resendIntervalSeconds: 60,
  sessionDays: 30,
  verificationSubject: '[{{site_name}}] 邮箱验证码',
  verificationHtml: DEFAULT_VERIFICATION_HTML,
})

const captcha = reactive({
  enabled: false,
  algorithm: 'PBKDF2/SHA-256',
  cost: 5000,
  expiresInSeconds: 300,
  secretConfigured: false,
})

const AI_PROVIDER_PRESETS = Object.freeze({
  deepseek: {
    apiFormat: 'openai_compatible',
    apiBaseUrl: 'https://api.deepseek.com/v1',
  },
})

const ai = reactive({
  provider: 'deepseek',
  apiFormat: 'openai_compatible',
  apiBaseUrl: AI_PROVIDER_PRESETS.deepseek.apiBaseUrl,
  modelId: '',
  modelDisplayName: '',
  models: [],
  apiKey: '',
  apiKeyConfigured: false,
  encryptionReady: false,
  temperature: 0.2,
  maxOutputTokens: 1200,
  timeoutSeconds: 30,
  retryCount: 1,
  dailyTokenQuota: 100000,
  enabled: false,
  lastTestStatus: 'not_tested',
  lastTestedAt: null,
  lastTestMessage: '',
})

const userStats = computed(() => ({
  total: users.value.length,
  active: users.value.filter((user) => user.status === 'active').length,
}))
const activeAdminCount = computed(() => adminAccounts.value.filter((account) => account.status === 'active').length)
const pendingAdminRequests = computed(() => adminRequests.value.filter((requestItem) => requestItem.status === 'pending'))
const reviewedAdminRequests = computed(() => adminRequests.value
  .filter((requestItem) => requestItem.status === 'approved' || requestItem.status === 'rejected')
  .sort((left, right) => new Date(right.reviewedAt || 0) - new Date(left.reviewedAt || 0)))

const previewSubject = computed(() => renderTemplate(mail.verificationSubject || ''))
const previewHtml = computed(() => renderTemplate(mail.verificationHtml || '').replaceAll('cid:solofirm-logo', `${window.location.origin}/favicon.svg`))
const aiTestLabel = computed(() => {
  if (ai.lastTestStatus === 'success') return '最近连接成功'
  if (ai.lastTestStatus === 'failed') return '最近连接失败'
  return '尚未测试连接'
})
const canDiscoverAiModels = computed(() => (
  Boolean(ai.apiBaseUrl)
  && ai.apiFormat === 'openai_compatible'
  && (Boolean(ai.apiKey.trim()) || ai.apiKeyConfigured)
))
const filteredAiModels = computed(() => {
  const query = aiModelSearch.value.trim().toLowerCase()
  if (!query) return ai.models
  return ai.models.filter((model) => (
    model.modelId.toLowerCase().includes(query)
    || model.displayName.toLowerCase().includes(query)
  ))
})

let aiModelRowSequence = 0

onMounted(() => {
  loadUsers()
  loadMailSettings()
  loadCaptchaSettings()
  loadAiSettings()
  loadAdminApprovals()
})

async function loadAiSettings() {
  aiLoading.value = true
  aiError.value = ''
  try {
    applyAiSettingsState(await getAiSettings())
    applyAiProviderPreset()
  } catch (err) {
    aiError.value = err.message || '智能体模型配置暂时无法读取。'
  } finally {
    aiLoading.value = false
  }
}

function applyAiSettingsState(settings) {
  const modelId = settings?.modelId || ''
  const sourceModels = Array.isArray(settings?.models) ? settings.models : []
  const models = sourceModels.length
    ? sourceModels
    : (modelId ? [{ modelId, displayName: modelId }] : [])
  Object.assign(ai, settings, {
    apiKey: '',
    modelId,
    modelDisplayName: models.find((model) => model.modelId === modelId)?.displayName || modelId,
    models: models.map(createAiModelRow),
  })
}

function createAiModelRow(model = {}) {
  aiModelRowSequence += 1
  return {
    clientId: `ai-model-${aiModelRowSequence}`,
    modelId: String(model.modelId || ''),
    displayName: String(model.displayName || model.modelId || ''),
  }
}

function serializeAiModels() {
  const unique = new Map()
  ai.models.forEach((model) => {
    const modelId = String(model.modelId || '').trim()
    if (!modelId || unique.has(modelId)) return
    unique.set(modelId, {
      modelId,
      displayName: String(model.displayName || '').trim() || modelId,
    })
  })
  const activeModelId = String(ai.modelId || '').trim()
  if (activeModelId) {
    unique.set(activeModelId, {
      modelId: activeModelId,
      displayName: String(ai.modelDisplayName || '').trim() || activeModelId,
    })
  }
  return [...unique.values()]
}

function applyAiProviderPreset({ forceBaseUrl = false } = {}) {
  const preset = AI_PROVIDER_PRESETS[ai.provider]
  if (!preset) return
  ai.apiFormat = preset.apiFormat
  if (forceBaseUrl || !ai.apiBaseUrl) {
    ai.apiBaseUrl = preset.apiBaseUrl
  }
}

async function loadAiModels() {
  if (!canDiscoverAiModels.value) return
  aiModelDiscovering.value = true
  aiNotice.value = ''
  aiError.value = ''
  try {
    const payload = {
      provider: ai.provider,
      apiFormat: ai.apiFormat,
      apiBaseUrl: ai.apiBaseUrl,
      timeoutSeconds: ai.timeoutSeconds,
    }
    if (ai.apiKey.trim()) payload.apiKey = ai.apiKey
    const discoveredModels = await discoverAiModels(payload)
    const merged = new Map(serializeAiModels().map((model) => [model.modelId, model]))
    ;(discoveredModels || []).forEach((model) => {
      const modelId = String(model?.modelId || '').trim()
      if (!modelId) return
      const existing = merged.get(modelId)
      merged.set(modelId, {
        modelId,
        displayName: existing?.displayName || String(model.displayName || '').trim() || modelId,
      })
    })
    ai.models = [...merged.values()].map(createAiModelRow)
    if (!ai.models.some((model) => model.modelId === ai.modelId)) {
      ai.modelId = ai.models[0]?.modelId || ''
    }
    const activeModel = ai.models.find((model) => model.modelId === ai.modelId)
    ai.modelDisplayName = activeModel?.displayName || ai.modelId
    aiModelSearch.value = ''
    aiNotice.value = discoveredModels?.length
      ? `已从供应商读取 ${discoveredModels.length} 个模型。选择默认模型后保存配置。`
      : '供应商未返回可用模型，可手工添加准确的 Model ID。'
  } catch (err) {
    aiError.value = err.message || '模型列表获取失败。'
  } finally {
    aiModelDiscovering.value = false
  }
}

function openAiModelDropdown() {
  aiModelSearch.value = ''
  aiModelDropdownOpen.value = true
}

function toggleAiModelDropdown() {
  aiModelSearch.value = ''
  aiModelDropdownOpen.value = !aiModelDropdownOpen.value
}

function closeAiModelDropdown(event) {
  if (event.currentTarget.contains(event.relatedTarget)) return
  aiModelDropdownOpen.value = false
}

function handleAiModelInput() {
  aiModelSearch.value = ai.modelId
  aiModelDropdownOpen.value = true
  const matchedModel = ai.models.find((model) => model.modelId === ai.modelId)
  ai.modelDisplayName = matchedModel?.displayName || ai.modelId
}

function selectAiModel(model) {
  ai.modelId = model.modelId
  ai.modelDisplayName = model.displayName || model.modelId
  aiModelSearch.value = ''
  aiModelDropdownOpen.value = false
}

async function startManualAiModel() {
  ai.modelId = ''
  ai.modelDisplayName = ''
  aiModelSearch.value = ''
  aiModelDropdownOpen.value = true
  await nextTick()
  aiModelIdInput.value?.focus()
}

async function saveAiSettings() {
  aiSaving.value = true
  aiNotice.value = ''
  aiError.value = ''
  try {
    const models = serializeAiModels()
    if (ai.enabled && !ai.modelId) {
      aiError.value = '启用智能体模型前，请先获取或添加模型并选择默认模型。'
      return
    }
    const payload = {
      provider: ai.provider,
      apiFormat: ai.apiFormat,
      apiBaseUrl: ai.apiBaseUrl,
      modelId: ai.modelId,
      models,
      temperature: ai.temperature,
      maxOutputTokens: ai.maxOutputTokens,
      timeoutSeconds: ai.timeoutSeconds,
      retryCount: ai.retryCount,
      dailyTokenQuota: ai.dailyTokenQuota,
      enabled: ai.enabled,
    }
    if (ai.apiKey) payload.apiKey = ai.apiKey
    applyAiSettingsState(await updateAiSettings(payload))
    aiNotice.value = '智能体模型配置已保存。'
  } catch (err) {
    aiError.value = err.message || '智能体模型配置保存失败。'
  } finally {
    aiSaving.value = false
  }
}

async function testAiConnection() {
  aiTesting.value = true
  aiNotice.value = ''
  aiError.value = ''
  try {
    const result = await testAiConnectionApi()
    ai.lastTestStatus = result.success ? 'success' : 'failed'
    ai.lastTestMessage = result.message
    ai.lastTestedAt = result.testedAt
    aiNotice.value = result.message
  } catch (err) {
    aiError.value = err.message || '模型连接测试失败。'
    await loadAiSettings()
  } finally {
    aiTesting.value = false
  }
}

async function loadAdminApprovals() {
  approvalLoading.value = true
  approvalError.value = ''
  adminAccountsError.value = ''
  try {
    const [requestsResult, accountsResult] = await Promise.allSettled([
      getAdminRegistrationRequests('all'),
      getAdminAccounts(),
    ])

    if (requestsResult.status === 'fulfilled') {
      adminRequests.value = requestsResult.value || []
    } else {
      approvalError.value = requestsResult.reason?.message || '管理员注册申请暂时无法读取。'
    }

    if (accountsResult.status === 'fulfilled') {
      adminAccounts.value = accountsResult.value || []
    } else {
      adminAccountsError.value = accountsResult.reason?.message || '管理员账号暂时无法读取。'
    }
  } finally {
    approvalLoading.value = false
  }
}

async function approveRequest(requestItem) {
  if (!window.confirm(`确认批准管理员账号「${requestItem.username}」吗？批准后该账号即可登录。`)) {
    return
  }
  approvalActionId.value = requestItem.id
  approvalNotice.value = ''
  approvalError.value = ''
  try {
    await approveAdminRegistration(requestItem.id)
    approvalNotice.value = `已批准管理员账号「${requestItem.username}」。`
    await loadAdminApprovals()
  } catch (err) {
    approvalError.value = err.message || '批准管理员申请失败。'
  } finally {
    approvalActionId.value = null
  }
}

async function rejectRequest(requestItem) {
  if (!window.confirm(`确认拒绝管理员账号「${requestItem.username}」的注册申请吗？`)) {
    return
  }
  approvalActionId.value = requestItem.id
  approvalNotice.value = ''
  approvalError.value = ''
  try {
    await rejectAdminRegistration(requestItem.id)
    approvalNotice.value = `已拒绝管理员账号「${requestItem.username}」的申请。`
    await loadAdminApprovals()
  } catch (err) {
    approvalError.value = err.message || '拒绝管理员申请失败。'
  } finally {
    approvalActionId.value = null
  }
}

async function removeApprovalRecord(requestItem) {
  const resultLabel = requestItem.status === 'approved' ? '已批准' : '已拒绝'
  if (!window.confirm(`确认删除管理员账号「${requestItem.username}」的${resultLabel}记录吗？此操作只删除审批记录，不会删除对应管理员账号。`)) {
    return
  }
  approvalHistoryActionId.value = requestItem.id
  approvalNotice.value = ''
  approvalError.value = ''
  try {
    await deleteAdminRegistrationRecord(requestItem.id)
    approvalNotice.value = `已删除管理员账号「${requestItem.username}」的审批记录。`
    await loadAdminApprovals()
  } catch (err) {
    approvalError.value = err.message || '删除审批记录失败。'
  } finally {
    approvalHistoryActionId.value = null
  }
}

async function loadUsers() {
  usersLoading.value = true
  usersError.value = ''
  try {
    users.value = await getAdminUsers(userQuery)
  } catch (err) {
    usersError.value = '注册账号暂时无法读取，请确认数据库服务是否运行。'
  } finally {
    usersLoading.value = false
  }
}

async function loadMailSettings() {
  resetMailNotice()
  try {
    const settings = await getMailSettings()
    Object.assign(mail, settings, { password: '', clearPassword: false })
  } catch (err) {
    mailError.value = '邮件设置暂时无法读取，请确认数据库服务是否运行。'
  }
}

async function loadCaptchaSettings() {
  resetCaptchaNotice()
  try {
    Object.assign(captcha, await getCaptchaSettings())
  } catch (err) {
    captchaError.value = err.message || '注册验证设置暂时无法读取。'
  }
}

async function saveCaptchaSettings() {
  resetCaptchaNotice()
  captchaSaving.value = true
  try {
    Object.assign(captcha, await updateCaptchaSettings({
      enabled: captcha.enabled,
      cost: captcha.cost,
      expiresInSeconds: captcha.expiresInSeconds,
    }))
    captchaNotice.value = '注册验证设置已保存。'
  } catch (err) {
    captchaError.value = err.message || '注册验证设置保存失败。'
  } finally {
    captchaSaving.value = false
  }
}

async function toggleUserStatus(user) {
  const nextStatus = user.status === 'active' ? 'disabled' : 'active'
  const action = nextStatus === 'disabled' ? '禁用' : '启用'
  if (!window.confirm(`确认${action}账号「${user.email}」吗？`)) {
    return
  }
  userActionId.value = user.id
  usersNotice.value = ''
  try {
    await updateAdminUserStatus(user.id, nextStatus)
    usersNotice.value = `已${action}用户账号「${user.username}」。`
    await loadUsers()
  } catch (err) {
    usersError.value = err.message || `${action}账号失败`
  } finally {
    userActionId.value = null
  }
}

async function revokeSessions(user) {
  if (!window.confirm(`确认撤销「${user.email}」的全部登录会话吗？`)) {
    return
  }
  userActionId.value = user.id
  usersNotice.value = ''
  try {
    await revokeAdminUserSessions(user.id)
    usersNotice.value = `已撤销用户账号「${user.username}」的全部登录会话。`
    await loadUsers()
  } catch (err) {
    usersError.value = err.message || '撤销会话失败'
  } finally {
    userActionId.value = null
  }
}

async function removeUser(user) {
  if (!window.confirm(`确认永久删除用户账号「${user.username}（${user.email}）」吗？该账号的全部登录会话也会被删除，且无法撤销。`)) {
    return
  }
  userActionId.value = user.id
  usersNotice.value = ''
  usersError.value = ''
  try {
    await deleteAdminUser(user.id)
    usersNotice.value = `已删除用户账号「${user.username}」。`
    await loadUsers()
  } catch (err) {
    usersError.value = err.message || '删除用户账号失败'
  } finally {
    userActionId.value = null
  }
}

function canDeleteAdminAccount(account) {
  if (account.username === currentAdminUsername) {
    return false
  }
  return account.status !== 'active' || activeAdminCount.value > 1
}

function adminDeleteTitle(account) {
  if (account.username === currentAdminUsername) {
    return '当前登录的管理员不能删除自己的账号'
  }
  if (account.status === 'active' && activeAdminCount.value <= 1) {
    return '至少需要保留一个启用的管理员账号'
  }
  return `删除管理员账号 ${account.username}`
}

async function removeAdminAccount(account) {
  if (!canDeleteAdminAccount(account)) {
    return
  }
  if (!window.confirm(`确认永久删除管理员账号「${account.username}」吗？该账号的全部管理会话也会被删除，且无法撤销。`)) {
    return
  }
  adminAccountActionId.value = account.id
  approvalNotice.value = ''
  adminAccountsError.value = ''
  try {
    await deleteAdminAccount(account.id)
    approvalNotice.value = `已删除管理员账号「${account.username}」。`
    await loadAdminApprovals()
  } catch (err) {
    adminAccountsError.value = err.message || '删除管理员账号失败。'
  } finally {
    adminAccountActionId.value = null
  }
}

async function saveMailSettings() {
  resetMailNotice()
  mailSaving.value = true
  try {
    const settings = await updateMailSettings(mailPayload())
    Object.assign(mail, settings, { password: '', clearPassword: false })
    mailNotice.value = '邮件和验证码设置已保存。'
  } catch (err) {
    mailError.value = err.message || '邮件设置保存失败'
  } finally {
    mailSaving.value = false
  }
}

async function testConnection() {
  resetMailNotice()
  mailTesting.value = true
  try {
    const result = await testMailConnection(mailPayload())
    mailNotice.value = `${result.message}：${result.host}:${result.port}`
  } catch (err) {
    mailError.value = err.message || 'SMTP 连接测试失败'
  } finally {
    mailTesting.value = false
  }
}

async function sendTestEmail() {
  resetMailNotice()
  testSending.value = true
  try {
    await sendMailTest({ ...mailPayload(), recipient: testRecipient.value })
    mailNotice.value = `测试邮件已发送至 ${testRecipient.value}。`
  } catch (err) {
    mailError.value = err.message || '测试邮件发送失败'
  } finally {
    testSending.value = false
  }
}

function mailPayload() {
  const payload = {
    mailEnabled: mail.mailEnabled,
    siteName: mail.siteName,
    host: mail.host,
    port: mail.port,
    username: mail.username,
    clearPassword: mail.clearPassword,
    fromEmail: mail.fromEmail,
    fromName: mail.fromName,
    securityMode: mail.securityMode,
    timeoutSeconds: mail.timeoutSeconds,
    verificationCodeMinutes: mail.verificationCodeMinutes,
    resendIntervalSeconds: mail.resendIntervalSeconds,
    sessionDays: mail.sessionDays,
    verificationSubject: mail.verificationSubject,
    verificationHtml: mail.verificationHtml,
  }
  if (mail.password) {
    payload.password = mail.password
  }
  return payload
}

function renderTemplate(template) {
  return String(template)
    .replaceAll('{{site_name}}', mail.siteName || 'SoloFirm')
    .replaceAll('{{code}}', '123456')
    .replaceAll('{{expires_minutes}}', String(mail.verificationCodeMinutes || 10))
}

function formatDate(value) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function reviewerName(requestItem) {
  if (requestItem.reviewedByUsername) {
    return requestItem.reviewedByUsername
  }
  return requestItem.reviewedBy ? `管理员 #${requestItem.reviewedBy}` : '未知管理员'
}

function resetMailNotice() {
  mailNotice.value = ''
  mailError.value = ''
}

function resetCaptchaNotice() {
  captchaNotice.value = ''
  captchaError.value = ''
}
</script>

<style scoped>
.admin-settings-page,
.admin-settings-page > *,
.admin-approval-panel,
.approval-section,
.settings-user-table-wrap {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

.admin-settings-page .settings-tabs {
  grid-template-columns: repeat(5, minmax(0, 1fr)) !important;
}

.ai-settings-panel {
  overflow: hidden;
}

.ai-settings-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.ai-settings-grid > label {
  align-self: start;
  align-content: start !important;
  grid-auto-rows: max-content;
}

.ai-settings-grid > label > :is(input, select) {
  height: 56px;
  min-height: 56px !important;
}

.ai-field-wide {
  grid-column: 1 / -1;
}

.ai-model-catalog {
  margin-top: 30px;
  padding: 28px 0 30px;
  border-top: 1px solid #d0d4cf;
  border-bottom: 1px solid #d0d4cf;
}

.ai-model-catalog-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
}

.ai-model-catalog-head > div:first-child {
  flex: 1 1 auto;
  min-width: 0;
}

.ai-model-catalog-head h3 {
  margin: 7px 0 8px;
  color: #181a18;
  font-size: 1.22rem;
  line-height: 1.2;
}

.ai-model-catalog-head p {
  margin: 0;
  color: #59605a;
  line-height: 1.65;
}

.ai-model-catalog-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.ai-model-catalog-actions .button {
  min-height: 44px;
  white-space: nowrap;
}

.ai-model-selector-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
  gap: 22px;
  margin-top: 24px;
  padding-top: 22px;
  border-top: 1px solid #c9cec9;
}

.ai-model-selector-field,
.ai-model-display-field {
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
  margin: 0;
}

.ai-model-selector-field > label,
.ai-model-display-field > span {
  color: #4b514c;
  font-size: 0.8rem;
  font-weight: 700;
}

.ai-model-selector-field > small,
.ai-model-display-field > small {
  color: #687069;
  font-size: 0.76rem;
  font-weight: 400;
  line-height: 1.55;
}

.ai-model-display-field input {
  width: 100%;
  height: 56px;
  min-height: 56px !important;
  margin: 0;
}

.ai-model-combobox {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 52px;
  min-width: 0;
  height: 56px;
  border: 1px solid #c8cdc8;
  border-radius: 6px;
  background: #f7f7f4;
}

.ai-model-combobox:focus-within {
  border-color: #676e68;
  outline: 2px solid rgba(24, 26, 24, 0.12);
  outline-offset: 1px;
}

.ai-model-combobox > input {
  width: 100%;
  height: 54px;
  min-height: 54px !important;
  margin: 0;
  padding-inline: 16px;
  border: 0 !important;
  border-radius: 6px 0 0 6px !important;
  background: transparent !important;
  box-shadow: none !important;
  outline: 0 !important;
}

.ai-model-combobox > button {
  display: inline-grid;
  width: 52px;
  height: 54px;
  place-items: center;
  padding: 0;
  border: 0;
  border-left: 1px solid #d2d6d1;
  border-radius: 0 6px 6px 0;
  background: transparent;
  color: #4f5650;
  cursor: pointer;
}

.ai-model-combobox > button:hover {
  background: #eceeea;
  color: #181a18;
}

.ai-model-combobox > button:focus-visible {
  outline: 2px solid #333733;
  outline-offset: -3px;
}

.ai-model-options {
  position: absolute;
  z-index: 20;
  top: calc(100% + 7px);
  right: 0;
  left: 0;
  max-height: 280px;
  overflow-y: auto;
  padding: 6px;
  border: 1px solid #c8cdc8;
  border-radius: 6px;
  background: #fbfbf8;
}

.ai-model-options > button {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 20px;
  width: 100%;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #202320;
  text-align: left;
  cursor: pointer;
}

.ai-model-options > button:hover,
.ai-model-options > button:focus-visible,
.ai-model-options > button[aria-selected='true'] {
  background: #eceeea;
  outline: 0;
}

.ai-model-options > button span {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.ai-model-options > button strong,
.ai-model-options > button small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-model-options > button strong {
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 0.92rem;
  font-weight: 600;
}

.ai-model-options > button small {
  color: #69706a;
  font-size: 0.75rem;
}

.ai-model-options-empty {
  padding: 14px 12px;
  color: #69706a;
  font-size: 0.8rem;
  line-height: 1.55;
}

.ai-settings-panel > .settings-notice {
  margin-top: 22px !important;
}

.ai-runtime-band {
  display: grid;
  grid-template-columns: minmax(280px, 1.25fr) repeat(2, minmax(190px, 1fr));
  gap: 24px 28px;
  margin-top: 26px;
  padding: 24px 0;
  border-top: 1px solid #d0d4cf;
  border-bottom: 1px solid #d0d4cf;
}

.ai-runtime-band label,
.ai-temperature-field {
  display: grid;
  grid-template-rows: 44px 60px auto;
  align-content: start;
  gap: 8px;
  min-width: 0;
}

.ai-runtime-band > label > span {
  display: flex;
  min-height: 44px;
  align-items: flex-end;
  line-height: 1.28;
}

.ai-runtime-band > label > input[type='number'] {
  width: 100%;
  height: 56px;
  min-height: 56px !important;
  margin: 0;
}

.ai-daily-quota-field {
  grid-column: span 2;
}

.ai-temperature-field > span {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.ai-temperature-field output {
  color: #181a18;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-variant-numeric: tabular-nums;
}

.ai-temperature-field input[type='range'] {
  appearance: none !important;
  -webkit-appearance: none !important;
  width: 100% !important;
  height: 20px !important;
  min-height: 20px !important;
  margin: 0 !important;
  padding: 0 !important;
  border: 0 !important;
  border-radius: 0 !important;
  outline: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
}

.ai-range-control {
  display: grid;
  align-content: center;
  gap: 4px;
  min-width: 0;
  height: 60px;
  padding: 7px 3px 4px;
  border-radius: 6px;
}

.ai-range-control:focus-within {
  outline: 2px solid rgba(24, 26, 24, 0.22);
  outline-offset: 2px;
}

.ai-temperature-field input[type='range']::-webkit-slider-runnable-track {
  height: 5px;
  border: 1px solid #b7bcb7;
  border-radius: 999px;
  background: #dfe2de;
}

.ai-temperature-field input[type='range']::-webkit-slider-thumb {
  appearance: none;
  -webkit-appearance: none;
  width: 19px;
  height: 19px;
  margin-top: -8px;
  border: 2px solid #f7f7f4;
  border-radius: 50%;
  background: #252825;
  box-shadow: 0 0 0 1px #252825;
}

.ai-temperature-field input[type='range']::-moz-range-track {
  height: 4px;
  border: 1px solid #b7bcb7;
  border-radius: 999px;
  background: #dfe2de;
}

.ai-temperature-field input[type='range']::-moz-range-thumb {
  width: 17px;
  height: 17px;
  border: 2px solid #f7f7f4;
  border-radius: 50%;
  background: #252825;
  box-shadow: 0 0 0 1px #252825;
}

.ai-range-scale {
  display: flex;
  justify-content: space-between;
  color: #747a75;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.ai-connection-state {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  margin-top: 24px;
  padding: 18px 0;
  color: #4f5650;
  transition: color 180ms ease, opacity 180ms ease;
}

.ai-connection-state div {
  display: grid;
  gap: 3px;
}

.ai-connection-state strong {
  color: #181a18;
}

.ai-connection-state small,
.ai-connection-state time {
  color: #686f69;
}

.ai-connection-state.is-success > svg {
  color: #3f6949;
}

.ai-connection-state.is-failed > svg {
  color: #742e26;
}

@media (max-width: 1120px) {
  .ai-model-catalog-head {
    display: grid;
    align-items: start;
    gap: 18px;
  }

  .ai-model-catalog-actions {
    justify-content: flex-start;
  }

  .ai-model-selector-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .ai-runtime-band {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ai-temperature-field {
    grid-column: 1 / -1;
  }

  .ai-daily-quota-field {
    grid-column: auto;
  }
}

.admin-approval-panel {
  display: grid;
  gap: 0;
}

.approval-notice {
  display: block;
  margin: 0 !important;
}

.approval-notice-slot {
  width: 100%;
  padding-bottom: 32px;
}

.account-notice {
  margin: 0 0 18px !important;
}

.approval-section + .approval-section {
  margin-top: 30px;
  padding-top: 30px;
  border-top: 1px solid #d0d4cf;
}

.approval-section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.approval-section-head h3 {
  margin: 5px 0 0;
  color: #181a18;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', STSong, SimSun, serif;
  font-size: 1.15rem;
  line-height: 1.25;
}

.approval-count {
  display: inline-grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border: 1px solid #c7ccc6;
  border-radius: 6px;
  background: #f1f2ee;
  color: #343a35;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-variant-numeric: tabular-nums;
}

.approval-table {
  min-width: 820px !important;
}

.settings-user-table th:last-child,
.settings-user-table td:last-child {
  min-width: 312px;
}

.settings-row-actions {
  display: grid !important;
  grid-template-columns: repeat(3, max-content) !important;
  align-items: center;
  justify-content: start !important;
  gap: 8px !important;
}

.settings-row-actions button {
  justify-content: center;
  min-height: 38px;
  padding: 8px 11px !important;
  white-space: nowrap;
}

.admin-account-actions {
  grid-template-columns: max-content !important;
}

.approval-actions {
  grid-template-columns: repeat(2, max-content) !important;
}

.approval-table .admin-account-actions,
.approval-table th:last-child,
.approval-table td:last-child {
  min-width: 132px;
}

.settings-delete-button {
  color: #742e26 !important;
}

.approval-table td:first-child strong {
  color: #181a18;
}

.approval-history-table {
  min-width: 1040px !important;
}

.approval-history-table th:last-child,
.approval-history-table td:last-child {
  min-width: 132px !important;
}

.approval-history-actions {
  grid-template-columns: max-content !important;
}

.approval-reviewer {
  display: grid;
  gap: 3px;
}

.approval-reviewer strong {
  color: #181a18;
  font-size: 0.92rem;
}

.approval-reviewer small {
  color: #6b716c;
  font-family: 'Bookman Old Style', Georgia, serif;
  font-size: 0.72rem;
}

.approval-actions button:disabled {
  cursor: wait !important;
  opacity: 0.58;
}

.approval-reject-button {
  color: #742e26 !important;
}

.approval-reject-button:hover,
.approval-reject-button:focus-visible {
  border-color: #d4b4ad !important;
  background: #f8eeeb !important;
}

.approval-error {
  display: grid;
  min-height: 150px;
  place-items: center;
  align-content: center;
  gap: 8px;
  padding: 22px;
  border: 1px solid #d4b4ad;
  border-radius: 8px;
  background: #f8eeeb;
  color: #742e26;
  text-align: center;
}

.approval-error p {
  margin: 0;
  color: #742e26;
}

.approval-error .button {
  margin-top: 4px;
}

@media (max-width: 760px) {
  .admin-settings-page .settings-tabs {
    grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  }

  .admin-settings-page .settings-tabs button {
    min-width: 0;
    white-space: nowrap;
  }

  .approval-section-head {
    align-items: center;
  }

  .ai-settings-grid,
  .ai-runtime-band {
    grid-template-columns: minmax(0, 1fr);
  }

  .ai-model-catalog-head {
    display: grid;
    align-items: start;
    gap: 18px;
  }

  .ai-model-catalog-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    justify-content: stretch;
  }

  .ai-model-catalog-actions .button {
    width: 100%;
    min-width: 0;
    justify-content: center;
  }

  .ai-temperature-field {
    grid-column: auto;
  }

  .ai-daily-quota-field {
    grid-column: auto;
  }

  .ai-runtime-band label,
  .ai-temperature-field {
    grid-template-rows: auto 60px auto;
  }

  .ai-runtime-band > label > span {
    min-height: 0;
  }

  .ai-connection-state {
    grid-template-columns: 22px minmax(0, 1fr);
  }

  .ai-connection-state time {
    grid-column: 2;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-connection-state {
    transition: none;
  }
}

@media (max-width: 380px) {
  .admin-settings-page .settings-tabs button {
    gap: 5px !important;
    padding-inline: 6px !important;
    font-size: 0.76rem !important;
  }

  .admin-settings-page .settings-tabs button svg {
    width: 15px;
    height: 15px;
  }
}
</style>
