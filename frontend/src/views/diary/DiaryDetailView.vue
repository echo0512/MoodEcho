<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteDiaryApi, getDiaryDetailApi } from '@/api/diary'
import ActionIcon from '@/components/ActionIcon.vue'
import DiaryContentRenderer from '@/components/DiaryContentRenderer.vue'
import DiaryImageViewer from '@/components/DiaryImageViewer.vue'
import DiaryUnsavedConfirm from '@/components/DiaryUnsavedConfirm.vue'
import ResonancePostDialog from '@/components/ResonancePostDialog.vue'
import { useDiaryImagePreview } from '@/composables/useDiaryImagePreview'
import { getDiaryCharacterCount, MAX_DIARY_CONTENT_LENGTH } from '@/utils/diaryContent'

const MAX_RESONANCE_CONTENT_LENGTH = 500
const BLOCK_ELEMENT_NAMES = new Set(['DIV', 'P', 'LI', 'BLOCKQUOTE', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6'])

const props = defineProps({
  embedded: {
    type: Boolean,
    default: false,
  },
  diaryId: {
    type: [Number, String],
    default: null,
  },
  initialDiary: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['close', 'deleted', 'edit'])

const diary = ref(null)
const loading = ref(false)
const contentRootRef = ref(null)
const titleRef = ref(null)
const resonanceDialogVisible = ref(false)
const deleteConfirmVisible = ref(false)
const selectedResonanceContent = ref('')
const selectionBubble = ref({
  visible: false,
  x: 0,
  y: 0,
})
let hasShownSelectionLengthWarning = false

const diaryId = computed(() => props.diaryId)
const contentLength = computed(() => getDiaryCharacterCount(diary.value?.content || ''))
const contentTooLong = computed(() => contentLength.value > MAX_DIARY_CONTENT_LENGTH)

const { previewVisible, previewUrls, previewInitialIndex, openImagePreview } = useDiaryImagePreview()

const applyInitialDiary = () => {
  if (!props.initialDiary || String(props.initialDiary.id) !== String(diaryId.value)) {
    return false
  }

  diary.value = props.initialDiary
  return true
}

const loadDiary = async () => {
  if (applyInitialDiary()) {
    return
  }

  loading.value = true

  try {
    diary.value = await getDiaryDetailApi(diaryId.value)
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  emit('close')
}

const goEdit = () => {
  emit('edit', diaryId.value)
}

const openDeleteConfirm = () => {
  deleteConfirmVisible.value = true
}

const deleteDiary = async () => {
  deleteConfirmVisible.value = false
  await deleteDiaryApi(diaryId.value)

  ElMessage.success('日记已删除')
  emit('deleted')
}

const openContentImagePreview = async (imageId) => {
  await openImagePreview(diary.value?.images || [], imageId)
}

const hideSelectionBubble = () => {
  selectionBubble.value.visible = false
}

const getSelectedText = (range) => {
  const fragment = range.cloneContents()
  const container = document.createElement('div')

  container.appendChild(fragment)
  container.querySelectorAll('img, .image-part, .image-error').forEach((element) => element.remove())

  const readText = (node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      return node.textContent || ''
    }

    if (node.nodeType !== Node.ELEMENT_NODE) {
      return ''
    }

    if (node.nodeName === 'BR') {
      return '\n'
    }

    const text = Array.from(node.childNodes).map(readText).join('')

    return BLOCK_ELEMENT_NAMES.has(node.nodeName) ? `${text}\n` : text
  }

  return Array.from(container.childNodes)
    .map(readText)
    .join('')
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n[ \t]+/g, '\n')
    .replace(/\n{2,}/g, '\n')
    .trim()
}

const getContentSelection = () => {
  const selection = window.getSelection()

  if (!selection || selection.rangeCount === 0) {
    return null
  }

  const contentRoot = contentRootRef.value
  const anchorNode = selection.anchorNode
  const focusNode = selection.focusNode

  if (
    !contentRoot ||
    !anchorNode ||
    !focusNode ||
    !contentRoot.contains(anchorNode) ||
    !contentRoot.contains(focusNode)
  ) {
    return null
  }

  const range = selection.getRangeAt(0)
  const selectedText = getSelectedText(range)

  if (!selectedText) {
    return null
  }

  return { range, selectedText }
}

const isTitleSelected = () => {
  const selection = window.getSelection()
  const title = titleRef.value

  if (!selection || selection.rangeCount === 0 || selection.isCollapsed || !title) {
    return false
  }

  return selection.getRangeAt(0).intersectsNode(title)
}

const isSelectionTooLong = (selectedText) =>
  selectedText.replace(/\s+/g, ' ').length > MAX_RESONANCE_CONTENT_LENGTH

const updateSelectionLengthWarning = () => {
  const contentSelection = getContentSelection()

  if (!contentSelection) {
    hasShownSelectionLengthWarning = false
    return
  }

  if (!isSelectionTooLong(contentSelection.selectedText)) {
    hasShownSelectionLengthWarning = false
    return
  }

  hideSelectionBubble()

  if (!hasShownSelectionLengthWarning) {
    ElMessage.warning('投递内容不能超过 500 字')
    hasShownSelectionLengthWarning = true
  }
}

const updateSelectionBubble = () => {
  if (isTitleSelected()) {
    hideSelectionBubble()
    ElMessage.warning('只能选择正文内容')
    return
  }

  const contentSelection = getContentSelection()

  if (!contentSelection) {
    hideSelectionBubble()
    hasShownSelectionLengthWarning = false
    return
  }

  const { range, selectedText } = contentSelection
  const rect = range.getBoundingClientRect()

  if (rect.width === 0 && rect.height === 0) {
    hideSelectionBubble()
    hasShownSelectionLengthWarning = false
    return
  }

  if (isSelectionTooLong(selectedText)) {
    hideSelectionBubble()
    if (!hasShownSelectionLengthWarning) {
      ElMessage.warning('投递内容不能超过 500 字')
      hasShownSelectionLengthWarning = true
    }
    return
  }

  hasShownSelectionLengthWarning = false
  selectedResonanceContent.value = selectedText
  selectionBubble.value = {
    visible: true,
    x: Math.min(window.innerWidth - 96, rect.right + 12),
    y: Math.max(18, rect.top + rect.height / 2),
  }
}

const openResonanceDialogFromSelection = () => {
  if (!selectedResonanceContent.value.trim()) {
    hideSelectionBubble()
    return
  }

  resonanceDialogVisible.value = true
  hideSelectionBubble()
  window.getSelection()?.removeAllRanges()
}

const loadPage = async () => {
  await loadDiary()
}

onMounted(() => {
  loadPage()
  document.addEventListener('selectionchange', updateSelectionLengthWarning)
  document.addEventListener('mouseup', updateSelectionBubble)
})

onBeforeUnmount(() => {
  document.removeEventListener('selectionchange', updateSelectionLengthWarning)
  document.removeEventListener('mouseup', updateSelectionBubble)
})

watch(
  () => diaryId.value,
  () => {
    diary.value = null
    selectedResonanceContent.value = ''
    hideSelectionBubble()
    loadPage()
  },
)
</script>

<template>
  <main class="diary-page" :class="{ embedded }">
    <section class="quick-record">
      <el-card class="record-card detail-card" shadow="never">
        <el-skeleton v-if="loading" :rows="8" animated />

        <template v-else-if="diary">
          <div class="diary-count-badge" :class="{ invalid: contentTooLong }">
            {{ contentLength }}/{{ MAX_DIARY_CONTENT_LENGTH }}
          </div>

          <article class="record-detail">
            <p class="date-text">{{ diary.recordDate }}</p>
            <h1 ref="titleRef" class="record-title" :class="{ empty: !diary.title }">
              {{ diary.title || ' ' }}
            </h1>

            <div
              ref="contentRootRef"
              class="content-field selectable-diary-content"
              @keyup="updateSelectionBubble"
              @touchend="updateSelectionBubble"
              @scroll="hideSelectionBubble"
            >
              <DiaryContentRenderer
                :content="diary.content"
                :images="diary.images || []"
                @image-click="openContentImagePreview"
              />
            </div>

            <div class="record-footer">
              <div class="record-delete">
                <el-button
                  class="record-button delete-button"
                  aria-label="删除"
                  title="删除"
                  @click="openDeleteConfirm"
                >
                  <ActionIcon name="trash" />
                </el-button>
              </div>

              <div class="record-actions">
                <el-button class="record-button" aria-label="返回" title="返回" @click="goBack">
                  <ActionIcon name="arrow-left" />
                </el-button>
                <el-button class="record-button" type="primary" aria-label="编辑" title="编辑" @click="goEdit">
                  <ActionIcon name="pencil" />
                </el-button>
              </div>
            </div>
          </article>
        </template>
      </el-card>
    </section>

    <DiaryImageViewer
      v-model="previewVisible"
      :url-list="previewUrls"
      :initial-index="previewInitialIndex"
    />

    <DiaryUnsavedConfirm
      :visible="deleteConfirmVisible"
      title="确定删除这篇日记？"
      message="删除后，这篇日记不会再出现在列表中。"
      stay-text="取消"
      confirm-text="删除"
      danger
      @stay="deleteConfirmVisible = false"
      @confirm="deleteDiary"
    />

    <button
      v-if="selectionBubble.visible"
      class="selection-resonance-button"
      type="button"
      aria-label="匿名投递"
      title="匿名投递"
      :style="{
        left: `${selectionBubble.x}px`,
        top: `${selectionBubble.y}px`,
      }"
      @mousedown.prevent
      @click="openResonanceDialogFromSelection"
    >
      投递
    </button>

    <ResonancePostDialog
      v-model="resonanceDialogVisible"
      title="从这篇日记投递一段"
      :diary-id="diaryId"
      :initial-content="selectedResonanceContent"
    />
  </main>
</template>

<style scoped>
.diary-page {
  display: flex;
  min-width: 360px;
  height: calc(100dvh - 150px);
  min-height: 640px;
  align-items: center;
  justify-content: center;
  overflow: visible;
}

.diary-page.embedded {
  width: min(100%, 640px);
  min-width: 0;
  height: min(88vh, 780px);
  min-height: min(88vh, 640px);
}

.diary-page.embedded .quick-record {
  width: 100%;
}

.quick-record {
  display: flex;
  width: min(100%, 1020px);
  height: 100%;
  align-items: center;
  justify-content: center;
}

.record-card {
  position: relative;
  width: min(100%, 590px);
  height: clamp(620px, calc(100dvh - 184px), 760px);
  min-height: 620px;
  border: 0;
  border-radius: 10px;
  background: var(--paper-card);
  box-shadow: none;
  overflow: hidden;
}

.record-card :deep(.el-card__body) {
  height: 100%;
  position: relative;
  padding: 34px 38px 30px;
}

.diary-count-badge {
  position: absolute;
  top: 30px;
  right: 38px;
  z-index: 3;
  color: var(--paper-muted);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.3;
}

.diary-count-badge.invalid {
  color: #9A352C;
}

.record-detail {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding-right: 4px;
}

.date-text {
  margin: 0 0 10px;
  color: var(--paper-muted);
  font-size: 15px;
  font-weight: 650;
  line-height: 26px;
  padding-left: 2px;
}

.record-title {
  margin: 0 0 12px;
  padding: 0 0 6px;
  border-bottom: 1px solid transparent;
  color: var(--paper-ink);
  font-size: 18px;
  font-weight: 500;
  line-height: 32px;
}

.content-field {
  grid-row: 3;
  min-height: 0;
  overflow-y: auto;
  box-sizing: border-box;
  padding: 0 8px 18px 0;
  scrollbar-gutter: stable;
}

.content-field :deep(.diary-content-renderer) {
  line-height: 1.8;
}

.record-footer {
  display: flex;
  grid-row: 4;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  padding-top: 4px;
}

.record-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}

