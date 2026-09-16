package com.xibao.iptv;
import android.view.KeyEvent;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={24,28,35},qualifiers="land-television-mdpi")
public class TvPlaybackTest {
    private Object get(MainActivity a,String name)throws Exception{var f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private void set(MainActivity a,String name,Object value)throws Exception{var f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);}
    private void call(MainActivity a,String name)throws Exception{var m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
    @Test public void remoteKeysRequireAuthorizationAndKeepPhoneChromeOffTelevision()throws Exception{
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();call(a,"buildPlayerScreen");assertEquals(false,get(a,"phoneUi"));assertNull(get(a,"touchControls"));
            List<Channel> channels=(List<Channel>)get(a,"allChannels");for(int i=1;i<=3;i++)channels.add(new Channel(i,"家庭频道"+i,"http://127.0.0.1:1/live","家庭","",""));
            set(a,"guideVisible",false);
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));assertEquals(-1,get(a,"currentIndex"));
            set(a,"accessGranted",true);
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));assertEquals(0,get(a,"currentIndex"));assertEquals(View.GONE,((View)get(a,"navigation")).getVisibility());
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));assertEquals(1,get(a,"currentIndex"));
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));assertEquals(0,get(a,"currentIndex"));
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU));assertEquals(true,get(a,"guideVisible"));assertEquals(View.VISIBLE,((View)get(a,"guidePanel")).getVisibility());
            a.getOnBackPressedDispatcher().onBackPressed();assertEquals(false,get(a,"guideVisible"));assertFalse(a.isFinishing());
            a.getOnBackPressedDispatcher().onBackPressed();assertFalse(a.isFinishing());
            assertNotEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER,a.getRequestedOrientation());
        }
    }
    @Test @Config(sdk=28,qualifiers="land-mdpi") public void genericAndroidBoxWithoutTouchscreenUsesRemoteLayout()throws Exception{
        org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication().getPackageManager()).setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN,false);
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {assertEquals(false,get(controller.get(),"phoneUi"));}
    }
    @Test @Config(sdk=28,qualifiers="land-mdpi") public void televisionOverrideWorksForIncorrectHardwareFlags()throws Exception{
        var app=org.robolectric.RuntimeEnvironment.getApplication();
        org.robolectric.Shadows.shadowOf(app.getPackageManager()).setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN,true);
        app.getSharedPreferences("xibao_tv",0).edit().putString("interface_mode","tv").commit();
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){assertEquals(false,get(controller.get(),"phoneUi"));}
    }
}
