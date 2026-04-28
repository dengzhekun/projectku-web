<script setup lang="ts">
import type { CustomerServiceLog } from '../../lib/knowledgeBase'

defineProps<{
  logs?: CustomerServiceLog[]
  loading?: boolean
}>()

const routeLabels: Record<string, string> = {
  product: '商品',
  wallet: '余额',
  order: '订单',
  coupon: '优惠券',
  aftersales: '售后',
  after_sales: '售后规则',
  logistics: '物流规则',
  payment_refund: '支付退款',
  shopping_guide: '导购',
  knowledge: '知识库',
}

const sourceLabels: Record<string, string> = {
  business: '实时业务',
  product: '商品接口',
  knowledge: '知识库',
}

const labelOf = (map: Record<string, string>, value?: string | null) => {
  if (!value) return '-'
  return map[value] || value
}
</script>

<template>
  <section class="panel">
    <div class="head">
      <div>
        <h3>客服查询日志</h3>
        <p>查看 AI 客服每次回答走了实时业务工具、商品接口还是知识库。</p>
      </div>
      <slot name="actions" />
    </div>

    <div v-if="loading" class="empty">正在加载...</div>
    <ul v-else-if="logs?.length" class="list">
      <li v-for="log in logs" :key="log.id" class="item">
        <div class="row">
          <strong>{{ labelOf(routeLabels, log.route) }}</strong>
          <span>{{ labelOf(sourceLabels, log.sourceType) }}</span>
          <span v-if="log.sourceId">来源 {{ log.sourceId }}</span>
          <span v-if="log.confidence !== null && log.confidence !== undefined">置信度 {{ log.confidence }}</span>
          <span>{{ log.createdAt || '-' }}</span>
        </div>
        <div class="query">{{ log.queryText }}</div>
        <div v-if="log.fallbackReason" class="reason">{{ log.fallbackReason }}</div>
        <div v-if="log.conversationId" class="sub">会话：{{ log.conversationId }}</div>
      </li>
    </ul>
    <div v-else class="empty">暂无客服查询日志</div>
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