.record-delete {
  display: flex;
  min-height: 1lh;
  align-items: center;
}

.record-button {
  min-width: 0;
  width: 40px;
  height: 40px;
  border: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  color: var(--paper-accent-dark) !important;
  font-size: 17px;
  font-weight: 740;
  padding: 0 !important;
}

.record-button:hover,
.record-button:focus-visible {
  color: #413A33 !important;
  background: transparent !important;
}

.delete-button {
  color: #a83f3f !important;
}

.delete-button:hover,
.delete-button:focus-visible {
  color: #842f2f !important;
}

.selectable-diary-content {
  position: relative;
}

.selection-resonance-button {
  position: fixed;
  z-index: 20;
  min-height: 36px;
  border: 0;
  background: #fffdf7;
  color: var(--paper-accent-dark);
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 740;
  line-height: 1;
  padding: 6px 12px;
  transform: translateY(-50%);
  box-shadow: 0 8px 20px rgba(47, 43, 38, 0.14);
  white-space: nowrap;
}

.selection-resonance-button:hover,
.selection-resonance-button:focus-visible {
  background: rgba(255, 253, 247, 0.96);
}

@media (max-width: 900px) {
  .diary-page {
    height: auto;
    min-height: max(640px, calc(100dvh - 126px));
  }

  .record-card {
    height: clamp(580px, calc(100dvh - 184px), 700px);
    min-height: 580px;
  }

  .record-card :deep(.el-card__body) {
    padding: 26px 20px 24px;
  }

  .diary-count-badge {
    top: 22px;
    right: 20px;
  }
}
</style>
