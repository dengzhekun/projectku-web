<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { useAdminAuthStore } from '../stores/adminAuth'

const router = useRouter()
const route = useRoute()
const adminAuth = useAdminAuthStore()

const account = ref('admin')
const password = ref('')
const errorText = ref('')
const submitting = ref(false)

const redirectTo = computed(() => {
  const raw = route.query.redirect
  return typeof raw === 'string' && raw.startsWith('/admin') ? raw : '/admin/kb'
})

const canSubmit = computed(() => account.value.trim().length > 0 && password.value.length >= 6 && !submitting.value)

const submit = async () => {
  if (!canSubmit.value) return
  submitting.value = true
  errorText.value = ''
  try {
    await adminAuth.login(account.value, password.value)
    await router.replace(redirectTo.value)
  } catch (error: any) {
    errorText.value = error?.message || '管理员登录失败'
  } finally {
    submitting.value = false
  }
}

const goHome = () => {
  router.replace({ name: 'home' })
}
</script>

<template>
  <div class="page">
    <UiPageHeader title="管理员登录" :show-back="false">
      <template #right>
        <UiButton size="sm" @click="goHome">返回商城</UiButton>
      </template>
    </UiPageHeader>

    <main class="main">
      <section class="card" aria-label="管理员登录表单">
        <div class="hero">
          <div class="eyebrow">Admin MVP</div>
          <h1>知识库管理后台</h1>
          <p>用于 AI 客服知识库的数据准备、文档切分、向量入库、索引记录和命中日志查看。</p>
        </div>

        <form class="form" @submit.prevent="submit">
          <label class="field" for="admin-account">
            <span>管理员账号</span>
            <UiInput id="admin-account" v-model="account" autocomplete="username" placeholder="默认 admin" />
          </label>

          <label class="field" for="admin-password">
            <span>管理员密码</span>
            <UiInput id="admin-password" v-model="password" type="password" autocomplete="current-password" placeholder="默认 123456" />
          </label>

          <div v-if="errorText" class="error" role="alert">{{ errorText }}</div>

          <UiButton variant="primary" type="submit" :disabled="!canSubmit" :loading="submitting">
            进入管理后台
          </UiButton>
        </form>

        <div class="tip">
          默认后台账号 <strong>admin</strong>，默认密码 <strong>123456</strong>。登录会获取后端令牌，用于访问知识库接口。
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100svh;
  background:
    radial-gradient(circle at top left, color-mix(in srgb, var(--accent-bg) 70%, transparent), transparent 34%),
    var(--code-bg);
}

.main {
  min-height: calc(100svh - 57px);
  display: grid;
  place-items: center;
  padding: 24px 16px 44px;
}

.card {
  width: min(520px, 100%);
  display: grid;
  gap: 22px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--bg);
  box-shadow: var(--shadow);
  padding: 28px;
}

.hero {
  display: grid;
  gap: 8px;
}

.eyebrow {
  width: fit-content;
  border-radius: 999px;
  background: var(--accent-bg);
  color: var(--accent);
  font: 800 12px/18px Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
  padding: 4px 10px;
}

h1 {
  margin: 0;
  color: var(--text-h);
  font: 900 28px/36px Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
}

p {
  margin: 0;
  color: var(--text);
  font: 400 14px/22px Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
}

.form,
.field {
  display: grid;
  gap: 10px;
}

.form {
  gap: 16px;
}

.field span {
  color: var(--text-h);
  font: 700 14px/20px Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
}

.error {
  border: 1px solid color-mix(in srgb, var(--danger) 38%, var(--border));
  border-radius: var(--radius-md);
  background: var(--danger-bg);
  color: var(--danger);
  padding: 10px 12px;
  font: 500 13px/20px Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
}

.tip {
  border-radius: var(--radius-md);
  background: var(--code-bg);
  color: var(--text);
  padding: 12px;
  font: 400 13px/21px Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
}

.tip strong,
.tip code {
  color: var(--text-h);
}
</style>
