package com.xibao.iptv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.json.*;
import java.io.*;
import java.time.Duration;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhoneLayoutTest {
    @org.junit.Before public void phoneHardware(){ org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication().getPackageManager()).setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN,true); }

    private Object get(MainActivity a,String key)throws Exception{var f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(a);}
    private void set(MainActivity a,String key,Object value)throws Exception{var f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);f.set(a,value);}
    private void call(MainActivity a,String name)throws Exception{var m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
    private void measure(View view,int width,int height){for(int i=0;i<3;i++){view.forceLayout();view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}}
    private View text(View v,String target){if(v instanceof TextView && target.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View match=text(((ViewGroup)v).getChildAt(i),target);if(match!=null)return match;}return null;}
    private void capture(View view,String name)throws Exception{File folder=new File("build/phone-previews");folder.mkdirs();Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}

    @Test public void rotationUsesAvailableBoundsAndVideoTapNeverOpensChannelDrawer()throws Exception{
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity a=controller.get();call(a,"buildPlayerScreen");
            FrameLayout root=(FrameLayout)get(a,"root");
            ((ViewGroup)root.getParent()).removeView(root);
            set(a,"accessGranted",true);set(a,"guideVisible",false);
            List<Channel> all=(List<Channel>)get(a,"allChannels");
            for(int i=1;i<=12;i++)all.add(new Channel(i,"CCTV-"+i+"  综合","https://example.invalid/live","央视频道","",""));
            ((List<Channel>)get(a,"visibleChannels")).addAll(all);call(a,"rebuildGroups");
            ((androidx.recyclerview.widget.RecyclerView)get(a,"channelList")).getAdapter().notifyDataSetChanged();
            long now=System.currentTimeMillis()/1000;JSONArray programs=(JSONArray)get(a,"currentPrograms");
            for(int i=0;i<8;i++)programs.put(new JSONObject().put("title",i==0?"正在播出的节目":"接下来播出的节目 "+i).put("start_time",now-600+i*1800).put("end_time",now+1200+i*1800));
            ((TextView)get(a,"channelTitle")).setText("CCTV-1 综合");call(a,"renderEpg");
            measure(root,393,830);call(a,"layoutPhone");measure(root,393,830);
            View video=(View)get(a,"playerView"),guide=(View)get(a,"guidePanel"),info=(View)get(a,"infoPanel");
            capture(root,"portrait");
            assertEquals(393*9/16,video.getHeight());assertTrue(guide.getTop()>=info.getBottom());assertTrue(guide.getHeight()>200);
            capture(root,"portrait");
            Object player=get(a,"player");
            measure(root,830,393);call(a,"layoutPhone");measure(root,830,393);
            assertEquals(393,video.getHeight());assertEquals(0,video.getTop());assertEquals(View.GONE,guide.getVisibility());
            capture(root,"landscape-controls");
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
            assertEquals(View.GONE,((View)get(a,"touchControls")).getVisibility());
            video.performClick();assertEquals(View.VISIBLE,((View)get(a,"touchControls")).getVisibility());
            video.performClick();assertEquals(View.GONE,guide.getVisibility());assertEquals(View.GONE,((View)get(a,"touchControls")).getVisibility());
            capture(root,"landscape-clean");
            video.performClick();assertEquals(View.VISIBLE,((View)get(a,"touchControls")).getVisibility());assertEquals(View.GONE,guide.getVisibility());
            text((View)get(a,"touchControls"),"频道").performClick();assertEquals(View.VISIBLE,guide.getVisibility());
            text(guide,"关闭").performClick();assertEquals(View.GONE,guide.getVisibility());
            measure(root,393,830);call(a,"layoutPhone");measure(root,393,830);assertEquals(393*9/16,video.getHeight());assertEquals(View.VISIBLE,guide.getVisibility());assertSame(player,get(a,"player"));
            assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER,a.getRequestedOrientation());
            call(a,"showPhonePrograms");androidx.appcompat.app.AlertDialog dialog=(androidx.appcompat.app.AlertDialog)get(a,"programmeDialog");View decor=dialog.getWindow().getDecorView();measure(decor,369,600);capture(decor,"programme-list");dialog.dismiss();
            set(a,"currentIndex",0);
            androidx.media3.exoplayer.ExoPlayer playback=(androidx.media3.exoplayer.ExoPlayer)player;
            playback.play();playback.pause();controller.pause().stop().start().resume();
            assertFalse("User-paused playback must stay paused after foregrounding",playback.getPlayWhenReady());
        }
    }
}
