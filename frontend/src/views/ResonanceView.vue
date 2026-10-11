<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ActionIcon from '@/components/ActionIcon.vue'
import ResonancePostDialog from '@/components/ResonancePostDialog.vue'
import {
  getResonancePlazaApi,
  getResonanceTopicPlazaApi,
  getResonanceTopicsApi,
} from '@/api/resonance'

const BATCH_SIZE = 4
const PREVIEW_LENGTH = 110

const plazaCache = {
  topics: [],
  activeTopicId: null,
  posts: [],
  excludedIds: [],
  postBatches: [],
  batchIndex: -1,
  reachedEnd: false,
  loaded: false,
}

const topics = ref([])
const activeTopicId = ref(null)
const posts = ref([])
const excludedIds = ref([])
const postBatches = ref([])
const batchIndex = ref(-1)
const reachedEnd = ref(false)
const loading = ref(false)
const topicsLoading = ref(false)
const initializing = ref(!plazaCache.loaded)
const showPlazaSkeleton = ref(false)
let plazaSkeletonTimer = null
const topicMenuOpen = ref(false)
const topicPickerRef = ref(null)
const switching = ref(false)
const batchDirection = ref('next')
const postDialogVisible = ref(false)
const expandedPost = ref(null)
const mobilePostIndex = ref(0)
const mobileTouchStartX = ref(0)
const mobileTouchStartY = ref(0)
const suppressMobileCardClick = ref(false)
const plazaLoading = computed(() => loading.value || initializing.value)
const mobilePosts = computed(() => {
  const seenPostIds = new Set()

  return postBatches.value.flat().filter((post) => {
    if (!post?.id || seenPostIds.has(post.id)) {
      return false
    }

    seenPostIds.add(post.id)
    return true
  })
})
const mobileActivePost = computed(() => mobilePosts.value[mobilePostIndex.value] || null)
const mobileReachedEnd = computed(
  () =>
    reachedEnd.value &&
    mobilePosts.value.length > 0 &&
    mobilePostIndex.value >= mobilePosts.value.length - 1,
)

watch(
  plazaLoading,
  (isLoading) => {
    clearTimeout(plazaSkeletonTimer)
    plazaSkeletonTimer = null

    if (!isLoading) {
      showPlazaSkeleton.value = false
      return
    }

    plazaSkeletonTimer = setTimeout(() => {
      showPlazaSkeleton.value = true
    }, 180)
  },
  { immediate: true, flush: 'sync' },
)

const activeTopicName = computed(() => {
  if (!activeTopicId.value) {
    return '全部'
  }

  return topics.value.find((topic) => topic.id === activeTopicId.value)?.name || '此刻话题'
})

const emptyText = computed(() => {
  if (activeTopicId.value) {
    return '这个话题还很安静，等第一段愿意被听见的话。'
  }

  return '广场暂时还没有回声，可以先投递一段。'
})

const excludeIdsText = computed(() => excludedIds.value.join(','))

const loadTopics = async () => {
  if (topics.value.length > 0) {
    return
  }

  topicsLoading.value = true

  try {
    topics.value = await getResonanceTopicsApi({ silentError: true })
    plazaCache.topics = topics.value
  } finally {
    topicsLoading.value = false
  }
}

