import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import RegionDirectoryView from '@/views/RegionDirectoryView.vue'
import PolicyListView from '@/views/PolicyListView.vue'
import PolicyDetailView from '@/views/PolicyDetailView.vue'
import CaseListView from '@/views/CaseListView.vue'
import CaseDetailView from '@/views/CaseDetailView.vue'
import SourceLedgerView from '@/views/SourceLedgerView.vue'
import AdminHomeView from '@/views/admin/AdminHomeView.vue'
import PolicyAdminView from '@/views/admin/PolicyAdminView.vue'
import CaseAdminView from '@/views/admin/CaseAdminView.vue'
import SourceAdminView from '@/views/admin/SourceAdminView.vue'
import TagAdminView from '@/views/admin/TagAdminView.vue'
import { isAdminAuthenticated } from '@/api/auth'

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
        path: 'sources',
        name: 'source-ledger',
        component: SourceLedgerView,
      },
    ],
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
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
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.meta.requiresAdmin && !isAdminAuthenticated()) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath,
      },
    }
  }
  if (to.name === 'login' && isAdminAuthenticated()) {
    return '/admin'
  }
  return true
})

export default router
