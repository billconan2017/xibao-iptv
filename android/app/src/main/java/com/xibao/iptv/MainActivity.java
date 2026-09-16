package com.xibao.iptv;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS = "xibao_tv";
    private static final String PREF_SERVER = "server_url";
    private static final String DEFAULT_SERVER = "https://iptv.billtv.top:6443";
    private static final int BG = Color.rgb(7, 10, 15);
    private static final int PANEL = Color.rgb(17, 23, 32);
    private static final int CARD = Color.rgb(28, 36, 48);
    private static final int ACCENT = Color.rgb(255, 181, 71);
    private static final int MUTED = Color.rgb(158, 170, 186);

    private final ApiClient api = new ApiClient();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Channel> allChannels = new ArrayList<>();
    private final List<Channel> visibleChannels = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();

    private FrameLayout root;
    private PlayerView playerView;
    private ExoPlayer player;
    private LinearLayout guidePanel;
    private LinearLayout infoPanel;
    private TextView channelTitle;
    private TextView epgText;
    private TextView statusText;
    private ProgressBar loading;
    private RecyclerView groupList;
    private RecyclerView channelList;
    private GroupAdapter groupAdapter;
    private ChannelAdapter channelAdapter;
    private String serverUrl = "";
    private String deviceId = "";
    private String selectedGroup = "全部频道";
    private int currentIndex = -1;
    private int sourceAttempts = 0;
    private long pendingChannelId = -1;
    private boolean guideVisible = true;
    private boolean compactUi = false;
    private String numberBuffer = "";
    private final Runnable hideGuide = () -> setGuideVisible(false);
    private final Runnable commitNumber = this::playNumberBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUi();
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        if (savedInstanceState != null) pendingChannelId = savedInstanceState.getLong("channel_id", -1);
        deviceId = getSharedPreferences(PREFS, MODE_PRIVATE).getString("device_id", "");
        if (deviceId.isBlank()) {
            deviceId = "xibao-" + UUID.randomUUID();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("device_id", deviceId).apply();
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (player != null) setGuideVisible(!guideVisible);
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        serverUrl = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SERVER, "");
        if (serverUrl.isBlank()) showConfigScreen(null); else loadChannels(true);
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void showConfigScreen(String error) {
        releasePlayer();
        root.removeAllViews();
        root.setBackgroundColor(BG);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(44), dp(34), dp(44), dp(34));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(rounded(PANEL, 22));

        TextView brand = text("喜宝 TV", 34, Color.WHITE, true);
        TextView subtitle = text("为电视和遥控器重新设计的直播播放器", 16, MUTED, false);
        LinearLayout.LayoutParams subtitleParams = wrap();
        subtitleParams.setMargins(0, dp(6), 0, dp(28));
        card.addView(brand, wrap());
        card.addView(subtitle, subtitleParams);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(serverUrl.isBlank() ? DEFAULT_SERVER : serverUrl);
        input.setHint("http://192.168.6.3:8089");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(100, 112, 128));
        input.setTextSize(18);
        input.setPadding(dp(18), 0, dp(18), 0);
        input.setBackground(rounded(CARD, 12));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        card.addView(input, inputParams);

        TextView hint = text("支持局域网 HTTP 地址和公网 HTTPS 地址", 14, MUTED, false);
        LinearLayout.LayoutParams hintParams = wrap();
        hintParams.gravity = Gravity.START;
        hintParams.setMargins(0, dp(10), 0, dp(18));
        card.addView(hint, hintParams);

        Button connect = button("连接服务器");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(280), dp(62));
        card.addView(connect, buttonParams);

        TextView message = text(error == null ? "" : error, 14, Color.rgb(255, 118, 118), false);
        LinearLayout.LayoutParams messageParams = wrap();
        messageParams.setMargins(0, dp(14), 0, 0);
        card.addView(message, messageParams);

        connect.setOnClickListener(v -> {
            try {
                String candidate = ApiClient.normalizeServerUrl(input.getText().toString());
                connect.setEnabled(false);
                connect.setText("正在连接…");
                message.setText("");
                api.test(candidate, new ApiClient.Callback<>() {
                    @Override public void onSuccess(String value) {
                        runOnUiThread(() -> {
                            serverUrl = candidate;
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_SERVER, serverUrl).apply();
                            loadChannels(true);
                        });
                    }

                    @Override public void onError(Exception e) {
                        runOnUiThread(() -> {
                            connect.setEnabled(true);
                            connect.setText("连接服务器");
                            message.setText("连接失败：" + friendlyError(e));
                        });
                    }
                });
            } catch (Exception e) {
                message.setText(e.getMessage());
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> { connect.performClick(); return true; });

        int cardWidth = Math.min(dp(720), getResources().getDisplayMetrics().widthPixels - dp(32));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(card, cardParams);
        connect.requestFocus();
    }

    private void loadChannels(boolean buildUi) {
        if (buildUi) buildPlayerScreen();
        setLoading(true, "正在连接管理平台…");
        String model = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        String deviceName = "喜宝-" + deviceId.substring(Math.max(0, deviceId.length() - 6));
        api.registerDevice(serverUrl, deviceId, deviceName, model, new ApiClient.Callback<>() {
            @Override public void onSuccess(JSONObject value) {
                runOnUiThread(() -> {
                    if (!value.optBoolean("authorized", false)) {
                        setLoading(false, authorizationMessage(value));
                        setGuideVisible(true);
                        return;
                    }
                    maybeShowNotice(value.optString("notice", ""));
                    fetchChannels();
                });
            }

            @Override public void onError(Exception e) {
                runOnUiThread(() -> setLoading(false, "连接管理平台失败：" + friendlyError(e)));
            }
        });
    }

    private void fetchChannels() {
        setLoading(true, "正在获取频道…");
        api.loadChannels(serverUrl, deviceId, new ApiClient.Callback<>() {
            @Override public void onSuccess(List<Channel> value) {
                runOnUiThread(() -> {
                    allChannels.clear();
                    allChannels.addAll(value);
                    rebuildGroups();
                    filterChannels(selectedGroup);
                    setLoading(false, value.isEmpty() ? "服务器还没有可播放的频道" : "");
                    if (!value.isEmpty()) {
                        Channel pending = null;
                        for (Channel channel : value) if (channel.id == pendingChannelId) { pending = channel; break; }
                        if (pending != null) play(pending);
                        else {
                            setGuideVisible(true);
                            channelList.post(() -> channelList.requestFocus());
                        }
                    }
                });
            }

            @Override public void onError(Exception e) {
                runOnUiThread(() -> setLoading(false, "频道加载失败：" + friendlyError(e)));
            }
        });
    }

    private String authorizationMessage(JSONObject status) {
        JSONObject tips = status.optJSONObject("tips");
        String tip;
        long expAt = status.optLong("exp_at", 0);
        if (status.optLong("meal_id", 0) > 0 && expAt > 0 && expAt <= System.currentTimeMillis() / 1000) {
            tip = tips == null ? "订阅已过期" : tips.optString("user_expired", "订阅已过期");
        } else {
            tip = tips == null ? "设备未授权" : tips.optString("user_noreg", "设备未授权");
        }
        return tip + "\n设备 ID：" + deviceId + "\n请在管理平台的“设备授权”中放行后按刷新";
    }

    private void maybeShowNotice(String notice) {
        String text = notice == null ? "" : notice.trim();
        if (text.isEmpty()) return;
        String shown = getSharedPreferences(PREFS, MODE_PRIVATE).getString("shown_notice", "");
        if (text.equals(shown)) return;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("shown_notice", text).apply();
        new AlertDialog.Builder(this)
            .setTitle("系统公告")
            .setMessage(text)
            .setPositiveButton("知道了", null)
            .show();
    }

    private void buildPlayerScreen() {
        releasePlayer();
        root.removeAllViews();

        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) setStatus("正在缓冲…");
                else if (state == Player.STATE_READY) setStatus("");
                else if (state == Player.STATE_ENDED) setStatus("节目已结束");
            }

            @Override public void onPlayerError(@NonNull PlaybackException error) {
                if (tryNextSource(true)) return;
                setStatus("播放失败，按确认键重试");
                setGuideVisible(true);
            }
        });

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(false);
        playerView.setKeepScreenOn(true);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setOnClickListener(v -> setGuideVisible(!guideVisible));
        root.addView(playerView, match());

        buildInfoPanel();
        buildGuidePanel();

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER);
        root.addView(loading, loadingParams);
    }

    private void buildInfoPanel() {
        infoPanel = new LinearLayout(this);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setPadding(dp(34), dp(24), dp(34), dp(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.argb(230, 5, 8, 12), Color.TRANSPARENT});
        infoPanel.setBackground(bg);
        channelTitle = text("请选择频道", 29, Color.WHITE, true);
        epgText = text("方向键浏览 · 确认键播放 · 数字键快速选台", 16, MUTED, false);
        statusText = text("", 15, ACCENT, false);
        infoPanel.addView(channelTitle, matchWidthWrap());
        infoPanel.addView(epgText, matchWidthWrap());
        infoPanel.addView(statusText, matchWidthWrap());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150), Gravity.TOP);
        root.addView(infoPanel, params);
    }

    private void buildGuidePanel() {
        compactUi = getResources().getConfiguration().smallestScreenWidthDp < 600;
        guidePanel = new LinearLayout(this);
        guidePanel.setOrientation(LinearLayout.VERTICAL);
        int panelPadding = dp(compactUi ? 12 : 24);
        guidePanel.setPadding(panelPadding, dp(compactUi ? 10 : 22), panelPadding, dp(compactUi ? 10 : 20));
        guidePanel.setBackground(rounded(Color.argb(244, 12, 17, 24), 0));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(compactUi ? "频道" : "直播频道", compactUi ? 21 : 26, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button refresh = button("刷新");
        Button settings = button("服务器");
        LinearLayout.LayoutParams smallButton = new LinearLayout.LayoutParams(dp(compactUi ? 88 : 120), dp(50));
        smallButton.setMargins(dp(10), 0, 0, 0);
        header.addView(refresh, smallButton);
        header.addView(settings, smallButton);
        guidePanel.addView(header, matchWidthWrap());

        LinearLayout lists = new LinearLayout(this);
        lists.setOrientation(compactUi ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        groupList = new RecyclerView(this);
        groupList.setLayoutManager(new LinearLayoutManager(this,
            compactUi ? RecyclerView.HORIZONTAL : RecyclerView.VERTICAL, false));
        channelList = new RecyclerView(this);
        channelList.setLayoutManager(new LinearLayoutManager(this));
        lists.addView(groupList, compactUi
            ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72))
            : new LinearLayout.LayoutParams(dp(245), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams channelsParams = compactUi
            ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1)
            : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        channelsParams.setMargins(compactUi ? 0 : dp(18), compactUi ? dp(10) : 0, 0, 0);
        lists.addView(channelList, channelsParams);
        guidePanel.addView(lists, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView footer = text("↑↓ 选择   ←→ 切换区域   OK 播放   菜单键显示频道   数字键选台", 14, MUTED, false);
        LinearLayout.LayoutParams footerParams = matchWidthWrap();
        footerParams.setMargins(0, dp(12), 0, 0);
        guidePanel.addView(footer, footerParams);

        groupAdapter = new GroupAdapter();
        channelAdapter = new ChannelAdapter();
        groupList.setAdapter(groupAdapter);
        channelList.setAdapter(channelAdapter);

        refresh.setOnClickListener(v -> loadChannels(false));
        settings.setOnClickListener(v -> showConfigScreen(null));
        int panelWidth = compactUi ? ViewGroup.LayoutParams.MATCH_PARENT
            : Math.min(dp(860), getResources().getDisplayMetrics().widthPixels);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        root.addView(guidePanel, params);
        guideVisible = true;
    }

    private void rebuildGroups() {
        Set<String> unique = new LinkedHashSet<>();
        for (Channel channel : allChannels) unique.add(channel.group);
        groups.clear();
        groups.add("全部频道");
        groups.addAll(unique);
        if (!groups.contains(selectedGroup)) selectedGroup = "全部频道";
        if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
    }

    private void filterChannels(String group) {
        selectedGroup = group;
        visibleChannels.clear();
        for (Channel channel : allChannels) {
            if ("全部频道".equals(group) || group.equals(channel.group)) visibleChannels.add(channel);
        }
        if (channelAdapter != null) channelAdapter.notifyDataSetChanged();
        if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
    }

    private void play(Channel channel) {
        int index = allChannels.indexOf(channel);
        if (index >= 0) currentIndex = index;
        sourceAttempts = 0;
        updateChannelTitle(channel);
        epgText.setText("正在获取节目单…");
        setStatus("正在连接频道…");
        startChannelSource(channel);
        loadEpg(channel);
        setGuideVisible(false);
        Toast.makeText(this, channel.name, Toast.LENGTH_SHORT).show();
    }

    private void startChannelSource(Channel channel) {
        player.setMediaItem(MediaItem.fromUri(channel.currentUrl()));
        player.prepare();
        player.play();
        updateChannelTitle(channel);
    }

    private void updateChannelTitle(Channel channel) {
        String source = channel.sources.size() > 1
            ? String.format(Locale.CHINA, "    线路 %d/%d", channel.sourceIndex + 1, channel.sources.size()) : "";
        channelTitle.setText(String.format(Locale.CHINA, "%03d  %s%s", currentIndex + 1, channel.name, source));
    }

    private boolean tryNextSource(boolean automatic) {
        if (currentIndex < 0 || currentIndex >= allChannels.size()) return false;
        Channel channel = allChannels.get(currentIndex);
        if (channel.sources.size() < 2) return false;
        if (automatic && sourceAttempts >= channel.sources.size() - 1) return false;
        sourceAttempts = automatic ? sourceAttempts + 1 : 0;
        channel.moveSource(1);
        setStatus(automatic ? "当前线路不可用，正在尝试备用线路…" : "正在切换线路…");
        startChannelSource(channel);
        Toast.makeText(this, String.format(Locale.CHINA, "线路 %d/%d", channel.sourceIndex + 1, channel.sources.size()), Toast.LENGTH_SHORT).show();
        return true;
    }

    private void loadEpg(Channel channel) {
        api.loadEpg(serverUrl, channel.name, new ApiClient.Callback<>() {
            @Override public void onSuccess(JSONObject root) {
                runOnUiThread(() -> {
                    if (currentIndex < 0 || allChannels.get(currentIndex).id != channel.id) return;
                    epgText.setText(formatEpg(root));
                });
            }

            @Override public void onError(Exception error) {
                runOnUiThread(() -> {
                    if (currentIndex >= 0 && allChannels.get(currentIndex).id == channel.id) epgText.setText("暂无节目单");
                });
            }
        });
    }

    private String formatEpg(JSONObject root) {
        JSONObject data = root.optJSONObject("data");
        if (data == null) return "暂无节目单";
        JSONArray programs = data.optJSONArray("programs");
        if (programs == null || programs.length() == 0) return "暂无节目单";
        long now = System.currentTimeMillis() / 1000;
        JSONObject current = null;
        JSONObject next = null;
        for (int i = 0; i < programs.length(); i++) {
            JSONObject item = programs.optJSONObject(i);
            if (item == null) continue;
            long start = item.optLong("start_time");
            long end = item.optLong("end_time");
            if (start <= now && now < end) current = item;
            else if (start > now) { next = item; break; }
        }
        SimpleDateFormat time = new SimpleDateFormat("HH:mm", Locale.CHINA);
        StringBuilder line = new StringBuilder();
        if (current != null) line.append("正在播  ").append(current.optString("title"));
        if (next != null) {
            if (line.length() > 0) line.append("    ·    ");
            line.append(time.format(new Date(next.optLong("start_time") * 1000))).append("  ").append(next.optString("title"));
        }
        return line.length() == 0 ? "暂无当前节目" : line.toString();
    }

    private void changeChannel(int delta) {
        if (allChannels.isEmpty()) return;
        int next = currentIndex < 0 ? 0 : (currentIndex + delta + allChannels.size()) % allChannels.size();
        play(allChannels.get(next));
    }

    private void setGuideVisible(boolean visible) {
        guideVisible = visible;
        if (guidePanel != null) guidePanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (infoPanel != null) infoPanel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideGuide);
        if (visible && channelList != null) channelList.post(() -> channelList.requestFocus());
        if (!visible && playerView != null) playerView.requestFocus();
        if (!visible) scheduleInfoHide();
    }

    private void scheduleInfoHide() {
        handler.removeCallbacks(hideGuide);
        handler.postDelayed(() -> {
            if (!guideVisible && infoPanel != null && statusText.getText().length() == 0) infoPanel.setVisibility(View.GONE);
        }, 5000);
    }

    private void setLoading(boolean active, String message) {
        if (loading != null) loading.setVisibility(active ? View.VISIBLE : View.GONE);
        if (!message.isEmpty()) setStatus(message);
        else if (!active) setStatus("");
    }

    private void setStatus(String message) {
        if (statusText == null) return;
        statusText.setText(message);
        if (!message.isEmpty()) infoPanel.setVisibility(View.VISIBLE); else scheduleInfoHide();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || player == null) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9) {
            numberBuffer += String.valueOf(key - KeyEvent.KEYCODE_0);
            channelTitle.setText("频道 " + numberBuffer);
            infoPanel.setVisibility(View.VISIBLE);
            handler.removeCallbacks(commitNumber);
            handler.postDelayed(commitNumber, 1200);
            return true;
        }
        if (guideVisible) {
            return super.dispatchKeyEvent(event);
        }
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: changeChannel(-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: changeChannel(1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (player.getPlaybackState() == Player.STATE_IDLE || player.getPlayerError() != null) {
                    player.prepare(); player.play();
                } else setGuideVisible(true);
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                setGuideVisible(true); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!tryNextSource(false)) Toast.makeText(this, "当前频道只有一条线路", Toast.LENGTH_SHORT).show();
                return true;
            default: return super.dispatchKeyEvent(event);
        }
    }

    private void playNumberBuffer() {
        if (numberBuffer.isEmpty()) return;
        try {
            int number = Integer.parseInt(numberBuffer);
            if (number >= 1 && number <= allChannels.size()) play(allChannels.get(number - 1));
            else Toast.makeText(this, "没有频道 " + number, Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException ignored) { }
        numberBuffer = "";
    }

    private String friendlyError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return "网络不可用";
        if (message.contains("timed out")) return "连接超时，请检查地址和网络";
        if (message.contains("Unable to resolve host")) return "找不到服务器，请检查地址";
        if (message.contains("Connection refused")) return "服务器拒绝连接，请检查端口";
        return message;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setBackground(rounded(CARD, 10));
        button.setOnFocusChangeListener((v, focused) -> v.setBackground(rounded(focused ? ACCENT : CARD, 10)));
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams matchWidthWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }

    private abstract class TextAdapter extends RecyclerView.Adapter<TextAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder { Holder(TextView item) { super(item); } }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView item = text("", 18, Color.WHITE, false);
            item.setFocusable(true);
            item.setClickable(true);
            item.setPadding(dp(18), 0, dp(16), 0);
            item.setSingleLine(true);
            item.setOnFocusChangeListener((v, focused) -> styleItem((TextView) v, focused, v.isSelected()));
            item.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
            return new Holder(item);
        }

        void styleItem(TextView item, boolean focused, boolean selected) {
            item.setTextColor(focused ? BG : selected ? ACCENT : Color.WHITE);
            item.setBackground(rounded(focused ? ACCENT : selected ? Color.rgb(49, 43, 30) : Color.TRANSPARENT, 9));
        }
    }

    private final class GroupAdapter extends TextAdapter {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Holder holder = super.onCreateViewHolder(parent, viewType);
            if (compactUi) holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(dp(170), dp(62)));
            return holder;
        }

        @Override public int getItemCount() { return groups.size(); }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            TextView item = (TextView) holder.itemView;
            String group = groups.get(position);
            int count = 0;
            for (Channel channel : allChannels) if (position == 0 || group.equals(channel.group)) count++;
            item.setText(group + "  " + count);
            item.setSelected(group.equals(selectedGroup));
            styleItem(item, item.hasFocus(), item.isSelected());
            item.setOnClickListener(v -> {
                filterChannels(group);
                channelList.requestFocus();
            });
            item.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    filterChannels(group); channelList.requestFocus(); return true;
                }
                return false;
            });
        }
    }

    private final class ChannelAdapter extends TextAdapter {
        @Override public int getItemCount() { return visibleChannels.size(); }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            TextView item = (TextView) holder.itemView;
            Channel channel = visibleChannels.get(position);
            int number = allChannels.indexOf(channel) + 1;
            String lines = channel.sources.size() > 1 ? "    · " + channel.sources.size() + " 条线路" : "";
            item.setText(String.format(Locale.CHINA, "%03d    %s%s", number, channel.name, lines));
            item.setSelected(number - 1 == currentIndex);
            styleItem(item, item.hasFocus(), item.isSelected());
            item.setOnClickListener(v -> play(channel));
            item.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    groupList.requestFocus(); return true;
                }
                return false;
            });
        }
    }

    private void releasePlayer() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override protected void onStart() {
        super.onStart();
        if (player != null && currentIndex >= 0) player.play();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (currentIndex >= 0 && currentIndex < allChannels.size()) outState.putLong("channel_id", allChannels.get(currentIndex).id);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        releasePlayer();
        api.shutdown();
        super.onDestroy();
    }
}
