import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import HomeView from '@/views/HomeView.vue'
import AnalysisOverviewView from '@/views/AnalysisOverviewView.vue'
import LoginView from '@/views/LoginView.vue'
import UserAccountView from '@/views/UserAccountView.vue'
import AdminLoginView from '@/views/AdminLoginView.vue'
import RegionDirectoryView from '@/views/RegionDirectoryView.vue'
import PolicyListView from '@/views/PolicyListView.vue'
import PolicyDetailView from '@/views/PolicyDetailView.vue'
import CaseListView from '@/views/CaseListView.vue'
import CaseDetailView from '@/views/CaseDetailView.vue'
import CaseAnalysisView from '@/views/CaseAnalysisView.vue'
import AssistantView from '@/views/AssistantView.vue'
import SourceLedgerView from '@/views/SourceLedgerView.vue'
import AdminHomeView from '@/views/admin/AdminHomeView.vue'
import PolicyAdminView from '@/views/admin/PolicyAdminView.vue'
import CaseAdminView from '@/views/admin/CaseAdminView.vue'
import SourceAdminView from '@/views/admin/SourceAdminView.vue'
import TagAdminView from '@/views/admin/TagAdminView.vue'
import AdminSettingsView from '@/views/admin/AdminSettingsView.vue'
import EvidenceReviewAdminView from '@/views/admin/EvidenceReviewAdminView.vue'
import { isAdminAuthenticated, isUserAuthenticated } from '@/api/auth'
import { recordVisit } from '@/api/visit'

const routes = [
  {
    path: '/',
    component: MainLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: HomeView,
      },
      {
        path: 'analysis',
        name: 'analysis-overview',
        component: AnalysisOverviewView,
      },
      {
        path: 'regions',
        name: 'region-directory',
        component: RegionDirectoryView,
      },
      {
        path: 'policies',
        name: 'policy-list',
        component: PolicyListView,
      },
      {
        path: 'policies/:id',
        name: 'policy-detail',
        component: PolicyDetailView,
        props: true,
      },
      {
        path: 'cases',
        name: 'case-list',
        component: CaseListView,
      },
      {
        path: 'cases/:id',
        name: 'case-detail',
        component: CaseDetailView,
        props: true,
      },
      {
        path: 'cases/:id/analysis',
        name: 'case-analysis',
        component: CaseAnalysisView,
        props: true,
        meta: {
          requiresUser: true,
        },
      },
      {
        path: 'assistant',
        name: 'assistant',
        component: AssistantView,
        meta: {
          requiresUser: true,
        },
      },
      {
        path: 'sources',
        name: 'source-ledger',
        component: SourceLedgerView,
      },
      {
        path: 'account',
        name: 'user-account',
        component: UserAccountView,
        meta: {
          requiresUser: true,
        },
      },
    ],
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
  },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: AdminLoginView,
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: {
      requiresAdmin: true,
    },
    children: [
      {
        path: '',
        name: 'admin-home',
        component: AdminHomeView,
      },
      {
        path: 'policies',
        name: 'admin-policies',
        component: PolicyAdminView,
      },
      {
        path: 'cases',
        name: 'admin-cases',
        component: CaseAdminView,
      },
      {
        path: 'sources',
        name: 'admin-sources',
        component: SourceAdminView,
      },
      {
        path: 'tags',
        name: 'admin-tags',
        component: TagAdminView,
      },
      {
        path: 'settings',
        name: 'admin-settings',
        component: AdminSettingsView,
      },
      {
        path: 'evidence-reviews',
        name: 'admin-evidence-reviews',
        component: EvidenceReviewAdminView,
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.meta.requiresUser && !isUserAuthenticated()) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath,
        reason: ['case-analysis', 'assistant'].includes(to.name) ? 'ai-login-required' : undefined,
      },
    }
  }
  if (to.meta.requiresAdmin && !isAdminAuthenticated()) {
    return {
      path: '/admin/login',
      query: {
        redirect: to.fullPath,
      },
    }
  }
  if (to.name === 'admin-login' && isAdminAuthenticated()) {
    return '/admin'
  }
  return true
})

router.afterEach((to) => {
  if (to.path.startsWith('/admin') || to.path === '/login') {
    return
  }
  if (to.name === 'policy-detail' || to.name === 'case-detail') {
    return
  }

  recordVisit(buildVisitPayload(to)).catch(() => {
    // 访问统计失败不能影响用户正常浏览。
  })
})

function buildVisitPayload(route) {
  const basePayload = {
    pagePath: route.fullPath,
    pageTitle: getPageTitle(route),
    targetType: 'other',
    targetId: null,
    referer: document.referrer || '',
  }

  if (route.name === 'home') {
    return {
      ...basePayload,
      targetType: 'site',
    }
  }

  if (route.name === 'analysis-overview') {
    return {
      ...basePayload,
      targetType: 'site',
    }
  }

  if (route.name === 'policy-detail') {
    return {
      ...basePayload,
      targetType: 'policy',
      targetId: Number(route.params.id),
    }
  }

  if (route.name === 'case-detail') {
    return {
      ...basePayload,
      targetType: 'case',
      targetId: Number(route.params.id),
    }
  }

  if (route.name === 'region-directory') {
    return {
      ...basePayload,
      targetType: 'region',
    }
  }

  if (route.name === 'source-ledger') {
    return {
      ...basePayload,
      targetType: 'source',
    }
  }

  return basePayload
}

function getPageTitle(route) {
  const titleMap = {
    home: 'OPC 信息平台首页',
    'analysis-overview': '资料分析',
    'region-directory': '地区目录',
    'policy-list': '政策索引',
    'policy-detail': `政策详情 #${route.params.id}`,
    'case-list': '案例索引',
    'case-detail': `案例详情 #${route.params.id}`,
    assistant: '创业研究助手',
    'source-ledger': '来源台账',
    'user-account': '个人主页',
  }

  return titleMap[route.name] || document.title || route.fullPath
}

export default router
