# 喜宝 TV

当前版本：**2.4.3**，新增电视兼容播放引擎与软件解码回退，修复遥控器一次按键连续跳台。参见 [更新说明](RELEASE-2.4.3.md)。

## 2.1.0 家庭版

- 修复 Android 15 启动时窗口尚未创建导致的空指针崩溃。
- 启动自动播放上次频道（按服务器、分组、频道名保存；首次播放第一个频道）。
- 上下键换台、确认打开频道列表、返回继续播放；长按频道收藏。
- 手机上提供上一台、频道、收藏、下一台四个触屏按钮。
- 缓冲超时尝试备用线路；失败后最多自动重连三次。
- 支持 HTTP/HLS 和 RTSP；组播 UDP 的实际播放需单独验证，解析成功不代表可播放。
- 自动化启动测试覆盖 Android 9、Android 15 和电视模式，尚不能替代具体盒子的实机测试。

正式签名 APK 请从 GitHub Releases 下载。Docker 构建产物仍是调试包，仅用于开发。

面向电视和遥控器设计的原生 Android IPTV 客户端。客户端连接 `xibao-server`，读取频道、分组和 EPG，并由 Android Media3/ExoPlayer 负责播放。

## 功能

- 原生 Android TV 横屏界面，同时保留普通 Android 启动入口
- 遥控器焦点导航、上下键切台、数字键选台
- 频道分组与刷新
- 当前和下一节目 EPG
- HLS 与 Android Media3 支持的常见直播格式
- 局域网 HTTP 与公网 HTTPS 后端

## 接口

客户端使用以下公开接口：

- `GET /health`
- `GET /api/channels.json`
- `GET /api/epg?id=<channel-id>`

首次启动会显示服务器配置页。默认填入 `https://iptv.billtv.top:6443`，也可改为群晖局域网地址，例如 `http://192.168.6.3:8089`。

## 构建 APK

本机已配置 JDK 17 与 Android SDK 时：

```bash
cd android
./gradlew assembleDebug
```

APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`。

正式发布时设置 `XIBAO_KEYSTORE`、`XIBAO_STORE_PASSWORD`、`XIBAO_KEY_ALIAS` 和
`XIBAO_KEY_PASSWORD`，然后运行 `./gradlew assembleRelease`。发布密钥必须长期备份；
丢失后，新版本将无法覆盖安装旧版本。

也可以使用 Docker 构建并启动 APK 下载页：

```bash
docker compose up -d --build
```

访问 `http://群晖IP:8080/` 下载 `xibao-tv.apk`。
