# ============================================
# 清和IPTV - 全架构 APK 构建镜像
# 使用 Capacitor + Android SDK 编译 arm64 APK
# 
# 编译出的 APK 打开就是配置页，用户自己输服务器地址
# 不需要每次改代码重新编译
# ============================================

# ---- 阶段1：构建前端 ----
FROM node:22-alpine AS frontend

WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
RUN npm run build

# ---- 阶段2：编译 Android APK ----
FROM openjdk:21-jdk-slim AS apk-builder

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/build-tools/35.0.0

# 安装编译依赖
RUN apt-get update && apt-get install -y \
    wget unzip git curl \
    && rm -rf /var/lib/apt/lists/*

# 安装 Android SDK command-line tools
RUN mkdir -p ${ANDROID_SDK_ROOT} && \
    cd /tmp && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip -d ${ANDROID_SDK_ROOT} && \
    mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/* ${ANDROID_SDK_ROOT}/cmdline-tools/latest/ 2>/dev/null; \
    rm /tmp/cmdline-tools.zip

# 安装 SDK 平台和构建工具
RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"

WORKDIR /app

# 复制前端和 Android 工程
COPY --from=frontend /app/dist ./dist
COPY --from=frontend /app/android ./android
COPY --from=frontend /app/capacitor.config.json ./
COPY --from=frontend /app/package.json ./

# 安装 Capacitor
RUN npm install -g @capacitor/cli && npm install @capacitor/core @capacitor/android

# 同步并编译
RUN npx cap sync android
WORKDIR /app/android
RUN chmod +x gradlew && ./gradlew assembleDebug

# 提取 APK
RUN mkdir -p /output && \
    (cp /app/android/app/build/outputs/apk/debug/*.apk /output/ 2>/dev/null || true) && \
    ls -la /output/

# ---- 阶段3：轻量 Web 镜像（Nginx 提供 APK 下载）----
FROM nginx:alpine

# 复制 APK
COPY --from=apk-builder /output/*.apk /usr/share/nginx/html/qinghe-iptv.apk

# 写入版本信息
COPY --from=frontend /app/package.json /tmp/pkg.json
RUN VERSION=$(cat /tmp/pkg.json | grep '"version"' | cut -d'"' -f4) && \
    echo "${VERSION:-1.0.0}" > /usr/share/nginx/html/version.txt

# 生成下载页面
RUN echo '<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>清和IPTV 下载</title><style>body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#0a0a0a;color:#fff;display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:24px;text-align:center}*{box-sizing:border-box}.card{background:#1a1a2e;border-radius:16px;padding:40px 32px;max-width:400px;width:100%}.logo{font-size:32px;margin:0 0 8px}.sub{color:#888;font-size:14px;margin-bottom:24px}.btn{display:inline-block;padding:16px 40px;background:#ff6b35;color:#fff;border-radius:12px;text-decoration:none;font-size:18px;font-weight:600;margin-top:8px;transition:background .2s}.btn:hover{background:#e85a2a}.info{color:#666;font-size:12px;margin-top:16px;line-height:1.6}.step{text-align:left;color:#aaa;font-size:13px;margin:16px 0;padding:12px;background:#111;border-radius:8px}</style></head><body><div class="card"><h1 class="logo">📺 清和IPTV</h1><p class="sub">开源 IPTV 播放器 · 手机/电视通用</p><a class="btn" href="/qinghe-iptv.apk" download>📥 下载 APK</a><div class="step"><strong>安装后配置：</strong><br>1. 安装 APK<br>2. 打开 APP<br>3. 输入你的服务器地址<br>4. 即可播放</div><p class="info">全架构 arm64-v8a / armeabi-v7a<br>minSdk 24 · Capacitor + Vue 3</p></div></body></html>' > /usr/share/nginx/html/index.html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]