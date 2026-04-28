<script setup lang="ts">
import type { KbHitLog } from '../../lib/knowledgeBase'

defineProps<{
  hits?: KbHitLog[]
}>()
</script>

<template>
  <section class="panel">
    <div class="head">
      <h3>命中日志</h3>
      <p>查看哪些问题真正命中了当前知识文档。</p>
    </div>
    <ul v-if="hits?.length" class="list">
      <li v-for="hit in hits" :key="hit.id" class="item">
        <div class="row">
          <strong>{{ hit.hitTime || '-' }}</strong>
          <span>chunk #{{ hit.chunkId }}</span>
        </div>
        <div class="query">{{ hit.queryText }}</div>
        <div v-if="hit.conversationId" class="sub">会话：{{ hit.conversationId }}</div>
      </li>
    </ul>
    <div v-else class="empty">暂无命中记录</div>
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
  display: grid;
  gap: 4px;
  margin-bottom: 14px;
}

.head h3 {
  margin: 0;
  font-size: 18px;
  color: var(--text-h);
}

.head p,
.empty,
.sub {
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
}
</style>
