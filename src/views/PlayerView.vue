<template>
  <div class="player-screen" @click="toggleOverlay">
    <video
      ref="video"
      class="video"
      autoplay
      playsinline
      webkit-playsinline
      controls
      @error="onError"
      @loadedmetadata="onLoad"
      @click.stop
    ></video>

    <div v-if="showOverlay" class="overlay">
      <div class="top-bar">
        <button class="btn-back" @click.stop="goBack">← 返回</button>
        <span class="ch-name">{{ name }}</span>
        <span v-if="error" class="err">⚠ {{ error }}</span>
      </div>
      <div class="bottom-bar">
        <div v-if="epg" class="epg">📋 {{ epg }}</div>
        <div class="hint">点击隐藏控制栏</div>
      </div>
    </div>
  </div>
</template>

<script>
import { getConfig, getEpg } from '../utils/api.js'

export default {
  name: 'PlayerView',
  data() {
    return {
      name: this.$route.query.name || '播放中',
      url: this.$route.query.url || '',
      showOverlay: true,
      error: '',
      epg: '',
      timer: null
    }
  },
  async mounted() {
    this.loadEpg()
    this.autoHide()

    // 如果有 URL 直接播放
    if (this.url) {
      this.$nextTick(() => {
        const video = this.$refs.video
        if (video) {
          video.src = this.url
          video.play().catch(() => {})
        }
      })
    }
  },
  beforeUnmount() {
    clearTimeout(this.timer)
    const video = this.$refs.video
    if (video) { video.pause(); video.src = ''; video.load() }
  },
  methods: {
    autoHide() {
      clearTimeout(this.timer)
      this.timer = setTimeout(() => { this.showOverlay = false }, 4000)
    },
    toggleOverlay() {
      this.showOverlay = !this.showOverlay
      if (this.showOverlay) this.autoHide()
    },
    onLoad() {
      this.error = ''
    },
    onError(e) {
      this.showOverlay = true
      this.error = this.url ? '播放链接不稳定，请尝试换源' : '无播放地址'
    },
    async loadEpg() {
      const cfg = getConfig()
      if (!cfg.serverUrl || !this.name) return
      try {
        const d = await getEpg(cfg.serverUrl, this.name)
        if (d?.data?.name) this.epg = `正在播放: ${d.data.name}`
      } catch (e) {}
    },
    goBack() {
      this.$router.push('/channels')
    }
  }
}
</script>

<style scoped>
.player-screen { position: relative; width: 100%; height: 100%; background: #000; }
.video { width: 100%; height: 100%; object-fit: contain; background: #000; }

.overlay {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; justify-content: space-between;
  background: linear-gradient(180deg, rgba(0,0,0,0.5) 0%, transparent 30%, transparent 70%, rgba(0,0,0,0.5) 100%);
}
.top-bar { display: flex; align-items: center; padding: 12px 16px; gap: 12px; }
.btn-back { background: none; border: none; color: #ff6b35; font-size: 16px; cursor: pointer; }
.ch-name { font-size: 18px; flex: 1; }
.err { color: #ff4444; font-size: 14px; }
.bottom-bar { padding: 12px 16px; }
.epg { background: rgba(0,0,0,0.7); padding: 8px 12px; border-radius: 8px; font-size: 13px; color: #ccc; margin-bottom: 8px; }
.hint { text-align: center; color: #555; font-size: 11px; }
</style>