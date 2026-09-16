package com.xibao.iptv;

import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={24,28}, qualifiers="land-television-mdpi")
public class TvGuideFocusTest {
    private Object get(MainActivity a,String name)throws Exception {var f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private void call(MainActivity a,String name)throws Exception {var m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
    private void layout(MainActivity a)throws Exception {
        View root=(View)get(a,"root");
        for(int i=0;i<4;i++){
            root.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));
            root.layout(0,0,1280,720);
            root.getViewTreeObserver().dispatchOnGlobalLayout();
            shadowOf(Looper.getMainLooper()).idle();
        }
    }
    private void key(View v,int code){v.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));v.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));}
    @Test public void switchingGroupsFocusesNewFirstChannelAndLeftReturnsToSelectedGroup()throws Exception {
        try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity a=c.get();call(a,"buildPlayerScreen");
            List<Channel> all=(List<Channel>)get(a,"allChannels");
            for(int i=0;i<40;i++)all.add(new Channel(i,"频道"+i,"http://127.0.0.1:1/live",i<20?"卫视":"熊猫专区","",""));
            call(a,"rebuildGroups");
            RecyclerView groups=(RecyclerView)get(a,"groupList"), channels=(RecyclerView)get(a,"channelList");
            layout(a);
            for(int groupIndex:new int[]{2,3,2,3}) {
                View group=groups.findViewHolderForAdapterPosition(groupIndex).itemView;
                group.requestFocus();group.performClick();layout(a);
                assertEquals(20,channels.getAdapter().getItemCount());
                View first=channels.findViewHolderForAdapterPosition(0).itemView;
                assertTrue("Group confirmation must enter a real channel row",first.hasFocus());
                key(first,KeyEvent.KEYCODE_DPAD_LEFT);layout(a);
                View selected=groups.findViewHolderForAdapterPosition(groupIndex).itemView;
                assertTrue("Left must return to selected group",selected.hasFocus());
                key(selected,KeyEvent.KEYCODE_DPAD_RIGHT);layout(a);
                assertTrue(channels.findViewHolderForAdapterPosition(0).itemView.hasFocus());
            }
            // An empty favourites group must leave navigation on the group, not a dead list.
            View favorites=groups.findViewHolderForAdapterPosition(1).itemView;
            favorites.requestFocus();favorites.performClick();layout(a);
            assertEquals(0,channels.getAdapter().getItemCount());assertTrue(groups.hasFocus());
        }
    }
}