const requestPosts = async ({ resetExclude = false, resetHistory = false } = {}) => {
  loading.value = true
  switching.value = true

  try {
    if (resetExclude) {
      excludedIds.value = []
    }

    if (resetHistory) {
      postBatches.value = []
      batchIndex.value = -1
      reachedEnd.value = false
    }

    const params = {
      size: BATCH_SIZE,
      ...(excludeIdsText.value ? { excludeIds: excludeIdsText.value } : {}),
    }

    const nextPosts = activeTopicId.value
      ? await getResonanceTopicPlazaApi(activeTopicId.value, params)
      : await getResonancePlazaApi(params)

    const normalizedPosts = Array.isArray(nextPosts) ? nextPosts : []

    if (normalizedPosts.length === 0 && postBatches.value.length > 0) {
      reachedEnd.value = true
      plazaCache.reachedEnd = true
      return
    }

    posts.value = normalizedPosts
    postBatches.value = [...postBatches.value.slice(0, batchIndex.value + 1), posts.value]
    batchIndex.value = postBatches.value.length - 1
    reachedEnd.value = posts.value.length < BATCH_SIZE
    excludedIds.value = [
      ...new Set([...excludedIds.value, ...posts.value.map((post) => post.id).filter(Boolean)]),
    ]

    plazaCache.activeTopicId = activeTopicId.value
    plazaCache.posts = posts.value
    plazaCache.excludedIds = excludedIds.value
    plazaCache.postBatches = postBatches.value
    plazaCache.batchIndex = batchIndex.value
    plazaCache.reachedEnd = reachedEnd.value
    plazaCache.loaded = true
  } finally {
    loading.value = false
    window.setTimeout(() => {
      switching.value = false
    }, 220)
  }
}

const switchTopic = async (topicId) => {
  if (activeTopicId.value === topicId && plazaCache.loaded) {
    return
  }

  mobilePostIndex.value = 0
  activeTopicId.value = topicId
  await requestPosts({ resetExclude: true, resetHistory: true })
}

const selectTopic = async (topicId) => {
  topicMenuOpen.value = false
  await switchTopic(topicId)
}

const closeTopicMenuOnOutsideClick = (event) => {
  if (!topicMenuOpen.value) return
  if (topicPickerRef.value?.contains(event.target)) return

  topicMenuOpen.value = false
}

const syncCurrentBatch = () => {
  if (batchIndex.value >= 0) {
    postBatches.value[batchIndex.value] = posts.value
    plazaCache.postBatches = postBatches.value
  }
}

const showBatch = (nextIndex, direction) => {
  batchDirection.value = direction
  switching.value = true
  batchIndex.value = nextIndex
  posts.value = postBatches.value[nextIndex]
  plazaCache.posts = posts.value
  plazaCache.batchIndex = batchIndex.value
  window.setTimeout(() => {
    switching.value = false
  }, 220)
}

const showPreviousBatch = () => {
  if (batchIndex.value > 0) {
    showBatch(batchIndex.value - 1, 'previous')
  }
}

const showNextBatch = async () => {
  if (batchIndex.value < postBatches.value.length - 1) {
    showBatch(batchIndex.value + 1, 'next')
    return
  }

  if (reachedEnd.value) {
    return
  }

  batchDirection.value = 'next'
  await requestPosts()
}

const showNextMobilePost = async () => {
  if (loading.value || mobilePosts.value.length === 0) {
    return
  }

  const nextIndex = mobilePostIndex.value + 1

  if (nextIndex < mobilePosts.value.length) {
    mobilePostIndex.value = nextIndex
    return
  }

  if (reachedEnd.value) {
    return
  }

  await showNextBatch()
  mobilePostIndex.value = nextIndex < mobilePosts.value.length ? nextIndex : mobilePosts.value.length - 1
}

const handleMobileCardTouchStart = (event) => {
  const touch = event.touches?.[0]

  if (!touch) {
    return
  }

  mobileTouchStartX.value = touch.clientX
  mobileTouchStartY.value = touch.clientY
}

const handleMobileCardTouchEnd = async (event) => {
  const touch = event.changedTouches?.[0]

  if (!touch) {
    return
  }

  const deltaX = touch.clientX - mobileTouchStartX.value
  const deltaY = touch.clientY - mobileTouchStartY.value
  const isLeftSwipe = deltaX < -48 && Math.abs(deltaX) > Math.abs(deltaY) * 1.35

  if (!isLeftSwipe) {
    return
  }

  event.preventDefault()
  suppressMobileCardClick.value = true
  await showNextMobilePost()

  window.setTimeout(() => {
    suppressMobileCardClick.value = false
  }, 260)
}

