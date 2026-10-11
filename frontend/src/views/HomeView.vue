<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ActionIcon from '@/components/ActionIcon.vue'
import DiaryDateSelect from '@/components/DiaryDateSelect.vue'
import DiaryImageViewer from '@/components/DiaryImageViewer.vue'
import DiaryRichEditor from '@/components/DiaryRichEditor.vue'
import { useDiaryImagePreview } from '@/composables/useDiaryImagePreview'
import {
  getDiaryCharacterCount,
  isDiaryBlank,
  isDiaryContentTooLong,
  MAX_DIARY_CONTENT_LENGTH,
  MAX_DIARY_IMAGE_COUNT,
} from '@/utils/diaryContent'
import {
  createDiaryApi,
  createDiaryDraftApi,
  deleteDiaryImageApi,
  publishDiaryDraftApi,
  uploadDiaryImageApi,
} from '@/api/diary'

const formRef = ref(null)
const editorRef = ref(null)
const loading = ref(false)
const uploading = ref(false)
const saving = ref(false)
const { previewVisible, previewUrls, previewInitialIndex, openImagePreview } = useDiaryImagePreview()

const formatDate = (date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

const getToday = () => formatDate(new Date())
const today = getToday()
const homeDraftStorageKey = 'moodecho.homeDraft'

const loadHomeDraftState = () => {
  try {
    const rawValue = sessionStorage.getItem(homeDraftStorageKey)

    return rawValue ? JSON.parse(rawValue) : null
  } catch {
    return null
  }
}

const savedHomeDraftState = loadHomeDraftState()
const recordDateAutoUpdateEnabled = ref(savedHomeDraftState?.recordDateAutoUpdateEnabled ?? true)

const form = reactive({
  title: savedHomeDraftState?.form?.title || '',
  content: savedHomeDraftState?.form?.content || '',
  recordDate: recordDateAutoUpdateEnabled.value
    ? getToday()
    : savedHomeDraftState?.form?.recordDate || today,
})

const draftDiaryId = ref(savedHomeDraftState?.draftDiaryId || null)
const images = ref(savedHomeDraftState?.images || [])
const currentImageIds = ref(new Set(savedHomeDraftState?.currentImageIds || []))

const visibleImages = computed(() =>
  images.value.filter((image) => currentImageIds.value.has(String(image.id))),
)
const contentLength = computed(() => getDiaryCharacterCount(form.content))
const contentTooLong = computed(() => contentLength.value > MAX_DIARY_CONTENT_LENGTH)

const validateContentLength = (_rule, value, callback) => {
  if (isDiaryContentTooLong(value)) {
    callback(new Error(`正文不能超过 ${MAX_DIARY_CONTENT_LENGTH} 字`))
    return
  }

  callback()
}

const rules = {
  title: [{ max: 50, message: '标题不能超过 50 字', trigger: 'blur' }],
  content: [{ validator: validateContentLength, trigger: 'blur' }],
  recordDate: [{ required: true, message: '请选择记录日期', trigger: 'change' }],
}

const hasHomeDraftContent = () =>
  form.title.trim() !== '' ||
  form.content.trim() !== '' ||
  images.value.length > 0 ||
  currentImageIds.value.size > 0 ||
  Boolean(draftDiaryId.value)

const saveHomeDraftState = () => {
  if (!hasHomeDraftContent()) {
    sessionStorage.removeItem(homeDraftStorageKey)
    return
  }

  sessionStorage.setItem(
    homeDraftStorageKey,
    JSON.stringify({
      form: {
        title: form.title,
        content: form.content,
        recordDate: form.recordDate,
      },
      draftDiaryId: draftDiaryId.value,
      images: images.value,
      currentImageIds: Array.from(currentImageIds.value),
      recordDateAutoUpdateEnabled: recordDateAutoUpdateEnabled.value,
    }),
  )
}

const clearHomeDraftState = () => {
  sessionStorage.removeItem(homeDraftStorageKey)
}

watch(
  [
    () => form.title,
    () => form.content,
    () => form.recordDate,
    draftDiaryId,
    images,
    currentImageIds,
    recordDateAutoUpdateEnabled,
  ],
  saveHomeDraftState,
  {
    deep: true,
  },
)

const syncRecordDateToToday = () => {
  if (!recordDateAutoUpdateEnabled.value) {
    return
  }

  const currentToday = getToday()

  if (form.recordDate !== currentToday) {
    form.recordDate = currentToday
  }
}

const handleRecordDateChange = () => {
  recordDateAutoUpdateEnabled.value = false
}

let recordDateSyncTimer = null

onMounted(() => {
  syncRecordDateToToday()
  recordDateSyncTimer = window.setInterval(syncRecordDateToToday, 60 * 1000)
  window.addEventListener('focus', syncRecordDateToToday)
  document.addEventListener('visibilitychange', syncRecordDateToToday)
})

onBeforeUnmount(() => {
  if (recordDateSyncTimer) {
    window.clearInterval(recordDateSyncTimer)
  }

  window.removeEventListener('focus', syncRecordDateToToday)
  document.removeEventListener('visibilitychange', syncRecordDateToToday)
})

const ensureDraftCreated = async () => {
  if (draftDiaryId.value) {
    return draftDiaryId.value
  }

  const draft = await createDiaryDraftApi({
    title: form.title,
    content: form.content || '',
    recordDate: form.recordDate,
  })

  draftDiaryId.value = draft.id
  return draft.id
}

const beforeUpload = (file) => {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']

  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('只支持 JPG、PNG、WebP 格式图片')
    return false
  }

  if (file.size / 1024 / 1024 > 5) {
    ElMessage.error('单张图片不能超过 5MB')
    return false
  }

  if (visibleImages.value.length >= MAX_DIARY_IMAGE_COUNT) {
    ElMessage.error(`每篇日记最多上传 ${MAX_DIARY_IMAGE_COUNT} 张图片`)
    return false
  }

  return true
}

