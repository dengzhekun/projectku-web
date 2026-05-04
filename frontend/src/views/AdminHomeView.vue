<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import AdminLayout from '../components/admin/AdminLayout.vue'
import UiButton from '../components/ui/UiButton.vue'
import { useAdminAuthStore } from '../stores/adminAuth'

const router = useRouter()
const adminAuth = useAdminAuthStore()

const cards = computed(() => [
  { key: 'kb', title: '知识库管理', desc: '文档上传、切分、索引与日志排查。', routeName: 'knowledgeBaseAdmin' as const },
  { key: 'payments', title: '支付订单看板', desc: '订单状态、支付流水和余额流水只读总览。', routeName: 'adminPayments' as const },
])

const loginStatusText = computed(() => {
  if (!adminAuth.isAdminLoggedIn) return '未登录'
  return `已登录：${adminAuth.account || '管理员'}`
})

const go = (routeName: 'knowledgeBaseAdmin' | 'adminPayments') => router.push({ name: routeName })
</script>

<template>
  <AdminLayout title="后台首页" subtitle="统一后台入口与状态总览">
    <section class="status panel">
      <h2>当前状态</h2>
      <p>{{ loginStatusText }}</p>
      <p>系统能力可用：知识库管理、支付订单看板。</p>
    </section>

    <section class="cards">
      <article v-for="card in cards" :key="card.key" class="panel card">
        <h3>{{ card.title }}</h3>
        <p>{{ card.desc }}</p>
        <UiButton size="sm" variant="primary" @click="go(card.routeName)">进入</UiButton>
      </article>
    </section>
  </AdminLayout>
</template>

<style scoped>
.panel {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg);
  box-shadow: var(--shadow);
  padding: 16px;
}

.status {
  display: grid;
  gap: 6px;
}

.status h2,
.card h3 {
  margin: 0;
  color: var(--text-h);
}

.status p,
.card p {
  margin: 0;
  color: var(--text);
  font-size: var(--font-sm);
}

.cards {
  margin-top: 16px;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.card {
  display: grid;
  gap: 10px;
  align-content: start;
}

@media (max-width: 760px) {
  .cards {
    grid-template-columns: 1fr;
  }
}
</style>
