# Build the native Android TV APK in a reproducible Linux image.
FROM eclipse-temurin:17-jdk-jammy AS apk-builder

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

RUN apt-get update && apt-get install -y --no-install-recommends wget unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/android-tools.zip \
    && unzip -q /tmp/android-tools.zip -d /tmp/android-tools \
    && mv /tmp/android-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm -rf /tmp/android-tools /tmp/android-tools.zip \
    && yes | sdkmanager --licenses >/dev/null 2>&1 \
    && sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools"

WORKDIR /app
COPY android ./android
RUN chmod +x android/gradlew \
    && cd android \
    && ./gradlew --no-daemon assembleDebug \
    && cp app/build/outputs/apk/debug/app-debug.apk /xibao-tv.apk

# Serve the APK and a small download page from the final image.
FROM nginx:alpine
COPY --from=apk-builder /xibao-tv.apk /usr/share/nginx/html/xibao-tv.apk
RUN printf '%s' '<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>喜宝 TV</title><style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#070a0f;color:#fff;font-family:system-ui}.card{width:min(88vw,440px);padding:42px;border:1px solid #273140;border-radius:24px;background:#111720;text-align:center}h1{margin:0 0 8px;font-size:34px}.sub{color:#9eaaba;margin:0 0 28px}.btn{display:block;padding:16px;border-radius:12px;background:#ffb547;color:#111;text-decoration:none;font-weight:700;font-size:18px}.meta{margin-top:18px;color:#718096;font-size:13px;line-height:1.8}</style></head><body><main class="card"><h1>喜宝 TV</h1><p class="sub">原生 Android TV 直播客户端</p><a class="btn" href="/xibao-tv.apk">下载 APK</a><p class="meta">支持电视遥控器、频道分组、数字选台与 EPG<br>Android 7.0 及以上</p></main></body></html>' > /usr/share/nginx/html/index.html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
