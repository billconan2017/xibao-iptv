package com.xibao.iptv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class StartupTest {
    @org.junit.Before public void phoneHardware(){ org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication().getPackageManager()).setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN,true); }

    @Test public void freshInstallOpensConfiguration() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertNotNull(controller.get().findViewById(android.R.id.content));
            assertTrue(hasText(controller.get().findViewById(android.R.id.content), "连接服务器"));
            controller.recreate();
            assertTrue(hasText(controller.get().findViewById(android.R.id.content), "连接服务器"));
        }
    }
    private boolean hasText(View view, String text) {
        if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(hasText(((ViewGroup)view).getChildAt(i),text))return true;
        return false;
    }
}