const handleMobileCardClick = (post) => {
  if (suppressMobileCardClick.value) {
    suppressMobileCardClick.value = false
    return
  }

  openExpandedPost(post)
}

const formatPreview = (content) => {
  const safeContent = content || ''

  if (safeContent.length <= PREVIEW_LENGTH) {
    return safeContent
  }

  return `${safeContent.slice(0, PREVIEW_LENGTH).trimEnd()}...`
}

const openExpandedPost = (post) => {
  expandedPost.value = post
}

const closeExpandedPost = () => {
  expandedPost.value = null
}

const handleKeydown = (event) => {
  if (event.key === 'Escape' && expandedPost.value) {
    closeExpandedPost()
  }
}

const openPostDialog = () => {
  postDialogVisible.value = true
}

const handlePostSubmitted = (post) => {
  if (!activeTopicId.value || post.topicId === activeTopicId.value) {
    posts.value = [post, ...posts.value].slice(0, BATCH_SIZE)
    excludedIds.value = [...new Set([...excludedIds.value, post.id].filter(Boolean))]
    mobilePostIndex.value = 0
    plazaCache.posts = posts.value
    plazaCache.excludedIds = excludedIds.value
    syncCurrentBatch()
  }
}

onMounted(async () => {
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('click', closeTopicMenuOnOutsideClick)

  if (plazaCache.loaded) {
    initializing.value = false
    topics.value = plazaCache.topics
    activeTopicId.value = plazaCache.activeTopicId
    posts.value = plazaCache.posts
    excludedIds.value = plazaCache.excludedIds
    postBatches.value = plazaCache.postBatches
    batchIndex.value = plazaCache.batchIndex
    reachedEnd.value = plazaCache.reachedEnd
    if (topics.value.length === 0) {
      try {
        await loadTopics()
      } catch {
        // 话题列表加载失败时，仍保留已缓存的广场内容。
      }
    }
    return
  }

  try {
    await loadTopics()
  } catch {
    // 话题列表是辅助筛选项，不应阻塞“全部共鸣”的加载。
  }
  try {
    await requestPosts({ resetExclude: true, resetHistory: true })
  } finally {
    initializing.value = false
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('click', closeTopicMenuOnOutsideClick)
  clearTimeout(plazaSkeletonTimer)
})
</script>

<template>
  <main class="resonance-page">
    <section class="resonance-toolbar" aria-label="共鸣广场操作">
      <div ref="topicPickerRef" class="topic-picker">
        <div class="active-topic">
          <strong>{{ activeTopicName }}</strong>
        </div>

        <button
          class="topic-menu-toggle"
          type="button"
          :aria-label="topicMenuOpen ? '收起话题选择' : '展开话题选择'"
          :aria-expanded="topicMenuOpen"
          aria-controls="resonance-topic-menu"
          @click="topicMenuOpen = !topicMenuOpen"
        >
          <span class="topic-menu-chevron" :class="{ open: topicMenuOpen }" aria-hidden="true"></span>
        </button>

        <transition name="topic-menu">
          <div v-if="topicMenuOpen" id="resonance-topic-menu" class="topic-menu">
            <button
              class="topic-option"
              :class="{ active: activeTopicId === null }"
              type="button"
              @click="selectTopic(null)"
            >
              全部
            </button>
            <button
              v-for="topic in topics"
              :key="topic.id"
              class="topic-option"
              :class="{ active: activeTopicId === topic.id }"
              type="button"
              @click="selectTopic(topic.id)"
            >
              {{ topic.name }}
            </button>
            <span v-if="topicsLoading" class="topic-loading">正在取回话题</span>
          </div>
        </transition>
      </div>

      <div class="hero-actions">
        <el-button class="post-button" type="primary" @click="openPostDialog">投递一段</el-button>
      </div>
    </section>

    <section class="resonance-stage" :aria-label="activeTopicName">
      <button
        class="batch-button batch-button-previous"
        type="button"
        aria-label="查看上一批匿名共鸣"
        :disabled="loading || batchIndex <= 0"
        @click="showPreviousBatch"
      >
        <span aria-hidden="true"></span>
      </button>

      <button
        class="batch-button batch-button-next"
        type="button"
        aria-label="换一批匿名共鸣"
        :disabled="loading || (batchIndex === postBatches.length - 1 && reachedEnd)"
        @click="showNextBatch"
      >
        <span aria-hidden="true"></span>
      </button>

      <div v-if="plazaLoading && posts.length === 0" class="resonance-loading-frame">
        <el-skeleton v-if="showPlazaSkeleton" :rows="8" animated />
      </div>

      <div v-else-if="posts.length === 0" class="resonance-empty">
        <h2>{{ activeTopicName }}</h2>
        <p>{{ emptyText }}</p>
        <button class="empty-post-link" type="button" @click="openPostDialog">投递一段</button>
      </div>

      <transition :name="`echo-page-${batchDirection}`" mode="out-in">
        <div
          v-if="posts.length > 0"
          :key="`${activeTopicId || 'all'}-${batchIndex}`"
          class="echo-grid desktop-echo-grid"
          :class="{ switching }"
        >
          <button
            v-for="post in posts"
            :key="post.id"
            class="echo-slip"
            type="button"
            aria-label="展开查看完整共鸣"
            @click="openExpandedPost(post)"
          >
            <p>{{ formatPreview(post.content) }}</p>
            <span v-if="post.topicName">{{ post.topicName }}</span>
          </button>
        </div>
      </transition>

      <div v-if="mobileActivePost" class="mobile-echo-stage">
        <div class="mobile-echo-stack">
          <div class="mobile-echo-backup-card" aria-hidden="true"></div>

          <transition name="mobile-echo-card" mode="out-in">
            <button
              :key="mobileActivePost.id"
              class="echo-slip mobile-echo-card"
              type="button"
              aria-label="展开查看完整共鸣"
              @touchstart="handleMobileCardTouchStart"
              @touchend="handleMobileCardTouchEnd"
              @click="handleMobileCardClick(mobileActivePost)"
            >
              <p>{{ formatPreview(mobileActivePost.content) }}</p>
              <span v-if="mobileActivePost.topicName">{{ mobileActivePost.topicName }}</span>
            </button>
          </transition>
        </div>

        <button
          class="mobile-next-button"
          type="button"
          :disabled="loading || mobileReachedEnd"
          @click="showNextMobilePost"
        >
          {{ loading ? '正在取回' : mobileReachedEnd ? '无更多投递' : '下一段' }}
        </button>
      </div>
    </section>

    <Teleport to="body">
      <transition name="echo-detail">
        <div v-if="expandedPost" class="echo-detail-backdrop" @click.self="closeExpandedPost">
          <article class="echo-detail-card" role="dialog" aria-modal="true" aria-label="完整共鸣">
            <button class="echo-detail-close" type="button" aria-label="关闭完整共鸣" @click="closeExpandedPost">
              <span aria-hidden="true"></span>
              <span aria-hidden="true"></span>
            </button>
            <div class="echo-detail-body">
              <p>{{ expandedPost.content }}</p>
              <span v-if="expandedPost.topicName">{{ expandedPost.topicName }}</span>
            </div>
          </article>
        </div>
      </transition>
    </Teleport>

    <ResonancePostDialog
      v-model="postDialogVisible"
      :initial-topic-id="activeTopicId"
      @submitted="handlePostSubmitted"
    />

  </main>
</template>

<style scoped>
.resonance-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 20px;
  height: calc(100dvh - 150px);
  overflow: hidden;
}

