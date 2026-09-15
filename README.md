# 清和IPTV - 开源 IPTV 客户端

基于 Vue 3 + Capacitor 构建，原生支持 arm64-v8a / armeabi-v7a / x86_64 全架构。

## 功能

- 📺 手机 + 电视双端一套代码
- 🚀 纯原生 Android，arm64 无压力
- 🔗 兼容清和IPTV后端 API（go-iptv）
- 📡 M3U 直播源播放
- 📋 实时 EPG 节目单
- 🐳 Docker 一键部署编译

## 快速开始

### 方式一：直接下载 APK

从 Release 下载 `qinghe-iptv.apk`，安装后输入你的服务器地址即可。

### 方式二：Docker 构建

```bash
docker compose up -d
```

访问 `http://服务器IP:8080` 下载 APK。

### 方式三：本地开发

```bash
npm install
npm run dev
npx cap sync android
npx cap open android
```

## 技术栈

| 层 | 技术 |
|---|---|
| 前端框架 | Vue 3 |
| 跨平台壳 | Capacitor |
| 打包 | Android SDK (arm64) |
| 容器 | Docker / Docker Compose |

## 对接 API

当前支持清和 IPTV 后端协议：
- `GET /mytv/getUserM3U8` - 获取 M3U 直播源列表
- `GET /apk/getEpg` - 获取 EPG 节目单
- `GET /apk/getver` - 版本检测

## License

MIT
