// 喜宝IPTV API 对接模块
const STORAGE_KEY = 'xibao_config'

let config = {
  serverUrl: '',
  deviceId: ''
}

export function loadConfig() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      config = { ...config, ...parsed }
    }
  } catch (e) {}
  return { ...config }
}

export function saveConfig(url) {
  config.serverUrl = url.replace(/\/+$/, '')
  if (!config.deviceId) {
    config.deviceId = 'ANDROID_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8)
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(config))
  return { ...config }
}

export function getConfig() {
  return { ...config }
}

// 后端 /mytv/getUserM3U8 是用 ts + deviceId 参数取 M3U 的
// 这个不走 AES 加密，是最简单的对接方式
export async function getM3U(serverUrl) {
  const did = config.deviceId || 'web_' + Date.now().toString(36)
  const ts = Date.now().toString(36)

  const url = `${serverUrl}/mytv/getUserM3U8?ts=${encodeURIComponent(ts)}&deviceId=${encodeURIComponent(did)}`
  const res = await fetch(url, { signal: AbortSignal.timeout(15000) })
  if (!res.ok) throw new Error(`服务器返回 ${res.status}`)
  return await res.text()
}

// 解析 M3U 为频道列表
export function parseM3U(text) {
  const channels = []
  const lines = text.split('\n')
  let currentName = ''
  let currentNum = 0

  for (const line of lines) {
    const t = line.trim()
    if (t.startsWith('#EXTINF:')) {
      // 格式: #EXTINF:-1 group-title="中央" tvg-id="CCTV1" tvg-logo="..." tvg-name="CCTV-1 综合",频道名
      const nameMatch = t.match(/,([^,]+)$/)
      currentName = nameMatch ? nameMatch[1].trim() : '未命名'

      const numMatch = t.match(/tvg-id=["']?CCTV(\d+)["']?/i)
      currentNum = numMatch ? parseInt(numMatch[1]) : channels.length + 1

      // 如果不是CCTV，尝试从 group-title 的数字提取
      if (!numMatch) {
        // 按顺序编号
        currentNum = channels.length + 1
      }
    } else if (t && !t.startsWith('#') && currentName) {
      channels.push({
        num: currentNum,
        name: currentName,
        url: t
      })
      currentName = ''
    }
  }

  return channels
}

// 测试服务器连接
export async function testConnection(serverUrl) {
  const cleanUrl = serverUrl.replace(/\/+$/, '')
  const res = await fetch(`${cleanUrl}/apk/getver`, {
    signal: AbortSignal.timeout(8000)
  })
  if (!res.ok) throw new Error(`服务器返回 ${res.status}`)
  return await res.json()
}

// 获取 EPG
export async function getEpg(serverUrl, channelName) {
  const res = await fetch(`${serverUrl}/apk/getEpg?id=${encodeURIComponent(channelName)}&simple=1`, {
    signal: AbortSignal.timeout(8000)
  })
  if (!res.ok) return null
  return await res.json()
}
