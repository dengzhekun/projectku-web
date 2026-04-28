import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../lib/api'

export type WalletTransaction = {
  id: string
  orderId: string | null
  tradeId: string | null
  type: string
  amount: number
  balanceAfter: number
  remark: string
  createTime: string | null
}

export const useWalletStore = defineStore('wallet', () => {
  const balance = ref(0)
  const transactions = ref<WalletTransaction[]>([])
  const loading = ref(false)
  const loaded = ref(false)

  const formattedBalance = computed(() =>
    new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(balance.value),
  )

  const fetch = async () => {
    loading.value = true
    try {
      const res = await api.get('/v1/me/wallet')
      const data = res.data?.data ?? {}
      const wallet = data.wallet ?? {}
      balance.value = Number(wallet.balance ?? 0)
      transactions.value = Array.isArray(data.transactions)
        ? data.transactions.map((item: any) => ({
            id: String(item.id ?? ''),
            orderId: item.orderId == null ? null : String(item.orderId),
            tradeId: item.tradeId == null ? null : String(item.tradeId),
            type: String(item.type ?? ''),
            amount: Number(item.amount ?? 0),
            balanceAfter: Number(item.balanceAfter ?? 0),
            remark: String(item.remark ?? ''),
            createTime: item.createTime ? new Date(item.createTime).toISOString() : null,
          }))
        : []
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  const reset = () => {
    balance.value = 0
    transactions.value = []
    loaded.value = false
  }

  return { balance, transactions, loading, loaded, formattedBalance, fetch, reset }
})
