<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { getDiaryImageBlobApi } from '@/api/diary'

const props = defineProps({
  content: {
    type: String,
    default: '',
  },
  images: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['image-click'])

const renderedContent = ref('')
const objectUrls = []
let renderVersion = 0

const clearObjectUrls = () => {
  objectUrls.forEach((url) => {
    URL.revokeObjectURL(url)
  })

  objectUrls.length = 0
}

const getImageUrl = async (imageId) => {
  const blob = await getDiaryImageBlobApi(imageId)
  const url = URL.createObjectURL(blob)

  objectUrls.push(url)
  return url
}

const createImageLoadingElement = (imageId) => {
  const loadingElement = document.createElement('div')
  loadingElement.className = 'image-loading'
  loadingElement.setAttribute('data-loading-image-id', String(imageId))
  loadingElement.setAttribute('aria-label', '图片加载中')
  loadingElement.innerHTML = '<span class="image-spinner" aria-hidden="true"></span>'

  return loadingElement
}

const createImageElement = (imageId, url, alt = '日记图片') => {
  const image = document.createElement('img')
  image.src = url
  image.alt = alt || '日记图片'
  image.setAttribute('data-image-id', String(imageId))

  return image
}

const renderImageIntoContainer = async (container, imageId, version, alt = '日记图片') => {
  const loadingElement = container.querySelector(`[data-loading-image-id="${imageId}"]`)

  if (!loadingElement) {
    return
  }

  try {
    const url = await getImageUrl(imageId)

    if (version !== renderVersion) {
      return
    }

    loadingElement.replaceWith(createImageElement(imageId, url, alt))
  } catch {
    if (version !== renderVersion) {
      return
    }

    const errorElement = document.createElement('div')
    errorElement.className = 'image-error'
    errorElement.textContent = '图片无法显示或已被删除'
    loadingElement.replaceWith(errorElement)
  }

  if (version === renderVersion) {
    renderedContent.value = container.innerHTML
  }
}

const renderMarkdownContent = (version) => {
  const container = document.createElement('div')
  const regex = /!\[图片\]\(moodecho-image:(\d+)\)/g
  const imageIds = []
  let lastIndex = 0
  let match

  while ((match = regex.exec(props.content || '')) !== null) {
    const textBefore = props.content.slice(lastIndex, match.index)

    if (textBefore) {
      const textNode = document.createElement('div')
      textNode.className = 'text-part'
      textNode.textContent = textBefore
      container.appendChild(textNode)
    }

    const imageWrapper = document.createElement('div')
    imageWrapper.className = 'image-part'
    imageWrapper.appendChild(createImageLoadingElement(match[1]))
    imageIds.push(match[1])

    container.appendChild(imageWrapper)
    lastIndex = regex.lastIndex
  }

  const textAfter = (props.content || '').slice(lastIndex)

  if (textAfter) {
    const textNode = document.createElement('div')
    textNode.className = 'text-part'
    textNode.textContent = textAfter
    container.appendChild(textNode)
  }

  renderedContent.value = container.innerHTML
  imageIds.forEach((imageId) => {
    renderImageIntoContainer(container, imageId, version)
  })
}

const renderHtmlContent = (version) => {
  const container = document.createElement('div')
  container.innerHTML = props.content || ''
  const loadQueue = []

  const imageList = props.images || []

  for (const image of imageList) {
    const imgs = container.querySelectorAll(`img[data-image-id="${image.id}"]`)

    for (const img of imgs) {
      const imageWrapper = document.createElement('div')
      imageWrapper.className = 'image-part'
      imageWrapper.appendChild(createImageLoadingElement(image.id))
      img.replaceWith(imageWrapper)
      loadQueue.push({
        id: image.id,
        alt: img.getAttribute('alt') || '日记图片',
      })
    }
  }

  renderedContent.value = container.innerHTML
  loadQueue.forEach((item) => {
    renderImageIntoContainer(container, item.id, version, item.alt)
  })
}

const isHtmlContent = (content) => /<\/?[a-z][\s\S]*>/i.test(content || '')

const renderContent = () => {
  const version = ++renderVersion
  clearObjectUrls()

  if (isHtmlContent(props.content)) {
    renderHtmlContent(version)
    return
  }

  renderMarkdownContent(version)
}

watch(
  () => [props.content, props.images],
  () => {
    renderContent()
  },
  {
    immediate: true,
    deep: true,
  },
)

const handleContentClick = (event) => {
  const img = event.target.closest?.('img[data-image-id]')

  if (!img) {
    return
  }

  const imageId = img.getAttribute('data-image-id')

  if (imageId) {
    emit('image-click', imageId)
  }
}

onBeforeUnmount(() => {
  clearObjectUrls()
})
</script>

<template>
  <div class="diary-content-renderer" @click="handleContentClick" v-html="renderedContent" />
</template>

<style scoped>
.diary-content-renderer {
  color: var(--paper-ink);
  font-size: 16px;
  line-height: 1.9;
}

:deep(p),
:deep(.text-part) {
  margin: 0;
  white-space: pre-wrap;
}

:deep(.image-part) {
  margin: 14px 0;
}

:deep(.image-loading) {
  display: flex;
  min-height: 160px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: rgba(255, 254, 250, 0.72);
  color: rgba(138, 117, 99, 0.42);
}

:deep(.image-spinner) {
  width: 22px;
  height: 22px;
  border: 2px solid rgba(138, 117, 99, 0.16);
  border-top-color: currentColor;
  border-radius: 50%;
  animation: diary-image-spin 760ms linear infinite;
}

:deep(img) {
  display: block;
  max-width: 100%;
  margin: 14px 0;
  border-radius: 10px;
  cursor: zoom-in;
}

:deep(.image-error) {
  padding: 12px;
  border: 1px dashed rgba(138, 117, 99, 0.22);
  border-radius: 10px;
  color: var(--paper-muted);
  font-size: 14px;
}

@keyframes diary-image-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  :deep(.image-spinner) {
    animation-duration: 1.4s;
  }
}
</style>
