import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import HomeView from '@/views/HomeView.vue'
import PolicyListView from '@/views/PolicyListView.vue'
import PolicyDetailView from '@/views/PolicyDetailView.vue'
import CaseListView from '@/views/CaseListView.vue'
import CaseDetailView from '@/views/CaseDetailView.vue'

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
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
