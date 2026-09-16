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
    private Runnable cancelPendingGuideFocus;
    private String playbackFailure = "";
    private final RemotePressGate remotePressGate = new RemotePressGate();
    private LiveCompatibilityPlayer compatibilityPlayer;
    private boolean compatibilityActive, liveRendered, liveTimeoutArmed;
    private int liveEngine, liveAttempt;
    private final java.util.Map<String, Integer> workingEngines = new java.util.HashMap<>();
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
    private TextView mobileTitle;
    private Button phoneGuideAction;
    private Button phonePauseButton;
    private Button fullscreenButton;
    private android.view.OrientationEventListener orientationListener;
    private boolean manualFullscreen, returningPortrait, physicallyPortrait;
    private boolean resumePlayback;
    private boolean phoneControlsVisible = true;
    private int phoneLayoutWidth = -1, phoneLayoutHeight = -1;
    private AlertDialog programmeDialog;
    private final Runnable hidePhoneControls = () -> {
        phoneControlsVisible=false;
        updatePhoneChrome();
    };
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
            public void onSuccess(JSONObject value) { runOnUiThread(()->{if(!isCurrent(generation))return;accessGranted=value.optBoolean("authorized",false);if(!accessGranted){stopPlayback();allChannels.clear();visibleChannels.clear();rebuildGroups();filterChannels(selectedGroup);setGuideVisible(true);setStatus(authorizationMessage(value));}}); }
            public void onError(Exception e) { runOnUiThread(()->{if(isCurrent(generation)) {accessGranted=false;stopPlayback();setStatus("授权连接失败，请按刷新重新连接："+friendlyError(e));setGuideVisible(true);}}); }
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
        liveTimeoutArmed = false;
        if (player == null || !accessGranted) return;
        if (vodMode) { setStatus("点播加载超时，请返回点播列表换线路"); return; }
        playbackFailure = "加载超时，未能正常输出画面";
        handleLiveFailure();
    };
    private String numberBuffer = "";
    private final Runnable hideGuide = () -> setGuideVisible(false);
    private final Runnable commitNumber = this::playNumberBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        phoneUi = shouldUsePhoneUi();
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        if(phoneUi){
            manualFullscreen=savedInstanceState!=null && savedInstanceState.getBoolean("manual_fullscreen");
            returningPortrait=savedInstanceState!=null && savedInstanceState.getBoolean("returning_portrait");
            setRequestedOrientation(manualFullscreen?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                returningPortrait?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            orientationListener=new android.view.OrientationEventListener(this){
                @Override public void onOrientationChanged(int angle){
                    physicallyPortrait=angle>=0 && (angle<25 || angle>335);
                    restoreAutomaticOrientation();
                }
            };
        }
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(phoneUi && player!=null)layoutPhone();
        });
        setContentView(root);
        hideSystemUi();
        if (savedInstanceState != null) pendingChannelId = savedInstanceState.getLong("channel_id", -1);
        initializeIdentity();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (player != null) {
                    if (guideVisible) setGuideVisible(false);
                    else if(phoneUi && (manualFullscreen || !compactUi)) leaveFullscreen();
                    else if (vodMode) { showVodExit(); }
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

    private boolean shouldUsePhoneUi(){
        String mode=getSharedPreferences(PREFS,MODE_PRIVATE).getString("interface_mode","auto");
        if("tv".equals(mode))return false;
        if("phone".equals(mode))return true;
        android.content.pm.PackageManager hardware=getPackageManager();
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK) != Configuration.UI_MODE_TYPE_TELEVISION
            && !hardware.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
            && !hardware.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
            && hardware.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN);
    }

    private void hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars=WindowCompat.getInsetsController(getWindow(),root);
        if(phoneUi && getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
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
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            if(phoneUi)view.post(()->{phoneLayoutWidth=-1;layoutPhone();});return insets;
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

        TextView modeLabel=text("界面模式 · 盒子识别不正确时可手动切换",phoneUi?12:16,MUTED,false);
        card.addView(modeLabel,matchWidthWrap());
        android.widget.Spinner interfaceMode=new android.widget.Spinner(this);
        interfaceMode.setPopupBackgroundDrawable(rounded(CARD,8));
        interfaceMode.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"自动识别","手机触控","电视遥控"}){
            @Override public View getView(int position,View convert,ViewGroup parent){TextView view=(TextView)super.getView(position,convert,parent);view.setTextColor(Color.WHITE);view.setTextSize(phoneUi?14:18);return view;}
            @Override public View getDropDownView(int position,View convert,ViewGroup parent){TextView view=(TextView)super.getDropDownView(position,convert,parent);view.setTextColor(Color.WHITE);view.setTextSize(phoneUi?14:18);view.setMinHeight(dp(48));return view;}
        });
        String savedMode=getSharedPreferences(PREFS,MODE_PRIVATE).getString("interface_mode","auto");
        interfaceMode.setSelection("tv".equals(savedMode)?2:"phone".equals(savedMode)?1:0);
        LinearLayout.LayoutParams modeParams=new LinearLayout.LayoutParams(-1,dp(48));modeParams.bottomMargin=dp(12);card.addView(interfaceMode,modeParams);

        Button connect = button("连接服务器");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(phoneUi?48:62));
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
                            String mode=interfaceMode.getSelectedItemPosition()==2?"tv":interfaceMode.getSelectedItemPosition()==1?"phone":"auto";
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_SERVER, serverUrl).putString("interface_mode",mode).apply();
                            phoneUi=shouldUsePhoneUi();
                            setRequestedOrientation(phoneUi?ActivityInfo.SCREEN_ORIENTATION_FULL_USER:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                            hideSystemUi();
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
        if(player!=null)stopPlayback();
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
                        stopPlayback();allChannels.clear();currentIndex=-1;rebuildGroups();filterChannels(selectedGroup);
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
        phoneLayoutWidth=-1;phoneLayoutHeight=-1;phoneControlsVisible=true;
        touchControls = null;
        navigation = null;
        vodMode = false;
        compactUi = phoneUi && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        root.removeAllViews();

        player = new ExoPlayer.Builder(this, new androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)).build();
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean playing) {
                if(phonePauseButton!=null)phonePauseButton.setText(playing?"暂停":"播放");
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (compatibilityActive) return;
                if (state == Player.STATE_BUFFERING) {
                    setStatus("正在连接，请稍候…");
                    armLiveTimeout();
                }
                else if (state == Player.STATE_READY) {
                    if (vodMode || liveRendered) markLiveReady();
                }
                else if (state == Player.STATE_ENDED) setStatus("节目已结束");
            }

            @Override public void onRenderedFirstFrame() {
                if (!compatibilityActive) markLiveReady();
            }

            @Override public void onPlayerError(@NonNull PlaybackException error) {
                if (compatibilityActive) return;
                playbackFailure = PlaybackErrors.describe(error);
                if (vodMode) {setStatus("点播播放失败，请换线路或返回点播列表");return;}
                handleLiveFailure();
            }
        });

        playerView = new PlayerView(this);
        playerView.setPlayer(player);
        playerView.setUseController(false);
        playerView.setKeepScreenOn(true);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setOnClickListener(v -> {
            if(vodMode)return;
            if(phoneUi){if(guideVisible)setGuideVisible(false);else showPhoneControls(!phoneControlsVisible);}
            else setGuideVisible(!guideVisible);
        });
        if(phoneUi)playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)visibility->{
            if(vodMode){phoneControlsVisible=visibility==View.VISIBLE;updatePhoneChrome();}
        });
        FrameLayout.LayoutParams videoParams=match();
        if(compactUi) {videoParams.height=(getResources().getDisplayMetrics().widthPixels-dp(24))*9/16;videoParams.topMargin=dp(70);videoParams.leftMargin=dp(12);videoParams.rightMargin=dp(12);}
        root.addView(playerView, videoParams);

        buildInfoPanel();
        buildGuidePanel();
        if(phoneUi){buildPhoneNavigation();buildTouchControls();root.post(this::layoutPhone);}
        else buildNavigation();
        handler.postDelayed(epgTicker,30000);
        handler.postDelayed(heartbeat,60000);

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER);
        root.addView(loading, loadingParams);
    }

    private void buildInfoPanel() {
        infoPanel = new LinearLayout(this);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setPadding(dp(phoneUi ? 16:34), dp(phoneUi ? 8:18), dp(phoneUi ? 16:34), dp(phoneUi ? 8:20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.argb(230, 5, 8, 12), Color.TRANSPARENT});
        infoPanel.setBackground(bg);
        channelTitle = text("请选择频道", phoneUi ? 16:29, Color.WHITE, true);
        epgText = text(phoneUi ? "选择频道开始播放" : "方向键浏览 · 确认键播放 · 数字键快速选台", phoneUi ? 12:16, MUTED, false);
        statusText = text("", phoneUi ? 12:15, ACCENT, false);
        if(phoneUi){channelTitle.setSingleLine(true);channelTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);epgText.setMaxLines(2);epgText.setEllipsize(android.text.TextUtils.TruncateAt.END);}
        if(phoneUi){
            LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.addView(channelTitle,new LinearLayout.LayoutParams(0,-2,1));
            android.widget.TextClock clock=new android.widget.TextClock(this);
            clock.setFormat12Hour("HH:mm");clock.setFormat24Hour("HH:mm");clock.setTextColor(MUTED);clock.setTextSize(12);
            heading.addView(clock,new LinearLayout.LayoutParams(-2,-2));infoPanel.addView(heading,matchWidthWrap());
        }else infoPanel.addView(channelTitle, matchWidthWrap());
        infoPanel.addView(epgText, matchWidthWrap());
        epgTime=text("节目单由管理平台的 EPG 源提供",phoneUi ? 11:14,MUTED,false);
        if(phoneUi)epgTime.setSingleLine(true);
        epgProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);epgProgress.setMax(1000);
        epgProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(-1,dp(6));progressParams.setMargins(0,dp(phoneUi?5:14),0,dp(phoneUi?3:8));
        infoPanel.addView(epgProgress,progressParams);infoPanel.addView(epgTime,matchWidthWrap());
        infoPanel.addView(statusText, matchWidthWrap());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        params.topMargin=compactUi ? dp(90)+(getResources().getDisplayMetrics().widthPixels-dp(24))*9/16 : dp(64);
        if(compactUi) {params.leftMargin=dp(12);params.rightMargin=dp(12);infoPanel.setBackground(rounded(PANEL,18));}
        root.addView(infoPanel, params);
    }

    private void buildGuidePanel() {
        if(!phoneUi)compactUi=false;
        guidePanel = new LinearLayout(this);
        guidePanel.setOrientation(LinearLayout.VERTICAL);
        int panelPadding = dp(phoneUi ? 12 : 24);
        guidePanel.setPadding(panelPadding, dp(phoneUi ? 6 : 22), panelPadding, dp(phoneUi ? 0 : 20));
        guidePanel.setBackground(rounded(Color.argb(244, 12, 17, 24), 0));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(phoneUi ? "频道" : "直播频道", phoneUi ? 15 : 26, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(phoneUi?44:56), 1));
        Button refresh = button("刷新");
        Button settings = button(phoneUi ? "收起" : "服务器");
        if(phoneUi)phoneGuideAction=settings;
        LinearLayout.LayoutParams smallButton = new LinearLayout.LayoutParams(dp(phoneUi ? 56 : 120), dp(phoneUi?44:50));
        smallButton.setMargins(dp(10), 0, 0, 0);
        if(phoneUi){refresh.setTextSize(12);settings.setTextSize(12);}
        header.addView(refresh, smallButton);
        header.addView(settings, smallButton);
        guidePanel.addView(header, matchWidthWrap());
        guideStatus=text("",14,ACCENT,false);
        guidePanel.addView(guideStatus,matchWidthWrap());

        LinearLayout lists = new LinearLayout(this);
        lists.setOrientation(phoneUi ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        groupList = new RecyclerView(this);
        groupList.setLayoutManager(new LinearLayoutManager(this,
            phoneUi ? RecyclerView.HORIZONTAL : RecyclerView.VERTICAL, false));
        channelList = new RecyclerView(this);
        channelList.setLayoutManager(new LinearLayoutManager(this));
        lists.addView(groupList, phoneUi
            ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(phoneUi?44:72))
            : new LinearLayout.LayoutParams(dp(245), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams channelsParams = phoneUi
            ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1)
            : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        channelsParams.setMargins(phoneUi ? 0 : dp(18), phoneUi ? dp(4) : 0, 0, 0);
        lists.addView(channelList, channelsParams);
        guidePanel.addView(lists, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView footer = text(phoneUi ? "点击频道播放 · 长按频道收藏" : "上下选台 · 确认播放 · 长按确认收藏 · 返回看电视", 17, MUTED, false);
        LinearLayout.LayoutParams footerParams = matchWidthWrap();
        footerParams.setMargins(0, dp(12), 0, 0);
        if(!phoneUi)guidePanel.addView(footer, footerParams);

        groupAdapter = new GroupAdapter();
        channelAdapter = new ChannelAdapter();
        groupList.setAdapter(groupAdapter);
        channelList.setAdapter(channelAdapter);
        if (!phoneUi) {
            // Replacing a group is navigation, not an animated list transition.
            groupList.setItemAnimator(null);
            channelList.setItemAnimator(null);
            groupList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            channelList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }

        refresh.setOnClickListener(v -> loadChannels(false));
        settings.setOnClickListener(v -> {if(phoneUi){setGuideVisible(false);return;}new AlertDialog.Builder(this).setTitle("服务器设置")
            .setMessage("更换服务器会暂时停止播放。是否继续？")
            .setNegativeButton("继续看电视", null).setPositiveButton("打开设置", (d,w)->showConfigScreen(null)).show();});
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
        clearLiveTimeout();
        retryCount = 0;
        playbackFailure = "";
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
        int mode = phoneUi ? 0 : getSharedPreferences(PREFS, MODE_PRIVATE).getInt("tv_playback_mode", 0);
        liveEngine = mode == 0 ? (phoneUi ? 0 : workingEngines.getOrDefault(channelKey(channel), 0)) : mode - 1;
        startLiveAttempt(channel);
        updateChannelTitle(channel);
    }

    private void armLiveTimeout() {
        if (liveTimeoutArmed) return;
        liveTimeoutArmed = true;
        handler.postDelayed(bufferingTimeout, 15000);
    }

    private void clearLiveTimeout() {
        handler.removeCallbacks(bufferingTimeout);
        liveTimeoutArmed = false;
    }

    private void markLiveReady() {
        if (!accessGranted) return;
        if (liveRendered && !liveTimeoutArmed) return;
        liveRendered = true;
        clearLiveTimeout();
        retryCount = 0;
        playbackFailure = "";
        handler.removeCallbacks(retryPlayback);
        if (!phoneUi && !vodMode && currentIndex >= 0 && currentIndex < allChannels.size())
            workingEngines.put(channelKey(allChannels.get(currentIndex)), liveEngine);
        setStatus("");
    }

    LiveCompatibilityPlayer createCompatibilityPlayer() { return new VlcLivePlayer(this); }

    private void startLiveAttempt(Channel channel) {
        final int token = ++liveAttempt;
        clearLiveTimeout();
        liveRendered = false;
        compatibilityActive = liveEngine != 0;
        if (compatibilityPlayer != null) compatibilityPlayer.stop();
        player.stop();
        if (!compatibilityActive) {
            if (compatibilityPlayer != null) compatibilityPlayer.view().setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
            player.setMediaItem(MediaItem.fromUri(channel.currentUrl()));
            player.prepare();
            player.play();
        } else {
            try {
                if (compatibilityPlayer == null) {
                    compatibilityPlayer = createCompatibilityPlayer();
                    root.addView(compatibilityPlayer.view(), 0, match());
                }
                playerView.setVisibility(View.GONE);
                compatibilityPlayer.view().setVisibility(View.VISIBLE);
                compatibilityPlayer.view().setOnClickListener(v -> setGuideVisible(!guideVisible));
                setStatus(liveEngine == 1 ? "正在使用兼容播放器 · 硬件优先…" : "正在使用兼容播放器 · 软件解码…");
                compatibilityPlayer.play(channel.currentUrl(), liveEngine == 2, new LiveCompatibilityPlayer.Listener() {
                    private boolean valid() { return liveAttempt == token && compatibilityActive && accessGranted; }
                    public void onVideo() { if (valid()) markLiveReady(); }
                    public void onBuffering() { if (valid()) armLiveTimeout(); }
                    public void onError() {
                        if (!valid()) return;
                        playbackFailure = "兼容播放器未能播放此线路";
                        handleLiveFailure();
                    }
                });
            } catch (RuntimeException | LinkageError error) {
                playbackFailure = "此设备无法启动兼容播放器";
                // Post to avoid recursing into a half-created native player.
                handler.post(() -> { if (liveAttempt == token) { liveEngine = 2; handleLiveFailure(); } });
            }
        }
        armLiveTimeout();
    }

    private void handleLiveFailure() {
        clearLiveTimeout();
        if (!accessGranted || vodMode || currentIndex < 0 || currentIndex >= allChannels.size()) return;
        int mode = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("tv_playback_mode", 0);
        if (!phoneUi && mode == 0 && liveEngine < 2) {
            liveEngine++;
            startLiveAttempt(allChannels.get(currentIndex));
            return;
        }
        if (!tryNextSource(true)) { stopPlayback(); scheduleRetry(); }
    }

    private void stopPlayback() {
        ++liveAttempt;
        clearLiveTimeout();
        handler.removeCallbacks(retryPlayback);
        if (compatibilityPlayer != null) compatibilityPlayer.stop();
        if (player != null) player.stop();
    }

    private void updateChannelTitle(Channel channel) {
        String source = channel.sources.size() > 1
            ? String.format(Locale.CHINA, "    线路 %d/%d", channel.sourceIndex + 1, channel.sources.size()) : "";
        channelTitle.setText(phoneUi ? channel.name+source : String.format(Locale.CHINA, "%03d  %s%s", currentIndex + 1, channel.name, source));
        if(mobileTitle!=null)mobileTitle.setText(channel.name);
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
        if(phoneUi){
            guideVisible=visible;
            if(guideVisible)handler.removeCallbacks(hidePhoneControls);
            else showPhoneControls(true);
            updatePhoneChrome();
            return;
        }
        if (guidePanel != null) guidePanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if(navigation!=null)navigation.setVisibility(visible?View.VISIBLE:View.GONE);
        if (touchControls != null) touchControls.setVisibility(visible || vodMode ? View.GONE : View.VISIBLE);
        if (infoPanel != null) infoPanel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideGuide);
        cancelGuideFocus();
        if (visible && channelList != null) {
            int position = currentIndex >= 0 && currentIndex < allChannels.size()
                ? visibleChannels.indexOf(allChannels.get(currentIndex)) : 0;
            focusChannelRow(Math.max(0, position));
        }
        if (!visible && playerView != null) {
            if (compatibilityActive && compatibilityPlayer != null) {
                compatibilityPlayer.view().setFocusable(true);
                compatibilityPlayer.view().requestFocus();
            } else playerView.requestFocus();
        }
        if (!visible) scheduleInfoHide();
    }

    private void scheduleInfoHide() {
        if(phoneUi)return;
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
        if(phoneUi){updatePhoneChrome();return;}
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
        // A held D-pad produces many ACTION_DOWN events. Some box remotes even
        // mark all of them as fresh presses, so also apply a short burst limit.
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (remotePressGate.accept(event.getRepeatCount(), event.getEventTime()))
                changeChannel(key == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1);
            return true;
        }
        if (event.getRepeatCount() > 0) return true;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                setGuideVisible(true);
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
        button.setTextSize(phoneUi?13:16);
        button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(6),0,dp(6),0);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setFocusableInTouchMode(!phoneUi);
        button.setBackground(rounded(CARD, 10));
        button.setOnFocusChangeListener((v, focused) -> {v.setBackground(rounded(focused ? ACCENT : CARD, 10));((Button)v).setTextColor(focused?BG:Color.WHITE);});
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
            TextView item = text("", phoneUi ? 14 : 24, Color.WHITE, false);
            item.setFocusable(true);
            item.setClickable(true);
            item.setPadding(dp(18), 0, dp(16), 0);
            item.setSingleLine(true);
            item.setOnFocusChangeListener((v, focused) -> styleItem((TextView) v, focused, v.isSelected()));
            item.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(phoneUi?48:62)));
            return new Holder(item);
        }

        void styleItem(TextView item, boolean focused, boolean selected) {
            item.setTextColor(focused ? BG : selected ? ACCENT : Color.WHITE);
            item.setBackground(rounded(focused ? ACCENT : selected ? Color.rgb(49, 43, 30) : Color.TRANSPARENT, 9));
        }
    }

    private void cancelGuideFocus() {
        if (cancelPendingGuideFocus != null) {
            cancelPendingGuideFocus.run();
            cancelPendingGuideFocus = null;
        }
    }

    private void focusChannelRow(int position) {
        if (visibleChannels.isEmpty()) {
            focusGuideRow(groupList, Math.max(0, groups.indexOf(selectedGroup)));
        } else focusGuideRow(channelList, position);
    }

    private void focusGuideRow(RecyclerView list, int position) {
        cancelGuideFocus();
        if (phoneUi || !guideVisible || list == null || list.getAdapter().getItemCount() == 0) return;
        final int target = Math.min(position, list.getAdapter().getItemCount() - 1);
        list.stopScroll();
        list.scrollToPosition(target);
        // notifyDataSetChanged invalidates attached rows. Wait for the new layout,
        // then focus the actual row rather than the RecyclerView container.
        android.view.ViewTreeObserver observer = list.getViewTreeObserver();
        android.view.ViewTreeObserver.OnGlobalLayoutListener listener = new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (!guideVisible) { cancelGuideFocus(); return; }
                if (list.hasPendingAdapterUpdates() || list.isComputingLayout()) return;
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(target);
                if (holder != null) {
                    cancelGuideFocus();
                    holder.itemView.requestFocus();
                }
            }
        };
        Runnable attempt = listener::onGlobalLayout;
        cancelPendingGuideFocus = () -> {
            list.removeCallbacks(attempt);
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
            else list.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        };
        observer.addOnGlobalLayoutListener(listener);
        list.post(attempt);
    }

    private final class GroupAdapter extends TextAdapter {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Holder holder = super.onCreateViewHolder(parent, viewType);
            if (phoneUi) holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(dp(116), dp(40)));
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
                if (!phoneUi) focusChannelRow(0);
            });
            item.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    filterChannels(group); focusChannelRow(0); return true;
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
                    focusGuideRow(groupList, Math.max(0, groups.indexOf(selectedGroup))); return true;
                }
                return false;
            });
        }
    }

    private void releasePlayer() {
        cancelGuideFocus();
        ++liveAttempt;
        clearLiveTimeout();
        if (compatibilityPlayer != null) { compatibilityPlayer.close(); compatibilityPlayer = null; }
        compatibilityActive = false;
        if(programmeDialog!=null){programmeDialog.dismiss();programmeDialog=null;}
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
        if(orientationListener!=null)orientationListener.disable();
        activeScreen=false;
        handler.removeCallbacks(epgTicker);
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(retryPlayback);
        clearLiveTimeout();
        if (player != null) {
            resumePlayback=compatibilityActive || player.getPlayWhenReady();
            if (compatibilityActive) stopPlayback(); else player.pause();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if(orientationListener!=null && orientationListener.canDetectOrientation())orientationListener.enable();
        activeScreen=true;
        if (player != null) {
            if(resumePlayback && accessGranted && (currentIndex>=0 || vodMode)) {
                if (compatibilityActive && !vodMode) startChannelSource(allChannels.get(currentIndex));
                else { player.play(); if (!vodMode && !liveRendered) armLiveTimeout(); }
            }
            handler.removeCallbacks(epgTicker);handler.post(epgTicker);handler.removeCallbacks(heartbeat);handler.post(heartbeat);
        }
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("manual_fullscreen",manualFullscreen);
        outState.putBoolean("returning_portrait",returningPortrait);
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
        clearLiveTimeout();
        if (++retryCount > 3) {
            setStatus((playbackFailure.isEmpty() ? "暂时无法播放" : playbackFailure) + "，请换线路或频道重试");
            setGuideVisible(true);
            return;
        }
        setStatus((playbackFailure.isEmpty() ? "信号暂时中断" : playbackFailure) + "，正在自动重连（" + retryCount + "/3）…");
        handler.postDelayed(retryPlayback, retryCount * 3000L);
    }
    private void buildTouchControls() {
        touchControls=new LinearLayout(this);touchControls.setGravity(Gravity.CENTER_VERTICAL);
        touchControls.setPadding(dp(8),0,dp(8),0);
        touchControls.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.TRANSPARENT,Color.argb(220,0,0,0)}));
        String[] labels={"上一台","暂停","下一台","频道","节目单"};
        for(int i=0;i<labels.length;i++){
            final int action=i;Button control=button(labels[i]);control.setTextSize(12);control.setBackgroundColor(Color.TRANSPARENT);
            if(i==1)phonePauseButton=control;
            touchControls.addView(control,new LinearLayout.LayoutParams(0,dp(48),1));
            control.setOnClickListener(v->{
                if(action==0)changeChannel(-1);
                else if(action==1){if(!accessGranted)return;if(player.getPlayWhenReady()){player.pause();control.setText("播放");}else{player.play();control.setText("暂停");}}
                else if(action==2)changeChannel(1);
                else if(action==3)setGuideVisible(true);
                else showPrograms();
                if(action<3)showPhoneControls(true);
            });
        }
        root.addView(touchControls,new FrameLayout.LayoutParams(-1,dp(48),Gravity.BOTTOM));
    }

    private void buildPhoneNavigation(){
        navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER_VERTICAL);navigation.setPadding(dp(8),0,dp(8),0);
        Button back=button("‹");back.setTextSize(26);back.setContentDescription("返回");back.setBackgroundColor(Color.TRANSPARENT);
        navigation.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        back.setOnClickListener(v->getOnBackPressedDispatcher().onBackPressed());
        mobileTitle=text("喜宝 TV",15,Color.WHITE,true);mobileTitle.setSingleLine(true);mobileTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        navigation.addView(mobileTitle,new LinearLayout.LayoutParams(0,dp(48),1));
        fullscreenButton=button("⛶");fullscreenButton.setTextSize(22);fullscreenButton.setContentDescription("全屏播放");fullscreenButton.setBackgroundColor(Color.TRANSPARENT);
        navigation.addView(fullscreenButton,new LinearLayout.LayoutParams(dp(48),dp(48)));
        fullscreenButton.setOnClickListener(v->toggleFullscreen());
        Button more=button("⋮");more.setTextSize(24);more.setContentDescription("更多选项");more.setBackgroundColor(Color.TRANSPARENT);
        navigation.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));
        more.setOnClickListener(v->{
            handler.removeCallbacks(hidePhoneControls);
            android.widget.PopupMenu menu=new android.widget.PopupMenu(this,more);
            String[] items={"节目单","收藏当前频道","点播","刷新频道","服务器设置","退出应用"};
            for(int i=0;i<items.length;i++)menu.getMenu().add(0,i,i,items[i]);
            menu.setOnMenuItemClickListener(item->{switch(item.getItemId()){
                case 0:showPrograms();break;case 1:toggleFavorite();break;case 2:openVod();break;
                case 3:loadChannels(false);break;case 4:showConfigScreen(null);break;case 5:confirmExit();break;
            }return true;});
            menu.setOnDismissListener(m->showPhoneControls(true));menu.show();
        });
        root.addView(navigation,new FrameLayout.LayoutParams(-1,dp(48),Gravity.TOP));
    }

    private void toggleFullscreen(){
        if(manualFullscreen || !compactUi){leaveFullscreen();return;}
        manualFullscreen=true;returningPortrait=false;
        setGuideVisible(false);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        showPhoneControls(true);
    }

    private void leaveFullscreen(){
        manualFullscreen=false;returningPortrait=true;
        setGuideVisible(false);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        showPhoneControls(true);
    }

    private void restoreAutomaticOrientation(){
        // Wait for both the UI and the physical phone to be upright. Releasing the
        // override earlier would immediately rotate back when auto-rotate is on.
        if(returningPortrait && physicallyPortrait && getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT){
            returningPortrait=false;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        }
    }

    private void showPhoneControls(boolean visible){
        phoneControlsVisible=visible;handler.removeCallbacks(hidePhoneControls);updatePhoneChrome();
        if(visible && !guideVisible)handler.postDelayed(hidePhoneControls,3500);
    }

    private void updatePhoneChrome(){
        if(!phoneUi || player==null || navigation==null)return;
        boolean sheet=guideVisible;
        navigation.setVisibility(compactUi || (phoneControlsVisible && !sheet)?View.VISIBLE:View.GONE);
        if(touchControls!=null)touchControls.setVisibility(!vodMode && phoneControlsVisible && !sheet?View.VISIBLE:View.GONE);
        if(guidePanel!=null)guidePanel.setVisibility(!vodMode && sheet?View.VISIBLE:View.GONE);
        if(infoPanel!=null)infoPanel.setVisibility(compactUi && !sheet?View.VISIBLE:View.GONE);
        if(fullscreenButton!=null){fullscreenButton.setText(compactUi?"⛶":"↙");fullscreenButton.setContentDescription(compactUi?"全屏播放":"退出全屏");}
    }

    private void layoutPhone(){
        if(!phoneUi || player==null || navigation==null || root.getWidth()==0)return;
        int width=root.getWidth()-root.getPaddingLeft()-root.getPaddingRight();
        int height=root.getHeight()-root.getPaddingTop()-root.getPaddingBottom();
        if(width==phoneLayoutWidth && height==phoneLayoutHeight)return;
        phoneLayoutWidth=width;phoneLayoutHeight=height;compactUi=height>=width;
        int videoHeight=compactUi?Math.max(width*9/16,height-dp(160)):height;
        FrameLayout.LayoutParams video=new FrameLayout.LayoutParams(-1,videoHeight,Gravity.TOP);video.topMargin=compactUi?dp(48):0;playerView.setLayoutParams(video);
        FrameLayout.LayoutParams info=new FrameLayout.LayoutParams(-1,dp(112),Gravity.TOP);info.topMargin=dp(48)+videoHeight;infoPanel.setLayoutParams(info);infoPanel.setBackgroundColor(BG);
        FrameLayout.LayoutParams guide=new FrameLayout.LayoutParams(compactUi?-1:Math.min(dp(360),width*2/3),-1,compactUi?Gravity.TOP:Gravity.END);
        guide.topMargin=compactUi?Math.max(dp(48),height*2/5):0;guidePanel.setLayoutParams(guide);
        guidePanel.bringToFront();
        FrameLayout.LayoutParams controls=new FrameLayout.LayoutParams(-1,dp(48),Gravity.TOP);controls.topMargin=(compactUi?dp(48):0)+videoHeight-dp(48);touchControls.setLayoutParams(controls);
        navigation.setBackgroundColor(compactUi?BG:Color.argb(160,0,0,0));
        phoneGuideAction.setText("关闭");
        if(!accessGranted)guideVisible=true;
        showPhoneControls(true);
    }

    private void buildNavigation() {
        navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setPadding(dp(10),dp(8),dp(10),dp(8));navigation.setBackgroundColor(BG);
        String[] labels={"返回","节目单","点播","收藏","播放设置","退出"};
        for(int i=0;i<labels.length;i++) {
            final int action=i;Button control=button(labels[i]);control.setTextSize(phoneUi?13:17);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(2),0,dp(2),0);
            navigation.addView(control,lp);
            control.setOnClickListener(v->{
                if(action==0){if(vodMode)showVodExit();else if(guideVisible)setGuideVisible(false);else setGuideVisible(true);}
                else if(action==1)showPrograms();
                else if(action==2)openVod();
                else if(action==3){toggleFavorite();}
                else if(action==4)showPlaybackSettings();
                else confirmExit();
            });
        }
        root.addView(navigation,new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP));
    }

    private void showPlaybackSettings() {
        String[] modes={"自动兼容（推荐）", "原播放器 · 硬件解码", "兼容播放器 · 硬件优先", "兼容播放器 · 软件解码"};
        int selected=getSharedPreferences(PREFS,MODE_PRIVATE).getInt("tv_playback_mode",0);
        new AlertDialog.Builder(this).setTitle("电视播放方式")
            .setSingleChoiceItems(modes,selected,(dialog,which)->{
                getSharedPreferences(PREFS,MODE_PRIVATE).edit().putInt("tv_playback_mode",which).apply();
                workingEngines.clear();dialog.dismiss();
                if(!vodMode && accessGranted && currentIndex>=0 && currentIndex<allChannels.size()) {
                    retryCount=0;sourceAttempts=0;startChannelSource(allChannels.get(currentIndex));setGuideVisible(false);
                }
            }).setNegativeButton("返回",null).show();
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
        if(phoneUi && !vodMode){showPhonePrograms();return;}
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
        stopPlayback();
        compatibilityActive = false;
        if (compatibilityPlayer != null) compatibilityPlayer.view().setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        handler.removeCallbacks(retryPlayback);clearLiveTimeout();
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
        restoreAutomaticOrientation();
        if(programmeDialog!=null){programmeDialog.dismiss();programmeDialog=null;}
        if(player==null){showConfigScreen(null);return;}
        if(phoneUi){phoneLayoutWidth=-1;root.post(this::layoutPhone);return;}
    }

    private void showPhonePrograms(){
        if(programmeDialog!=null)programmeDialog.dismiss();
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(12),dp(16),dp(12));
        TextView heading=text("节目单",16,Color.WHITE,true);body.addView(heading,matchWidthWrap());
        ScrollView scroll=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        SimpleDateFormat time=new SimpleDateFormat("HH:mm",Locale.CHINA);long now=System.currentTimeMillis()/1000;
        if(currentPrograms.length()==0){TextView empty=text("暂无节目数据，请在管理平台同步 EPG。",14,MUTED,false);empty.setPadding(0,dp(16),0,dp(16));rows.addView(empty);}
        for(int i=0;i<currentPrograms.length();i++){
            JSONObject p=currentPrograms.optJSONObject(i);if(p==null)continue;
            boolean current=p.optLong("start_time")<=now && now<p.optLong("end_time");
            TextView row=text(time.format(new Date(p.optLong("start_time")*1000))+" — "+time.format(new Date(p.optLong("end_time")*1000))+(current?"  · 正在播":"")+"\n"+p.optString("title"),14,current?ACCENT:Color.WHITE,current);
            row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackground(rounded(current?CARD:PANEL,8));
            LinearLayout.LayoutParams lp=matchWidthWrap();lp.bottomMargin=dp(4);rows.addView(row,lp);
        }
        Button close=button("关闭");body.addView(close,new LinearLayout.LayoutParams(-1,dp(48)));
        programmeDialog=new AlertDialog.Builder(this).setView(body).create();close.setOnClickListener(v->programmeDialog.dismiss());
        programmeDialog.show();programmeDialog.getWindow().setBackgroundDrawable(rounded(PANEL,16));
        int width=root.getWidth()-root.getPaddingLeft()-root.getPaddingRight(),height=root.getHeight()-root.getPaddingTop()-root.getPaddingBottom();
        programmeDialog.getWindow().setLayout(Math.min(dp(420),width-dp(24)),(int)(height*.8));
    }
}