const uploadImage = async (options) => {
  const file = options.file

  if (!beforeUpload(file)) {
    return
  }

  uploading.value = true

  try {
    if (draftDiaryId.value) {
      await deleteImagesRemovedFromContent()
    }

    const diaryId = await ensureDraftCreated()
    const image = await uploadDiaryImageApi(diaryId, file)

    images.value.push(image)
    await editorRef.value.insertImage(image)
  } finally {
    uploading.value = false
  }
}

const openEditorImagePreview = async (imageId) => {
  await openImagePreview(images.value, imageId)
}

const handleEditorImageIdsChange = (imageIds) => {
  currentImageIds.value = new Set(imageIds.map(String))
}

const deleteImagesRemovedFromContent = async () => {
  const removedImages = images.value.filter((image) => !currentImageIds.value.has(String(image.id)))

  for (const image of removedImages) {
    try {
      await deleteDiaryImageApi(image.id)
    } catch {
      // 图片已经删除或无权访问时不阻断日记保存。
    }
  }

  images.value = images.value.filter((image) => currentImageIds.value.has(String(image.id)))
}

const resetForm = () => {
  form.title = ''
  form.content = ''
  form.recordDate = getToday()
  recordDateAutoUpdateEnabled.value = true
  draftDiaryId.value = null
  images.value = []
  currentImageIds.value = new Set()
  clearHomeDraftState()
  formRef.value.clearValidate()
}

const submit = async () => {
  if (contentTooLong.value) {
    ElMessage.error(`正文不能超过 ${MAX_DIARY_CONTENT_LENGTH} 字`)
    return
  }

  await formRef.value.validate()

  if (isDiaryBlank({ title: form.title, content: form.content, imageCount: visibleImages.value.length })) {
    ElMessage.warning('请写下一点内容，或添加一张图片')
    return
  }

  loading.value = true
  saving.value = true

  try {
    if (draftDiaryId.value) {
      await publishDiaryDraftApi(draftDiaryId.value, {
        title: form.title,
        content: form.content,
        recordDate: form.recordDate,
      })
      await deleteImagesRemovedFromContent()
    } else {
      await createDiaryApi({
        title: form.title,
        content: form.content,
        recordDate: form.recordDate,
      })
    }

    ElMessage.success('保存成功')
    resetForm()
  } finally {
    loading.value = false
    saving.value = false
  }
}

</script>

<template>
  <main class="home-page">
    <section class="quick-record">
      <p class="side-label side-label-left">Today Entry</p>

      <el-card class="record-card" shadow="never">
        <div class="diary-count-badge" :class="{ invalid: contentTooLong }">
          {{ contentLength }}/{{ MAX_DIARY_CONTENT_LENGTH }}
        </div>

        <el-form ref="formRef" class="record-form" :model="form" :rules="rules" label-position="top">
          <el-form-item class="date-field" prop="recordDate">
            <DiaryDateSelect v-model="form.recordDate" @change="handleRecordDateChange" />
          </el-form-item>

          <el-form-item class="title-field" prop="title">
            <el-input v-model="form.title" maxlength="50" placeholder="标题" />
          </el-form-item>

          <el-form-item class="content-field" prop="content">
            <DiaryRichEditor
              ref="editorRef"
              v-model="form.content"
              :images="images"
              :max-length="MAX_DIARY_CONTENT_LENGTH"
              @image-click="openEditorImagePreview"
              @image-ids-change="handleEditorImageIdsChange"
            />
          </el-form-item>

          <div class="record-footer">
            <div class="image-upload-control">
              <el-upload
                :show-file-list="false"
                :http-request="uploadImage"
                :disabled="uploading || visibleImages.length >= MAX_DIARY_IMAGE_COUNT"
                accept="image/jpeg,image/png,image/webp"
              >
                <el-button class="image-button" plain :loading="uploading" aria-label="添加图片" title="添加图片">
                  <ActionIcon name="plus" />
                </el-button>
              </el-upload>
              <span class="image-count" :class="{ full: visibleImages.length >= MAX_DIARY_IMAGE_COUNT }">
                {{ visibleImages.length }}/{{ MAX_DIARY_IMAGE_COUNT }}
              </span>
            </div>

            <el-button
              class="record-button"
              type="primary"
              :loading="loading"
              aria-label="记录"
              title="记录"
              @click="submit"
            >
              <ActionIcon v-if="!loading" name="send" />
            </el-button>
          </div>
        </el-form>
      </el-card>

      <h1 class="side-label side-label-right">今天想记录什么？</h1>
    </section>

    <DiaryImageViewer
      v-model="previewVisible"
      :url-list="previewUrls"
      :initial-index="previewInitialIndex"
    />
  </main>
