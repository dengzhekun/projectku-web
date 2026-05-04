<script setup lang="ts">
import AdminShell from './AdminShell.vue'

withDefaults(defineProps<{ title: string; subtitle?: string }>(), {
  subtitle: '',
})
</script>

<template>
  <AdminShell :title="title" :subtitle="subtitle">
    <template #actions>
      <slot name="actions" />
    </template>
    <slot />
  </AdminShell>
</template>
