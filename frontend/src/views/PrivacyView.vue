<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { legalTabs } from '@/constants/legalContent'

const route = useRoute()
const router = useRouter()

const getValidTab = (value) => {
  return legalTabs.some((item) => item.key === value) ? value : legalTabs[0].key
}

const activeTab = ref(getValidTab(route.query.tab))

const activeContent = computed(() => {
  return legalTabs.find((item) => item.key === activeTab.value) || legalTabs[0]
})

const backTarget = computed(() => {
  if (route.query.from === 'profile' || route.path.startsWith('/profile')) {
    return {
      path: '/profile',
      label: '返回个人主页',
    }
  }

  return {
    path: '/register',
    label: '返回注册',
  }
})

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = getValidTab(tab)
  },
)

const handleTabChange = (tab) => {
  router.replace({
    path: route.path,
    query: {
      ...route.query,
      tab,
    },
  })
}

const goBack = () => {
  router.push(backTarget.value.path)
}
</script>

<template>
  <main class="privacy-page">
    <section class="privacy-shell" aria-labelledby="privacy-title">
      <header class="privacy-header">
        <button class="privacy-back-button" type="button" :aria-label="backTarget.label" @click="goBack">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="m15 18-6-6 6-6" />
          </svg>
        </button>
        <h1 id="privacy-title">隐私与协议</h1>
      </header>

      <el-card class="privacy-card" shadow="never">
        <el-tabs v-model="activeTab" class="privacy-tabs" @tab-change="handleTabChange">
          <el-tab-pane
            v-for="item in legalTabs"
            :key="item.key"
            :label="item.label"
            :name="item.key"
          />
        </el-tabs>

        <article class="legal-article">
          <h2>{{ activeContent.title }}</h2>
          <div class="legal-meta">
            <span>更新日期：{{ activeContent.updatedAt }}</span>
            <span>生效日期：{{ activeContent.effectiveAt }}</span>
          </div>
          <p v-for="paragraph in activeContent.paragraphs" :key="paragraph">
            {{ paragraph }}
          </p>
        </article>
      </el-card>
    </section>
  </main>
</template>

<style scoped>
.privacy-page {
  min-height: 100%;
  padding:
    18px max(16px, env(safe-area-inset-right, 0px)) 32px
    max(16px, env(safe-area-inset-left, 0px));
}

.privacy-shell {
  width: min(100%, 920px);
  margin: 0 auto;
}

.privacy-header {
  position: relative;
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
}

.privacy-header h1 {
  margin: 0;
  color: var(--paper-ink);
  font-size: 34px;
  font-weight: 760;
  line-height: 1.16;
  text-align: center;
}

.privacy-back-button {
  position: absolute;
  left: 0;
  display: inline-flex;
  width: 44px;
  height: 44px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--paper-accent);
  cursor: pointer;
  padding: 0;
}

.privacy-back-button svg {
  width: 24px;
  height: 24px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.privacy-back-button:hover,
.privacy-back-button:focus-visible {
  background: rgba(138, 117, 99, 0.06);
  color: var(--paper-accent-dark);
  outline: none;
}

.privacy-card {
  background: var(--paper-card);
  box-shadow: none;
}

.privacy-card :deep(.el-card__body) {
  padding: 20px;
}

.privacy-tabs {
  margin-bottom: 10px;
}

.legal-article {
  color: var(--paper-ink);
}

.legal-article h2 {
  margin: 8px 0 8px;
  font-size: 24px;
  font-weight: 740;
  line-height: 1.25;
}

.legal-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
  margin-bottom: 22px;
  color: var(--paper-muted);
  font-size: 13px;
}

.legal-article p {
  margin: 0 0 16px;
  color: var(--paper-ink);
  font-size: 15px;
  line-height: 1.82;
}

@media (max-width: 760px) {
  .privacy-page {
    padding: 8px 0 24px;
  }

  .privacy-header {
    padding:
      0 max( env(safe-area-inset-right, 0px)) 0
      max( env(safe-area-inset-left, 0px));
  }

  .privacy-back-button {
    left: max( env(safe-area-inset-left, 0px));
  }

  .privacy-card :deep(.el-card__body) {
    padding:
      16px max(16px, env(safe-area-inset-right, 0px)) 18px
      max(16px, env(safe-area-inset-left, 0px));
  }

  .privacy-header h1 {
    font-size: 28px;
  }

  .legal-article h2 {
    font-size: 22px;
  }
}
</style>
