import { onBeforeUnmount, ref } from 'vue'
import { getDiaryImageBlobApi } from '@/api/diary'

export function useDiaryImagePreview() {
  const previewVisible = ref(false)
  const previewUrls = ref([])
  const previewInitialIndex = ref(0)
  const objectUrls = ref([])

  const clearObjectUrls = () => {
    objectUrls.value.forEach((url) => {
      URL.revokeObjectURL(url)
    })

    objectUrls.value = []
    previewUrls.value = []
  }

  const buildPreviewUrls = async (images) => {
    clearObjectUrls()

    const result = []

    for (const image of images || []) {
      try {
        const blob = await getDiaryImageBlobApi(image.id)
        const objectUrl = URL.createObjectURL(blob)

        objectUrls.value.push(objectUrl)

        result.push({
          id: String(image.id),
          url: objectUrl,
        })
      } catch {
        // 某张图片加载失败时跳过，不影响其他图片预览。
      }
    }

    return result
  }

  const openImagePreview = async (images, clickedImageId) => {
    const imageList = await buildPreviewUrls(images)

    if (imageList.length === 0) {
      return
    }

    previewUrls.value = imageList.map((item) => item.url)

    const index = imageList.findIndex((item) => item.id === String(clickedImageId))

    previewInitialIndex.value = index >= 0 ? index : 0
    previewVisible.value = true
  }

  const closeImagePreview = () => {
    previewVisible.value = false
  }

  onBeforeUnmount(() => {
    clearObjectUrls()
  })

  return {
    previewVisible,
    previewUrls,
    previewInitialIndex,
    openImagePreview,
    closeImagePreview,
  }
}