.resonance-toolbar {
  position: relative;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 2px 0 4px;
}

.topic-picker {
  position: relative;
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 16px;
}

.active-topic {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 10px;
}

.active-topic strong {
  overflow: hidden;
  color: var(--paper-ink);
  font-size: 20px;
  font-weight: 400;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topic-menu-toggle,
.topic-option {
  border: 0;
  background: transparent;
  color: var(--paper-accent-dark);
  cursor: pointer;
  font: inherit;
}

.topic-menu-toggle {
  display: inline-flex;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0;
  font-size: 15px;
  font-weight: 400;
  border-radius: 5px;
  transition:
    background-color 160ms ease,
    color 160ms ease;
}

.topic-menu-toggle:hover,
.topic-menu-toggle:focus-visible {
  background: rgba(239, 238, 236, 0.72);
  color: var(--paper-ink);
}

.topic-menu-chevron {
  width: 7px;
  height: 7px;
  border-right: 1px solid currentColor;
  border-bottom: 1px solid currentColor;
  transform: translateY(-2px) rotate(45deg);
  transition: transform 160ms ease;
}

.topic-menu-chevron.open {
  transform: translateY(2px) rotate(225deg);
}

.topic-menu {
  position: absolute;
  z-index: 20;
  top: 0;
  left: 0;
  display: grid;
  min-width: 180px;
  width: max-content;
  max-width: min(560px, calc(100vw - 48px));
  max-height: min(220px, calc(100vh - 120px));
  gap: 0;
  padding: 0;
  border: 1px solid rgba(47, 43, 38, 0.18);
  border-radius: 5px;
  background: #fff;
  box-shadow:
    0 8px 18px rgba(47, 43, 38, 0.14),
    0 2px 6px rgba(47, 43, 38, 0.08);
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  transform-origin: 18px 18px;
  scrollbar-width: thin;
  scrollbar-color: rgba(138, 117, 99, 0.28) transparent;
}

.topic-menu-enter-active,
.topic-menu-leave-active {
  transition:
    opacity 160ms ease,
    transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

.topic-menu-enter-from,
.topic-menu-leave-to {
  opacity: 0;
  transform: scale(0.96);
}

.topic-option {
  min-height: 38px;
  padding: 0 20px;
  color: var(--paper-ink);
  font-size: 14px;
  font-weight: 400;
  line-height: 1;
  text-align: left;
  white-space: nowrap;
  transition:
    background-color 160ms ease,
    color 160ms ease;
}

.topic-option:hover,
.topic-option:focus-visible {
  /* background: rgba(239, 238, 236, 0.86); */
  background: rgba(138, 117, 99, 0.07);

  color: var(--paper-ink);
}

.topic-option.active {
  color: var(--paper-ink);
  font-weight: 400;
}

.hero-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 14px;
}

.post-button {
  font-weight: 400;
}

.topic-loading {
  color: var(--paper-muted);
  font-size: 13px;
  white-space: nowrap;
}

.resonance-stage {
  --echo-card-half-height: clamp(220px, 29vh, 270px);
  --echo-card-height: clamp(440px, 58vh, 540px);
  --echo-grid-offset: 28px;
  --echo-arrow-space: 44px;
  position: relative;
  min-height: 0;
  overflow: visible;
  padding: 0 16px;
}

.echo-grid {
  display: grid;
  width: calc(100% - var(--echo-arrow-space) - var(--echo-arrow-space));
  grid-template-columns: repeat(4, minmax(0, 258px));
  justify-content: center;
  gap: clamp(8px, 1vw, 14px);
  align-items: start;
  margin-left: var(--echo-arrow-space);
  padding: var(--echo-grid-offset) 0 18px;
}

.mobile-echo-stream,
.mobile-echo-stage {
  display: none;
}

.echo-slip {
  position: relative;
  display: flex;
  min-height: var(--echo-card-height);
  flex-direction: column;
  justify-content: space-between;
  border: 0;
  border-radius: 10px;
  background: var(--paper-card);
  color: var(--paper-ink);
  cursor: pointer;
  font: inherit;
  padding: 38px 30px 28px;
  text-align: center;
  box-shadow: none;
  transition:
    box-shadow 180ms ease,
    transform 180ms ease;
}

.echo-slip::after {
  position: absolute;
  right: 14px;
  bottom: 12px;
  left: 14px;
  height: 1px;
  background: rgba(138, 117, 99, 0.08);
  content: '';
}

.echo-slip p {
  margin: auto 0;
  color: #2F2B26;
  line-height: 1.72;
  white-space: pre-wrap;
}

.echo-slip span {
  align-self: center;
  margin-top: 18px;
  color: var(--paper-accent-dark);
  font-size: 13px;
  font-weight: 700;
}

.echo-slip:hover,
.echo-slip:focus-visible {
  box-shadow:
    0 16px 25px rgba(47, 43, 38, 0.16),
    0 4px 12px rgba(47, 43, 38, 0.08);
  outline: none;
  transform: translateY(-2px);
}

.echo-slip:focus-visible {
  box-shadow:
    0 0 0 3px rgba(138, 117, 99, 0.14),
    0 16px 25px rgba(47, 43, 38, 0.16),
    0 4px 12px rgba(47, 43, 38, 0.08);
}

.echo-detail-backdrop {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(47, 43, 38, 0.16);
  backdrop-filter: blur(8px);
}

.echo-detail-card {
  position: relative;
  display: grid;
  width: min(90vw, 560px);
  max-width: 560px;
  max-height: min(78vh, 680px);
  grid-template-rows: minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid rgba(138, 117, 99, 0.16);
  border-radius: 10px;
  background: #fffdf7;
  color: var(--paper-ink);
  padding: 58px 44px 42px;
  box-shadow: 0 30px 70px -28px rgba(47, 43, 38, 0.48);
  text-align: center;
}

.echo-detail-body {
  display: flex;
  max-width: 420px;
  min-height: 0;
  max-height: calc(min(78vh, 680px) - 100px);
  flex-direction: column;
  justify-self: center;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 4px 12px 8px;
  scrollbar-gutter: stable;
}

.echo-detail-body p {
  margin: auto 0;
  line-height: 1.86;
  white-space: pre-wrap;
}

.echo-detail-body > span {
  margin-top: 26px;
  color: var(--paper-accent-dark);
  font-size: 13px;
  font-weight: 700;
}

.echo-detail-close {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--paper-accent-dark);
  cursor: pointer;
}

