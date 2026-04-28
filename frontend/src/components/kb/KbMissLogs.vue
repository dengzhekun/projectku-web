<script setup lang="ts">
import type { KbMissLog } from '../../lib/knowledgeBase'

defineProps<{
  misses?: KbMissLog[]
  loading?: boolean
}>()
</script>

<template>
  <section class="panel">
    <div class="head">
      <div>
        <h3>未命中问题池</h3>
        <p>收集低置信度或没有命中文档的问题，用来反向补知识库。</p>
      </div>
      <slot name="actions" />
    </div>

    <div v-if="loading" class="empty">正在加载...</div>
    <ul v-else-if="misses?.length" class="list">
      <li v-for="miss in misses" :key="miss.id" class="item">
        <div class="row">
          <strong>{{ miss.status || 'open' }}</strong>
          <span>{{ miss.createdAt || '-' }}</span>
          <span v-if="miss.confidence !== null && miss.confidence !== undefined">
            置信度 {{ miss.confidence }}
          </span>
        </div>
        <div class="query">{{ miss.queryText }}</div>
        <div v-if="miss.fallbackReason" class="reason">{{ miss.fallbackReason }}</div>
        <div v-if="miss.conversationId" class="sub">会话：{{ miss.conversationId }}</div>
      </li>
    </ul>
    <div v-else class="empty">暂无未命中问题</div>
  </section>
</template>

<style scoped>
.panel,
.item {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg);
}

.panel {
  padding: 18px;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.head h3 {
  margin: 0;
  font-size: 18px;
  color: var(--text-h);
}

.head p,
.empty,
.sub,
.reason {
  color: var(--text);
  font-size: var(--font-sm);
}

.list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 12px;
}

.item {
  padding: 14px;
}

.row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--text-h);
}

.query {
  margin-top: 8px;
  color: var(--text-h);
  font-weight: 800;
}

.reason {
  margin-top: 8px;
}

@media (max-width: 640px) {
  .head {
    display: grid;
  }
}
</style>
