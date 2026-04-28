<script setup lang="ts">
import type { KbChunk } from '../../lib/knowledgeBase'

defineProps<{
  chunks?: KbChunk[]
}>()
</script>

<template>
  <section class="panel">
    <div class="head">
      <h3>切分预览</h3>
      <p>确认切分边界和长度是否合适。</p>
    </div>
    <ul v-if="chunks?.length" class="list">
      <li v-for="chunk in chunks" :key="chunk.id" class="item">
        <div class="meta">
          <strong>#{{ chunk.chunkIndex }}</strong>
          <span>{{ chunk.charCount }} 字</span>
          <span>{{ chunk.status || 'active' }}</span>
        </div>
        <pre class="content">{{ chunk.content }}</pre>
      </li>
    </ul>
    <div v-else class="empty">暂无切分结果</div>
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

.head p {
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

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
  color: var(--text);
  font-size: var(--font-sm);
}

.content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-h);
  font: inherit;
}

.empty {
  color: var(--text);
  font-size: var(--font-sm);
}
</style>
