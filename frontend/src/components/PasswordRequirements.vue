<script setup>
import { computed } from 'vue'
import { PASSWORD_ALLOWED_PATTERN } from '@/utils/password'

const props = defineProps({
  password: {
    type: String,
    default: '',
  },
})

const requirements = computed(() => [
  {
    label: '长度 8-64 位',
    met: props.password.length >= 8 && props.password.length <= 64,
  },
  {
    label: '同时包含字母和数字',
    met: /[A-Za-z]/.test(props.password) && /\d/.test(props.password),
  },
  {
    label: '仅支持字母、数字、下划线',
    met: Boolean(props.password) && PASSWORD_ALLOWED_PATTERN.test(props.password),
  },
])
</script>

<template>
  <div class="password-requirements" aria-live="polite">
    <span
      v-for="requirement in requirements"
      :key="requirement.label"
      class="requirement-item"
      :class="{ met: requirement.met }"
    >
      <span class="requirement-mark" aria-hidden="true">{{ requirement.met ? '✓' : '○' }}</span>
      <span>{{ requirement.label }}</span>
    </span>
  </div>
</template>

<style scoped>
.password-requirements {
  display: grid;
  gap: 2px;
  width: 100%;
  margin-top: 6px;
  color: var(--paper-muted);
  font-size: 12px;
  line-height: 1.55;
}

.requirement-item {
  display: flex;
  align-items: center;
  gap: 6px;
  transition: color 180ms ease;
}

.requirement-item.met {
  color: var(--feedback-success);
}

.requirement-mark {
  width: 12px;
  flex: 0 0 12px;
  text-align: center;
}
</style>
