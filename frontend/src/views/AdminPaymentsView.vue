<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import AdminLayout from '../components/admin/AdminLayout.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import UiErrorPanel from '../components/ui/UiErrorPanel.vue'
import UiInput from '../components/ui/UiInput.vue'
import {
  fetchAdminPaymentOverview,
  type AdminOrderRow,
  type AdminPaymentOverview,
  type AdminPaymentRow,
  type AdminWalletTransactionRow,
} from '../lib/adminPayments'
import { useToastStore } from '../stores/toast'

const toast = useToastStore()

const loading = ref(false)
const loadError = ref('')
const limitText = ref('20')
const overview = ref<AdminPaymentOverview | null>(null)

const priceFmt = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' })

const stats = computed(() => overview.value?.stats || {})

const statCards = computed(() => [
  { label: '订单总数', value: stats.value.totalOrders ?? 0 },
  { label: '待支付订单', value: stats.value.pendingOrders ?? 0 },
  { label: '已支付订单', value: stats.value.paidOrders ?? 0 },
  { label: '支付成功数', value: stats.value.successPayments ?? 0 },
  { label: '支付失败数', value: stats.value.failedPayments ?? 0 },
  { label: '已支付金额', value: money(stats.value.paidAmount) },
  { label: '余额支付支出', value: money(stats.value.walletPaymentAmount) },
  { label: '注册赠送余额', value: money(stats.value.registrationBonusAmount) },
])

const parseLimit = () => {
  const next = Number(limitText.value.trim() || '20')
  if (!Number.isInteger(next) || next <= 0 || next > 100) return null
  return next
}

const loadOverview = async () => {
  const limit = parseLimit()
  if (limit == null) {
    toast.push({ type: 'error', message: 'limit 需为 1 到 100 的整数' })
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    overview.value = await fetchAdminPaymentOverview(limit)
  } catch (error: any) {
    loadError.value = error?.message || '加载支付看板失败'
  } finally {
    loading.value = false
  }
}

function money(value: unknown) {
  const num = Number(value ?? 0)
  return priceFmt.format(Number.isFinite(num) ? num : 0)
}