.echo-detail-close span {
  position: absolute;
  top: 19px;
  left: 11px;
  width: 18px;
  height: 1px;
  background: currentColor;
  transform: rotate(45deg);
}

.echo-detail-close span + span {
  transform: rotate(-45deg);
}

.echo-detail-close:hover,
.echo-detail-close:focus-visible {
  background: rgba(138, 117, 99, 0.06);
  outline: none;
}

.batch-button {
  position: absolute;
  top: calc(var(--echo-grid-offset) + var(--echo-card-half-height));
  z-index: 2;
  display: grid;
  width: 44px;
  height: 104px;
  place-items: center;
  border: 0;
  background: transparent;
  cursor: pointer;
  opacity: 0.34;
  transform: translateY(-50%);
  transition:
    background-color 160ms ease,
    opacity 160ms ease;
}

.batch-button-previous {
  left: 0;
}

.batch-button-next {
  right: 0;
}

.batch-button:hover,
.batch-button:focus-visible {
  background: transparent;
  outline: none;
  opacity: 0.9;
}

.batch-button:disabled {
  cursor: default;
  opacity: 0.36;
}

.batch-button span {
  width: 14px;
  height: 14px;
  border-top: 2px solid var(--paper-accent-dark);
  border-right: 2px solid var(--paper-accent-dark);
  transform: rotate(45deg);
}

