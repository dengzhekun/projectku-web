<script setup lang="ts">
import type { KbIndexRecord } from '../../lib/knowledgeBase'

defineProps<{
  records?: KbIndexRecord[]
}>()
</script>

<template>
  <section class="panel">
    <div class="head">
      <h3>索引记录</h3>
      <p>查看最近的向量化执行结果。</p>
    </div>
    <ul v-if="records?.length" class="list">
      <li v-for="record in records" :key="record.id" class="item">
        <div class="row">
          <strong>{{ record.status }}</strong>
          <span>v{{ record.version }}</span>
          <span>{{ record.indexedChunkCount }} chunks</span>
        </div>
        <div class="sub">
          <span>{{ record.embeddingProvider || 'unknown' }}</span>
          <span>{{ record.vectorCollection || 'unknown' }}</span>
          <span>{{ record.createdAt || '-' }}</span>
        </div>
        <div v-if="record.errorMessage" class="error">{{ record.errorMessage }}</div>
      </li>
    </ul>
    <div v-else class="empty">暂无索引记录</div>
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
.empty {
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

.row,
.sub {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.row {
  color: var(--text-h);
}

.sub {
  margin-top: 8px;
  color: var(--text);
  font-size: var(--font-sm);
}

.error {
  margin-top: 10px;
  color: var(--danger);
  font-size: var(--font-sm);
}
</style>
