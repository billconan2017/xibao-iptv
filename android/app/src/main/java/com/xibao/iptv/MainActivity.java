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
import android.widget.ScrollView;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.security.SecureRandom;

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

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
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
    private boolean phoneUi;
    private int requestGeneration = 0;
    private LinearLayout touchControls;
    private int retryCount = 0;
    private ProgressBar epgProgress;
    private TextView epgTime;
    private JSONArray currentPrograms = new JSONArray();
    private TextView guideStatus;
    private LinearLayout navigation;
    private boolean vodMode = false, activeScreen = false, accessGranted = false;
    private VodBrowser vodBrowser;
    private final Runnable epgTicker = new Runnable() { public void run() {
        if (player == null || !activeScreen) return;
        if (!vodMode) renderEpg();
        handler.postDelayed(this, 30000);
    }};
    private final Runnable heartbeat = new Runnable() { public void run() {
        if (player == null || !activeScreen) return;
        final int generation = requestGeneration;
        api.registerDevice(serverUrl,deviceId,"喜宝-"+deviceId.substring(deviceId.length()-6),Build.MANUFACTURER+" "+Build.MODEL,new ApiClient.Callback<JSONObject>() {
            public void onSuccess(JSONObject value) { runOnUiThread(()->{if(!isCurrent(generation))return;accessGranted=value.optBoolean("authorized",false);if(!accessGranted){player.stop();allChannels.clear();visibleChannels.clear();rebuildGroups();filterChannels(selectedGroup);setGuideVisible(true);setStatus(authorizationMessage(value));}}); }
            public void onError(Exception e) { runOnUiThread(()->{if(isCurrent(generation)) {accessGranted=false;player.stop();setStatus("授权连接失败，请按刷新重新连接："+friendlyError(e));setGuideVisible(true);}}); }
        });
        if(!vodMode && currentIndex>=0 && currentIndex<allChannels.size()) loadEpg(allChannels.get(currentIndex));
        handler.postDelayed(this,60000);
    }};
    private final Runnable retryPlayback = () -> {
        if (!vodMode && player != null && currentIndex >= 0 && currentIndex < allChannels.size()) {
            sourceAttempts = 0;
            startChannelSource(allChannels.get(currentIndex));
        }
    };
    private final Runnable bufferingTimeout = () -> {
        if (player != null && player.getPlaybackState() == Player.STATE_BUFFERING) {
            if (vodMode) setStatus("点播加载超时，请返回点播列表换线路"); else if (!tryNextSource(true)) scheduleRetry();
        }
    };
    private String numberBuffer = "";
    private final Runnable hideGuide = () -> setGuideVisible(false);
    private final Runnable commitNumber = this::playNumberBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        phoneUi = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK) != Configuration.UI_MODE_TYPE_TELEVISION;
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        hideSystemUi();
        if (savedInstanceState != null) pendingChannelId = savedInstanceState.getLong("channel_id", -1);
        initializeIdentity();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (player != null) {
                    if (vodMode) { showVodExit(); }
                    else if (guideVisible) setGuideVisible(false);
                    else confirmExit();
                }
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        serverUrl = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SERVER, "");
        if (serverUrl.trim().isEmpty()) showConfigScreen(null); else loadChannels(true);
    }

    private void initializeIdentity() {
        android.content.SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        deviceId=prefs.getString("secure_device_id", "");
        String token=prefs.getString("device_token", "");
        if(deviceId.isEmpty() || token.isEmpty()) {
            deviceId="xibao-"+UUID.randomUUID();
            byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder hex=new StringBuilder();
            for(byte value:bytes)hex.append(String.format(Locale.ROOT,"%02x",value & 255));token=hex.toString();
            prefs.edit().putString("secure_device_id",deviceId).putString("device_token",token).apply();
        }
        api.setCredentials(deviceId,token);
    }

    private void hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars=WindowCompat.getInsetsController(getWindow(),root);
        if(phoneUi) {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            bars.show(WindowInsetsCompat.Type.systemBars());
            bars.setAppearanceLightStatusBars(false);
            bars.setAppearanceLightNavigationBars(false);
        } else {
            bars.hide(WindowInsetsCompat.Type.systemBars());
            bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
            androidx.core.graphics.Insets safe=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void confirmExit() {
        new AlertDialog.Builder(this).setTitle("退出喜宝 TV？").setMessage("退出后会停止播放。下次打开继续看上次的频道。")
            .setNegativeButton("继续观看",null).setPositiveButton("退出",(d,w)->{releasePlayer();finish();}).show();
    }

    private void showConfigScreen(String error) {
        requestGeneration++;
        releasePlayer();
        root.removeAllViews();
        root.setBackgroundColor(BG);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(phoneUi ? 24 : 44), dp(30), dp(phoneUi ? 24 : 44), dp(30));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(rounded(PANEL, 22));

        TextView brand = text("喜宝 TV", 34, Color.WHITE, true);
        TextView subtitle = text("连接一次，以后打开就看电视", 18, MUTED, false);
        LinearLayout.LayoutParams subtitleParams = wrap();
        subtitleParams.setMargins(0, dp(6), 0, dp(28));
        card.addView(brand, wrap());
        card.addView(subtitle, subtitleParams);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(serverUrl.trim().isEmpty() ? DEFAULT_SERVER : serverUrl);
        input.setHint("http://192.168.6.3:8189");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
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
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        card.addView(connect, buttonParams);

        TextView message = text(error == null ? "" : error, 14, Color.rgb(255, 118, 118), false);
        LinearLayout.LayoutParams messageParams = wrap();
        messageParams.setMargins(0, dp(14), 0, 0);
        card.addView(message, messageParams);

        connect.setOnClickListener(v -> {
            try {
                final int generation = ++requestGeneration;
                String candidate = ApiClient.normalizeServerUrl(input.getText().toString());
                connect.setEnabled(false);
                connect.setText("正在连接…");
                message.setText("");
                api.test(candidate, new ApiClient.Callback<>() {
                    @Override public void onSuccess(String value) {
                        runOnUiThread(() -> {
                            if (!isCurrent(generation)) return;
                            serverUrl = candidate;
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_SERVER, serverUrl).apply();
                            loadChannels(true);
                        });
                    }

                    @Override public void onError(Exception e) {
                        runOnUiThread(() -> {
                            if (!isCurrent(generation)) return;
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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(card);
        root.addView(scroll, cardParams);
        connect.post(connect::requestFocus);
    }

    private void loadChannels(boolean buildUi) {
        final int generation = ++requestGeneration;
        accessGranted=false;
        if(player!=null)player.pause();
        if (buildUi) buildPlayerScreen();
        setLoading(true, "正在连接管理平台…");
        String model = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        String deviceName = "喜宝-" + deviceId.substring(Math.max(0, deviceId.length() - 6));
        api.registerDevice(serverUrl, deviceId, deviceName, model, new ApiClient.Callback<>() {
            @Override public void onSuccess(JSONObject value) {
                runOnUiThread(() -> {
                    if (!isCurrent(generation)) return;
                    accessGranted=value.optBoolean("authorized",false);
                    if (!value.optBoolean("authorized", false)) {
                        player.stop();allChannels.clear();currentIndex=-1;rebuildGroups();filterChannels(selectedGroup);
                        setLoading(false, authorizationMessage(value));
                        setGuideVisible(true);
                        return;
                    }
                    maybeShowNotice(value.optString("notice", ""));
                    fetchChannels();
                });
            }

            @Override public void onError(Exception e) {
                runOnUiThread(() -> { if(isCurrent(generation)) setLoading(false, "连接管理平台失败：" + friendlyError(e)); });
            }
        });
    }

    private void fetchChannels() {
        final int generation = requestGeneration;
        setLoading(true, "正在获取频道…");
        api.loadChannels(serverUrl, deviceId, new ApiClient.Callback<>() {
            @Override public void onSuccess(List<Channel> value) {
                runOnUiThread(() -> {
                    if (!isCurrent(generation)) return;
                    String lastKey = currentIndex >= 0 && currentIndex < allChannels.size()
                        ? channelKey(allChannels.get(currentIndex)) : getSharedPreferences(PREFS, MODE_PRIVATE).getString("last_channel", "");
                    currentIndex = -1;
                    allChannels.clear();
                    allChannels.addAll(value);
                    rebuildGroups();
                    filterChannels(selectedGroup);
                    setLoading(false, value.isEmpty() ? "服务器还没有可播放的频道" : "");
                    if (!value.isEmpty()) {
                        Channel pending = null;
                        for (Channel channel : value) if (channelKey(channel).equals(lastKey)) { pending = channel; break; }
                        if (pending != null) play(pending);
                        else {
                            play(value.get(0));
                        }
                    }
                });
            }

            @Override public void onError(Exception e) {
                runOnUiThread(() -> { if(isCurrent(generation)) setLoading(false, "频道加载失败：" + friendlyError(e)); });
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
        touchControls = null;
        navigation = null;
        vodMode = false;
        compactUi = phoneUi && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        root.removeAllViews();

        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                handler.removeCallbacks(bufferingTimeout);
                if (state == Player.STATE_BUFFERING) {
                    setStatus("正在连接，请稍候…");
                    handler.postDelayed(bufferingTimeout, 20000);
                }
                else if (state == Player.STATE_READY) {
                    retryCount = 0;
                    handler.removeCallbacks(retryPlayback);
                    setStatus("");
                }
                else if (state == Player.STATE_ENDED) setStatus("节目已结束");
            }

            @Override public void onPlayerError(@NonNull PlaybackException error) {
                if (vodMode) {setStatus("点播播放失败，请换线路或返回点播列表");return;}
                if (tryNextSource(true)) return;
                scheduleRetry();
            }
        });

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(false);
        playerView.setKeepScreenOn(true);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setOnClickListener(v -> {if(!vodMode)setGuideVisible(!guideVisible);});
        FrameLayout.LayoutParams videoParams=match();
        if(compactUi) {videoParams.height=(getResources().getDisplayMetrics().widthPixels-dp(24))*9/16;videoParams.topMargin=dp(70);videoParams.leftMargin=dp(12);videoParams.rightMargin=dp(12);}
        root.addView(playerView, videoParams);

        buildInfoPanel();
        buildGuidePanel();
        buildNavigation();
        if (phoneUi) buildTouchControls();
        handler.postDelayed(epgTicker,30000);
        handler.postDelayed(heartbeat,60000);

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER);
        root.addView(loading, loadingParams);
    }

    private void buildInfoPanel() {
        infoPanel = new LinearLayout(this);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setPadding(dp(compactUi ? 20:34), dp(18), dp(compactUi ? 20:34), dp(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.argb(230, 5, 8, 12), Color.TRANSPARENT});
        infoPanel.setBackground(bg);
        channelTitle = text("请选择频道", compactUi ? 23:29, Color.WHITE, true);
        epgText = text("方向键浏览 · 确认键播放 · 数字键快速选台", 16, MUTED, false);
        statusText = text("", 15, ACCENT, false);
        infoPanel.addView(channelTitle, matchWidthWrap());
        infoPanel.addView(epgText, matchWidthWrap());
        epgTime=text("节目单由管理平台的 EPG 源提供",14,MUTED,false);
        epgProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);epgProgress.setMax(1000);
        epgProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(-1,dp(6));progressParams.setMargins(0,dp(14),0,dp(8));
        infoPanel.addView(epgProgress,progressParams);infoPanel.addView(epgTime,matchWidthWrap());
        infoPanel.addView(statusText, matchWidthWrap());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        params.topMargin=compactUi ? dp(90)+(getResources().getDisplayMetrics().widthPixels-dp(24))*9/16 : dp(64);
        if(compactUi) {params.leftMargin=dp(12);params.rightMargin=dp(12);infoPanel.setBackground(rounded(PANEL,18));}
        root.addView(infoPanel, params);
    }

    private void buildGuidePanel() {
        compactUi = phoneUi && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        guidePanel = new LinearLayout(this);
        guidePanel.setOrientation(LinearLayout.VERTICAL);
        int panelPadding = dp(compactUi ? 12 : 24);
        guidePanel.setPadding(panelPadding, dp(compactUi ? 10 : 22), panelPadding, dp(compactUi ? 10 : 20));
        guidePanel.setBackground(rounded(Color.argb(244, 12, 17, 24), 0));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(compactUi ? "喜宝 TV" : "直播频道", compactUi ? 21 : 26, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button refresh = button("刷新");
        Button settings = button("服务器");
        LinearLayout.LayoutParams smallButton = new LinearLayout.LayoutParams(dp(compactUi ? 88 : 120), dp(50));
        smallButton.setMargins(dp(10), 0, 0, 0);
        header.addView(refresh, smallButton);
        header.addView(settings, smallButton);
        guidePanel.addView(header, matchWidthWrap());
        guideStatus=text("",14,ACCENT,false);
        guidePanel.addView(guideStatus,matchWidthWrap());

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

        TextView footer = text(phoneUi ? "点击频道播放 · 长按频道收藏" : "上下选台 · 确认播放 · 长按确认收藏 · 返回看电视", 17, MUTED, false);
        LinearLayout.LayoutParams footerParams = matchWidthWrap();
        footerParams.setMargins(0, dp(12), 0, 0);
        guidePanel.addView(footer, footerParams);

        groupAdapter = new GroupAdapter();
        channelAdapter = new ChannelAdapter();
        groupList.setAdapter(groupAdapter);
        channelList.setAdapter(channelAdapter);

        refresh.setOnClickListener(v -> loadChannels(false));
        settings.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("服务器设置")
            .setMessage("更换服务器会暂时停止播放。是否继续？")
            .setNegativeButton("继续看电视", null).setPositiveButton("打开设置", (d,w)->showConfigScreen(null)).show());
        int panelWidth = compactUi ? ViewGroup.LayoutParams.MATCH_PARENT
            : Math.min(dp(860), getResources().getDisplayMetrics().widthPixels);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        params.topMargin=dp(64);
        root.addView(guidePanel, params);
        guideVisible = true;
    }

    private void rebuildGroups() {
        Set<String> unique = new LinkedHashSet<>();
        for (Channel channel : allChannels) unique.add(channel.group);
        groups.clear();
        groups.add("全部频道");
        groups.add("我的收藏");
        groups.addAll(unique);
        if (!groups.contains(selectedGroup)) selectedGroup = "全部频道";
        if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
    }

    private void filterChannels(String group) {
        selectedGroup = group;
        visibleChannels.clear();
        for (Channel channel : allChannels) {
            if ("全部频道".equals(group) || group.equals(channel.group) || ("我的收藏".equals(group) && isFavorite(channel))) visibleChannels.add(channel);
        }
        if (channelAdapter != null) channelAdapter.notifyDataSetChanged();
        if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
    }

    private void play(Channel channel) {
        if(!accessGranted){setStatus("请先连接服务器并完成设备授权，再按刷新");return;}
        vodMode=false;playerView.setUseController(false);currentPrograms=new JSONArray();
        if(epgProgress!=null)epgProgress.setProgress(0);
        handler.removeCallbacks(retryPlayback);
        handler.removeCallbacks(bufferingTimeout);
        retryCount = 0;
        int index = allChannels.indexOf(channel);
        if (index >= 0) currentIndex = index;
        sourceAttempts = 0;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("last_channel", channelKey(channel)).apply();
        updateChannelTitle(channel);
        epgText.setText("正在获取节目单…");
        setStatus("正在连接频道…");
        startChannelSource(channel);
        loadEpg(channel);
        setGuideVisible(false);
        Toast.makeText(this, channel.name, Toast.LENGTH_SHORT).show();
    }

    private void startChannelSource(Channel channel) {
        if (player == null || !accessGranted) return;
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
        if (vodMode || currentIndex < 0 || currentIndex >= allChannels.size()) return false;
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
                    if (isDestroyed() || player == null || currentIndex < 0 || currentIndex >= allChannels.size() || allChannels.get(currentIndex) != channel) return;
                    if(vodMode)return;
                    JSONObject data=root.optJSONObject("data");
                    currentPrograms=data==null?new JSONArray():data.optJSONArray("programs");
                    if(currentPrograms==null)currentPrograms=new JSONArray();
                    renderEpg();
                });
            }

            @Override public void onError(Exception error) {
                runOnUiThread(() -> {
                    if (!vodMode && !isDestroyed() && player != null && currentIndex >= 0 && currentIndex < allChannels.size() && allChannels.get(currentIndex) == channel) epgText.setText("暂无节目单");
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
        if(vodMode){showVodExit();return;}
        if (allChannels.isEmpty()) return;
        int next = currentIndex < 0 ? 0 : (currentIndex + delta + allChannels.size()) % allChannels.size();
        play(allChannels.get(next));
    }

    private void setGuideVisible(boolean visible) {
        guideVisible = visible;
        if (guidePanel != null) guidePanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (touchControls != null) touchControls.setVisibility(visible || vodMode ? View.GONE : View.VISIBLE);
        if (infoPanel != null) infoPanel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideGuide);
        if (visible && channelList != null) channelList.post(() -> channelList.requestFocus());
        if (!visible && playerView != null) playerView.requestFocus();
        if (!visible) scheduleInfoHide();
    }

    private void scheduleInfoHide() {
        if(compactUi)return;
        handler.removeCallbacks(hideGuide);
        handler.postDelayed(() -> {
            if (!compactUi && !guideVisible && infoPanel != null && statusText.getText().length() == 0) infoPanel.setVisibility(View.GONE);
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
        if(guideStatus!=null){guideStatus.setText(message);guideStatus.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);}
        if (!message.isEmpty()) infoPanel.setVisibility(View.VISIBLE); else scheduleInfoHide();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || player == null || vodMode || (phoneUi && !guideVisible)) return super.dispatchKeyEvent(event);
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
            case KeyEvent.KEYCODE_BOOKMARK:
                toggleFavorite(); return true;
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
        if (message == null || message.trim().isEmpty()) return "网络不可用";
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
        button.setFocusableInTouchMode(true);
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
            TextView item = text("", phoneUi ? 20 : 24, Color.WHITE, false);
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
            for (Channel channel : allChannels) if (position == 0 || group.equals(channel.group) || ("我的收藏".equals(group) && isFavorite(channel))) count++;
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
            item.setOnLongClickListener(v -> {
                boolean favorite = !isFavorite(channel);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("fav:" + channelKey(channel), favorite).apply();
                filterChannels(selectedGroup);
                Toast.makeText(MainActivity.this, favorite ? "已加入我的收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
                return true;
            });
            item.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    groupList.requestFocus(); return true;
                }
                return false;
            });
        }
    }

    private void releasePlayer() {
        if(vodBrowser!=null){vodBrowser.close();vodBrowser=null;}
        handler.removeCallbacksAndMessages(null);
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override protected void onStop() {
        super.onStop();
        activeScreen=false;
        handler.removeCallbacks(epgTicker);
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(retryPlayback);
        handler.removeCallbacks(bufferingTimeout);
        if (player != null) player.pause();
    }

    @Override protected void onStart() {
        super.onStart();
        activeScreen=true;
        if (player != null) { if(accessGranted && (currentIndex>=0 || vodMode))player.play(); handler.removeCallbacks(epgTicker);handler.post(epgTicker);handler.removeCallbacks(heartbeat);handler.post(heartbeat); }
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (currentIndex >= 0 && currentIndex < allChannels.size()) outState.putLong("channel_id", allChannels.get(currentIndex).id);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        requestGeneration++;
        releasePlayer();
        api.shutdown();
        super.onDestroy();
    }

    private boolean isCurrent(int generation) { return generation == requestGeneration && !isFinishing() && !isDestroyed(); }
    private String channelKey(Channel channel) { return serverUrl + "|" + channel.group + "|" + channel.name; }
    private boolean isFavorite(Channel channel) { return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("fav:" + channelKey(channel), false); }
    private void toggleFavorite() {
        if(vodMode)return;
        if (currentIndex < 0 || currentIndex >= allChannels.size()) return;
        Channel channel = allChannels.get(currentIndex);
        boolean favorite = !isFavorite(channel);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("fav:" + channelKey(channel), favorite).apply();
        filterChannels(selectedGroup);
        Toast.makeText(this, favorite ? "已加入我的收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }
    private void scheduleRetry() {
        handler.removeCallbacks(retryPlayback);
        handler.removeCallbacks(bufferingTimeout);
        if (++retryCount > 3) {
            setStatus("暂时无法播放，请换个频道或打开频道列表重试");
            setGuideVisible(true);
            return;
        }
        setStatus("信号暂时中断，正在自动重连（" + retryCount + "/3）…");
        handler.postDelayed(retryPlayback, retryCount * 3000L);
    }
    private void buildTouchControls() {
        touchControls = new LinearLayout(this);
        touchControls.setPadding(dp(12), dp(8), dp(12), dp(8));
        touchControls.setBackgroundColor(Color.argb(220,7,10,15));
        String[] labels = {"上一台", "频道", "收藏", "下一台"};
        for (int i=0;i<labels.length;i++) {
            final int action=i;
            Button control=button(labels[i]);
            control.setTextSize(15);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(54),1);
            params.setMargins(dp(3),0,dp(3),0);
            touchControls.addView(control,params);
            control.setOnClickListener(v->{if(action==0)changeChannel(-1);else if(action==1){if(vodMode)openVod();else setGuideVisible(true);}else if(action==2)toggleFavorite();else changeChannel(1);});
        }
        root.addView(touchControls,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        touchControls.setVisibility(View.GONE);
    }

    private void buildNavigation() {
        navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setPadding(dp(10),dp(8),dp(10),dp(8));navigation.setBackgroundColor(BG);
        String[] labels={"返回","节目单","点播",phoneUi?"横/竖屏":"收藏","退出"};
        for(int i=0;i<labels.length;i++) {
            final int action=i;Button control=button(labels[i]);control.setTextSize(phoneUi?13:17);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(2),0,dp(2),0);
            navigation.addView(control,lp);
            control.setOnClickListener(v->{
                if(action==0){if(vodMode)showVodExit();else if(guideVisible)setGuideVisible(false);else setGuideVisible(true);}
                else if(action==1)showPrograms();
                else if(action==2)openVod();
                else if(action==3){if(phoneUi)setRequestedOrientation(compactUi?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);else toggleFavorite();}
                else confirmExit();
            });
        }
        root.addView(navigation,new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP));
    }

    private void renderEpg() {
        if(epgText==null || vodMode)return;
        JSONObject payload=new JSONObject(), data=new JSONObject();
        try {data.put("programs",currentPrograms);payload.put("data",data);} catch(Exception ignored) { }
        epgText.setText(formatEpg(payload));
        long now=System.currentTimeMillis()/1000;
        JSONObject current=null;
        for(int i=0;i<currentPrograms.length();i++) {
            JSONObject item=currentPrograms.optJSONObject(i);
            if(item!=null && item.optLong("start_time")<=now && item.optLong("end_time")>now) {current=item;break;}
        }
        if(current==null){epgProgress.setProgress(0);epgTime.setText("暂无当前节目时间 · 可在后台同步 EPG");return;}
        long start=current.optLong("start_time"),end=current.optLong("end_time");
        int progress=(int)((now-start)*1000/Math.max(1,end-start));epgProgress.setProgress(progress);
        SimpleDateFormat time=new SimpleDateFormat("HH:mm",Locale.CHINA);
        epgTime.setText(time.format(new Date(start*1000))+" — "+time.format(new Date(end*1000))+"    已播 "+progress/10+"% · 约剩 "+Math.max(1,(end-now)/60)+" 分钟");
    }
    private void showPrograms() {
        if(vodMode){Toast.makeText(this,"点播进度可在视频控制条上查看和拖动",Toast.LENGTH_LONG).show();playerView.showController();return;}
        ArrayList<String> items=new ArrayList<>();SimpleDateFormat time=new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA);
        for(int i=0;i<currentPrograms.length();i++){JSONObject p=currentPrograms.optJSONObject(i);if(p!=null)items.add(time.format(new Date(p.optLong("start_time")*1000))+"  "+p.optString("title"));}
        if(items.isEmpty())new AlertDialog.Builder(this).setTitle("暂无节目单").setMessage("请在管理平台添加 XMLTV 节目源并同步。节目单用于显示播出时间，不代表直播支持回看或拖动。").setPositiveButton("知道了",null).show();
        else new AlertDialog.Builder(this).setTitle("节目预告 · 直播不能快进").setItems(items.toArray(new String[0]),(d,w)->{}).setNegativeButton("返回",null).show();
    }
    private void openVod() {
        if(vodBrowser!=null)vodBrowser.close();
        vodBrowser=new VodBrowser(this,api,serverUrl,this::playVod);vodBrowser.show();
    }
    private void playVod(String title,String url) {
        if(player==null)return;
        if(!accessGranted){setStatus("设备未授权，请按刷新后重试");return;}
        handler.removeCallbacks(retryPlayback);handler.removeCallbacks(bufferingTimeout);
        vodMode=true;channelTitle.setText(title);epgText.setText("家庭点播 · 点击画面可暂停、拖动进度");epgTime.setText("返回按钮可继续看直播或重新选集");epgProgress.setProgress(0);
        playerView.setUseController(true);player.setMediaItem(MediaItem.fromUri(url));player.prepare();player.play();setGuideVisible(false);playerView.showController();
    }
    private void showVodExit() {
        new AlertDialog.Builder(this).setTitle("返回哪里？").setItems(new String[]{"继续看直播","重新选片 / 选集","退出应用"},(d,w)->{
            if(w==0){if(!allChannels.isEmpty())play(allChannels.get(Math.max(0,Math.min(currentIndex,allChannels.size()-1))));else {vodMode=false;player.stop();playerView.setUseController(false);setGuideVisible(true);}}
            else if(w==1)openVod();else confirmExit();
        }).setNegativeButton("继续观看",null).show();
    }
    @Override public void onConfigurationChanged(@NonNull Configuration config) {
        super.onConfigurationChanged(config);hideSystemUi();
        if(player==null){showConfigScreen(null);return;}
        compactUi=phoneUi && config.orientation==Configuration.ORIENTATION_PORTRAIT;
        FrameLayout.LayoutParams video=match();
        if(compactUi){video.height=(getResources().getDisplayMetrics().widthPixels-dp(24))*9/16;video.topMargin=dp(70);video.leftMargin=dp(12);video.rightMargin=dp(12);}
        playerView.setLayoutParams(video);
        FrameLayout.LayoutParams info=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        info.topMargin=compactUi?dp(90)+video.height:dp(64);
        if(compactUi){info.leftMargin=dp(12);info.rightMargin=dp(12);}
        infoPanel.setLayoutParams(info);channelTitle.setTextSize(compactUi?23:29);
        boolean visible=guideVisible;String status=statusText.getText().toString();root.removeView(guidePanel);buildGuidePanel();setStatus(status);setGuideVisible(visible);
    }
}
