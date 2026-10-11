<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const nav = ref(null)
const navItemRefs = ref([])
const navIndicatorX = ref(0)
const navIndicatorWidth = ref(0)
const navIndicatorVisible = ref(false)
const pageAnimating = ref(false)
let navResizeObserver
let pageAnimationFrame
let pageAnimationTimer

const nickname = computed(() => userStore.userInfo?.nickname || 'MoodEcho用户')

const navItems = [
  {
    label: '首页',
    path: '/home',
  },
  {
    label: '我的日记',
    path: '/diaries',
  },
  {
    label: '回响',
    path: '/resonance',
  },
  {
    label: '个人主页',
    path: '/profile',
  },
]

const isActive = (path) => {
  if (path === '/diaries') {
    return route.path.startsWith('/diaries')
  }

  if (path === '/profile') {
    return route.path.startsWith('/profile')
  }

  if (path === '/resonance') {
    return route.path.startsWith('/resonance')
  }

  return route.path === path
}

const setNavItemRef = (element, index) => {
  if (element) {
    navItemRefs.value[index] = element
  }
}

const updateNavIndicator = () => {
  const activeIndex = navItems.findIndex((item) => isActive(item.path))
  const activeItem = navItemRefs.value[activeIndex]

  if (!activeItem) {
    navIndicatorVisible.value = false
    navIndicatorWidth.value = 0
    return
  }

  const activeText = activeItem.querySelector('.nav-item-label')
  navIndicatorX.value = activeItem.offsetLeft + activeItem.offsetWidth / 2
  navIndicatorWidth.value = activeText?.offsetWidth || activeItem.offsetWidth
  navIndicatorVisible.value = true
}

const playPageAnimation = () => {
  pageAnimating.value = false
  cancelAnimationFrame(pageAnimationFrame)
  clearTimeout(pageAnimationTimer)

  pageAnimationFrame = requestAnimationFrame(() => {
    pageAnimating.value = true
    pageAnimationTimer = setTimeout(() => {
      pageAnimating.value = false
    }, 220)
  })
}

watch(
  () => route.path,
  async () => {
    await nextTick()
    updateNavIndicator()
    playPageAnimation()
  },
)

onMounted(async () => {
  await nextTick()
  updateNavIndicator()

  navResizeObserver = new ResizeObserver(updateNavIndicator)
  navResizeObserver.observe(nav.value)
  navItemRefs.value.forEach((item) => navResizeObserver.observe(item))
})

onBeforeUnmount(() => {
  navResizeObserver?.disconnect()
  cancelAnimationFrame(pageAnimationFrame)
  clearTimeout(pageAnimationTimer)
})

const goPage = (path) => {
  router.push(path)
}

const logout = () => {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <div class="app-layout" :class="{ 'profile-route': route.path.startsWith('/profile') }">
    <header class="app-header">
      <div class="header-inner">
        <div class="brand" @click="goPage('/home')">MoodEcho</div>

        <nav ref="nav" class="nav" aria-label="主导航">
          <span
            class="nav-indicator"
            :class="{ visible: navIndicatorVisible }"
            :style="{
              width: `${navIndicatorWidth}px`,
              transform: `translateX(${navIndicatorX}px) translateX(-50%)`,
            }"
            aria-hidden="true"
          />
          <button
            v-for="(item, index) in navItems"
            :key="item.path"
            :ref="(element) => setNavItemRef(element, index)"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
            :aria-current="isActive(item.path) ? 'page' : undefined"
            type="button"
            @click="goPage(item.path)"
          >
            <span class="nav-item-label">{{ item.label }}</span>
          </button>
        </nav>

        <div class="user-area">
          <span class="nickname">{{ nickname }}</span>
          <button class="logout-button" type="button" @click="logout">退出登录</button>
        </div>
      </div>
    </header>

    <main class="app-main" :class="{ 'page-entering': pageAnimating }">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  --nav-hover-line: rgba(116, 107, 97, 0.42);
  min-height: 100vh;
  background: #EFEEEC;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #EFEEEC;
}

.header-inner {
  display: flex;
  width: calc(100% - 80px);
  min-height: 64px;
  align-items: center;
  gap: 24px;
  margin: 0 auto;
  padding: 0 24px;
}

.brand {
  color: var(--paper-ink);
  cursor: pointer;
  font-size: 24px;
  font-weight: 1000;
  white-space: nowrap;
  transition:
    opacity 180ms ease,
    transform 180ms ease;
}

.brand:hover,
.brand:focus-visible {
  transform: translateY(-1px);
}

.nav {
  position: relative;
  display: flex;
  flex: 1;
  align-items: center;
  gap: 18px;
}

.nav-indicator {
  position: absolute;
  bottom: 4px;
  left: 0;
  height: 1px;
  background: var(--paper-ink);
  opacity: 0;
  pointer-events: none;
  transition:
    transform 260ms cubic-bezier(0.22, 1, 0.36, 1),
    width 260ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 140ms ease;
}

