package com.xibao.iptv;

import androidx.media3.common.PlaybackException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class PlaybackErrorsTest {
    @Test public void decoderFailuresAreNotReportedAsNetworkAndPrivateMessagesStayHidden() {
        PlaybackException error=new PlaybackException("http://private.invalid/live?token=secret",null,PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
        String text=PlaybackErrors.describe(error);
        assertTrue(text.contains("解码失败"));assertTrue(text.contains("4001"));
        assertFalse(text.contains("private"));assertFalse(text.contains("secret"));assertFalse(text.contains("连接失败"));
    }
    @Test public void audioOutputAndTimeoutHaveDifferentMessages() {
        assertTrue(PlaybackErrors.describe(new PlaybackException("",null,PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)).contains("音频输出失败"));
        assertTrue(PlaybackErrors.describe(new PlaybackException("",null,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)).contains("连接超时"));
    }
}
