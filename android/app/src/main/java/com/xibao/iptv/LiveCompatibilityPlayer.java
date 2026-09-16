package com.xibao.iptv;

import android.view.View;

interface LiveCompatibilityPlayer {
    interface Listener {
        void onVideo();
        void onBuffering();
        void onError();
    }
    View view();
    void play(String url, boolean software, Listener listener);
    void stop();
    void close();
}
