import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { api, setAuthToken } from '../lib/api'

type AdminSnapshot = {
  v: 2
  account: string | null
  token: string | null
  expiresAt: number | null
  loggedInAt: number | null
}

const STORAGE_KEY = 'admin-auth:v1'
const DEFAULT_ADMIN_ACCOUNT = 'admin'

const readSnapshot = (): AdminSnapshot => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { v: 2, account: null, token: null, expiresAt: null, loggedInAt: null }
    const parsed = JSON.parse(raw) as AdminSnapshot | ({ v: 1; account: string | null; loggedInAt: number | null })
    if ((parsed as any)?.v === 1) return { v: 2, account: null, token: null, expiresAt: null, loggedInAt: null }
    if (parsed?.v !== 2) return { v: 2, account: null, token: null, expiresAt: null, loggedInAt: null }
    return {
      v: 2,
      account: parsed.account || null,
      token: parsed.token || null,
      expiresAt: parsed.expiresAt || null,
      loggedInAt: parsed.loggedInAt || null,
    }
  } catch {
    return { v: 2, account: null, token: null, expiresAt: null, loggedInAt: null }
  }
}

export const useAdminAuthStore = defineStore('adminAuth', () => {
  const snapshot = ref<AdminSnapshot>(readSnapshot())
  if (snapshot.value.token) {
    setAuthToken(snapshot.value.token)
  }

  const account = computed(() => snapshot.value.account)
  const token = computed(() => snapshot.value.token)
  const isAdminLoggedIn = computed(() => {
    if (!snapshot.value.account || !snapshot.value.token || !snapshot.value.loggedInAt) return false
    return !snapshot.value.expiresAt || snapshot.value.expiresAt > Date.now()
  })

  const login = async (inputAccount: string, password: string) => {
    const normalizedAccount = inputAccount.trim()
    if (!normalizedAccount || !password) {
      throw new Error('请输入管理员账号和密码')
    }

    const expectedAccount = import.meta.env.VITE_ADMIN_ACCOUNT || DEFAULT_ADMIN_ACCOUNT
    if (normalizedAccount !== expectedAccount) {
      throw new Error('管理员账号或密码错误')
    }

    const res = await api.post('/v1/auth/login', { account: normalizedAccount, password })
    const data = res.data?.data
    const nextToken: string | null = data?.token ?? null
    if (!nextToken) {
      throw new Error('管理员登录失败，后端未返回令牌')
    }
    const expiresIn: number | null = data?.expiresIn ?? null
    snapshot.value = {
      v: 2,
      account: normalizedAccount,
      token: nextToken,
      expiresAt: expiresIn ? Date.now() + expiresIn * 1000 : null,
      loggedInAt: Date.now(),
    }
    setAuthToken(nextToken)
  }

  const logout = () => {
    snapshot.value = { v: 2, account: null, token: null, expiresAt: null, loggedInAt: null }
    setAuthToken(null)
  }

  watch(
    snapshot,
    (value) => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
    },
    { deep: true },
  )

  return { account, token, isAdminLoggedIn, login, logout }
})