function time(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function orderStatus(status: number) {
  const map: Record<number, string> = {
    0: '待支付',
    1: '已支付',
    2: '已发货',
    3: '已完成',
    4: '已取消',
  }
  return map[status] || `状态 ${status}`
}

function statusClass(status: string | number) {
  const text = String(status)
  return {
    success: text === 'SUCCESS' || text === '1' || text === '2' || text === '3',
    warn: text === 'PENDING' || text === '0',
    danger: text === 'FAILED' || text === '4',
  }
}

onMounted(loadOverview)
</script>

<template>
  <AdminLayout title="支付订单看板" subtitle="订单、支付流水与余额流水只读查看">
    <div class="main">
      <section class="panel toolbar">
        <div class="toolbar-copy">
          <h1>支付与订单概览</h1>
          <p>只读看板，用于排查订单状态、支付流水和余额流水。</p>
        </div>
        <div class="toolbar-actions">
          <UiInput v-model="limitText" type="number" inputmode="numeric" placeholder="最近条数" />
          <UiButton variant="primary" :loading="loading" @click="loadOverview">刷新</UiButton>
        </div>
      </section>

      <UiErrorPanel v-if="loadError" :desc="loadError" @action="loadOverview" />

      <section class="stats">
        <div v-for="card in statCards" :key="card.label" class="stat-card">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
        </div>
      </section>

      <section class="grid">
        <article class="panel">
          <div class="section-head">
            <h2>最近订单</h2>
            <span>{{ overview?.recentOrders.length ?? 0 }} 条</span>
          </div>
          <UiEmptyState v-if="!loading && !overview?.recentOrders.length" title="暂无订单" desc="有用户下单后会显示在这里。" />
          <div v-else class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>订单号</th>
                  <th>用户</th>
                  <th>金额</th>
                  <th>状态</th>
                  <th>创建时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in overview?.recentOrders" :key="row.id">
                  <td>{{ (row as AdminOrderRow).orderNo }}</td>
                  <td>{{ row.userId }}</td>
                  <td>{{ money(row.payAmount) }}</td>
                  <td><span class="badge" :class="statusClass(row.status)">{{ orderStatus(row.status) }}</span></td>
                  <td>{{ time(row.createTime) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>

        <article class="panel">
          <div class="section-head">
            <h2>最近支付流水</h2>
            <span>{{ overview?.recentPayments.length ?? 0 }} 条</span>
          </div>
          <UiEmptyState v-if="!loading && !overview?.recentPayments.length" title="暂无支付流水" desc="发起支付后会显示在这里。" />
          <div v-else class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>流水号</th>
                  <th>订单</th>
                  <th>渠道</th>
                  <th>金额</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in overview?.recentPayments" :key="row.id">
                  <td class="mono">{{ (row as AdminPaymentRow).tradeId }}</td>
                  <td>{{ row.orderId }}</td>
                  <td>{{ row.channel }}</td>
                  <td>{{ money(row.amount) }}</td>
                  <td><span class="badge" :class="statusClass(row.status)">{{ row.status }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </section>

      <section class="panel">
        <div class="section-head">
          <h2>最近余额流水</h2>
          <span>{{ overview?.recentWalletTransactions.length ?? 0 }} 条</span>
        </div>
        <UiEmptyState v-if="!loading && !overview?.recentWalletTransactions.length" title="暂无余额流水" desc="注册赠送或余额支付后会显示在这里。" />
        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>用户</th>
                <th>订单</th>
                <th>类型</th>
                <th>变动金额</th>
                <th>变动后余额</th>
                <th>备注</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in overview?.recentWalletTransactions" :key="row.id">
                <td>{{ (row as AdminWalletTransactionRow).userId }}</td>
                <td>{{ row.orderId || '-' }}</td>
                <td>{{ row.type }}</td>
                <td>{{ money(row.amount) }}</td>
                <td>{{ money(row.balanceAfter) }}</td>
                <td>{{ row.remark || '-' }}</td>
                <td>{{ time(row.createTime) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </AdminLayout>
</template>

<style scoped>
.main {
  display: grid;
  gap: 16px;
}

.panel,
.stat-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg);
  box-shadow: var(--shadow);
}

.panel {
  padding: 18px;
}

.toolbar,
.toolbar-actions,
.section-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-actions,
.section-head {
  justify-content: space-between;
}

.toolbar {
  justify-content: space-between;
  flex-wrap: wrap;
}

.toolbar-copy h1,
.section-head h2 {
  margin: 0;
  color: var(--text-h);
}

.toolbar-copy h1 {
  font-size: 22px;
}

.toolbar-copy p {
  margin: 6px 0 0;
  color: var(--text);
  font-size: var(--font-sm);
}

.toolbar-actions {
  width: min(320px, 100%);
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  min-height: 84px;
  padding: 14px;
  display: grid;
  align-content: center;
  gap: 8px;
}

.stat-card span,
.section-head span {
  color: var(--text);
  font-size: var(--font-sm);
}

.stat-card strong {
  color: var(--text-h);
  font-size: 24px;
  line-height: 1.1;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.table-wrap {
  overflow: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 620px;
}

th,
td {
  padding: 10px 8px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  color: var(--text);
  font-size: var(--font-sm);
  white-space: nowrap;
}

th {
  color: var(--text-h);
  font-weight: 800;
}

.mono {
  font-family: Consolas, ui-monospace, SFMono-Regular, Menlo, Monaco, monospace;
}

.badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  background: var(--bg);
  color: var(--text-h);
}

.badge.success {
  border-color: color-mix(in srgb, var(--success) 55%, var(--border));
  background: var(--success-bg);
}

.badge.warn {
  border-color: color-mix(in srgb, var(--warning, #c97a00) 45%, var(--border));
  background: color-mix(in srgb, var(--warning, #c97a00) 12%, var(--bg));
}

.badge.danger {
  border-color: color-mix(in srgb, var(--danger) 55%, var(--border));
  background: var(--danger-bg);
}

@media (max-width: 960px) {
  .stats,
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .stats,
  .grid {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    width: 100%;
  }
}
</style>