.nav-indicator.visible {
  opacity: 1;
}

.nav-item {
  position: relative;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--paper-ink);
  cursor: pointer;
  font-size: 14px;
  /* font-weight: 450; */
  padding: 8px 12px;
  transition:
    color 180ms ease,
    transform 180ms ease;
}

.nav-item:hover,
.nav-item:focus-visible {
  background: transparent;
  color: var(--paper-ink);
  transform: translateY(-1px);
}

.nav-item.active {
  background: transparent;
  color: var(--paper-ink);
}

.nav-item-label {
  display: inline-block;
  position: relative;
}

.nav-item-label::after {
  position: absolute;
  right: 0;
  bottom: -4px;
  left: 0;
  height: 1px;
  background: var(--nav-hover-line);
  content: '';
  opacity: 0;
  transform: translateY(2px);
  transition:
    opacity 160ms ease,
    transform 180ms ease;
}

.nav-item:not(.active):hover .nav-item-label::after,
.nav-item:not(.active):focus-visible .nav-item-label::after {
  opacity: 1;
  transform: translateY(0);
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nickname {
  max-width: 140px;
  overflow: hidden;
  color: var(--paper-ink);
  font-size: 14px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
  /* font-weight: 650; */
}

.logout-button {
  border: 0;
  background: transparent;
  color: var(--paper-ink);
  cursor: pointer;
  font-size: 14px;
  /* font-weight: 650; */
  line-height: 1.25;
  padding: 0;
  white-space: nowrap;
  transition:
    opacity 180ms ease,
    transform 180ms ease;
}

.logout-button:hover,
.logout-button:focus-visible {
  color: var(--paper-ink);
  transform: translateY(-1px);
  text-decoration: underline;
  text-decoration-color: var(--nav-hover-line);
  text-decoration-thickness: 1px;
  text-underline-offset: 10px;
}

.logout-button:active {
  opacity: 0.72;
  transform: translateY(1px);
}

.app-main {
  width: 100%;
  min-height: calc(100vh - 64px);
  margin: 0 auto;
  padding: 30px 64px 56px;
  border-radius: 10px 10px 0 0;
  background: #fff;
  overflow: clip;
}

.app-main.page-entering :deep(> *) {
  animation: page-content-enter 200ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes page-content-enter {
  from {
    filter: blur(2px);
    opacity: 0.78;
    transform: translateY(6px);
  }

  to {
    filter: blur(0);
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 720px) {
  .app-layout {
    --mobile-safe-area-top: env(safe-area-inset-top, 0px);
    --mobile-safe-area-bottom: env(safe-area-inset-bottom, 0px);
    --mobile-app-header-height: calc(70px + var(--mobile-safe-area-top));
    display: flex;
    height: 100dvh;
    min-height: 0;
    flex-direction: column;
    overflow: hidden;
  }

  .app-header {
    flex: 0 0 auto;
    padding: calc(12px + var(--mobile-safe-area-top)) 0 12px;
  }

  .header-inner {
    width: 100%;
    min-height: auto;
    align-items: center;
    flex-direction: row;
    flex-wrap: wrap;
    gap: 14px 14px;
    padding: 10px 16px 11px;
  }

  .brand {
    flex: 0 0 auto;
    line-height: 1.12;
  }

  .nav {
    flex: 1 1 0;
    min-width: 0;
    width: auto;
    gap: 20px;
    justify-content: safe flex-end;
    overflow-x: auto;
    padding-bottom: 0;
  }

  .nav-item {
    line-height: 1.25;
    padding: 2px 0 6px;
  }

  .nav-indicator {
    bottom: 1px;
  }

  .user-area {
    display: none;
  }

  .app-main {
    width: 100%;
    min-height: 0;
    flex: 1 1 auto;
    overflow-x: clip;
    overflow-y: auto;
    overscroll-behavior-y: contain;
    padding:
      16px max(16px, env(safe-area-inset-right, 0px))
      calc(16px + var(--mobile-safe-area-bottom)) max(16px, env(safe-area-inset-left, 0px));
  }

  .profile-route .app-main {
    overflow-y: auto;
    scrollbar-width: none;
    -ms-overflow-style: none;
  }

  .profile-route .app-main::-webkit-scrollbar {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .nav-indicator,
  .nav-item,
  .brand,
  .logout-button {
    transition-duration: 1ms;
  }

  .brand:hover,
  .brand:focus-visible,
  .nav-item:hover,
  .nav-item:focus-visible,
  .logout-button:hover,
  .logout-button:focus-visible,
  .logout-button:active {
    transform: none;
  }

  .app-main.page-entering :deep(> *) {
    animation: none;
  }

}
</style>
