import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

import AppLayout from '@/layouts/AppLayout.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import ProfileView from '@/views/ProfileView.vue'
import PrivacyView from '@/views/PrivacyView.vue'
import RegisterView from '@/views/RegisterView.vue'
import ResetPasswordView from '@/views/ResetPasswordView.vue'
import ResonanceView from '@/views/ResonanceView.vue'
import DiaryListView from '@/views/diary/DiaryListView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: {
        guestOnly: true,
      },
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: {
        guestOnly: true,
      },
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: ResetPasswordView,
      meta: {
        guestOnly: true,
      },
    },
    {
      path: '/privacy',
      name: 'privacy',
      component: PrivacyView,
    },
    {
      path: '/',
      component: AppLayout,
      meta: {
        requiresAuth: true,
      },
      children: [
        {
          path: '',
          redirect: '/home',
        },
        {
          path: 'home',
          name: 'home',
          component: HomeView,
        },
        {
          path: 'profile',
          name: 'profile',
          component: ProfileView,
        },
        {
          path: 'profile/privacy',
          name: 'profile-privacy',
          component: PrivacyView,
        },
        {
          path: 'settings',
          redirect: '/profile',
        },
        {
          path: 'diaries',
          name: 'diary-list',
          component: DiaryListView,
        },
        {
          path: 'resonance',
          name: 'resonance',
          component: ResonanceView,
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.token) {
    return '/login'
  }

  if (userStore.token && !userStore.userInfo) {
    try {
      await userStore.fetchCurrentUser()
    } catch {
      userStore.logout()
      return '/login'
    }
  }

  if (to.meta.guestOnly && userStore.token) {
    return '/home'
  }

  return true
})

export default router
