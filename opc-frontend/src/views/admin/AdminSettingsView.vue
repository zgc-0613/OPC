<template>
  <div class="admin-stack admin-settings-page">
    <section class="admin-panel settings-intro-panel">
      <div>
        <span class="caption">CONTROL CENTER</span>
        <h2>账号、会话与注册安全</h2>
        <p>集中管理注册账号、验证码策略、ALTCHA 与 SMTP 投递配置。敏感密码只写入后端加密存储，不会返回浏览器。</p>
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
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Ban,
  CheckCircle,
  LogOut,
  Mail,
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
  getAdminAccounts,
  getAdminRegistrationRequests,
  getAdminUsers,
  getCaptchaSettings,
  getMailSettings,
  rejectAdminRegistration,
  revokeAdminUserSessions,
  sendMailTest,
  testMailConnection,
  updateAdminUserStatus,
  updateCaptchaSettings,
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

onMounted(() => {
  loadUsers()
  loadMailSettings()
  loadCaptchaSettings()
  loadAdminApprovals()
})

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
  grid-template-columns: repeat(4, minmax(0, 1fr)) !important;
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
