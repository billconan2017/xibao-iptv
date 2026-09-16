package com.xibao.iptv;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Independent MPEG-TS demuxer and video decoder fallback for television firmware. */
final class VlcLivePlayer implements LiveCompatibilityPlayer {
    private final LibVLC library;
    private final MediaPlayer player;
    private final VLCVideoLayout layout;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private boolean closed;

    VlcLivePlayer(Context context) {
        library = new LibVLC(context, Arrays.asList("--no-video-title-show", "--network-caching=1500"));
        player = new MediaPlayer(library);
        // Decode audio to PCM instead of relying on HDMI compressed-audio support.
        player.setAudioOutput("audiotrack");
        player.setAudioDigitalOutputEnabled(false);
        layout = new VLCVideoLayout(context);
        layout.setKeepScreenOn(true);
        player.attachViews(layout, null, false, false);
    }

    public View view() { return layout; }

    public void play(String url, boolean software, Listener listener) {
        if (closed) return;
        int token = generation.incrementAndGet();
        worker.execute(() -> {
            player.setEventListener(null);
            player.stop();
            if (token != generation.get()) return;
            boolean[] videoOutput = {false};
            player.setEventListener(event -> {
                if (token != generation.get()) return;
                if (event.type == MediaPlayer.Event.Vout) videoOutput[0] = event.getVoutCount() > 0;
                if (event.type == MediaPlayer.Event.TimeChanged && event.getTimeChanged() > 0 && videoOutput[0])
                    deliver(token, listener::onVideo);
                else if (event.type == MediaPlayer.Event.Buffering && event.getBuffering() < 100)
                    deliver(token, listener::onBuffering);
                else if (event.type == MediaPlayer.Event.EncounteredError || event.type == MediaPlayer.Event.EndReached)
                    deliver(token, listener::onError);
            });
            try {
                Media media = new Media(library, Uri.parse(url));
                try {
                    media.setHWDecoderEnabled(!software, !software);
                    media.addOption(":network-caching=1500");
                    player.setMedia(media);
                } finally { media.release(); }
                if (token == generation.get()) player.play();
            } catch (RuntimeException e) { deliver(token, listener::onError); }
        });
    }

    private void deliver(int token, Runnable event) {
        main.post(() -> { if (token == generation.get()) event.run(); });
    }

    public void stop() {
        if (closed) return;
        generation.incrementAndGet();
        worker.execute(() -> { player.setEventListener(null); player.stop(); });
    }

    public void close() {
        if (closed) return;
        closed = true;
        generation.incrementAndGet();
        player.detachViews();
        worker.execute(() -> { player.setEventListener(null); player.stop(); player.release(); library.release(); });
        worker.shutdown();
    }
}
