<script setup>
import { onBeforeUnmount, watch } from 'vue'
import { Editor, EditorContent } from '@tiptap/vue-3'
import Image from '@tiptap/extension-image'
import { Placeholder } from '@tiptap/extensions/placeholder'
import StarterKit from '@tiptap/starter-kit'
import { getDiaryImageBlobApi } from '@/api/diary'

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  images: {
    type: Array,
    default: () => [],
  },
  maxLength: {
    type: Number,
    default: Infinity,
  },
})

const emit = defineEmits(['update:modelValue', 'image-click', 'image-ids-change'])

const objectUrls = []
const imageIdToUrl = new Map()
let isComposingText = false
let restoreVersion = 0

const SPINNER_IMAGE_SRC = `data:image/svg+xml,${encodeURIComponent(`
<svg xmlns="http://www.w3.org/2000/svg" width="180" height="120" viewBox="0 0 180 120">
  <rect width="180" height="120" rx="12" fill="#fffefa" opacity="0.72"/>
  <g transform="translate(90 60)">
    <circle r="14" fill="none" stroke="rgba(138, 117, 99, 0.16)" stroke-width="4"/>
    <path d="M 0 -14 A 14 14 0 0 1 14 0" fill="none" stroke="rgba(138, 117, 99, 0.48)" stroke-width="4" stroke-linecap="round">
      <animateTransform attributeName="transform" type="rotate" from="0" to="360" dur="0.76s" repeatCount="indefinite"/>
    </path>
  </g>
</svg>
`)}`

const ERROR_IMAGE_SRC = `data:image/svg+xml,${encodeURIComponent(`
<svg xmlns="http://www.w3.org/2000/svg" width="180" height="120" viewBox="0 0 180 120">
  <rect width="180" height="120" rx="12" fill="#fffefa" opacity="0.72"/>
  <text x="90" y="63" text-anchor="middle" fill="#909399" font-size="13">图片暂时无法显示</text>
</svg>
`)}`

const DiaryImage = Image.extend({
  addAttributes() {
    return {
      ...(this.parent ? this.parent() : {}),
      dataImageId: {
        default: null,
        parseHTML: (element) => element.getAttribute('data-image-id'),
        renderHTML: (attributes) => {
          if (!attributes.dataImageId) {
            return {}
          }

          return {
            'data-image-id': attributes.dataImageId,
          }
        },
      },
    }
  },
})

let editor = null

const getImageUrl = async (imageId) => {
  const key = String(imageId)

  if (imageIdToUrl.has(key)) {
    return imageIdToUrl.get(key)
  }

  const blob = await getDiaryImageBlobApi(imageId)
  const url = URL.createObjectURL(blob)

  imageIdToUrl.set(key, url)
  objectUrls.push(url)

  return url
}

const normalizeEditorImages = (html) => {
  const div = document.createElement('div')
  div.innerHTML = html || ''

  div.querySelectorAll('img[data-image-id]').forEach((img) => {
    const src = img.getAttribute('src') || ''

    if (!src.startsWith('blob:')) {
      img.setAttribute('src', SPINNER_IMAGE_SRC)
    }
  })

  return div.innerHTML
}

const getStorageHtml = () => {
  if (!editor) {
    return ''
  }

  const div = document.createElement('div')
  div.innerHTML = editor.getHTML()

  div.querySelectorAll('img[data-image-id]').forEach((img) => {
    const imageId = img.getAttribute('data-image-id')
    img.setAttribute('src', `moodecho-image:${imageId}`)
  })

  return div.innerHTML
}

const extractImageIdsFromHtml = (html) => {
  const div = document.createElement('div')
  div.innerHTML = html || ''

  return Array.from(div.querySelectorAll('img[data-image-id]'))
    .map((img) => img.getAttribute('data-image-id'))
    .filter(Boolean)
}

const emitEditorChange = () => {
  const storageHtml = getStorageHtml()

  emit('update:modelValue', storageHtml)
  emit('image-ids-change', extractImageIdsFromHtml(storageHtml))
}

const hasTextLimit = () => Number.isFinite(props.maxLength)

