<script setup>
defineProps({
  name: {
    type: String,
    required: true,
  },
})
</script>

<template>
  <svg class="action-icon" viewBox="0 0 24 24" aria-hidden="true">
    <template v-if="name === 'plus'">
      <path d="M12 5v14M5 12h14" />
    </template>

    <template v-else-if="name === 'send'">
      <path d="m21 3-7.4 18-3.8-7.8L3 9.4 21 3Z" />
      <path d="m9.8 13.2 4.4-4.4" />
    </template>

    <template v-else-if="name === 'trash'">
      <path d="M4 7h16M9 7V4h6v3m3 0-1 13H7L6 7m4 4v5m4-5v5" />
    </template>

    <template v-else-if="name === 'arrow-left'">
      <path d="m15 18-6-6 6-6M9 12h10" />
    </template>

    <template v-else-if="name === 'pencil'">
      <path d="m14.5 5.5 4 4M4 20l4.5-1 10-10a2.8 2.8 0 0 0-4-4l-10 10L4 20Z" />
      <path d="m12.5 7.5 4 4" />
    </template>

    <template v-else-if="name === 'search'">
      <circle cx="11" cy="11" r="6" />
      <path d="m16 16 4 4" />
    </template>

    <template v-else-if="name === 'book-open'">
      <path d="M12 6.5v13" />
      <path d="M4 5.5c2.8 0 5.4.7 8 2.1v13c-2.6-1.4-5.2-2.1-8-2.1V5.5Z" />
      <path d="M20 5.5c-2.8 0-5.4.7-8 2.1v13c2.6-1.4 5.2-2.1 8-2.1V5.5Z" />
    </template>

    <template v-else-if="name === 'message-circle'">
      <path d="M20 11.5a7.5 7.5 0 0 1-10.4 6.9L4 20l1.6-5.1A7.5 7.5 0 1 1 20 11.5Z" />
      <path d="M8.5 11h7" />
      <path d="M8.5 14h4" />
    </template>

    <template v-else-if="name === 'lock'">
      <rect x="5" y="10" width="14" height="10" rx="2" />
      <path d="M8 10V7a4 4 0 0 1 8 0v3" />
      <path d="M12 14v2" />
    </template>

    <template v-else-if="name === 'log-out'">
      <path d="M10 5H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h4" />
      <path d="M14 8l4 4-4 4" />
      <path d="M18 12H9" />
    </template>

    <template v-else-if="name === 'user-x'">
      <circle cx="9" cy="8" r="4" />
      <path d="M3.5 20a6.2 6.2 0 0 1 10.8-4.2" />
      <path d="m17 17 4 4" />
      <path d="m21 17-4 4" />
    </template>
  </svg>
</template>

<style scoped>
.action-icon {
  display: block;
  width: 1.2em;
  height: 1.2em;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}
</style>
