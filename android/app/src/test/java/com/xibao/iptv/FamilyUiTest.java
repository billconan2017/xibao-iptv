package com.xibao.iptv;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35},qualifiers="port")
public class FamilyUiTest {
    @org.junit.Before public void phoneHardware(){ org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication().getPackageManager()).setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN,true); }

    private Object field(MainActivity a,String name)throws Exception{var f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private void invoke(MainActivity a,String name)throws Exception{var m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
    private boolean hasText(View v,String value){if(v instanceof TextView && ((TextView)v).getText().toString().contains(value))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasText(((ViewGroup)v).getChildAt(i),value))return true;return false;}
    @Test public void playbackNavigationEpgAndRotationKeepPlayer()throws Exception {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();invoke(a,"buildPlayerScreen");View content=a.findViewById(android.R.id.content);
            for(String label:new String[]{"频道","节目单"})assertTrue(label,hasText(content,label));
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER,a.getRequestedOrientation());
            long now=System.currentTimeMillis()/1000;
            JSONArray programs=(JSONArray)field(a,"currentPrograms");programs.put(new JSONObject().put("title","家庭新闻").put("start_time",now-600).put("end_time",now+600));invoke(a,"renderEpg");
            assertTrue(hasText(content,"家庭新闻"));assertTrue(hasText(content,"已播 50%"));
            Object player=field(a,"player");Configuration config=new Configuration(a.getResources().getConfiguration());config.orientation=Configuration.ORIENTATION_LANDSCAPE;a.onConfigurationChanged(config);
            assertSame(player,field(a,"player"));
        }
    }
    @Test public void deviceSecretPersistsAndIsNotThePublicId()throws Exception {
        String first;
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {first=controller.get().getSharedPreferences("xibao_tv",0).getString("device_token","");assertEquals(64,first.length());assertNotEquals(first,field(controller.get(),"deviceId"));controller.recreate();assertEquals(first,controller.get().getSharedPreferences("xibao_tv",0).getString("device_token",""));}
    }
}
