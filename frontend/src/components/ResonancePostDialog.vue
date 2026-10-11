<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ActionIcon from '@/components/ActionIcon.vue'
import { createResonancePostApi, getResonanceTopicsApi } from '@/api/resonance'

const MIN_CONTENT_LENGTH = 10
const MAX_CONTENT_LENGTH = 500

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  diaryId: {
    type: [Number, String],
    default: null,
  },
  initialContent: {
    type: String,
    default: '',
  },
  initialTopicId: {
    type: [Number, String],
    default: null,
  },
  title: {
    type: String,
    default: '投递一段匿名共鸣',
  },
})

const emit = defineEmits(['update:modelValue', 'submitted'])

const topics = ref([])
const loadingTopics = ref(false)
const submitting = ref(false)

const form = reactive({
  content: '',
  topicId: null,
})

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const cleanPreviewContent = computed(() => form.content.trim().replace(/\s+/g, ' '))
const contentLength = computed(() => cleanPreviewContent.value.length)
const isContentInvalid = computed(
  () => contentLength.value < MIN_CONTENT_LENGTH || contentLength.value > MAX_CONTENT_LENGTH,
)

const loadTopics = async () => {
  if (topics.value.length > 0 || loadingTopics.value) {
    return
  }

  loadingTopics.value = true

  try {
    topics.value = await getResonanceTopicsApi()
  } finally {
    loadingTopics.value = false
  }
}

const resetForm = () => {
  form.content = props.initialContent || ''
  form.topicId = props.initialTopicId || null
}

const closeDialog = () => {
  visible.value = false
}

const handleKeydown = (event) => {
  if (event.key === 'Escape' && visible.value) {
    closeDialog()
  }
}

const submit = async () => {
  if (isContentInvalid.value) {
    ElMessage.error('投递内容长度必须在 10 到 500 字之间')
    return
  }

  submitting.value = true

  try {
    const post = await createResonancePostApi({
      diaryId: props.diaryId || null,
      topicId: form.topicId || null,
      content: form.content,
    })

    ElMessage.success('已匿名投递')
    emit('submitted', post)
    closeDialog()
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      resetForm()
      loadTopics()
    }
  },
)

watch(
  () => [props.initialContent, props.initialTopicId],
  () => {
    if (visible.value) {
      resetForm()
    }
  },
)

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)

  if (visible.value) {
    loadTopics()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <transition name="resonance-compose">
      <div v-if="visible" class="resonance-compose-backdrop" @click.self="closeDialog">
        <section class="resonance-compose-card" role="dialog" aria-modal="true" :aria-label="title">
          <button class="compose-close" type="button" aria-label="关闭投递弹框" @click="closeDialog">
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
          </button>

          <textarea
            id="resonance-content"
            v-model="form.content"
            class="compose-textarea"
            :maxlength="MAX_CONTENT_LENGTH"
            placeholder="写下愿意被陌生人读到的一小段。"
            aria-label="匿名内容"
          ></textarea>

          <div class="content-meta" :class="{ invalid: isContentInvalid }">
            <span>{{ contentLength }}/{{ MAX_CONTENT_LENGTH }}</span>
          </div>

          <div class="compose-footer">
            <el-select
              id="resonance-topic"
              v-model="form.topicId"
              class="topic-select"
              :loading="loadingTopics"
              clearable
              placeholder="可选择话题"
              aria-label="选择话题"
            >
              <el-option
                v-for="topic in topics"
                :key="topic.id"
                :label="topic.name"
                :value="topic.id"
              />
            </el-select>

            <el-button
              class="compose-submit-button"
              type="primary"
              :loading="submitting"
              aria-label="投递"
              title="投递"
              @click="submit"
            >
              <ActionIcon name="send" />
            </el-button>
          </div>
        </section>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.resonance-compose-backdrop {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(47, 43, 38, 0.16);
  backdrop-filter: blur(8px);
}

.resonance-compose-card {
  position: relative;
  display: flex;
  width: min(90vw, 560px);
  min-height: min(56vh, 480px);
  flex-direction: column;
  gap: 14px;
  border: 1px solid rgba(138, 117, 99, 0.16);
  border-radius: 10px;
  background: #fffdf7;
  color: var(--paper-ink);
  padding: 56px 48px 42px;
  box-shadow: 0 30px 70px -28px rgba(47, 43, 38, 0.48);
}

.compose-close {
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

.compose-close span {
  position: absolute;
  top: 19px;
  left: 11px;
  width: 18px;
  height: 1px;
  background: currentColor;
  transform: rotate(45deg);
}

.compose-close span + span {
  transform: rotate(-45deg);
}

.compose-close:hover,
.compose-close:focus-visible {
  background: rgba(138, 117, 99, 0.06);
  outline: none;
}

.compose-textarea {
  min-height: 280px;
  flex: 1 1 auto;
  border: 0;
  background: transparent;
  color: var(--paper-ink);
  font: inherit;
  line-height: 1.8;
  outline: none;
  resize: none;
}

.compose-textarea::placeholder {
  color: var(--paper-muted);
  opacity: 0.78;
}

.content-meta {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  color: var(--paper-muted);
  font-size: 13px;
  line-height: 1.45;
}

.content-meta.invalid {
  color: var(--paper-muted);
}

.topic-select {
  width: min(100%, 280px);
}

.topic-select :deep(.el-select__wrapper) {
  border-bottom: 1px solid rgba(138, 117, 99, 0.32);
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  padding-right: 2px;
  padding-left: 2px;
}

.topic-select :deep(.el-select__wrapper.is-focused),
.topic-select :deep(.el-select__wrapper:hover) {
  border-bottom-color: var(--paper-accent-dark);
  box-shadow: 0 2px 0 rgba(138, 117, 99, 0.08);
}

.compose-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.compose-submit-button {
  width: 44px;
  min-width: 44px;
  height: 36px;
  padding: 0;
}

.compose-submit-button :deep(.action-icon) {
  width: 18px;
  height: 18px;
}

.resonance-compose-enter-active,
.resonance-compose-leave-active {
  transition: opacity 220ms ease;
}

.resonance-compose-enter-active .resonance-compose-card,
.resonance-compose-leave-active .resonance-compose-card {
  transition: transform 220ms ease;
}

.resonance-compose-enter-from,
.resonance-compose-leave-to {
  opacity: 0;
}

.resonance-compose-enter-from .resonance-compose-card,
.resonance-compose-leave-to .resonance-compose-card {
  transform: translateY(18px) scale(0.96);
}

@media (max-width: 560px) {
  .resonance-compose-card {
    width: min(92vw, 520px);
    min-height: min(64vh, 500px);
    padding: 54px 28px 32px;
  }

  .content-meta {
    display: grid;
    gap: 4px;
  }

  .compose-footer {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .topic-select {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .resonance-compose-enter-active,
  .resonance-compose-leave-active {
    transition: opacity 120ms ease;
  }

  .resonance-compose-enter-from .resonance-compose-card,
  .resonance-compose-leave-to .resonance-compose-card {
    transform: none;
  }
}
</style>
