package com.xibao.iptv;

import android.view.View;
import android.widget.FrameLayout;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={24,28},qualifiers="land-television-mdpi")
public class TvFallbackTest {
    public static class TestActivity extends MainActivity {
        Fake fallback;
        @Override LiveCompatibilityPlayer createCompatibilityPlayer(){return fallback=new Fake(this);}
    }
    static class Fake implements LiveCompatibilityPlayer {
        final View view; Listener listener; boolean software; int starts, stops;
        Fake(MainActivity a){view=new FrameLayout(a);}
        public View view(){return view;}
        public void play(String url,boolean software,Listener listener){this.listener=listener;this.software=software;starts++;}
        public void stop(){stops++;}
        public void close(){stop();}
    }
    private Object get(MainActivity a,String name)throws Exception{var f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private void set(MainActivity a,String name,Object value)throws Exception{var f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);}
    private void call(MainActivity a,String name)throws Exception{var m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
    private void start(MainActivity a,Channel channel)throws Exception{var m=MainActivity.class.getDeclaredMethod("startChannelSource",Channel.class);m.setAccessible(true);m.invoke(a,channel);}
    @Test public void silentStartupHangTriesBothEnginesAndStaleCallbacksCannotAffectNewAttempt()throws Exception {
        try(var controller=Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity a=controller.get();call(a,"buildPlayerScreen");set(a,"accessGranted",true);set(a,"currentIndex",0);
            Channel channel=new Channel(1,"测试","http://127.0.0.1:1/live","测试","","");
            ((List<Channel>)get(a,"allChannels")).add(channel);start(a,channel);
            Runnable timeout=(Runnable)get(a,"bufferingTimeout");
            assertEquals(0,get(a,"liveEngine"));assertNull(a.fallback);
            timeout.run();assertEquals(1,get(a,"liveEngine"));assertFalse(a.fallback.software);
            LiveCompatibilityPlayer.Listener obsolete=a.fallback.listener;
            timeout.run();assertEquals(2,get(a,"liveEngine"));assertTrue(a.fallback.software);
            obsolete.onVideo();assertEquals(false,get(a,"liveRendered"));
            a.fallback.listener.onVideo();assertEquals(true,get(a,"liveRendered"));assertEquals(false,get(a,"liveTimeoutArmed"));
            // A proven compatible engine is reused for this channel in the session.
            start(a,channel);assertEquals(2,get(a,"liveEngine"));
            LiveCompatibilityPlayer.Listener stopped=a.fallback.listener;
            set(a,"accessGranted",false);call(a,"stopPlayback");int starts=a.fallback.starts;
            stopped.onError();timeout.run();assertEquals(starts,a.fallback.starts);
        }
    }
    @Test public void exhaustedFallbackIsBoundedAndDoesNotResetRetriesWithoutVideo()throws Exception {
        try(var controller=Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity a=controller.get();call(a,"buildPlayerScreen");set(a,"accessGranted",true);set(a,"currentIndex",0);
            Channel channel=new Channel(1,"测试","http://127.0.0.1:1/live","测试","","");
            ((List<Channel>)get(a,"allChannels")).add(channel);start(a,channel);
            Runnable timeout=(Runnable)get(a,"bufferingTimeout");
            timeout.run();timeout.run();timeout.run();
            assertEquals(1,get(a,"retryCount"));assertEquals(false,get(a,"liveTimeoutArmed"));
            assertEquals(2,a.fallback.starts);
        }
    }
}