.batch-button:disabled::after {
  position: absolute;
  width: 2px;
  height: 18px;
  background: var(--paper-accent-dark);
  content: '';
}

.batch-button-previous:disabled::after {
  left: 8px;
}

.batch-button-next:disabled::after {
  right: 8px;
}

.batch-button-previous span {
  transform: rotate(225deg);
}

.resonance-empty {
  display: grid;
  min-height: var(--echo-card-height);
  align-content: center;
  justify-items: center;
  gap: 12px;
  margin-top: var(--echo-grid-offset);
  text-align: center;
}

.resonance-loading-frame {
  min-height: var(--echo-card-height);
  margin-top: var(--echo-grid-offset);
}

.resonance-empty h2 {
  margin: 0;
  color: var(--paper-ink);
  font-size: 24px;
  font-weight: 740;
}

.resonance-empty p {
  max-width: 420px;
  margin: 0;
  color: var(--paper-muted);
}

.empty-post-link {
  border: 0;
  border-bottom: 1px solid rgba(138, 117, 99, 0.34);
  background: transparent;
  color: var(--paper-accent-dark);
  cursor: pointer;
  font-family: inherit;
  font-size: inherit;
  font-style: inherit;
  font-weight: 400;
  padding: 5px 1px 3px;
}

.empty-post-link:hover,
.empty-post-link:focus-visible {
  border-bottom-color: var(--paper-accent-dark);
  outline: none;
}

