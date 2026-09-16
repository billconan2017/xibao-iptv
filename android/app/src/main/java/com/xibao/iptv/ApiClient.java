package com.xibao.iptv;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ApiClient {
    interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception error);
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private String deviceId = "", deviceToken = "";
    void setCredentials(String id, String token) { deviceId=id; deviceToken=token; }

    void loadVodSources(String serverUrl, Callback<JSONObject> callback) {
        request(serverUrl + "/api/vod/sources", JSONObject::new, callback);
    }
    void loadVod(String serverUrl, long source, int page, String search, String id, Callback<JSONObject> callback) {
        request(serverUrl + "/api/vod/catalog?source="+source+"&page="+page+"&search="+Uri.encode(search)+"&id="+Uri.encode(id), JSONObject::new, callback);
    }

    static String normalizeServerUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty()) throw new IllegalArgumentException("请输入服务器地址");
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
        URI uri = URI.create(value);
        if (uri.getHost() == null) throw new IllegalArgumentException("服务器地址格式不正确");
        return value;
    }

    void test(String serverUrl, Callback<String> callback) {
        request(serverUrl + "/health", body -> {
            JSONObject json = new JSONObject(body);
            if (!"ok".equalsIgnoreCase(json.optString("status"))) {
                throw new IllegalStateException("服务器健康检查未通过");
            }
            return json.optString("name", "xibao-server");
        }, callback);
    }

    void registerDevice(String serverUrl, String deviceId, String name, String model, Callback<JSONObject> callback) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("device_id", deviceId);
            payload.put("name", name);
            payload.put("model", model);
        } catch (Exception ignored) { }
        post(serverUrl + "/api/device/heartbeat", payload.toString(), body -> {
            JSONObject root = new JSONObject(body);
            if (root.optInt("code", 1) != 0) throw new IllegalStateException(root.optString("msg", "设备注册失败"));
            JSONObject data = root.optJSONObject("data");
            if (data == null) throw new IllegalStateException("设备接口数据无效");
            return data;
        }, callback);
    }

    void loadChannels(String serverUrl, String deviceId, Callback<List<Channel>> callback) {
        String encoded = Uri.encode(deviceId);
        request(serverUrl + "/api/channels.json?device_id=" + encoded, body -> {
            JSONObject root = new JSONObject(body);
            if (root.optInt("code", 1) != 0) throw new IllegalStateException(root.optString("msg", "频道接口返回错误"));
            JSONArray rows = root.optJSONArray("data");
            Map<String, Channel> merged = new LinkedHashMap<>();
            if (rows == null) return new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String url = row.optString("url", "").trim();
                if (url.isEmpty()) continue;
                String name = row.optString("name", "未命名频道");
                String group = row.optString("group_name", "未分组");
                String key = group.trim() + "\u0000" + name.trim();
                Channel channel = merged.get(key);
                if (channel == null) {
                    channel = new Channel(row.optLong("id"), name, url, group,
                        row.optString("tvg_id", ""), row.optString("tvg_logo", ""));
                    merged.put(key, channel);
                } else channel.addSource(url);
            }
            return new ArrayList<>(merged.values());
        }, callback);
    }

    void loadEpg(String serverUrl, String channelName, Callback<JSONObject> callback) {
        request(serverUrl + "/api/epg?name=" + Uri.encode(channelName), body -> new JSONObject(body), callback);
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private interface Parser<T> { T parse(String body) throws Exception; }

    private <T> void request(String address, Parser<T> parser, Callback<T> callback) {
        execute("GET", address, null, parser, callback);
    }

    private <T> void post(String address, String json, Parser<T> parser, Callback<T> callback) {
        execute("POST", address, json, parser, callback);
    }

    private <T> void execute(String method, String address, String json, Parser<T> parser, Callback<T> callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "XibaoTV/2.2");
                connection.setRequestProperty("X-Device-Id", deviceId);
                connection.setRequestProperty("X-Device-Token", deviceToken);
                // Do not forward a device credential to a different host through redirects.
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod(method);
                if (json != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
                String body = read(stream);
                if (status < 200 || status >= 300) {
                    String message="服务器返回 HTTP " + status;
                    try { message=new JSONObject(body).optString("msg",message); } catch(Exception ignored) { }
                    throw new IllegalStateException(message);
                }
                callback.onSuccess(parser.parse(body));
            } catch (Exception error) {
                callback.onError(error);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }
}
