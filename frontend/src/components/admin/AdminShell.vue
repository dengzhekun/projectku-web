<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import UiButton from '../ui/UiButton.vue'
import { useAdminAuthStore } from '../../stores/adminAuth'
import { useToastStore } from '../../stores/toast'

type NavItem = {
  key: string
  label: string
  routeName?: string
  action?: 'front' | 'logout'
}

const props = withDefaults(defineProps<{ title: string; subtitle?: string }>(), {
  subtitle: '',
})

const route = useRoute()
const router = useRouter()
const adminAuth = useAdminAuthStore()
const toast = useToastStore()

const navItems: NavItem[] = [
  { key: 'home', label: '后台首页', routeName: 'adminHome' },
  { key: 'kb', label: '知识库管理', routeName: 'knowledgeBaseAdmin' },
  { key: 'payments', label: '支付订单看板', routeName: 'adminPayments' },
  { key: 'front', label: '返回前台', action: 'front' },
  { key: 'logout', label: '退出', action: 'logout' },
]

const currentRouteName = computed(() => String(route.name ?? ''))

const handleNav = async (item: NavItem) => {
  if (item.routeName) {
    if (item.routeName !== currentRouteName.value) {
      await router.push({ name: item.routeName })
    }
    return
  }
  if (item.action === 'front') {
    await router.push({ name: 'home' })
    return
  }
  if (item.action === 'logout') {
    adminAuth.logout()
    toast.push({ type: 'info', message: '已退出管理员后台' })
    await router.replace({ name: 'adminLogin' })
  }
}

const isActive = (item: NavItem) => (item.routeName ? currentRouteName.value === item.routeName : false)
</script>

<template>
  <div class="admin-shell">
    <aside class="side">
      <div class="brand">管理后台</div>
      <nav class="nav">
        <button
          v-for="item in navItems"
          :key="item.key"
          type="button"
          class="nav-item"
          :class="{ active: isActive(item) }"
          @click="handleNav(item)"
        >
          {{ item.label }}
        </button>
      </nav>
    </aside>

    <div class="content">
      <header class="top">
        <div>
          <h1>{{ props.title }}</h1>
          <p v-if="props.subtitle">{{ props.subtitle }}</p>
        </div>
        <div class="top-actions">
          <slot name="actions" />
          <UiButton size="sm" @click="handleNav({ key: 'front', label: '', action: 'front' })">返回前台</UiButton>
          <UiButton size="sm" @click="handleNav({ key: 'logout', label: '', action: 'logout' })">退出</UiButton>
        </div>
      </header>
      <main class="main">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  min-height: 100svh;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  background: var(--code-bg);
}

.side {
  border-right: 1px solid var(--border);
  background: var(--bg);
  padding: 18px 12px;
}

.brand {
  color: var(--text-h);
  font-size: 18px;
  font-weight: 700;
  padding: 6px 10px 12px;
}

.nav {
  display: grid;
  gap: 6px;
}

.nav-item {
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text);
  text-align: left;
  padding: 10px;
  cursor: pointer;
}

.nav-item.active {
  color: var(--accent);
  background: var(--accent-bg);
  border-color: var(--accent-border);
}

.content {
  min-width: 0;
}

.top {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--bg) 90%, transparent);
  backdrop-filter: blur(8px);
}

.top h1 {
  margin: 0;
  font-size: 22px;
}

.top p {
  margin-top: 4px;
  color: var(--text);
  font-size: var(--font-sm);
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.main {
  padding: 16px;
}

@media (max-width: 900px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }

  .side {
    border-right: none;
    border-bottom: 1px solid var(--border);
    padding-bottom: 10px;
  }

  .nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .top {
    position: static;
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