const getDocumentTextLength = (doc = editor?.state.doc) => {
  if (!doc) {
    return 0
  }

  return doc.textBetween(0, doc.content.size, '', '').length
}

const getSelectionTextLength = ({ doc, from, to }) => doc.textBetween(from, to, '', '').length

const getRemainingTextLength = ({ doc, from, to }) => {
  if (!hasTextLimit()) {
    return Infinity
  }

  const currentLength = getDocumentTextLength(doc)
  const replacedLength = getSelectionTextLength({ doc, from, to })
  return Math.max(props.maxLength - currentLength + replacedLength, 0)
}

const deleteTextBeforePosition = ({ view, position, deleteCount }) => {
  if (deleteCount <= 0) {
    return
  }

  const ranges = []

  view.state.doc.descendants((node, pos) => {
    if (!node.isText || pos >= position) {
      return
    }

    const from = pos
    const to = Math.min(pos + node.text.length, position)

    if (to > from) {
      ranges.push({ from, to })
    }
  })

  let remaining = deleteCount
  const transaction = view.state.tr

  for (let index = ranges.length - 1; index >= 0 && remaining > 0; index -= 1) {
    const range = ranges[index]
    const rangeLength = range.to - range.from
    const removeLength = Math.min(rangeLength, remaining)

    transaction.delete(range.to - removeLength, range.to)
    remaining -= removeLength
  }

  if (transaction.docChanged) {
    view.dispatch(transaction)
  }
}

const enforceTextLimitAfterComposition = (view) => {
  if (!hasTextLimit()) {
    return
  }

  const overLimit = getDocumentTextLength(view.state.doc) - props.maxLength

  if (overLimit <= 0) {
    return
  }

  deleteTextBeforePosition({
    view,
    position: view.state.selection.to,
    deleteCount: overLimit,
  })
}

const insertLimitedText = ({ view, from, to, text }) => {
  if (!hasTextLimit()) {
    return false
  }

  const remainingLength = getRemainingTextLength({ doc: view.state.doc, from, to })

  if (remainingLength >= text.length) {
    return false
  }

  if (remainingLength > 0) {
    view.dispatch(view.state.tr.insertText(text.slice(0, remainingLength), from, to))
  }

  return true
}

const moveDownFromDocumentEnd = ({ editor: currentEditor, event }) => {
  if (event.key !== 'ArrowDown' || !currentEditor.state.selection.empty) {
    return false
  }

  const { selection, doc } = currentEditor.state
  const isAtDocumentEnd = selection.to >= doc.content.size - 1

  if (!isAtDocumentEnd) {
    return false
  }

  event.preventDefault()
  currentEditor.chain().focus().insertContent({ type: 'paragraph' }).run()
  return true
}

const restoreImageUrls = async () => {
  if (!editor) {
    return
  }

  const version = ++restoreVersion
  const imageNodes = []

  editor.state.doc.descendants((node, pos) => {
    if (node.type.name !== 'image') {
      return
    }

    const imageId = node.attrs.dataImageId

    if (imageId) {
      imageNodes.push({
        pos,
        imageId,
        src: node.attrs.src,
      })
    }
  })

  for (const item of imageNodes) {
    if (item.src && item.src.startsWith('blob:')) {
      continue
    }

    try {
      const url = await getImageUrl(item.imageId)

      if (!editor || version !== restoreVersion) {
        return
      }

      editor
        .chain()
        .setNodeSelection(item.pos)
        .updateAttributes('image', {
          src: url,
          dataImageId: item.imageId,
        })
        .run()
    } catch {
      if (!editor || version !== restoreVersion) {
        return
      }

      editor
        .chain()
        .setNodeSelection(item.pos)
        .updateAttributes('image', {
          src: ERROR_IMAGE_SRC,
          dataImageId: item.imageId,
        })
        .run()
    }
  }
}

