import { api, authHeaders } from './api'

export type CustomerServiceCitation = {
  sourceType: string
  sourceId: string
  title: string
}

export type CustomerServiceAction = {
  type: string
  label: string
  url?: string | null
}

export type CustomerServiceHitLog = {
  documentId: number
  chunkId: number
}

export type CustomerServiceRetrievalTrace = {
  route?: string | null
  sourceType?: string | null
  retriever?: string | null
  requestedTopK?: number | null
  returnedChunkCount?: number
  selectedChunkCount?: number
  citationCount?: number
  hitLogCount?: number
  attributionStatus?: string
  selectedCategories?: string[]
  selectedSourceIds?: string[]
  fallbackReason?: string | null
  notes?: string[]
}

export type CustomerServiceReply = {
  answer: string
  confidence?: number | null
  route?: string | null
  sourceType?: string | null
  citations?: CustomerServiceCitation[]
  actions?: CustomerServiceAction[]
  hitLogs?: CustomerServiceHitLog[]
  fallbackReason?: string | null
  retrievalTrace?: CustomerServiceRetrievalTrace | null
}

export const askCustomerService = async (message: string, conversationId?: string | null) => {
  const { data } = await api.post<{ code: number; message: string; data: CustomerServiceReply }>(
    '/v1/customer-service/chat',
    { message, conversationId: conversationId || null },
  )
  return data.data
}

export type CustomerServiceStreamHandlers = {
  onStatus?: (message: string) => void
  onDelta?: (text: string) => void
  onFinal?: (reply: CustomerServiceReply) => void
}

type CustomerServiceStreamEvent =
  | { type: 'status'; message?: string }
  | { type: 'delta'; text?: string }
  | { type: 'final'; reply?: CustomerServiceReply }
  | { type: 'error'; message?: string }

const apiBase = () => String(api.defaults.baseURL || '/api').replace(/\/+$/, '')

const emitSseBlock = (block: string, handlers: CustomerServiceStreamHandlers) => {
  const data = block
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')
  if (!data) return null

  const event = JSON.parse(data) as CustomerServiceStreamEvent
  if (event.type === 'status') {
    handlers.onStatus?.(event.message || '')
    return null
  }
  if (event.type === 'delta') {
    handlers.onDelta?.(event.text || '')
    return null
  }
  if (event.type === 'final' && event.reply) {
    handlers.onFinal?.(event.reply)
    return event.reply
  }
  if (event.type === 'error') {
    throw new Error(event.message || '在线客服流式响应失败')
  }
  return null
}

export const askCustomerServiceStream = async (
  message: string,
  conversationId?: string | null,
  handlers: CustomerServiceStreamHandlers = {},
) => {
  const response = await fetch(`${apiBase()}/v1/customer-service/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...authHeaders(),
    },
    body: JSON.stringify({ message, conversationId: conversationId || null }),
  })

  if (!response.ok || !response.body) {
    throw new Error('在线客服流式响应不可用')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finalReply: CustomerServiceReply | null = null

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      finalReply = emitSseBlock(block, handlers) || finalReply
    }
  }

  buffer += decoder.decode()
  if (buffer.trim()) {
    finalReply = emitSseBlock(buffer, handlers) || finalReply
  }

  if (!finalReply) {
    throw new Error('在线客服流式响应未完成')
  }
  return finalReply
}
