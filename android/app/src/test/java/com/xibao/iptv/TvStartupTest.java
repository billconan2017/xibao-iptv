package com.xibao.iptv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import android.content.res.Configuration;
import android.os.Looper;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "land-television-xhdpi")
public class TvStartupTest {
    @Test public void tvCanOpenConfigurationAndReturnFromBackground() {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            assertEquals(Configuration.UI_MODE_TYPE_TELEVISION,controller.get().getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK);
            shadowOf(Looper.getMainLooper()).idle();
            android.view.View focused=controller.get().findViewById(android.R.id.content).findFocus();
            assertTrue(focused instanceof android.widget.Button);
            assertEquals("连接服务器", ((android.widget.Button)focused).getText().toString());
            controller.pause().stop().start().resume();
            assertFalse(controller.get().isFinishing());
        }
    }
}
