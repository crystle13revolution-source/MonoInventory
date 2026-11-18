package com.mono.inventory;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.List;

public class MainActivity extends Activity {
    private InventoryDb db;
    private LinearLayout rows;
    private TextView count, alerts;
    private String keyword="";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);db=new InventoryDb(this);
        LinearLayout root=Ui.vertical(this);root.setBackgroundColor(Ui.BG);Ui.pad(root,this,18,22,18,10);setContentView(root);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView brand=new ImageView(this);brand.setImageResource(R.drawable.logo_app);
        heading.addView(brand,Ui.lp(this,42,42,10));
        TextView title=Ui.text(this,"物集",30,true);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button add=new Button(this);add.setText("＋ 添加物品");add.setTextColor(Color.WHITE);add.setAllCaps(false);add.setBackground(Ui.shape(Ui.GREEN,12,this));Ui.pad(add,this,14,8,14,8);
        heading.addView(add);root.addView(heading,Ui.lp(this,-1,-2,18));
        add.setOnClickListener(v->startActivity(new Intent(this,EditItemActivity.class)));
        TextView subtitle=Ui.text(this,"自己的东西，心中有数。",14,false);subtitle.setTextColor(Ui.MUTED);root.addView(subtitle,Ui.lp(this,-1,-2,18));
        EditText search=new EditText(this);search.setSingleLine();search.setTextSize(15);search.setHint("搜索物品 / 分类 / 标签 / 位置");
        search.setBackground(Ui.shape(Color.WHITE,12,this));Ui.pad(search,this,16,11,16,11);
        root.addView(search,Ui.lp(this,-1,-2,16));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int c,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){keyword=s.toString();render();}public void afterTextChanged(Editable e){}});
        count=Ui.text(this,"",16,true);root.addView(count,Ui.lp(this,-1,-2,6));
        alerts=Ui.text(this,"",13,false);alerts.setTextColor(Ui.MUTED);root.addView(alerts,Ui.lp(this,-1,-2,10));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        rows=Ui.vertical(this);scroll.addView(rows);render();
        TextView footer=Ui.text(this,"本地模式 · 数据只保存在这台设备",12,false);footer.setTextColor(Ui.MUTED);footer.setGravity(Gravity.CENTER);root.addView(footer,Ui.lp(this,-1,-2,4));
    }
    @Override protected void onResume(){super.onResume();if(rows!=null)render();}
    private void render() {
        List<Item> items=db.all(keyword);rows.removeAllViews();count.setText("物品清单 · "+items.size()+" 件记录");
        int expiring=0,expired=0;LocalDate now=LocalDate.now();
        for(Item i:db.all("")) {
            if(!i.expiryDate.isEmpty())try{LocalDate expiry=LocalDate.parse(i.expiryDate);if(expiry.isBefore(now))expired++;else if(!expiry.isAfter(now.plusDays(7)))expiring++;}catch(Exception ignored){}
        }
        alerts.setText("已过期 "+expired+"  ·  7 天内到期 "+expiring);
        if(items.isEmpty()){TextView empty=Ui.text(this,"还没有物品。点击右上角添加第一件吧。",15,false);empty.setTextColor(Ui.MUTED);Ui.pad(empty,this,14,35,14,14);rows.addView(empty);return;}
        for(Item i:items){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(Ui.shape(Color.WHITE,14,this));Ui.pad(card,this,12,12,12,12);
            ImageView cover=new ImageView(this);cover.setBackground(Ui.shape(Ui.BG,10,this));cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            PhotoStore.show(cover,i.photoPath);card.addView(cover,Ui.lp(this,76,76,0));
            LinearLayout body=Ui.vertical(this);Ui.pad(body,this,12,0,2,0);card.addView(body,new LinearLayout.LayoutParams(0,-2,1));
            TextView name=Ui.text(this,i.name,17,true);body.addView(name);
            TextView meta=Ui.text(this,i.category+" · "+i.location,13,false);meta.setTextColor(Ui.MUTED);body.addView(meta);
            try{org.json.JSONArray ts=new org.json.JSONArray(i.tags);java.util.ArrayList<String> labels=new java.util.ArrayList<>();for(int t=0;t<ts.length();t++)labels.add("#"+ts.getString(t));if(!labels.isEmpty()){TextView tagLine=Ui.text(this,String.join("  ",labels),12,false);tagLine.setTextColor(Ui.GREEN);body.addView(tagLine);}}catch(org.json.JSONException ignored){}
            String qty=(i.quantity==Math.rint(i.quantity))?String.valueOf((long)i.quantity):String.valueOf(i.quantity);
            String details=i.status+"  ·  "+qty+i.unit;
            if(!i.expiryDate.isEmpty())details+="  ·  到期 "+i.expiryDate;
            TextView detail=Ui.text(this,details,12,false);detail.setTextColor(Ui.GREEN);body.addView(detail);
            card.setOnClickListener(v->{Intent intent=new Intent(this,EditItemActivity.class);intent.putExtra("item_id",i.id);startActivity(intent);});
            rows.addView(card,Ui.lp(this,-1,-2,10));
        }
    }
}
