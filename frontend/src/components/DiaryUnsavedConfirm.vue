<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    default: '日记还没有保存，是否退出？',
  },
  message: {
    type: String,
    default: '退出后，这次书写的内容不会被保存。',
  },
  stayText: {
    type: String,
    default: '继续编辑',
  },
  confirmText: {
    type: String,
    default: '退出',
  },
  danger: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['stay', 'confirm'])

const displayTitle = computed(() => props.title || '日记还没有保存，是否退出？')
const displayMessage = computed(() => props.message || '退出后，这次书写的内容不会被保存。')
const displayStayText = computed(() => props.stayText || '继续编辑')
const displayConfirmText = computed(() => props.confirmText || '退出')
</script>

<template>
  <Teleport to="body">
    <transition name="unsaved-confirm">
      <div v-if="visible" class="unsaved-backdrop">
        <section class="unsaved-card" role="dialog" aria-modal="true" aria-labelledby="unsaved-title">
          <h2 id="unsaved-title">{{ displayTitle }}</h2>
          <p>{{ displayMessage }}</p>

          <div class="unsaved-actions">
            <button class="unsaved-button subtle" type="button" @click="emit('stay')">
              {{ displayStayText }}
            </button>
            <button
              class="unsaved-button primary"
              :class="{ danger: props.danger }"
              type="button"
              @click="emit('confirm')"
            >
              {{ displayConfirmText }}
            </button>
          </div>
        </section>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.unsaved-backdrop {
  position: fixed;
  z-index: 4000;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(47, 43, 38, 0.1);
  backdrop-filter: blur(3px);
}

.unsaved-card {
  position: relative;
  width: min(100%, 360px);
  border: 1px solid rgba(138, 117, 99, 0.12);
  border-radius: 10px;
  background: #fffdf7;
  box-shadow:
    0 16px 36px -28px rgba(47, 43, 38, 0.46),
    0 1px 2px rgba(47, 43, 38, 0.08);
  padding: 28px 30px 24px;
  text-align: center;
}

.unsaved-card > * {
  position: relative;
  z-index: 1;
}

.unsaved-card h2 {
  margin: 0;
  color: var(--paper-ink, #2F2B26);
  font-size: 18px;
  font-weight: 720;
  line-height: 1.45;
}

.unsaved-card p {
  margin: 12px 0 0;
  color: var(--paper-muted, #746B61);
  font-size: 14px;
  line-height: 1.7;
}

.unsaved-actions {
  display: flex;
  justify-content: center;
  gap: 18px;
  margin-top: 24px;
}

.unsaved-button {
  min-width: 86px;
  height: 38px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--paper-ink, #2F2B26);
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 650;
  padding: 0 18px;
  transition:
    background-color 160ms ease,
    color 160ms ease,
    transform 160ms ease;
}

.unsaved-button:hover,
.unsaved-button:focus-visible {
  transform: translateY(-1px);
}

.unsaved-button.subtle {
  color: var(--paper-muted, #746B61);
}

.unsaved-button.subtle:hover,
.unsaved-button.subtle:focus-visible {
  background: rgba(138, 117, 99, 0.08);
  color: var(--paper-ink, #2F2B26);
}

.unsaved-button.primary {
  background: var(--paper-ink, #2F2B26);
  color: #fffdf8;
}

.unsaved-button.primary:hover,
.unsaved-button.primary:focus-visible {
  background: var(--paper-accent-dark, #413A33);
}

.unsaved-button.primary.danger {
  background: #7a3f46;
}

.unsaved-button.primary.danger:hover,
.unsaved-button.primary.danger:focus-visible {
  background: #6b343b;
}

.unsaved-confirm-enter-active,
.unsaved-confirm-leave-active {
  transition: opacity 180ms ease;
}

.unsaved-confirm-enter-active .unsaved-card,
.unsaved-confirm-leave-active .unsaved-card {
  transition: transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

.unsaved-confirm-enter-from,
.unsaved-confirm-leave-to {
  opacity: 0;
}

.unsaved-confirm-enter-from .unsaved-card,
.unsaved-confirm-leave-to .unsaved-card {
  transform: translateY(10px) scale(0.98);
}

@media (max-width: 600px) {
  .unsaved-card {
    padding: 26px 22px 22px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .unsaved-confirm-enter-active,
  .unsaved-confirm-leave-active,
  .unsaved-confirm-enter-active .unsaved-card,
  .unsaved-confirm-leave-active .unsaved-card,
  .unsaved-button {
    transition-duration: 1ms;
  }

  .unsaved-button:hover,
  .unsaved-button:focus-visible,
  .unsaved-confirm-enter-from .unsaved-card,
  .unsaved-confirm-leave-to .unsaved-card {
    transform: none;
  }
}
</style>
