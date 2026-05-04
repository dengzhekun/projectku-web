import { api } from './api'

export type AdminStats = Record<string, number | string | null>

export type AdminOrderRow = {
  id: number
  userId: number
  orderNo: string
  totalAmount: number
  payAmount: number
  status: number
  createTime?: string
  updateTime?: string
}

export type AdminPaymentRow = {
  id: number
  orderId: number
  tradeId: string
  channel: string
  amount: number
  status: string
  paidAt?: string | null
  createTime?: string
  updateTime?: string
}

export type AdminWalletTransactionRow = {
  id: number
  userId: number
  orderId?: number | null
  tradeId?: string | null
  type: string
  amount: number
  balanceAfter: number
  remark?: string | null
  createTime?: string
}

export type AdminPaymentOverview = {
  stats: AdminStats
  recentOrders: AdminOrderRow[]
  recentPayments: AdminPaymentRow[]
  recentWalletTransactions: AdminWalletTransactionRow[]
}

export const fetchAdminPaymentOverview = async (limit = 20): Promise<AdminPaymentOverview> => {
  const res = await api.get('/v1/admin/payments/overview', { params: { limit } })
  const data = res.data?.data || {}
  return {
    stats: data.stats || {},
    recentOrders: Array.isArray(data.recentOrders) ? data.recentOrders : [],
    recentPayments: Array.isArray(data.recentPayments) ? data.recentPayments : [],
    recentWalletTransactions: Array.isArray(data.recentWalletTransactions) ? data.recentWalletTransactions : [],
  }
}