.echo-page-next-enter-active,
.echo-page-next-leave-active,
.echo-page-previous-enter-active,
.echo-page-previous-leave-active {
  transition:
    opacity 220ms ease,
    transform 220ms ease;
}

.echo-page-next-enter-from {
  opacity: 0;
  transform: translateX(22px);
}

.echo-page-next-leave-to {
  opacity: 0;
  transform: translateX(-12px);
}

.echo-page-previous-enter-from {
  opacity: 0;
  transform: translateX(-22px);
}

.echo-page-previous-leave-to {
  opacity: 0;
  transform: translateX(12px);
}

.echo-detail-enter-active,
.echo-detail-leave-active {
  transition: opacity 220ms ease;
}

.echo-detail-enter-active .echo-detail-card,
.echo-detail-leave-active .echo-detail-card {
  transition: transform 220ms ease;
}

.echo-detail-enter-from,
.echo-detail-leave-to {
  opacity: 0;
}

.echo-detail-enter-from .echo-detail-card,
.echo-detail-leave-to .echo-detail-card {
  transform: translateY(18px) scale(0.96);
}

@media (max-width: 980px) {
  .echo-grid {
    width: auto;
    grid-template-columns: repeat(4, 244px);
    justify-content: start;
    overflow-x: auto;
    padding-right: 18px;
    padding-left: 18px;
    gap: 8px;
  }
}

