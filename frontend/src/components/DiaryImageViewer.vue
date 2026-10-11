<script setup>
defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  urlList: {
    type: Array,
    default: () => [],
  },
  initialIndex: {
    type: Number,
    default: 0,
  },
})

const emit = defineEmits(['update:modelValue'])

const close = () => {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-image-viewer
    v-if="modelValue"
    :url-list="urlList"
    :initial-index="initialIndex"
    hide-on-click-modal
    @close="close"
  />
</template>
