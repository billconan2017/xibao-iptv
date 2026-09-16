# 喜宝IPTV

开源 IPTV 播放器，Vue 3 + Capacitor 构建，原生 Android 全架构 APK。

## 快速部署（群晖）

```bash
docker pull billcoann/xibao-iptv:latest
docker run -d --name xibao-iptv -p 8080:80 billcoann/xibao-iptv:latest
```

访问 `http://群晖IP:8080` 下载 APK。

## 使用

APK 安装后打开 APP，输入你的 IPTV 服务器地址即可。

## Docker

```yaml
services:
  xibao-iptv:
    image: billcoann/xibao-iptv:latest
    container_name: xibao-iptv
    restart: unless-stopped
    ports:
      - "8080:80"
```

## 技术栈

- Vue 3 + Vite
- Capacitor (Android)
- Nginx (Docker)

## License

MIT
