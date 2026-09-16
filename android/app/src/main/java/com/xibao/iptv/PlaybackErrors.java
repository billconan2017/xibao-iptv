package com.xibao.iptv;

import androidx.media3.common.PlaybackException;

/** Safe, actionable error text: never expose stream URLs or access tokens. */
final class PlaybackErrors {
    private PlaybackErrors() {}
    static String describe(PlaybackException error) {
        int code = error.errorCode;
        String reason;
        if (code >= 4000 && code < 5000) reason = "盒子解码失败";
        else if (code >= 5000 && code < 6000) reason = "音频输出失败";
        else if (code >= 3000 && code < 4000) reason = "直播流格式解析失败";
        else if (code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) reason = "直播源连接超时";
        else if (code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) reason = "直播源返回异常";
        else if (code >= 2000 && code < 3000) reason = "直播源连接失败";
        else reason = "播放失败";
        return reason + "（" + code + "）";
    }
}
