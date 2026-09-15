# ============================================
# 清和IPTV - 全架构 APK 构建镜像
# 使用 Capacitor + Android SDK 编译 arm64 APK
# ============================================

# ---- 阶段1：构建前端 ----
FROM node:22-alpine AS frontend

WORKDIR /app

# 安装依赖
COPY package.json package-lock.json* ./
RUN npm ci

# 构建前端
COPY . .
RUN npm run build

# ---- 阶段2：编译 Android APK ----
FROM openjdk:17-jdk-slim AS apk-builder

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/build-tools/35.0.0

# 安装依赖
RUN apt-get update && apt-get install -y \
    wget unzip git curl \
    && rm -rf /var/lib/apt/lists/*

# 安装 Android SDK command-line tools
RUN mkdir -p ${ANDROID_SDK_ROOT} && \
    cd /tmp && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip -d ${ANDROID_SDK_ROOT} && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools-latest && \
    mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools-latest ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# 安装 SDK 平台和构建工具
RUN yes | ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 && \
    ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-35" \
    "build-tools;35.0.0" \
    "platform-tools"

WORKDIR /app

# 复制前端构建产物
COPY --from=frontend /app/dist ./dist
COPY --from=frontend /app/android ./android
COPY --from=frontend /app/capacitor.config.json ./
COPY --from=frontend /app/package.json ./

# 安装 Capacitor CLI 和 Node 依赖
RUN npm install -g @capacitor/cli && \
    npm install @capacitor/core @capacitor/android

# 同步 Capacitor 配置
RUN npx cap sync android

# 编译 APK（release 模式）
WORKDIR /app/android
RUN chmod +x gradlew && \
    ANDROID_HOME=/opt/android-sdk ./gradlew assembleRelease

# 将 APK 复制到输出目录
RUN mkdir -p /output && \
    cp /app/android/app/build/outputs/apk/release/*.apk /output/ 2>/dev/null || \
    cp /app/android/app/build/outputs/apk/debug/*.apk /output/ 2>/dev/null || true && \
    ls -la /output/

# ---- 阶段3：最终镜像（仅包含 APK + Web 管理）----
FROM alpine:latest AS web

RUN apk add --no-cache nginx

WORKDIR /app

# 复制前端（Web 版管理页面）
COPY --from=frontend /app/dist /usr/share/nginx/html

# 复制编译好的 APK
COPY --from=apk-builder /output/*.apk /usr/share/nginx/html/ 2>/dev/null || true

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]