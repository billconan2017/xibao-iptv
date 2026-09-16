<template>
  <div class="config-screen">
    <div class="config-card">
      <h1 class="logo">📺 喜宝IPTV</h1>
      <p class="subtitle">开源 IPTV 播放器 · 手机/电视通用</p>

      <div class="input-group">
        <label>服务器地址</label>
        <input
          v-model="serverUrl"
          type="url"
          placeholder="https://iptv.yourdomain.com:6443"
          ref="urlInput"
          @keydown.enter="connect"
        />
      </div>

      <button class="btn" @click="connect" :disabled="!serverUrl || loading">
        {{ loading ? '连接中...' : '连接服务器' }}
      </button>

      <div v-if="error" class="error-msg">{{ error }}</div>
    </div>

    <div class="footer">
      <span>v1.0.0</span>
      <span>开源 · 全架构 ARM64</span>
    </div>
  </div>
</template>

<script>
import { loadConfig, saveConfig, testConnection } from '../utils/api.js'

export default {
  name: 'ConfigView',
  data() {
    const cfg = loadConfig()
    return {
      serverUrl: cfg.serverUrl || '',
      loading: false,
      error: '',
    }
  },
  mounted() {
    this.$nextTick(() => this.$refs.urlInput?.focus())
    // 如果已有配置，直接跳转
    if (this.serverUrl) this.connect()
  },
  methods: {
    async connect() {
      this.loading = true
      this.error = ''
      try {
        await testConnection(this.serverUrl)
        saveConfig(this.serverUrl)
        this.$router.push('/channels')
      } catch (e) {
        this.error = `连接失败: ${e.message}`
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.config-screen {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  height: 100%; padding: 24px;
}
.config-card {
  width: 100%; max-width: 420px;
  background: #1a1a2e; border-radius: 16px; padding: 32px 24px; text-align: center;
}
.logo { font-size: 28px; margin-bottom: 4px; }
.subtitle { color: #888; font-size: 13px; margin-bottom: 24px; }
.input-group { margin-bottom: 16px; text-align: left; }
.input-group label { display: block; font-size: 13px; color: #aaa; margin-bottom: 6px; }
.input-group input {
  width: 100%; padding: 12px 14px; border-radius: 10px; border: 1px solid #333;
  background: #0d0d1a; color: #fff; font-size: 15px; outline: none;
}
.input-group input:focus { border-color: #ff6b35; }
.btn {
  width: 100%; padding: 14px; border: none; border-radius: 10px;
  background: #ff6b35; color: #fff; font-size: 16px; font-weight: 600; cursor: pointer; margin-top: 8px;
}
.btn:disabled { background: #555; cursor: not-allowed; }
.btn:focus { outline: 2px solid #fff; outline-offset: 2px; }
.error-msg { color: #ff4444; margin-top: 12px; font-size: 13px; }
.footer { position: fixed; bottom: 16px; display: flex; gap: 16px; color: #555; font-size: 12px; }
</style>