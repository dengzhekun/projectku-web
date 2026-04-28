<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { askCustomerService, askCustomerServiceStream, type CustomerServiceReply } from '../lib/customerService'

type ChatMessage = {
  role: 'assistant' | 'user'
  text: string
  reply?: CustomerServiceReply | null
}

const open = ref(false)
const input = ref('')
const loading = ref(false)
const messages = ref<ChatMessage[]>([
  {
    role: 'assistant',
    text: '您好，我是元气购在线客服。可以咨询商品、下单、支付、物流和售后问题。',
    reply: null,
  },
])
const panelRef = ref<HTMLElement | null>(null)

const scrollToBottom = async () => {
  await nextTick()
  const panel = panelRef.value
  if (panel) panel.scrollTop = panel.scrollHeight
}

const toggle = () => {
  open.value = !open.value
  if (open.value) scrollToBottom()
}

const send = async () => {
  const text = input.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', text })
  input.value = ''
  loading.value = true
  await scrollToBottom()

  const assistantMessage: ChatMessage = { role: 'assistant', text: '', reply: null }
  messages.value.push(assistantMessage)

  try {
    const reply = await askCustomerServiceStream(text, null, {
      onStatus: (message) => {
        if (!assistantMessage.text) {
          assistantMessage.text = message === 'generating' ? '正在生成回答...' : '正在查询...'
        }
      },
      onDelta: (chunk) => {
        if (assistantMessage.text === '正在查询...' || assistantMessage.text === '正在生成回答...') {
          assistantMessage.text = ''
        }
        assistantMessage.text += chunk
        void scrollToBottom()
      },
      onFinal: (reply) => {
        assistantMessage.reply = reply
        assistantMessage.text = reply.answer || assistantMessage.text
      },
    })
    assistantMessage.reply = reply
    assistantMessage.text = reply.answer || assistantMessage.text
  } catch (error: any) {
    try {
      const reply = await askCustomerService(text)
      assistantMessage.text = reply.answer
      assistantMessage.reply = reply
      return
    } catch {
      assistantMessage.reply = null
    }
    messages.value.push({
      role: 'assistant',
      text: error?.message || '在线客服暂时不可用，请稍后再试。',
      reply: null,
    })
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}
</script>

<template>
  <section class="customer-service" aria-label="在线客服">
    <div v-if="open" class="chat-card" role="dialog" aria-modal="false" aria-label="元气购在线客服">
      <header class="chat-head">
        <div>
          <strong>在线客服</strong>
          <p>AI 客服会结合知识库与平台规则回答</p>
        </div>
        <button class="icon-btn" type="button" aria-label="关闭在线客服" @click="toggle">×</button>
      </header>

      <div ref="panelRef" class="chat-messages" aria-live="polite">
        <div v-for="(message, index) in messages" :key="index" class="message-wrap" :class="message.role">
          <div class="message" :class="message.role">
            {{ message.text }}
          </div>
          <div v-if="message.role === 'assistant' && message.reply?.citations?.length" class="chat-citations">
            <span
              v-for="item in message.reply.citations"
              :key="`${item.sourceType}-${item.sourceId}`"
              class="citation-tag"
            >
              {{ item.title }}
            </span>
          </div>
          <p v-if="message.role === 'assistant' && message.reply?.fallbackReason" class="fallback-tip">
            {{ message.reply.fallbackReason }}
          </p>
        </div>
        <div v-if="loading && !messages[messages.length - 1]?.text" class="message assistant loading">正在思考...</div>
      </div>

      <form class="chat-form" @submit.prevent="send">
        <label class="sr-only" for="customer-service-input">请输入咨询内容</label>
        <input
          id="customer-service-input"
          v-model="input"
          maxlength="500"
          placeholder="问问商品、支付、物流、售后"
          :disabled="loading"
        />
        <button type="submit" :disabled="loading || !input.trim()">发送</button>
      </form>
      <p class="chat-tip">订单、支付、退款等结果以系统页面为准。</p>
    </div>

    <button class="float-btn" type="button" :aria-expanded="open" aria-label="打开在线客服" @click="toggle">
      <span>客服</span>
    </button>
  </section>
</template>

<style scoped>
.customer-service {
  position: fixed;
  right: 18px;
  bottom: calc(var(--bottom-nav-h, 64px) + 18px);
  z-index: 80;
  font-size: 14px;
}

.float-btn,
.icon-btn,
.chat-form button {
  min-width: 44px;
  min-height: 44px;
  border: 0;
  cursor: pointer;
}

.float-btn {
  width: 58px;
  height: 58px;
  border-radius: 999px;
  background: linear-gradient(135deg, #ff7a45, #ff4d4f);
  color: #fff;
  box-shadow: 0 14px 34px rgba(255, 77, 79, 0.32);
  font-weight: 800;
}

.chat-card {
  width: min(360px, calc(100vw - 32px));
  max-height: min(620px, calc(100svh - 140px));
  display: flex;
  flex-direction: column;
  margin-bottom: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.18);
  backdrop-filter: blur(14px);
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  color: #fff;
  background: linear-gradient(135deg, #ff7a45, #ff4d4f);
}

.chat-head p {
  margin: 4px 0 0;
  opacity: 0.9;
  font-size: 12px;
}

.icon-btn {
  border-radius: 999px;
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
  font-size: 24px;
}

.chat-messages {
  flex: 1 1 auto;
  min-height: 220px;
  padding: 14px;
  overflow-y: auto;
  background: #f8fafc;
}

.message-wrap {
  margin-bottom: 10px;
}

.message {
  width: fit-content;
  max-width: 86%;
  padding: 10px 12px;
  border-radius: 16px;
  line-height: 1.55;
  white-space: pre-wrap;
}

.message.assistant {
  color: #1f2937;
  background: #fff;
  border: 1px solid #e5e7eb;
}

.message.user {
  margin-left: auto;
  color: #fff;
  background: #ff6b3d;
}

.message.loading {
  color: #64748b;
}

.chat-citations {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
  max-width: 86%;
}

.citation-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  background: #fff4ed;
  color: #c2410c;
  font-size: 12px;
}

.fallback-tip {
  margin: 6px 0 0;
  max-width: 86%;
  color: #64748b;
  font-size: 12px;
}

.chat-form {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #e5e7eb;
  background: #fff;
}

.chat-form input {
  flex: 1;
  min-width: 0;
  height: 44px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  padding: 0 14px;
  font-size: 16px;
  outline: none;
}

.chat-form input:focus {
  border-color: #ff6b3d;
  box-shadow: 0 0 0 3px rgba(255, 107, 61, 0.15);
}

.chat-form button {
  border-radius: 999px;
  padding: 0 16px;
  background: #111827;
  color: #fff;
  font-weight: 700;
}

.chat-form button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.chat-tip {
  margin: 0;
  padding: 0 14px 12px;
  color: #64748b;
  background: #fff;
  font-size: 12px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 520px) {
  .customer-service {
    right: 12px;
    bottom: calc(var(--bottom-nav-h, 64px) + 12px);
  }

  .chat-card {
    width: calc(100vw - 24px);
    max-height: calc(100svh - 120px);
  }
}
</style>
