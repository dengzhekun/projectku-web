<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '../stores/auth'
import { useNotificationsStore } from '../stores/notifications'
import { useWalletStore } from '../stores/wallet'

const router = useRouter()
const auth = useAuthStore()
const notifications = useNotificationsStore()
const wallet = useWalletStore()

const nickname = computed(() => auth.user?.nickname ?? '未登录')
const unread = computed(() => notifications.unreadCount)
const latestTransactions = computed(() => wallet.transactions.slice(0, 3))
const priceFmt = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' })

const goLogin = () => {
  router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
}

const goRegister = () => {
  router.push({ name: 'register', query: { redirect: router.currentRoute.value.fullPath } })
}

const goAuthed = (name: string) => {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  router.push({ name })
}

const logout = () => {
  const ok = window.confirm('确认退出登录？')
  if (ok) {
    auth.logout()
    wallet.reset()
  }
}

onMounted(() => {
  if (auth.isLoggedIn) {
    wallet.fetch().catch(() => {})
  }
})
</script>

<template>
  <div class="page">
    <h1 class="title">我的</h1>
    <p class="subtitle">管理订单、收藏与消息通知</p>

    <div class="card">
      <div class="row">
        <div class="name">{{ nickname }}</div>
        <span class="badge" :class="{ on: auth.isLoggedIn }">{{ auth.isLoggedIn ? '已登录' : '未登录' }}</span>
      </div>

      <p class="desc">登录后可查看订单、地址、消息与售后进度。</p>

      <div class="actions" v-if="!auth.isLoggedIn">
        <button class="primary" type="button" @click="goLogin">去登录</button>
        <button class="ghost" type="button" @click="goRegister">去注册</button>
      </div>
      <div class="actions" v-else>
        <button class="ghost" type="button" @click="logout">退出登录</button>
      </div>
    </div>

    <div v-if="auth.isLoggedIn" class="card walletCard">
      <div class="row">
        <div class="name">账户余额</div>
        <span class="badge on">{{ wallet.loading ? '同步中' : '可用' }}</span>
      </div>

      <div class="balance">{{ wallet.formattedBalance }}</div>

      <div v-if="latestTransactions.length" class="txList" aria-label="余额明细">
        <div v-for="tx in latestTransactions" :key="tx.id" class="txItem">
          <span>{{ tx.remark || tx.type }}</span>
          <span :class="{ income: tx.amount > 0 }">{{ priceFmt.format(tx.amount) }}</span>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="row">
        <div class="name">常用入口</div>
      </div>
      <div class="entries" aria-label="个人中心入口">
        <button class="entry" type="button" @click="goAuthed('orders')">
          <span>我的订单</span>
          <span class="arrow">›</span>
        </button>
        <button class="entry" type="button" @click="goAuthed('favorites')">
          <span>我的收藏</span>
          <span class="arrow">›</span>
        </button>
        <button class="entry" type="button" @click="goAuthed('messages')">
          <span>消息中心</span>
          <span class="right">
            <span v-if="unread > 0" class="unread" aria-label="未读消息数">{{ unread > 99 ? '99+' : unread }}</span>
            <span class="arrow">›</span>
          </span>
        </button>
        <button class="entry" type="button" @click="goAuthed('aftersales')">
          <span>售后服务</span>
          <span class="arrow">›</span>
        </button>
        <button class="entry" type="button" @click="router.push({ name: 'helpCenter' })">
          <span>帮助中心</span>
          <span class="arrow">›</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 18px 16px 28px;
  width: min(840px, 100%);
  margin: 0 auto;
}

.title {
  margin: 0;
  font-size: 28px;
  line-height: 36px;
  font-weight: 700;
  color: var(--text-h);
}

.subtitle {
  margin: 6px 0 0;
  color: var(--text);
  font-size: 14px;
  line-height: 20px;
}

.desc {
  margin: 0;
  color: var(--text);
  line-height: 1.45;
}

.walletCard {
  gap: 10px;
}

.balance {
  color: var(--text-h);
  font-size: 30px;
  line-height: 1;
  font-weight: 700;
}

.txList {
  display: grid;
  gap: 10px;
}

.txItem {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--text);
  font-size: 14px;
  line-height: 20px;
}

.income {
  color: var(--success);
  font-weight: 600;
}

.card {
  margin-top: 12px;
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 14px;
  background: var(--bg);
  box-shadow: var(--shadow);
  display: grid;
  gap: 12px;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.name {
  color: var(--text-h);
  font-size: 18px;
  line-height: 26px;
  font-weight: 600;
}

.badge {
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--text);
  background: color-mix(in srgb, var(--code-bg) 70%, transparent);
}

.badge.on {
  color: var(--text-h);
  border-color: color-mix(in srgb, var(--accent) 50%, var(--border));
  background: var(--accent-bg);
}

.actions {
  display: flex;
  gap: 10px;
}

.entries {
  display: grid;
  gap: 10px;
}

.entry {
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 12px;
  background: color-mix(in srgb, var(--code-bg) 55%, transparent);
  color: var(--text-h);
  font-size: 15px;
  line-height: 22px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
}

.entry:hover {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--border));
}

.right {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.unread {
  min-width: 22px;
  height: 22px;
  border-radius: 999px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
  color: #fff;
  background: var(--accent);
}

.arrow {
  color: var(--text);
  font-size: 18px;
  line-height: 1;
}

.primary {
  border: 0;
  border-radius: 12px;
  padding: 12px 14px;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  background: var(--accent);
  cursor: pointer;
  flex: 1 1 auto;
}

.ghost {
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 12px 14px;
  font-size: 14px;
  background: var(--bg);
  color: var(--text-h);
  cursor: pointer;
  flex: 1 1 auto;
}
</style>
