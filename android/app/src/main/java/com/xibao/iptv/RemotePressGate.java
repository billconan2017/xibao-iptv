package com.xibao.iptv;

/** Some IR remotes send repeatCount=0 for every burst. Bound both kinds of repeats. */
final class RemotePressGate {
    private long lastAccepted = Long.MIN_VALUE;
    boolean accept(int repeatCount, long eventTime) {
        if (repeatCount != 0) return false;
        if (lastAccepted != Long.MIN_VALUE && eventTime - lastAccepted < 400) return false;
        lastAccepted = eventTime;
        return true;
    }
}