@media (max-width: 720px) {
  .resonance-page {
    gap: 12px;
    height: 100%;
    min-height: 0;
    overflow: hidden;
  }

  .resonance-toolbar {
    position: relative;
    top: auto;
    z-index: 8;
    align-items: center;
    gap: 12px;
    margin-right: -16px;
    margin-left: -16px;
    padding: 8px 16px;
    background: #fff;
  }

  .topic-picker {
    flex: 1 1 auto;
    justify-content: flex-start;
    gap: 12px;
  }

  .active-topic strong {
    font-size: 18px;
  }

  .topic-menu {
    right: 0;
    max-width: none;
    width: max-content;
    max-width: calc(100vw - 32px);
  }

  .hero-actions {
    flex: 0 0 auto;
  }

  .resonance-stage {
    --echo-grid-offset: 14px;
    display: grid;
    min-height: 0;
    align-items: center;
    justify-items: center;
    overflow: hidden;
    padding: 0;
  }

  .desktop-echo-grid,
  .resonance-stage > .batch-button {
    display: none;
  }

  .mobile-echo-stage {
    display: grid;
    width: 100%;
    height: 100%;
    min-height: 0;
    align-content: center;
    justify-items: center;
    gap: 35px;
    padding: 0 0 4px;
  }

  .mobile-echo-stack {
    position: relative;
    width: min(72vw, 300px);
    max-width: calc(100vw - 72px);
    aspect-ratio: 9 / 15.6;
  }

  .mobile-echo-backup-card {
    position: absolute;
    z-index: 2;
    inset: 0;
    border-radius: 10px;
    background: var(--paper-card);
    box-shadow: 0 10px 22px rgba(47, 43, 38, 0.08);
    content: '';
    pointer-events: none;
    transform: none;
  }

  .mobile-echo-stack::before,
  .mobile-echo-stack::after {
    position: absolute;
    inset: 2px;
    border-radius: 10px;
    content: '';
    pointer-events: none;
    transform-origin: 50% 58%;
  }

  .mobile-echo-stack::before {
    z-index: 1;
    background: #EFEEEC;
    box-shadow: 0 12px 24px rgba(47, 43, 38, 0.12);
    transform: translate(15px, 10px) rotate(4deg) scale(0.97);
  }

  .mobile-echo-stack::after {
    z-index: 0;
    background: #DED8D1;
    box-shadow: 0 10px 20px rgba(47, 43, 38, 0.1);
    transform: translate(-14px, 18px) rotate(-5deg) scale(0.93);
  }

  .echo-slip {
    position: absolute;
    z-index: 3;
    inset: 0;
    width: 100%;
    min-height: 0;
    padding: 34px 26px 28px;
    text-align: center;
    touch-action: pan-y;
  }

  .echo-slip p {
    margin: auto 0;
    line-height: 1.78;
  }

  .echo-slip span {
    align-self: center;
    margin-top: 18px;
  }

  .mobile-next-button {
    display: grid;
    min-width: 116px;
    min-height: 40px;
    place-items: center;
    border: 0;
    border-radius: 5px;
    background: rgba(239, 238, 236, 0.72);
    color: var(--paper-ink);
    cursor: pointer;
    font: inherit;
    font-size: 15px;
    font-weight: 400;
    padding: 0 18px;
    transition:
      background-color 160ms ease,
      transform 160ms ease;
  }

  .mobile-next-button:hover,
  .mobile-next-button:focus-visible {
    background: rgba(239, 238, 236, 0.72);
    outline: none;
    transform: translateY(-1px);
  }

  .mobile-next-button:disabled {
    cursor: default;
    opacity: 0.42;
    transform: none;
  }

  .mobile-echo-card-enter-active,
  .mobile-echo-card-leave-active {
    transition:
      opacity 220ms ease,
      transform 240ms cubic-bezier(0.22, 1, 0.36, 1);
  }

  .mobile-echo-card-enter-from {
    opacity: 0;
    transform: scale(0.96);
  }

  .mobile-echo-card-enter-to,
  .mobile-echo-card-leave-from {
    opacity: 1;
    transform: scale(1);
  }

  .mobile-echo-card-leave-to {
    opacity: 0;
    transform: scale(0.99);
  }

  .echo-detail-card {
    width: min(92vw, 520px);
    max-height: min(78vh, 560px);
    padding: 56px 30px 40px;
  }

  .echo-detail-body {
    max-height: calc(min(78vh, 560px) - 96px);
  }

}

@keyframes mobile-loading-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .echo-page-next-enter-active,
  .echo-page-next-leave-active,
  .echo-page-previous-enter-active,
  .echo-page-previous-leave-active,
  .echo-detail-enter-active,
  .echo-detail-leave-active,
  .mobile-echo-card-enter-active,
  .mobile-echo-card-leave-active,
  .topic-menu-enter-active,
  .topic-menu-leave-active {
    transition: opacity 120ms ease;
  }

  .echo-page-next-enter-from,
  .echo-page-next-leave-to,
  .echo-page-previous-enter-from,
  .echo-page-previous-leave-to,
  .echo-detail-enter-from .echo-detail-card,
  .echo-detail-leave-to .echo-detail-card,
  .mobile-echo-card-enter-from,
  .mobile-echo-card-leave-to,
  .topic-menu-enter-from,
  .topic-menu-leave-to {
    transform: none;
  }

  .mobile-loading-spinner {
    animation-duration: 1400ms;
  }
}
</style>
