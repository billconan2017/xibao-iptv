package com.xibao.iptv;

import android.graphics.Color;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import org.json.*;
import java.util.ArrayList;

/** A source-backed catalogue; it never ships third-party content or parses web pages. */
final class VodBrowser {
    interface Playback { void play(String title, String url); }
    private final MainActivity activity;
    private final ApiClient api;
    private final String server;
    private final Playback playback;
    private AlertDialog dialog;
    private LinearLayout results;
    private TextView status;
    private EditText search;
    private long source;
    private int page=1, pages=1, generation=0;
    private String query="";
    private boolean closed;
    VodBrowser(MainActivity a, ApiClient client, String url, Playback callback) {activity=a;api=client;server=url;playback=callback;}
    void show() {
        LinearLayout body=new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(20,12,20,12);
        search=new EditText(activity);search.setSingleLine(true); search.setHint("搜索影片名称");body.addView(search);
        LinearLayout actions=new LinearLayout(activity);
        addButton(actions,"换源",this::sources);addButton(actions,"搜索",()->{query=search.getText().toString().trim();page=1;load();});body.addView(actions);
        search.setOnEditorActionListener((v,id,event)->{query=search.getText().toString().trim();page=1;load();return true;});
        status=new TextView(activity); body.addView(status);
        ScrollView scroll=new ScrollView(activity);results=new LinearLayout(activity);results.setOrientation(LinearLayout.VERTICAL);scroll.addView(results);
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout pager=new LinearLayout(activity);
        addButton(pager,"上一页",()->{if(page>1){page--;load();}});addButton(pager,"下一页",()->{if(page<pages){page++;load();}});body.addView(pager);
        dialog=new AlertDialog.Builder(activity).setTitle("家庭点播").setView(body).setNegativeButton("返回电视",(d,w)->{}).create();
        dialog.setOnDismissListener(d->{closed=true;generation++;});dialog.show();
        dialog.getWindow().setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-24,900), (int)(activity.getResources().getDisplayMetrics().heightPixels*.85));
        sources();
    }
    void close(){closed=true;generation++;if(dialog!=null)dialog.dismiss();}
    private boolean valid(int gen){return !closed && generation==gen && !activity.isDestroyed() && !activity.isFinishing();}
    private void addButton(LinearLayout row,String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setTextSize(18);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->action.run());}
    private void sources(){
        final int gen=++generation;status.setText("正在读取点播源…");
        api.loadVodSources(server,new ApiClient.Callback<JSONObject>(){
            public void onSuccess(JSONObject value){activity.runOnUiThread(()->{
                if(!valid(gen))return;JSONArray rows=value.optJSONArray("data");
                if(rows==null||rows.length()==0){status.setText("尚未添加点播源。请在管理平台 → 点播源，添加苹果 CMS JSON 接口。");return;}
                String[] names=new String[rows.length()];for(int i=0;i<names.length;i++)names[i]=rows.optJSONObject(i).optString("name");
                new AlertDialog.Builder(activity).setTitle("选择点播源").setItems(names,(d,w)->{source=rows.optJSONObject(w).optLong("id");page=1;query="";search.setText("");load();}).setNegativeButton("返回",null).show();
                status.setText("选择源后浏览或搜索影片");
            });}
            public void onError(Exception e){error(gen,e);}
        });
    }
    private void load(){
        if(source==0){sources();return;}final int gen=++generation;status.setText("正在加载…");results.removeAllViews();
        api.loadVod(server,source,page,query,"",new ApiClient.Callback<JSONObject>(){
            public void onSuccess(JSONObject value){activity.runOnUiThread(()->{
                if(!valid(gen))return;JSONObject data=value.optJSONObject("data");if(data==null){status.setText(value.optString("msg","接口数据无效"));return;}
                pages=data.optInt("pages",1);JSONArray items=data.optJSONArray("items");status.setText("第 "+page+" / "+pages+" 页");
                if(items==null||items.length()==0){status.setText("没有找到影片，可换源或换个关键词");return;}
                for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;Button b=new Button(activity);b.setText(item.optString("name")+"  "+item.optString("remarks")+"\n"+item.optString("year")+"  "+item.optString("type"));b.setTextSize(18);results.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->details(item));}
            });}
            public void onError(Exception e){error(gen,e);}
        });
    }
    private void details(JSONObject item){
        JSONArray lines=item.optJSONArray("lines");
        if(lines==null||lines.length()==0){status.setText("正在读取影片详情…");final int gen=++generation;
            api.loadVod(server,source,1,"",item.optString("id"),new ApiClient.Callback<JSONObject>(){
                public void onSuccess(JSONObject value){activity.runOnUiThread(()->{if(!valid(gen))return;JSONObject data=value.optJSONObject("data");JSONArray items=data==null?null:data.optJSONArray("items");JSONObject detail=items==null?null:items.optJSONObject(0);if(detail==null||detail.optJSONArray("lines")==null||detail.optJSONArray("lines").length()==0){status.setText("此影片没有可直接播放的 HTTP 视频线路");return;}selectLine(detail);});}
                public void onError(Exception e){error(gen,e);}
            });return;
        }selectLine(item);
    }
    private void selectLine(JSONObject item){JSONArray lines=item.optJSONArray("lines");String[] names=new String[lines.length()];for(int i=0;i<names.length;i++)names[i]=lines.optJSONObject(i).optString("name");
        new AlertDialog.Builder(activity).setTitle(item.optString("name")+" · 选择线路").setItems(names,(d,w)->{
            JSONArray episodes=lines.optJSONObject(w).optJSONArray("episodes");String[] labels=new String[episodes.length()];for(int i=0;i<labels.length;i++)labels[i]=episodes.optJSONObject(i).optString("name");
            new AlertDialog.Builder(activity).setTitle("选择集数").setItems(labels,(x,k)->{JSONObject episode=episodes.optJSONObject(k);close();playback.play(item.optString("name")+" · "+episode.optString("name"),episode.optString("url"));}).setNegativeButton("返回",null).show();
        }).setNegativeButton("返回",null).show();
    }
    private void error(int gen,Exception e){activity.runOnUiThread(()->{if(valid(gen))status.setText("加载失败："+e.getMessage()+"。可按搜索重试或换源。");});}
}
