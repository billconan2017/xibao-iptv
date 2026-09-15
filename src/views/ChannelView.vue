<template>
  <div class="channel-screen">
    <div class="header">
      <button class="btn-back" @click="$router.push('/config')">← 设置</button>
      <h2>频道列表</h2>
      <span class="badge">{{ channels.length }}</span>
    </div>

    <div class="list" ref="listRef" @keydown="onKeyNav">
      <div
        v-for="(ch, i) in channels"
        :key="i"
        class="item"
        :class="{ active: focused === i }"
        :ref="el => { if (el) items[i] = el }"
        tabindex="0"
        @click="play(ch)"
        @keydown.enter="play(ch)"
      >
        <span class="num">{{ ch.num }}</span>
        <span class="name">{{ ch.name }}</span>
        <span class="arrow">▶</span>
      </div>
    </div>

    <div v-if="loading" class="overlay-loading">加载中...</div>
    <div v-if="error" class="error-bar">{{ error }}</div>
  </div>
</template>

<script>
import { getConfig, getM3U, parseM3U } from '../utils/api.js'

export default {
  name: 'ChannelView',
  data() {
    return {
      channels: [],
      focused: 0,
      items: [],
      loading: true,
      error: ''
    }
  },
  async mounted() {
    const cfg = getConfig()
    if (!cfg.serverUrl) {
      this.$router.push('/config')
      return
    }
    await this.load()
    this.$nextTick(() => this.focusItem(0))
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const cfg = getConfig()
        const m3u = await getM3U(cfg.serverUrl)
        this.channels = parseM3U(m3u)
        if (this.channels.length === 0) this.channels = this.demoData()
      } catch (e) {
        this.error = `加载失败: ${e.message}`
        this.channels = this.demoData()
      }
      this.loading = false
    },
    demoData() {
      return [
        { num: 1, name: 'CCTV-1 综合', url: '' },
        { num: 2, name: 'CCTV-2 财经', url: '' },
        { num: 3, name: 'CCTV-3 综艺', url: '' },
        { num: 4, name: 'CCTV-4 中文国际', url: '' },
        { num: 5, name: 'CCTV-5 体育', url: '' },
        { num: 6, name: 'CCTV-6 电影', url: '' },
        { num: 7, name: 'CCTV-7 军事', url: '' },
        { num: 8, name: 'CCTV-8 电视剧', url: '' },
        { num: 9, name: 'CCTV-9 记录', url: '' },
        { num: 10, name: 'CCTV-10 科教', url: '' },
        { num: 14, name: '湖南卫视', url: '' },
        { num: 15, name: '浙江卫视', url: '' },
        { num: 16, name: '江苏卫视', url: '' },
        { num: 17, name: '东方卫视', url: '' },
        { num: 18, name: '北京卫视', url: '' },
      ]
    },
    focusItem(i) {
      this.focused = i
      const el = this.items[i]
      if (el) el.scrollIntoView?.({ block: 'nearest' })
    },
    onKeyNav(e) {
      if (e.key === 'ArrowDown') this.focusItem(Math.min(this.focused + 1, this.channels.length - 1))
      else if (e.key === 'ArrowUp') this.focusItem(Math.max(this.focused - 1, 0))
      else if (e.key === 'Enter') this.play(this.channels[this.focused])
      else if (e.key === 'Backspace' || e.key === 'Escape') this.$router.push('/config')
    },
    play(ch) {
      this.$router.push({ path: '/player', query: { name: ch.name, url: ch.url, num: ch.num } })
    }
  }
}
</script>

<style scoped>
.channel-screen { height: 100%; display: flex; flex-direction: column; }
.header {
  display: flex; align-items: center; padding: 12px 16px;
  background: #111; border-bottom: 1px solid #222; flex-shrink: 0;
}
.btn-back { background: none; border: none; color: #ff6b35; font-size: 16px; cursor: pointer; }
.header h2 { flex: 1; text-align: center; font-size: 17px; }
.badge { background: #333; padding: 2px 8px; border-radius: 10px; font-size: 12px; color: #aaa; }

.list { flex: 1; overflow-y: auto; }
.item {
  display: flex; align-items: center; padding: 14px 16px;
  border-bottom: 1px solid #1a1a1a; cursor: pointer;
}
.item.active, .item:focus { background: #1a2a3a; }
.num { width: 36px; color: #666; font-size: 13px; }
.name { flex: 1; font-size: 15px; }
.arrow { color: #444; }

.overlay-loading {
  position: fixed; inset: 0; display: flex; align-items: center; justify-content: center;
  background: rgba(0,0,0,0.7); font-size: 18px; color: #ff6b35;
}
.error-bar { background: #442222; padding: 10px; text-align: center; font-size: 13px; }
</style>