</template>

<style scoped>
.home-page {
  display: flex;
  min-width: 360px;
  height: calc(100dvh - 150px);
  min-height: 640px;
  align-items: center;
  justify-content: center;
  overflow: visible;
}

.quick-record {
  position: relative;
  display: flex;
  width: min(100%, 1020px);
  height: 100%;
  align-items: center;
  justify-content: center;
}

.side-label {
  margin: 0;
  color: var(--paper-ink);
  line-height: 1.25;
  text-align: center;
  width: 250px;
}

.side-label-left {
  position: absolute;
  right: calc(50% + 323px);
  align-self: center;
  font-size: 18px;
  font-weight: 740;
}

.side-label-right {
  position: absolute;
  left: calc(50% + 323px);
  align-self: center;
  /* color: var(--paper-ink); */
  font-weight: 740;
  font-size: 18px;
  white-space: nowrap;
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

.record-form {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding-right: 4px;
}

.record-form :deep(.el-form-item__label) {
  display: none;
}

.date-field {
  margin-bottom: 10px;
}

.title-field {
  margin-bottom: 12px;
}

.title-field :deep(.el-input__wrapper) {
  padding: 0 0 6px;
  border-radius: 0 !important;
  background: transparent;
  border-bottom: 1px solid rgba(138, 117, 99, 0.22);
  box-shadow: none;
}

.title-field :deep(.el-input__inner) {
  height: 32px;
  color: var(--paper-ink);
  font-size: 20px;
  font-weight: 720;
}

.title-field :deep(.el-input__inner::placeholder) {
  color: rgba(116, 107, 97, 0.32);
}

.content-field {
  min-height: 0;
  margin-bottom: 0;
  overflow: hidden;
}

.content-field :deep(.el-form-item__content) {
  min-height: 0;
  height: 100%;
  align-items: stretch;
  overflow: hidden;
}

.content-field :deep(.diary-rich-editor) {
  width: 100%;
  height: 100%;
  overflow-y: auto;
  overscroll-behavior: auto;
  scrollbar-gutter: stable;
}

.content-field :deep(.rich-editor-content) {
  box-sizing: border-box;
  min-height: 100%;
  overflow: visible;
  padding: 0 8px 18px 0;
  background: transparent;
  box-shadow: none;
}

.content-field :deep(.diary-editor-image) {
  width: auto;
  max-width: calc(100% - 8px);
}

.content-field :deep(.rich-editor-content:focus) {
  box-shadow: none;
}

.record-footer {
  display: flex;
  position: relative;
  z-index: 2;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  padding-top: 4px;
}

.image-upload-control {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}

.image-upload-control :deep(.el-upload) {
  display: inline-flex;
  height: 24px;
  align-items: center;
}

.image-count {
  display: inline-flex;
  height: 24px;
  align-items: center;
  color: var(--paper-muted);
  font-size: 12px;
  font-weight: 650;
  line-height: 1;
}

.image-count.full {
  color: #9A352C;
}

.image-button,
.record-button {
  display: inline-flex;
  min-width: 0;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  color: var(--paper-accent-dark) !important;
  font-size: 17px;
  font-weight: 740;
  padding: 0;
}

.image-button :deep(.el-button__content),
.record-button :deep(.el-button__content) {
  position: relative;
  z-index: 2;
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.image-button :deep(.el-icon.is-loading),
.record-button :deep(.el-icon.is-loading) {
  margin: 0;
  font-size: 17px;
}

.image-button.is-loading::before,
.record-button.is-loading::before {
  display: none;
}

.image-button:hover,
.image-button:focus-visible,
.record-button:hover,
.record-button:focus-visible {
  color: #413A33 !important;
  background: transparent !important;
}

@media (max-width: 900px) {
  .home-page {
    height: auto;
    min-height: max(640px, calc(100dvh - 126px));
    overflow: visible;
  }

  .quick-record {
    min-height: 0;
    flex-direction: column;
    gap: 18px;
  }

  .side-label-left,
  .side-label-right {
    position: static;
    width: auto;
    font-size: 18px;
  }

  .record-card {
    height: clamp(580px, calc(100dvh - 184px), 700px);
    min-height: 580px;
    order: 2;
  }

  .record-card :deep(.el-card__body) {
    padding: 26px 20px 24px;
  }

  .diary-count-badge {
    top: 22px;
    right: 20px;
  }

  .side-label-left {
    order: 1;
  }

  .side-label-right {
    order: 3;
    /* margin-top: 18px; */
  }

  .record-footer {
    align-items: stretch;
  }

  .image-button,
  .record-button {
    min-width: 0;
  }
}
</style>
