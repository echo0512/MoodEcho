<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['update:modelValue', 'change'])

const currentYear = new Date().getFullYear()
const year = ref(currentYear)
const month = ref(1)
const day = ref(1)
const syncingFromModel = ref(false)
const open = ref(false)
const rootRef = ref(null)
const yearWheelRef = ref(null)
const monthWheelRef = ref(null)
const dayWheelRef = ref(null)
const wheelThreshold = 52
const wheelCooldown = 140
const wheelState = {
  year: { delta: 0, lastShiftAt: 0 },
  month: { delta: 0, lastShiftAt: 0 },
  day: { delta: 0, lastShiftAt: 0 },
}

const years = computed(() =>
  Array.from({ length: currentYear + 5 - 1900 + 1 }, (_, index) => currentYear + 5 - index),
)
const months = Array.from({ length: 12 }, (_, index) => index + 1)
const days = computed(() => {
  const lastDay = new Date(year.value, month.value, 0).getDate()

  return Array.from({ length: lastDay }, (_, index) => index + 1)
})

const pad = (value) => String(value).padStart(2, '0')
const displayDate = computed(() => `${year.value}-${pad(month.value)}-${pad(day.value)}`)

const syncPartsFromModel = (value) => {
  const [nextYear, nextMonth, nextDay] = (value || '').split('-').map(Number)

  if (!nextYear || !nextMonth || !nextDay) return

  syncingFromModel.value = true
  year.value = nextYear
  month.value = nextMonth
  day.value = Math.min(nextDay, new Date(nextYear, nextMonth, 0).getDate())
  syncingFromModel.value = false
}

const emitDate = () => {
  if (syncingFromModel.value) return

  day.value = Math.min(day.value, days.value.length)
  const value = `${year.value}-${pad(month.value)}-${pad(day.value)}`

  emit('update:modelValue', value)
  emit('change', value)
}

const scrollSelectedIntoView = () => {
  nextTick(() => {
    ;[
      [yearWheelRef.value, year.value],
      [monthWheelRef.value, month.value],
      [dayWheelRef.value, day.value],
    ].forEach(([element, value]) => {
      element?.querySelector(`[data-value="${value}"]`)?.scrollIntoView({
        block: 'center',
      })
    })
  })
}

const toggleOpen = () => {
  open.value = !open.value

  if (open.value) {
    scrollSelectedIntoView()
  }
}

const selectPart = (part, value) => {
  if (part === 'year') year.value = value
  if (part === 'month') month.value = value
  if (part === 'day') day.value = value

  emitDate()
  scrollSelectedIntoView()
}

const shiftPart = (part, offset) => {
  const options = part === 'year' ? years.value : part === 'month' ? months : days.value
  const currentValue = part === 'year' ? year.value : part === 'month' ? month.value : day.value
  const currentIndex = options.indexOf(currentValue)
  const nextIndex = Math.min(Math.max(currentIndex + offset, 0), options.length - 1)

  selectPart(part, options[nextIndex])
}

const handleWheel = (part, event) => {
  event.preventDefault()

  const state = wheelState[part]
  const now = Date.now()

  if (now - state.lastShiftAt < wheelCooldown) {
    return
  }

  if (state.delta && Math.sign(state.delta) !== Math.sign(event.deltaY)) {
    state.delta = 0
  }

  state.delta += event.deltaY

  if (Math.abs(state.delta) < wheelThreshold) {
    return
  }

  shiftPart(part, state.delta > 0 ? 1 : -1)
  state.delta = 0
  state.lastShiftAt = now
}

const closeOnOutsideClick = (event) => {
  if (!rootRef.value?.contains(event.target)) {
    open.value = false
  }
}

watch(() => props.modelValue, syncPartsFromModel, { immediate: true })

onMounted(() => {
  window.addEventListener('click', closeOnOutsideClick)
})

onBeforeUnmount(() => {
  window.removeEventListener('click', closeOnOutsideClick)
})
</script>

<template>
  <div ref="rootRef" class="diary-date-select">
    <button
      class="date-trigger"
      type="button"
      :aria-expanded="open"
      aria-label="选择记录日期"
      @click="toggleOpen"
    >
      {{ displayDate }}
    </button>

    <div v-if="open" class="date-panel" role="dialog" aria-label="选择记录日期">
      <div class="wheel-heading">
        <span>年</span>
        <span>月</span>
        <span>日</span>
      </div>

      <div class="wheel-row">
        <div
          ref="yearWheelRef"
          class="date-wheel"
          role="listbox"
          aria-label="记录年份"
          @wheel="handleWheel('year', $event)"
        >
          <button
            v-for="option in years"
            :key="option"
            class="wheel-option"
            :class="{ selected: option === year }"
            type="button"
            role="option"
            :aria-selected="option === year"
            :data-value="option"
            @click="selectPart('year', option)"
          >
            {{ option }}
          </button>
        </div>

        <div
          ref="monthWheelRef"
          class="date-wheel"
          role="listbox"
          aria-label="记录月份"
          @wheel="handleWheel('month', $event)"
        >
          <button
            v-for="option in months"
            :key="option"
            class="wheel-option"
            :class="{ selected: option === month }"
            type="button"
            role="option"
            :aria-selected="option === month"
            :data-value="option"
            @click="selectPart('month', option)"
          >
            {{ option }}
          </button>
        </div>

        <div
          ref="dayWheelRef"
          class="date-wheel"
          role="listbox"
          aria-label="记录日期"
          @wheel="handleWheel('day', $event)"
        >
          <button
            v-for="option in days"
            :key="option"
            class="wheel-option"
            :class="{ selected: option === day }"
            type="button"
            role="option"
            :aria-selected="option === day"
            :data-value="option"
            @click="selectPart('day', option)"
          >
            {{ option }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.diary-date-select {
  position: relative;
  display: inline-flex;
}

.date-trigger {
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  color: var(--paper-muted);
  cursor: pointer;
  font: inherit;
  font-size: 15px;
  font-weight: 650;
  line-height: 26px;
  padding: 0 2px;
}

.date-trigger:hover,
.date-trigger:focus-visible {
  border-bottom-color: rgba(138, 117, 99, 0.38);
  color: var(--paper-accent-dark);
  outline: none;
}

.date-panel {
  position: absolute;
  z-index: 20;
  top: calc(100% + 8px);
  left: 0;
  width: 220px;
  border: 1px solid rgba(138, 117, 99, 0.14);
  border-radius: 10px;
  background: #fffdf7;
  box-shadow: 0 12px 24px rgba(47, 43, 38, 0.14);
  padding: 10px 12px 12px;
}

.wheel-heading,
.wheel-row {
  display: grid;
  grid-template-columns: 1.35fr 1fr 1fr;
  gap: 8px;
}

.wheel-heading {
  margin-bottom: 5px;
  color: var(--paper-muted);
  font-size: 12px;
  font-weight: 650;
  text-align: center;
}

.date-wheel {
  height: 108px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: none;
  scroll-snap-type: y mandatory;
}

.date-wheel::-webkit-scrollbar {
  display: none;
}

.wheel-option {
  display: block;
  width: 100%;
  height: 36px;
  border: 0;
  border-bottom: 1px solid rgba(138, 117, 99, 0.06);
  background: transparent;
  color: var(--paper-muted);
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  scroll-snap-align: center;
}

.wheel-option:hover,
.wheel-option:focus-visible {
  color: var(--paper-accent-dark);
  outline: none;
}

.wheel-option.selected {
  background: rgba(138, 117, 99, 0.07);
  color: var(--paper-accent-dark);
  font-weight: 740;
}
</style>
