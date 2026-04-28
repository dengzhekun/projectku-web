<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

import UiButton from '../components/ui/UiButton.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { useNotificationsStore } from '../stores/notifications'

const router = useRouter()
const notifications = useNotificationsStore()

onMounted(() => {
  notifications.fetch().catch(() => {})
})

const formatType = (type: string) => {
  if (type === 'order') return '订单消息'
  if (type === 'system') return '系统消息'
  return '站内通知'
}

const formatTime = (ts: string | number) =>
  new Date(ts).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })

const onOpen = (id: string, relatedId?: string | number) => {
  notifications.markRead(id)
  if (relatedId != null && relatedId !== '') router.push({ name: 'orderDetail', params: { id: String(relatedId) } })
}
</script>

<template>
  <div class="page">
    <UiPageHeader title="消息中心">
      <template #right>
        <UiButton size="sm" type="button" :disabled="notifications.items.length === 0" @click="notifications.markAllRead()">
          全部已读
        </UiButton>
      </template>
    </UiPageHeader>

    <main class="main" aria-live="polite">
      <UiEmptyState v-if="notifications.items.length === 0" title="暂无消息" desc="有新消息会在这里展示" />

      <div v-else class="list" aria-label="消息列表">
        <article v-for="n in notifications.items" :key="n.id" class="card" :class="{ unread: !n.read }" @click="onOpen(n.id, n.relatedId)">
          <div class="row">
            <div class="title">{{ n.title }}</div>
            <div class="time">{{ formatTime(n.ts) }}</div>
          </div>
          <div class="content">{{ n.content }}</div>
          <div class="meta">
            <span class="type">{{ formatType(n.type) }}</span>
            <span v-if="!n.read" class="dot" aria-label="未读"></span>
          </div>
        </article>
      </div>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.main {
  padding: 14px 16px 28px;
}

.list {
  display: grid;
  gap: 12px;
}

.card {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg);
  padding: 14px;
  display: grid;
  gap: 8px;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
}

.card:hover {
  border-color: color-mix(in srgb, var(--accent) 30%, var(--border));
}

.card.unread {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--border));
  background: color-mix(in srgb, var(--accent-bg) 45%, transparent);
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
}

.title {
  color: var(--text-h);
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
}

.time {
  color: var(--text);
  font-size: var(--font-xs);
  white-space: nowrap;
}

.content {
  color: var(--text);
  font-size: var(--font-sm);
  line-height: 1.5;
}

.meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.type {
  color: var(--text);
  font-size: var(--font-xs);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
}

@media (min-width: 920px) {
  .main {
    max-width: 1120px;
    margin: 0 auto;
    width: 100%;
  }
}
</style>
