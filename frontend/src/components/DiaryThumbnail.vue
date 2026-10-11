<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { getDiaryImageBlobApi } from '@/api/diary'

const props = defineProps({
  image: {
    type: Object,
    default: null,
  },
})

const thumbnailUrl = ref('')
const loadError = ref(false)
const thumbnailRef = ref(null)
const shouldLoad = ref(false)

let objectUrl = ''
let observer = null

const clearObjectUrl = () => {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }

  thumbnailUrl.value = ''
  loadError.value = false
}

const loadThumbnail = async () => {
  clearObjectUrl()

  if (!shouldLoad.value || !props.image || !props.image.id) {
    return
  }

  try {
    const blob = await getDiaryImageBlobApi(props.image.id)
    objectUrl = URL.createObjectURL(blob)
    thumbnailUrl.value = objectUrl
  } catch {
    loadError.value = true
  }
}

watch(
  () => props.image?.id,
  () => {
    loadThumbnail()
  },
)

watch(
  thumbnailRef,
  (element) => {
    if (!element || shouldLoad.value) {
      return
    }

    if (!('IntersectionObserver' in window)) {
      shouldLoad.value = true
      loadThumbnail()
      return
    }

    observer?.disconnect()
    observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) {
          return
        }

        shouldLoad.value = true
        observer?.disconnect()
        observer = null
        loadThumbnail()
      },
      {
        rootMargin: '160px',
      },
    )
    observer.observe(element)
  },
  {
    immediate: true,
  },
)

onBeforeUnmount(() => {
  observer?.disconnect()
  clearObjectUrl()
})
</script>

<template>
  <div v-if="image" ref="thumbnailRef" class="thumbnail-wrapper">
    <div class="thumbnail-box">
      <img
        v-if="thumbnailUrl"
        :src="thumbnailUrl"
        alt="日记图片缩略图"
        class="thumbnail-image"
      />

      <div v-else-if="loadError" class="thumbnail-error">图片暂时无法显示</div>

      <div v-else class="thumbnail-placeholder" aria-label="图片加载中">
        <span class="thumbnail-spinner" aria-hidden="true"></span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.thumbnail-wrapper {
  height: 100%;
  min-height: 0;
}

.thumbnail-box {
  width: 100%;
  height: 100%;
  overflow: hidden;
  border-radius: 10px;
  background: rgba(255, 254, 250, 0.78);
  box-shadow: inset 0 0 0 1px rgba(138, 117, 99, 0.08);
}

.thumbnail-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumbnail-placeholder,
.thumbnail-error {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
}

.thumbnail-placeholder {
  color: rgba(138, 117, 99, 0.42);
}

.thumbnail-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(138, 117, 99, 0.16);
  border-top-color: currentColor;
  border-radius: 50%;
  animation: thumbnail-spin 760ms linear infinite;
}

.thumbnail-error {
  color: #909399;
  font-size: 13px;
  line-height: 1.35;
  padding: 8px;
  text-align: center;
}

@keyframes thumbnail-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .thumbnail-spinner {
    animation-duration: 1.4s;
  }
}

@media (max-width: 600px) {
  .thumbnail-box {
    height: 100%;
  }
}
</style>
