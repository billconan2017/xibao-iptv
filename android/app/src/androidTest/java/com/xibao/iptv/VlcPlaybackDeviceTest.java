package com.xibao.iptv;

import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VlcPlaybackDeviceTest {
    @Test public void hardwarePreferredTsPlayerProducesVideo()throws Exception{play(false);}
    @Test public void softwareTsPlayerProducesVideo()throws Exception{play(true);}
    private void play(boolean software)throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        File fixture=new File(instrumentation.getTargetContext().getCacheDir(),"synthetic.ts");
        try(InputStream in=instrumentation.getContext().getAssets().open("synthetic.ts");FileOutputStream out=new FileOutputStream(fixture)){
            byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)out.write(block,0,n);
        }
        CountDownLatch output=new CountDownLatch(1);AtomicBoolean failed=new AtomicBoolean();
        VlcLivePlayer[] player=new VlcLivePlayer[1];
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            try {
                scenario.onActivity(a->{
                    player[0]=new VlcLivePlayer(a);a.setContentView(player[0].view());
                    player[0].play(android.net.Uri.fromFile(fixture).toString(),software,new LiveCompatibilityPlayer.Listener(){
                        public void onVideo(){output.countDown();}
                        public void onBuffering(){}
                        public void onError(){failed.set(true);output.countDown();}
                    });
                });
                assertTrue("VLC must open and advance the video output",output.await(25,TimeUnit.SECONDS));
                assertFalse("Playback failed before video output",failed.get());
            } finally {scenario.onActivity(a->{if(player[0]!=null)player[0].close();});}
        }
    }
}