editor = new Editor({
  content: normalizeEditorImages(props.modelValue),
  extensions: [
    StarterKit,
    Placeholder.configure({
      placeholder: '正文…',
    }),
    DiaryImage.configure({
      allowBase64: true,
      inline: false,
      HTMLAttributes: {
        class: 'diary-editor-image',
      },
    }),
  ],
  editorProps: {
    attributes: {
      class: 'rich-editor-content',
    },
    handleKeyDown: (_view, event) => moveDownFromDocumentEnd({ editor, event }),
    handleTextInput: (view, from, to, text) => {
      if (isComposingText || view.composing) {
        return false
      }

      return insertLimitedText({ view, from, to, text })
    },
    handlePaste: (view, event) => {
      const pastedText = event.clipboardData?.getData('text/plain') || ''

      if (!pastedText) {
        return false
      }

      const { from, to } = view.state.selection
      const handled = insertLimitedText({ view, from, to, text: pastedText })

      if (handled) {
        event.preventDefault()
      }

      return handled
    },
    handleDOMEvents: {
      compositionstart: () => {
        isComposingText = true
        return false
      },
      compositionend: (view) => {
        isComposingText = false
        requestAnimationFrame(() => {
          enforceTextLimitAfterComposition(view)
          emitEditorChange()
        })
        return false
      },
    },
  },
  onUpdate: () => {
    if (isComposingText || editor?.view.composing) {
      return
    }

    emitEditorChange()
  },
  onCreate: () => {
    restoreImageUrls()
    emitEditorChange()
  },
})

watch(
  () => props.modelValue,
  (newValue) => {
    if (!editor) {
      return
    }

    const current = getStorageHtml()

    if (newValue !== current) {
      editor.commands.setContent(normalizeEditorImages(newValue), false)
      restoreImageUrls()
      emitEditorChange()
    }
  },
)

const insertImage = async (image) => {
  const { selection } = editor.state
  const insertPosition = selection.node?.type.name === 'image' ? selection.to : selection.from

  editor
    .chain()
    .focus()
    .insertContentAt(
      insertPosition,
      [
        {
          type: 'image',
          attrs: {
            src: SPINNER_IMAGE_SRC,
            alt: image.originalName || '日记图片',
            dataImageId: String(image.id),
          },
        },
        {
          type: 'paragraph',
        },
      ],
      {
        updateSelection: true,
      },
    )
    .run()

  emitEditorChange()
  restoreImageUrls()
}

const removeImage = (imageId) => {
  const div = document.createElement('div')
  div.innerHTML = getStorageHtml()

  div.querySelectorAll(`img[data-image-id="${imageId}"]`).forEach((img) => {
    img.remove()
  })

  editor.commands.setContent(div.innerHTML, false)
  restoreImageUrls()
  emitEditorChange()
}

const handleEditorClick = (event) => {
  const target = event.target

  if (!target || target.tagName !== 'IMG') {
    return
  }

  const imageId = target.getAttribute('data-image-id')

  if (imageId) {
    emit('image-click', imageId)
  }
}

defineExpose({
  insertImage,
  removeImage,
})

onBeforeUnmount(() => {
  objectUrls.forEach((url) => {
    URL.revokeObjectURL(url)
  })

  editor?.destroy()
})
</script>

<template>
  <div class="diary-rich-editor" @click="handleEditorClick">
    <EditorContent :editor="editor" />
  </div>
</template>

<style scoped>
:deep(.rich-editor-content) {
  min-height: 360px;
  padding: 22px 24px;
  border: 0;
  border-radius: 10px;
  outline: none;
  background: var(--paper-card);
  box-shadow: none;
  color: var(--paper-ink);
  font-size: 16px;
  line-height: 1.8;
}

:deep(.rich-editor-content:focus) {
  border-color: rgba(138, 117, 99, 0.28);
  box-shadow: none;
}

:deep(.rich-editor-content p) {
  margin: 0;
}

:deep(.rich-editor-content p.is-editor-empty:first-child::before) {
  height: 0;
  float: left;
  color: rgba(116, 107, 97, 0.32);
  content: attr(data-placeholder);
  pointer-events: none;
}

:deep(.diary-editor-image) {
  display: block;
  max-width: 100%;
  margin: 12px 0;
  border-radius: 10px;
  cursor: zoom-in;
}
</style>
